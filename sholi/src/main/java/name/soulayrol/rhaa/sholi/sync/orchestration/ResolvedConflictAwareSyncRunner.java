package name.soulayrol.rhaa.sholi.sync.orchestration;

import java.util.List;

import name.soulayrol.rhaa.sholi.sync.merge.SyncConflict;
import name.soulayrol.rhaa.sholi.sync.merge.SyncMetadataStore;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavSyncResult;

public final class ResolvedConflictAwareSyncRunner {

    private final SyncMetadataStore metadataStore;
    private final NormalSyncOperation normalSyncOperation;
    private final ResolvedConflictResumeOperation resolvedConflictResumeOperation;

    public ResolvedConflictAwareSyncRunner(
            SyncMetadataStore metadataStore,
            NormalSyncOperation normalSyncOperation,
            ResolvedConflictResumeOperation resolvedConflictResumeOperation) {
        if (metadataStore == null) {
            throw new IllegalArgumentException("metadataStore must not be null");
        }
        if (normalSyncOperation == null) {
            throw new IllegalArgumentException("normalSyncOperation must not be null");
        }
        if (resolvedConflictResumeOperation == null) {
            throw new IllegalArgumentException("resolvedConflictResumeOperation must not be null");
        }
        this.metadataStore = metadataStore;
        this.normalSyncOperation = normalSyncOperation;
        this.resolvedConflictResumeOperation = resolvedConflictResumeOperation;
    }

    public WebDavSyncResult synchronizeOrResume() {
        List<SyncConflict> conflicts = metadataStore.loadConflicts();
        if (conflicts.isEmpty()) {
            return normalSyncOperation.synchronize();
        }
        if (hasUnresolved(conflicts)) {
            return WebDavSyncResult.conflicts(conflicts);
        }

        WebDavSyncResult result = resolvedConflictResumeOperation.resumeResolvedConflicts();
        if (isStaleResolvedConflictResult(result)) {
            return normalSyncOperation.synchronize();
        }
        return result;
    }

    private static boolean hasUnresolved(List<SyncConflict> conflicts) {
        for (SyncConflict conflict: conflicts) {
            if (SyncConflict.STATUS_UNRESOLVED.equals(conflict.getStatus())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStaleResolvedConflictResult(WebDavSyncResult result) {
        return result.getStatus() == WebDavSyncResult.Status.PRECONDITION_FAILED
                || result.getStatus() == WebDavSyncResult.Status.LOCAL_CHANGED;
    }

    public interface NormalSyncOperation {
        WebDavSyncResult synchronize();
    }

    public interface ResolvedConflictResumeOperation {
        WebDavSyncResult resumeResolvedConflicts();
    }
}
