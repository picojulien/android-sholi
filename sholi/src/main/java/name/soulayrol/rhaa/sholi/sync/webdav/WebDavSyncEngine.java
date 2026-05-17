package name.soulayrol.rhaa.sholi.sync.webdav;

import java.util.List;

import name.soulayrol.rhaa.sholi.sync.document.SyncDocument;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocumentJson;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocumentParseException;
import name.soulayrol.rhaa.sholi.sync.merge.SyncConflict;
import name.soulayrol.rhaa.sholi.sync.merge.SyncMergeResult;
import name.soulayrol.rhaa.sholi.sync.merge.SyncMerger;
import name.soulayrol.rhaa.sholi.sync.merge.SyncMetadataStore;
import name.soulayrol.rhaa.sholi.sync.merge.SyncStateRecorder;

public final class WebDavSyncEngine {

    private final WebDavClient client;
    private final String remoteUrl;
    private final LocalSyncDocumentStore localStore;
    private final SyncMetadataStore metadataStore;

    public WebDavSyncEngine(
            WebDavClient client,
            String remoteUrl,
            LocalSyncDocumentStore localStore,
            SyncMetadataStore metadataStore) {
        if (client == null) {
            throw new IllegalArgumentException("client must not be null");
        }
        if (remoteUrl == null || remoteUrl.length() == 0) {
            throw new IllegalArgumentException("remoteUrl must not be empty");
        }
        if (localStore == null) {
            throw new IllegalArgumentException("localStore must not be null");
        }
        if (metadataStore == null) {
            throw new IllegalArgumentException("metadataStore must not be null");
        }
        this.client = client;
        this.remoteUrl = remoteUrl;
        this.localStore = localStore;
        this.metadataStore = metadataStore;
    }

    public WebDavSyncResult synchronize() {
        return synchronize(System.currentTimeMillis());
    }

    public WebDavSyncResult synchronize(long conflictTimestamp) {
        if (conflictTimestamp < 0L) {
            throw new IllegalArgumentException("conflictTimestamp must not be negative");
        }
        List<SyncConflict> existingConflicts = metadataStore.loadConflicts();
        for (SyncConflict conflict: existingConflicts) {
            if (SyncConflict.STATUS_UNRESOLVED.equals(conflict.getStatus())) {
                return WebDavSyncResult.conflicts(existingConflicts);
            }
        }

        SyncDocument localDocument = localStore.loadCurrentDocument();
        if (localDocument == null) {
            throw new IllegalStateException("local document must not be null");
        }
        SyncDocument baselineDocument = metadataStore.loadBaselineDocument();
        String lastRemoteMarker = metadataStore.loadRemoteVersionMarker();

        WebDavMetadataResult metadata = client.head(remoteUrl);
        if (metadata.getStatus() == WebDavMetadataResult.Status.MISSING) {
            return createRemote(localDocument, baselineDocument, conflictTimestamp);
        }
        if (metadata.getStatus() != WebDavMetadataResult.Status.PRESENT) {
            return fromMetadataError(metadata);
        }
        return synchronizeExistingRemote(
                metadata,
                localDocument,
                baselineDocument,
                lastRemoteMarker,
                conflictTimestamp);
    }

    private WebDavSyncResult createRemote(
            SyncDocument localDocument,
            SyncDocument baselineDocument,
            long conflictTimestamp) {
        WebDavPutResult put = client.putIfAbsent(remoteUrl, SyncDocumentJson.serialize(localDocument));
        if (put.getStatus() == WebDavPutResult.Status.SUCCESS) {
            SyncStateRecorder.recordSuccessfulUpload(
                    metadataStore,
                    localDocument,
                    markerFrom(put.getEtag()));
            return WebDavSyncResult.status(
                    WebDavSyncResult.Status.CREATED_REMOTE,
                    "Created remote sync document");
        }
        if (put.getStatus() == WebDavPutResult.Status.PRECONDITION_FAILED) {
            return recoverFromPreconditionFailure(localDocument, baselineDocument, conflictTimestamp);
        }
        return fromPutError(put);
    }

