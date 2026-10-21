package name.soulayrol.rhaa.sholi;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class ForegroundWebDavAutoSyncController {

    static final long FOREGROUND_SYNC_INTERVAL_SECONDS = 30L;

    private final Delegate delegate;
    private final Handler mainHandler;
    private final ScheduledExecutorService scheduler;
    private final Object lock = new Object();

    private ScheduledFuture<?> scheduledFuture;
    private boolean started;
    private boolean shutdown;

    public ForegroundWebDavAutoSyncController(Delegate delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "sholi-foreground-webdav-auto-sync");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public void start() {
        synchronized (lock) {
            if (shutdown || started) {
                return;
            }
            started = true;
            scheduledFuture = scheduler.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    tick();
                }
            }, 0L, FOREGROUND_SYNC_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
    }

    public void stop() {
        synchronized (lock) {
            started = false;
            if (scheduledFuture != null) {
                scheduledFuture.cancel(false);
                scheduledFuture = null;
            }
        }
    }

    public void shutdown() {
        stop();
        synchronized (lock) {
            shutdown = true;
        }
        scheduler.shutdownNow();
    }

    public boolean isStarted() {
        synchronized (lock) {
            return started;
        }
    }

    private void tick() {
        if (!isStarted()) {
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!isStarted() || !delegate.isAutomaticSynchronizationAllowed()) {
                    return;
                }
                delegate.startAutomaticSynchronization();
            }
        });
    }

    public interface Delegate {

        boolean isAutomaticSynchronizationAllowed();

        void startAutomaticSynchronization();
    }
}
