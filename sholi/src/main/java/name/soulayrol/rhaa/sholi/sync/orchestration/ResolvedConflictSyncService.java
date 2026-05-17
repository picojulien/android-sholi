package name.soulayrol.rhaa.sholi.sync.orchestration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import name.soulayrol.rhaa.sholi.sync.document.ModifiedBy;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocument;
import name.soulayrol.rhaa.sholi.sync.document.SyncItem;
import name.soulayrol.rhaa.sholi.sync.merge.ConflictChoice;
import name.soulayrol.rhaa.sholi.sync.merge.ConflictResolution;
import name.soulayrol.rhaa.sholi.sync.merge.SyncConflict;
import name.soulayrol.rhaa.sholi.sync.merge.SyncMetadataStore;
import name.soulayrol.rhaa.sholi.sync.merge.SyncStateRecorder;
import name.soulayrol.rhaa.sholi.sync.webdav.LocalSyncDocumentStore;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavEtag;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavPutResult;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavSyncResult;

public final class ResolvedConflictSyncService {

    private final LocalSyncDocumentStore localStore;
    private final SyncMetadataStore metadataStore;
    private final ConflictUploader uploader;

    public ResolvedConflictSyncService(
            LocalSyncDocumentStore localStore,
            SyncMetadataStore metadataStore,
            ConflictUploader uploader) {
        if (localStore == null) {
            throw new IllegalArgumentException("localStore must not be null");
        }
        if (metadataStore == null) {
            throw new IllegalArgumentException("metadataStore must not be null");
        }
        if (uploader == null) {
            throw new IllegalArgumentException("uploader must not be null");
        }
        this.localStore = localStore;
        this.metadataStore = metadataStore;
        this.uploader = uploader;
    }

    public SyncConflict choose(String syncId, ConflictChoice choice) {
        if (syncId == null || syncId.length() == 0) {
            throw new IllegalArgumentException("syncId must not be empty");
        }
        if (choice == null) {
            throw new IllegalArgumentException("choice must not be null");
        }
        List<SyncConflict> conflicts = metadataStore.loadConflicts();
        ArrayList<SyncConflict> updated = new ArrayList<SyncConflict>(conflicts.size());
        SyncConflict resolved = null;
        for (SyncConflict conflict: conflicts) {
            if (syncId.equals(conflict.getSyncId())) {
                resolved = ConflictResolution.markResolved(conflict, choice);
                updated.add(resolved);
            } else {
                updated.add(conflict);
            }
        }
        if (resolved == null) {
            throw new IllegalArgumentException("No sync conflict found for sync_id " + syncId);
        }
        metadataStore.replaceConflicts(updated);
        return resolved;
    }

    public UploadPlan prepareUpload() {
        List<SyncConflict> conflicts = metadataStore.loadConflicts();
        if (conflicts.isEmpty()) {
            return UploadPlan.noConflicts();
        }
        for (SyncConflict conflict: conflicts) {
            if (SyncConflict.STATUS_UNRESOLVED.equals(conflict.getStatus())) {
                return UploadPlan.blocked(conflicts);
            }
        }

        List<SyncConflict> repairedConflicts = markMissingSideResolutionsUnresolved(conflicts);
        if (repairedConflicts != null) {
            metadataStore.replaceConflicts(repairedConflicts);
            return UploadPlan.blocked(repairedConflicts);
        }

        String remoteVersionMarker = sharedRemoteVersionMarker(conflicts);
        if (!WebDavEtag.classify(remoteVersionMarker).isStrong()) {
            return UploadPlan.confirmationRequired(conflicts);
        }
        SyncDocument currentDocument = localStore.loadCurrentDocument();
        if (!conflictLocalSnapshotsMatch(currentDocument, conflicts)) {
            return UploadPlan.localChanged(conflicts);
        }
        return UploadPlan.ready(
                buildResolvedDocument(currentDocument, conflicts),
                remoteVersionMarker,
                currentDocument);
    }