    private WebDavSyncResult synchronizeExistingRemote(
            WebDavMetadataResult metadata,
            SyncDocument localDocument,
            SyncDocument baselineDocument,
            String lastRemoteMarker,
            long conflictTimestamp) {
        WebDavEtag remoteEtag = metadata.getEtag();
        if (remoteEtag.isStrong() && sameStrongMarker(remoteEtag.getValue(), lastRemoteMarker)) {
            if (sameDocument(localDocument, baselineDocument)) {
                return WebDavSyncResult.status(WebDavSyncResult.Status.UP_TO_DATE, "No sync changes detected");
            }
            return uploadWithIfMatch(
                    localDocument,
                    remoteEtag.getValue(),
                    false,
                    true,
                    localDocument,
                    baselineDocument,
                    conflictTimestamp);
        }

        RemoteSnapshot snapshot = downloadRemote(metadata);
        if (snapshot.result != null) {
            return snapshot.result;
        }
        if (snapshot.missing) {
            return createRemote(localDocument, baselineDocument, conflictTimestamp);
        }

        String safeUploadMarker = snapshot.safeUploadMarker;
        if (safeUploadMarker == null) {
            return analyzeWithoutSafeUploadMarker(
                    localDocument,
                    baselineDocument,
                    snapshot,
                    conflictTimestamp);
        }

        return analyzeWithSafeUploadMarker(
                localDocument,
                baselineDocument,
                snapshot,
                safeUploadMarker,
                conflictTimestamp,
                true);
    }

    private WebDavSyncResult analyzeWithSafeUploadMarker(
            SyncDocument localDocument,
            SyncDocument baselineDocument,
            RemoteSnapshot snapshot,
            String safeUploadMarker,
            long conflictTimestamp,
            boolean retryOnPrecondition) {
        if (baselineDocument != null && sameDocument(snapshot.document, baselineDocument)) {
            if (sameDocument(localDocument, baselineDocument)) {
                SyncStateRecorder.recordSuccessfulPullOnly(
                        metadataStore,
                        snapshot.document,
                        markerFrom(snapshot.etag));
                return WebDavSyncResult.status(
                        WebDavSyncResult.Status.UP_TO_DATE,
                        "Remote sync document matches baseline");
            }
            return uploadWithIfMatch(
                    localDocument,
                    safeUploadMarker,
                    false,
                    retryOnPrecondition,
                    localDocument,
                    baselineDocument,
                    conflictTimestamp);
        }

        SyncMergeResult merge = SyncMerger.merge(
                baselineDocument,
                localDocument,
                snapshot.document,
                markerFrom(snapshot.etag),
                conflictTimestamp);
        if (merge.hasConflicts()) {
            SyncStateRecorder.persistConflicts(metadataStore, merge.getConflicts());
            return WebDavSyncResult.conflicts(merge.getConflicts());
        }

        SyncDocument mergedDocument = merge.getMergedDocument();
        if (sameDocument(mergedDocument, snapshot.document)) {
            return recordPullOnly(mergedDocument, markerFrom(snapshot.etag), !sameDocument(localDocument, mergedDocument));
        }
        return uploadWithIfMatch(
                mergedDocument,
                safeUploadMarker,
                !sameDocument(localDocument, mergedDocument),
                retryOnPrecondition,
                localDocument,
                baselineDocument,
                conflictTimestamp);
    }

    private WebDavSyncResult analyzeWithoutSafeUploadMarker(
            SyncDocument localDocument,
            SyncDocument baselineDocument,
            RemoteSnapshot snapshot,
            long conflictTimestamp) {
        if (baselineDocument == null) {
            if (sameDocument(localDocument, snapshot.document)) {
                SyncStateRecorder.recordSuccessfulPullOnly(
                        metadataStore,
                        snapshot.document,
                        markerFrom(snapshot.etag));
                return WebDavSyncResult.status(
                        WebDavSyncResult.Status.UP_TO_DATE,
                        "Remote sync document already matches local document");
            }
            return confirmationRequired();
        }

        if (sameDocument(snapshot.document, baselineDocument)) {
            if (sameDocument(localDocument, baselineDocument)) {
                SyncStateRecorder.recordSuccessfulPullOnly(
                        metadataStore,
                        snapshot.document,
                        markerFrom(snapshot.etag));
                return WebDavSyncResult.status(
                        WebDavSyncResult.Status.UP_TO_DATE,
                        "Remote sync document matches baseline");
            }
            return confirmationRequired();
        }

        SyncMergeResult merge = SyncMerger.merge(
                baselineDocument,
                localDocument,
                snapshot.document,
                markerFrom(snapshot.etag),
                conflictTimestamp);
        if (merge.hasConflicts()) {
            SyncStateRecorder.persistConflicts(metadataStore, merge.getConflicts());
            return WebDavSyncResult.conflicts(merge.getConflicts());
        }

        SyncDocument mergedDocument = merge.getMergedDocument();
        if (sameDocument(mergedDocument, snapshot.document)) {
            return recordPullOnly(mergedDocument, markerFrom(snapshot.etag), !sameDocument(localDocument, mergedDocument));
        }
        return confirmationRequired();
    }

