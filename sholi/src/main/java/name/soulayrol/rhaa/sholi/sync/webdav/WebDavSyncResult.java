package name.soulayrol.rhaa.sholi.sync.webdav;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import name.soulayrol.rhaa.sholi.sync.merge.SyncConflict;

public final class WebDavSyncResult {

    public enum Status {
        CREATED_REMOTE,
        UPDATED_REMOTE,
        PULLED_REMOTE,
        UP_TO_DATE,
        CONFLICTS,
        CONFIRMATION_REQUIRED,
        PRECONDITION_FAILED,
        AUTH_ERROR,
        NETWORK_ERROR,
        SERVER_ERROR,
        INVALID_REMOTE_DOCUMENT,
        LOCAL_APPLY_ERROR
    }

    private final Status status;
    private final String message;
    private final List<SyncConflict> conflicts;

    private WebDavSyncResult(Status status, String message, List<SyncConflict> conflicts) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (conflicts == null) {
            throw new IllegalArgumentException("conflicts must not be null");
        }
        this.status = status;
        this.message = WebDavSafeText.message(message);
        this.conflicts = Collections.unmodifiableList(new ArrayList<SyncConflict>(conflicts));
    }

    public static WebDavSyncResult status(Status status, String message) {
        return new WebDavSyncResult(status, message, Collections.<SyncConflict>emptyList());
    }

    public static WebDavSyncResult conflicts(List<SyncConflict> conflicts) {
        return new WebDavSyncResult(
                Status.CONFLICTS,
                "Synchronization stopped with unresolved conflicts",
                conflicts);
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public List<SyncConflict> getConflicts() {
        return conflicts;
    }

    public boolean isSuccess() {
        return status == Status.CREATED_REMOTE
                || status == Status.UPDATED_REMOTE
                || status == Status.PULLED_REMOTE
                || status == Status.UP_TO_DATE;
    }

    @Override
    public String toString() {
        return "WebDavSyncResult{status=" + status
                + ", message='" + WebDavSafeText.message(message) + '\''
                + ", conflictCount=" + conflicts.size()
                + '}';
    }
}