    public WebDavSyncResult resumeResolvedConflicts() {
        UploadPlan plan = prepareUpload();
        if (plan.getStatus() == UploadPlan.Status.NO_CONFLICTS) {
            return WebDavSyncResult.status(
                    WebDavSyncResult.Status.UP_TO_DATE,
                    "No resolved synchronization conflicts to upload");
        }
        if (plan.getStatus() == UploadPlan.Status.BLOCKED_UNRESOLVED) {
            return WebDavSyncResult.conflicts(plan.getConflicts());
        }
        if (plan.getStatus() == UploadPlan.Status.CONFIRMATION_REQUIRED) {
            return WebDavSyncResult.status(
                    WebDavSyncResult.Status.CONFIRMATION_REQUIRED,
                    "Resolved conflicts cannot be uploaded safely without confirmation");
        }
        if (plan.getStatus() == UploadPlan.Status.LOCAL_CHANGED) {
            return localChanged();
        }

        WebDavSyncResult staleLocalResult = validateLocalUnchanged(plan.getExpectedDocument());
        if (staleLocalResult != null) {
            return staleLocalResult;
        }

        WebDavPutResult put = uploader.uploadResolvedDocument(
                plan.getDocument(),
                plan.getRemoteVersionMarker());
        if (put.getStatus() == WebDavPutResult.Status.SUCCESS) {
            WebDavSyncResult applyResult = applyResolvedDocumentIfCurrent(
                    plan.getExpectedDocument(),
                    plan.getDocument());
            if (applyResult != null) {
                return applyResult;
            }
            SyncStateRecorder.recordSuccessfulUpload(
                    metadataStore,
                    plan.getDocument(),
                    markerFrom(put.getEtag()));
            return WebDavSyncResult.status(
                    WebDavSyncResult.Status.UPDATED_REMOTE,
                    "Uploaded resolved synchronization conflicts");
        }
        if (put.getStatus() == WebDavPutResult.Status.PRECONDITION_FAILED) {
            return WebDavSyncResult.status(
                    WebDavSyncResult.Status.PRECONDITION_FAILED,
                    "Remote sync document changed before resolved conflicts could upload");
        }
        if (put.getStatus() == WebDavPutResult.Status.AUTH_ERROR) {
            return WebDavSyncResult.status(WebDavSyncResult.Status.AUTH_ERROR, put.getMessage());
        }
        if (put.getStatus() == WebDavPutResult.Status.NETWORK_ERROR) {
            return WebDavSyncResult.status(WebDavSyncResult.Status.NETWORK_ERROR, put.getMessage());
        }
        return WebDavSyncResult.status(WebDavSyncResult.Status.SERVER_ERROR, put.getMessage());
    }

    private WebDavSyncResult validateLocalUnchanged(SyncDocument expectedDocument) {
        try {
            if (!localStore.isCurrentDocument(expectedDocument)) {
                return localChanged();
            }
            return null;
        } catch (RuntimeException e) {
            return WebDavSyncResult.status(
                    WebDavSyncResult.Status.LOCAL_APPLY_ERROR,
                    "Local resolved sync document validation failed");
        }
    }

    private WebDavSyncResult applyResolvedDocumentIfCurrent(
            SyncDocument expectedDocument,
            SyncDocument document) {
        try {
            if (!localStore.applyDocumentIfCurrent(expectedDocument, document)) {
                return localChanged();
            }
            return null;
        } catch (RuntimeException e) {
            return WebDavSyncResult.status(
                    WebDavSyncResult.Status.LOCAL_APPLY_ERROR,
                    "Local resolved sync document apply failed");
        }
    }

    private static SyncDocument buildResolvedDocument(
            SyncDocument currentDocument,
            List<SyncConflict> conflicts) {
        if (currentDocument == null) {
            throw new IllegalStateException("Current local sync document is not available");
        }
        LinkedHashMap<String, SyncItem> bySyncId = new LinkedHashMap<String, SyncItem>();
        for (SyncItem item: currentDocument.getItems()) {
            bySyncId.put(item.getSyncId(), item);
        }
        for (SyncConflict conflict: conflicts) {
            bySyncId.put(conflict.getSyncId(), resolvedItem(conflict));
        }
        return new SyncDocument(new ArrayList<SyncItem>(bySyncId.values()));
    }