    private WebDavSyncResult recoverFromPreconditionFailure(
            SyncDocument localDocument,
            SyncDocument baselineDocument,
            long conflictTimestamp) {
        RemoteSnapshot snapshot = downloadRemote(null);
        if (snapshot.result != null) {
            return snapshot.result;
        }
        if (snapshot.missing) {
            return WebDavSyncResult.status(
                    WebDavSyncResult.Status.PRECONDITION_FAILED,
                    "Remote sync document changed during upload");
        }
        if (snapshot.safeUploadMarker == null) {
            return confirmationRequired();
        }
        return analyzeWithSafeUploadMarker(
                localDocument,
                baselineDocument,
                snapshot,
                snapshot.safeUploadMarker,
                conflictTimestamp,
                false);
    }

    private WebDavSyncResult uploadWithIfMatch(
            SyncDocument uploadDocument,
            String safeUploadMarker,
            boolean applyUploadedDocument,
            boolean retryOnPrecondition,
            SyncDocument localDocument,
            SyncDocument baselineDocument,
            long conflictTimestamp) {
        WebDavPutResult put = client.putIfMatch(
                remoteUrl,
                SyncDocumentJson.serialize(uploadDocument),
                safeUploadMarker);
        if (put.getStatus() == WebDavPutResult.Status.SUCCESS) {
            if (applyUploadedDocument) {
                WebDavSyncResult applyResult = applyLocalDocument(uploadDocument);
                if (applyResult != null) {
                    return applyResult;
                }
            }
            SyncStateRecorder.recordSuccessfulUpload(
                    metadataStore,
                    uploadDocument,
                    markerFrom(put.getEtag()));
            return WebDavSyncResult.status(
                    WebDavSyncResult.Status.UPDATED_REMOTE,
                    "Updated remote sync document");
        }
        if (put.getStatus() == WebDavPutResult.Status.PRECONDITION_FAILED) {
            if (retryOnPrecondition) {
                return recoverFromPreconditionFailure(localDocument, baselineDocument, conflictTimestamp);
            }
            return WebDavSyncResult.status(
                    WebDavSyncResult.Status.PRECONDITION_FAILED,
                    "Remote sync document changed during upload retry");
        }
        return fromPutError(put);
    }

    private WebDavSyncResult recordPullOnly(
            SyncDocument document,
            String marker,
            boolean applyDocument) {
        if (applyDocument) {
            WebDavSyncResult applyResult = applyLocalDocument(document);
            if (applyResult != null) {
                return applyResult;
            }
        }
        SyncStateRecorder.recordSuccessfulPullOnly(metadataStore, document, marker);
        return WebDavSyncResult.status(
                WebDavSyncResult.Status.PULLED_REMOTE,
                "Pulled remote sync document");
    }

    private WebDavSyncResult applyLocalDocument(SyncDocument document) {
        try {
            localStore.applyDocument(document);
            return null;
        } catch (RuntimeException e) {
            return WebDavSyncResult.status(
                    WebDavSyncResult.Status.LOCAL_APPLY_ERROR,
                    "Local sync document apply failed");
        }
    }

    private RemoteSnapshot downloadRemote(WebDavMetadataResult metadata) {
        WebDavGetResult get = client.get(remoteUrl);
        if (get.getStatus() == WebDavGetResult.Status.MISSING) {
            return RemoteSnapshot.missing();
        }
        if (get.getStatus() != WebDavGetResult.Status.PRESENT) {
            return RemoteSnapshot.result(fromGetError(get));
        }
        try {
            SyncDocument document = SyncDocumentJson.parse(get.getBody());
            WebDavEtag headEtag = metadata == null ? WebDavEtag.classify(null) : metadata.getEtag();
            WebDavEtag getEtag = get.getEtag();
            return RemoteSnapshot.present(document, getEtag, chooseSafeUploadMarker(headEtag, getEtag));
        } catch (SyncDocumentParseException e) {
            return RemoteSnapshot.result(WebDavSyncResult.status(
                    WebDavSyncResult.Status.INVALID_REMOTE_DOCUMENT,
                    "Remote sync document is invalid"));
        }
    }

