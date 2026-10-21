/*
 * ShoLi, a simple tool to produce short (shopping) lists.
 *
 * Copyright (C) 2013  David Soulayrol
 *
 * ShoLi is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ShoLi is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package name.soulayrol.rhaa.sholi;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import name.soulayrol.rhaa.sholi.sync.android.AndroidWebDavSyncRunner;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavSyncResult;


public class MainActivity extends Activity {

    private static final long AUTOMATIC_ERROR_NOTIFICATION_INTERVAL_MILLIS = 5L * 60L * 1000L;

    private static final Object SYNCHRONIZATION_GUARD = new Object();
    private static boolean synchronizationInProgress;

    private ForegroundWebDavAutoSyncController foregroundAutoSyncController;
    private boolean foregroundAutoSyncResumed;
    private WebDavSyncResult.Status lastAutomaticErrorStatus;
    private long nextAutomaticErrorNotificationAtMillis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        foregroundAutoSyncController = new ForegroundWebDavAutoSyncController(
                new ForegroundWebDavAutoSyncController.Delegate() {
                    @Override
                    public boolean isAutomaticSynchronizationAllowed() {
                        return MainActivity.this.isAutomaticSynchronizationAllowed();
                    }

                    @Override
                    public void startAutomaticSynchronization() {
                        MainActivity.this.startAutomaticSynchronization();
                    }
                });

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        if (savedInstanceState == null && findViewById(R.id.container) != null) {
            getFragmentManager().beginTransaction().add(R.id.container, new CheckingFragment()).commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startForegroundAutoSyncIfMainListVisible();
    }

    @Override
    protected void onPause() {
        stopForegroundAutoSync();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        shutdownForegroundAutoSync();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_about:
                displayAboutDialog();
                return true;
            case R.id.action_data_overview:
                startActivity(new Intent(this, DataOverviewActivity.class));
                return true;
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.action_sync:
                startSynchronization();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigateUp() {
        getFragmentManager().popBackStack();
        getActionBar().setDisplayHomeAsUpEnabled(false);
        return true;
    }

    private void displayAboutDialog() {
        View view = getLayoutInflater().inflate(R.layout.about_dlg, null, false);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            TextView text = (TextView) view.findViewById(R.id.version_text);
            text.setText(getString(R.string.dialog_about_version,
                    info.versionName, info.versionCode));
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        builder.setIcon(android.R.drawable.ic_dialog_info)
                .setTitle(R.string.dialog_about_title)
                .setView(view)
                .setNeutralButton(android.R.string.ok, null)
                .create()
                .show();
    }

    private void startSynchronization() {
        startSynchronization(SynchronizationTrigger.MANUAL);
    }

    private void startAutomaticSynchronization() {
        startSynchronization(SynchronizationTrigger.AUTOMATIC);
    }

    private void startSynchronization(final SynchronizationTrigger trigger) {
        if (trigger == SynchronizationTrigger.AUTOMATIC && !isAutomaticSynchronizationAllowed()) {
            return;
        }
        if (!tryAcquireSynchronizationSlot()) {
            if (trigger == SynchronizationTrigger.MANUAL) {
                Toast.makeText(this, R.string.sync_already_running, Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (trigger == SynchronizationTrigger.MANUAL) {
            Toast.makeText(this, R.string.sync_started, Toast.LENGTH_SHORT).show();
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final WebDavSyncResult result;
                try {
                    result = runSynchronization();
                } finally {
                    releaseSynchronizationSlot();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onSynchronizationResult(trigger, result);
                    }
                });
            }
        }, synchronizationThreadName(trigger)).start();
    }

    private WebDavSyncResult runSynchronization() {
        try {
            return new AndroidWebDavSyncRunner(MainActivity.this).synchronizeOrResume();
        } catch (RuntimeException e) {
            return WebDavSyncResult.status(
                    WebDavSyncResult.Status.CONFIGURATION_ERROR,
                    "WebDAV synchronization failed before it could start");
        }
    }

    private void onSynchronizationResult(SynchronizationTrigger trigger, WebDavSyncResult result) {
        if (trigger == SynchronizationTrigger.AUTOMATIC) {
            onAutomaticSynchronizationResult(result);
        } else if (!isFinishing()) {
            onManualSynchronizationResult(result);
        }
    }

    private void onManualSynchronizationResult(WebDavSyncResult result) {
        if (result.getStatus() == WebDavSyncResult.Status.CONFLICTS) {
            Toast.makeText(this, R.string.sync_conflicts_found, Toast.LENGTH_LONG).show();
            SyncConflictDialogFragment.showNext(getFragmentManager(), conflictMessage(result));
            return;
        }
        if (result.isSuccess()) {
            refreshVisibleList();
        }
        Toast.makeText(this, result.getMessage(), Toast.LENGTH_LONG).show();
    }

    private void onAutomaticSynchronizationResult(WebDavSyncResult result) {
        if (!isAutomaticSynchronizationAllowed()) {
            return;
        }
        if (isSilentAutomaticResult(result)) {
            resetAutomaticErrorSuppression();
            return;
        }
        if (result.getStatus() == WebDavSyncResult.Status.CONFLICTS) {
            if (!SyncConflictDialogFragment.isShowing(getFragmentManager())) {
                SyncConflictDialogFragment.showNext(getFragmentManager(), conflictMessage(result));
            }
            return;
        }
        if (result.isSuccess()) {
            resetAutomaticErrorSuppression();
            refreshVisibleList();
            return;
        }
        if (shouldShowAutomaticError(result, System.currentTimeMillis())) {
            Toast.makeText(this, result.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean isSilentAutomaticResult(WebDavSyncResult result) {
        return result.getStatus() == WebDavSyncResult.Status.UP_TO_DATE;
    }

    private boolean shouldShowAutomaticError(WebDavSyncResult result, long nowMillis) {
        if (lastAutomaticErrorStatus == result.getStatus()
                && nowMillis < nextAutomaticErrorNotificationAtMillis) {
            return false;
        }
        lastAutomaticErrorStatus = result.getStatus();
        nextAutomaticErrorNotificationAtMillis = nowMillis + AUTOMATIC_ERROR_NOTIFICATION_INTERVAL_MILLIS;
        return true;
    }

    private void resetAutomaticErrorSuppression() {
        lastAutomaticErrorStatus = null;
        nextAutomaticErrorNotificationAtMillis = 0L;
    }

    private boolean tryAcquireSynchronizationSlot() {
        synchronized (SYNCHRONIZATION_GUARD) {
            if (synchronizationInProgress) {
                return false;
            }
            synchronizationInProgress = true;
            return true;
        }
    }

    private void releaseSynchronizationSlot() {
        synchronized (SYNCHRONIZATION_GUARD) {
            synchronizationInProgress = false;
        }
    }

    private String synchronizationThreadName(SynchronizationTrigger trigger) {
        return trigger == SynchronizationTrigger.AUTOMATIC
                ? "sholi-automatic-webdav-sync"
                : "sholi-webdav-sync";
    }

    private void startForegroundAutoSyncIfMainListVisible() {
        foregroundAutoSyncResumed = true;
        if (foregroundAutoSyncController != null && isMainListUiVisible()) {
            foregroundAutoSyncController.start();
        }
    }

    private void stopForegroundAutoSync() {
        foregroundAutoSyncResumed = false;
        if (foregroundAutoSyncController != null) {
            foregroundAutoSyncController.stop();
        }
    }

    private void shutdownForegroundAutoSync() {
        foregroundAutoSyncResumed = false;
        if (foregroundAutoSyncController != null) {
            foregroundAutoSyncController.shutdown();
            foregroundAutoSyncController = null;
        }
    }

    private boolean isAutomaticSynchronizationAllowed() {
        return foregroundAutoSyncResumed
                && isMainListUiVisible()
                && !SyncConflictDialogFragment.isShowing(getFragmentManager());
    }

    private boolean isMainListUiVisible() {
        return !isFinishing() && findViewById(R.id.container) != null;
    }

    private void refreshVisibleList() {
        Fragment fragment = getFragmentManager().findFragmentById(R.id.container);
        if (fragment instanceof AbstractListFragment
                && fragment.isAdded()
                && fragment.getActivity() == this) {
            ((AbstractListFragment) fragment).refreshList();
        }
    }

    private String conflictMessage(WebDavSyncResult result) {
        String message = result.getMessage();
        if (message != null && message.trim().length() > 0) {
            return message;
        }
        return getString(R.string.sync_conflicts_found);
    }

    private enum SynchronizationTrigger {
        MANUAL,
        AUTOMATIC
    }
}