    private static boolean conflictLocalSnapshotsMatch(
            SyncDocument currentDocument,
            List<SyncConflict> conflicts) {
        if (currentDocument == null) {
            throw new IllegalStateException("Current local sync document is not available");
        }
        LinkedHashMap<String, SyncItem> bySyncId = new LinkedHashMap<String, SyncItem>();
        for (SyncItem item: currentDocument.getItems()) {
            bySyncId.put(item.getSyncId(), item);
        }
        for (SyncConflict conflict: conflicts) {
            if (!sameItem(bySyncId.get(conflict.getSyncId()), conflict.getLocalItem())) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameItem(SyncItem left, SyncItem right) {
        if (left == null || right == null) {
            return left == right;
        }
        return sameString(left.getSyncId(), right.getSyncId())
                && sameString(left.getName(), right.getName())
                && left.getStatus() == right.getStatus()
                && left.isDeleted() == right.isDeleted()
                && left.getModifiedAt() == right.getModifiedAt()
                && sameModifiedBy(left.getModifiedBy(), right.getModifiedBy());
    }

    private static boolean sameModifiedBy(ModifiedBy left, ModifiedBy right) {
        if (left == null || right == null) {
            return left == right;
        }
        return sameString(left.getName(), right.getName())
                && sameString(left.getClientId(), right.getClientId());
    }

    private static boolean sameString(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static SyncItem resolvedItem(SyncConflict conflict) {
        if (SyncConflict.STATUS_RESOLVED_LOCAL.equals(conflict.getStatus())) {
            return ConflictResolution.resolve(conflict, ConflictChoice.LOCAL);
        }
        if (SyncConflict.STATUS_RESOLVED_REMOTE.equals(conflict.getStatus())) {
            return ConflictResolution.resolve(conflict, ConflictChoice.REMOTE);
        }
        throw new IllegalStateException("unresolved conflict for sync_id " + conflict.getSyncId());
    }

    private static List<SyncConflict> markMissingSideResolutionsUnresolved(List<SyncConflict> conflicts) {
        ArrayList<SyncConflict> updated = null;
        for (int i = 0; i < conflicts.size(); ++i) {
            SyncConflict conflict = conflicts.get(i);
            SyncConflict replacement = conflict;
            if (SyncConflict.STATUS_RESOLVED_LOCAL.equals(conflict.getStatus())
                    && conflict.getLocalItem() == null) {
                replacement = conflict.withStatus(SyncConflict.STATUS_UNRESOLVED);
            } else if (SyncConflict.STATUS_RESOLVED_REMOTE.equals(conflict.getStatus())
                    && conflict.getRemoteItem() == null) {
                replacement = conflict.withStatus(SyncConflict.STATUS_UNRESOLVED);
            }
            if (replacement != conflict && updated == null) {
                updated = new ArrayList<SyncConflict>(conflicts.size());
                for (int j = 0; j < i; ++j) {
                    updated.add(conflicts.get(j));
                }
            }
            if (updated != null) {
                updated.add(replacement);
            }
        }
        return updated;
    }

    private static String sharedRemoteVersionMarker(List<SyncConflict> conflicts) {
        String marker = null;
        boolean markerInitialized = false;
        for (SyncConflict conflict: conflicts) {
            String conflictMarker = conflict.getRemoteVersionMarker();
            if (!markerInitialized) {
                marker = conflictMarker;
                markerInitialized = true;
            } else if (marker == null ? conflictMarker != null : !marker.equals(conflictMarker)) {
                return null;
            }
        }
        return marker;
    }

    private static String markerFrom(WebDavEtag etag) {
        if (etag == null || etag.isMissing()) {
            return null;
        }
        return etag.getValue();
    }

    private static WebDavSyncResult localChanged() {
        return WebDavSyncResult.status(
                WebDavSyncResult.Status.LOCAL_CHANGED,
                "Local items changed during resolved conflict synchronization; please retry");
    }

    public interface ConflictUploader {
        WebDavPutResult uploadResolvedDocument(SyncDocument document, String remoteVersionMarker);
    }

    public static final class UploadPlan {

        public enum Status {
            READY,
            BLOCKED_UNRESOLVED,
            CONFIRMATION_REQUIRED,
            LOCAL_CHANGED,
            NO_CONFLICTS
        }

        private final Status status;
        private final SyncDocument document;
        private final SyncDocument expectedDocument;
        private final String remoteVersionMarker;
        private final List<SyncConflict> conflicts;

        private UploadPlan(
                Status status,
                SyncDocument document,
                SyncDocument expectedDocument,
                String remoteVersionMarker,
                List<SyncConflict> conflicts) {
            this.status = status;
            this.document = document;
            this.expectedDocument = expectedDocument;
            this.remoteVersionMarker = remoteVersionMarker;
            this.conflicts = conflicts == null
                    ? new ArrayList<SyncConflict>()
                    : new ArrayList<SyncConflict>(conflicts);
        }

        static UploadPlan ready(
                SyncDocument document,
                String remoteVersionMarker,
                SyncDocument expectedDocument) {
            return new UploadPlan(Status.READY, document, expectedDocument, remoteVersionMarker, null);
        }

        static UploadPlan blocked(List<SyncConflict> conflicts) {
            return new UploadPlan(Status.BLOCKED_UNRESOLVED, null, null, null, conflicts);
        }

        static UploadPlan confirmationRequired(List<SyncConflict> conflicts) {
            return new UploadPlan(Status.CONFIRMATION_REQUIRED, null, null, null, conflicts);
        }

        static UploadPlan localChanged(List<SyncConflict> conflicts) {
            return new UploadPlan(Status.LOCAL_CHANGED, null, null, null, conflicts);
        }

        static UploadPlan noConflicts() {
            return new UploadPlan(Status.NO_CONFLICTS, null, null, null, null);
        }

        public Status getStatus() {
            return status;
        }

        public SyncDocument getDocument() {
            return document;
        }

        SyncDocument getExpectedDocument() {
            return expectedDocument;
        }

        public String getRemoteVersionMarker() {
            return remoteVersionMarker;
        }

        public List<SyncConflict> getConflicts() {
            return new ArrayList<SyncConflict>(conflicts);
        }
    }
}