    private static String chooseSafeUploadMarker(WebDavEtag headEtag, WebDavEtag getEtag) {
        if (getEtag != null && getEtag.isStrong()) {
            return getEtag.getValue();
        }
        if (headEtag != null && headEtag.isStrong()) {
            return headEtag.getValue();
        }
        return null;
    }

    private static boolean sameStrongMarker(String remoteMarker, String lastRemoteMarker) {
        WebDavEtag last = WebDavEtag.classify(lastRemoteMarker);
        return last.isStrong() && remoteMarker != null && remoteMarker.equals(last.getValue());
    }

    private static boolean sameDocument(SyncDocument left, SyncDocument right) {
        if (left == null || right == null) {
            return left == right;
        }
        return SyncDocumentJson.serialize(left).equals(SyncDocumentJson.serialize(right));
    }

    private static String markerFrom(WebDavEtag etag) {
        if (etag == null || etag.isMissing()) {
            return null;
        }
        return etag.getValue();
    }

    private static WebDavSyncResult confirmationRequired() {
        return WebDavSyncResult.status(
                WebDavSyncResult.Status.CONFIRMATION_REQUIRED,
                "Remote sync document cannot be replaced safely without confirmation");
    }

    private static WebDavSyncResult fromMetadataError(WebDavMetadataResult result) {
        if (result.getStatus() == WebDavMetadataResult.Status.AUTH_ERROR) {
            return WebDavSyncResult.status(WebDavSyncResult.Status.AUTH_ERROR, result.getMessage());
        }
        if (result.getStatus() == WebDavMetadataResult.Status.NETWORK_ERROR) {
            return WebDavSyncResult.status(WebDavSyncResult.Status.NETWORK_ERROR, result.getMessage());
        }
        return WebDavSyncResult.status(WebDavSyncResult.Status.SERVER_ERROR, result.getMessage());
    }

    private static WebDavSyncResult fromGetError(WebDavGetResult result) {
        if (result.getStatus() == WebDavGetResult.Status.AUTH_ERROR) {
            return WebDavSyncResult.status(WebDavSyncResult.Status.AUTH_ERROR, result.getMessage());
        }
        if (result.getStatus() == WebDavGetResult.Status.NETWORK_ERROR) {
            return WebDavSyncResult.status(WebDavSyncResult.Status.NETWORK_ERROR, result.getMessage());
        }
        return WebDavSyncResult.status(WebDavSyncResult.Status.SERVER_ERROR, result.getMessage());
    }

    private static WebDavSyncResult fromPutError(WebDavPutResult result) {
        if (result.getStatus() == WebDavPutResult.Status.AUTH_ERROR) {
            return WebDavSyncResult.status(WebDavSyncResult.Status.AUTH_ERROR, result.getMessage());
        }
        if (result.getStatus() == WebDavPutResult.Status.NETWORK_ERROR) {
            return WebDavSyncResult.status(WebDavSyncResult.Status.NETWORK_ERROR, result.getMessage());
        }
        return WebDavSyncResult.status(WebDavSyncResult.Status.SERVER_ERROR, result.getMessage());
    }

    private static final class RemoteSnapshot {
        private final SyncDocument document;
        private final WebDavEtag etag;
        private final String safeUploadMarker;
        private final boolean missing;
        private final WebDavSyncResult result;

        private RemoteSnapshot(
                SyncDocument document,
                WebDavEtag etag,
                String safeUploadMarker,
                boolean missing,
                WebDavSyncResult result) {
            this.document = document;
            this.etag = etag;
            this.safeUploadMarker = safeUploadMarker;
            this.missing = missing;
            this.result = result;
        }

        static RemoteSnapshot present(SyncDocument document, WebDavEtag etag, String safeUploadMarker) {
            return new RemoteSnapshot(document, etag, safeUploadMarker, false, null);
        }

        static RemoteSnapshot missing() {
            return new RemoteSnapshot(null, WebDavEtag.classify(null), null, true, null);
        }

        static RemoteSnapshot result(WebDavSyncResult result) {
            return new RemoteSnapshot(null, WebDavEtag.classify(null), null, false, result);
        }
    }
}
