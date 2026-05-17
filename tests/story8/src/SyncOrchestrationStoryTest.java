package story8;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import name.soulayrol.rhaa.sholi.sync.credentials.WebDavCredentials;
import name.soulayrol.rhaa.sholi.sync.credentials.WebDavSyncProfile;
import name.soulayrol.rhaa.sholi.sync.document.ModifiedBy;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocument;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocumentJson;
import name.soulayrol.rhaa.sholi.sync.document.SyncItem;
import name.soulayrol.rhaa.sholi.sync.merge.ConflictChoice;
import name.soulayrol.rhaa.sholi.sync.merge.SyncConflict;
import name.soulayrol.rhaa.sholi.sync.merge.SyncMetadataStore;
import name.soulayrol.rhaa.sholi.sync.orchestration.ResolvedConflictSyncService;
import name.soulayrol.rhaa.sholi.sync.orchestration.SyncConflictDisplayModel;
import name.soulayrol.rhaa.sholi.sync.orchestration.WebDavSyncController;
import name.soulayrol.rhaa.sholi.sync.settings.ConfiguredWebDavEndpoint;
import name.soulayrol.rhaa.sholi.sync.webdav.LocalSyncDocumentStore;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavPutResult;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavRequest;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavResponse;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavSyncResult;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavTransport;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavTransportException;

public final class SyncOrchestrationStoryTest {

    private static final String REMOTE_URL = "https://cloud.example.net/dav/sholi/sync.json";
    private static final String SECRET = "story8-secret-token";

    private SyncOrchestrationStoryTest() {
    }

    public static void run() throws Exception {
        verifySuccessfulNonConflictingSyncAppliesAndRecordsAfterUpload();
        verifySuccessfulSyncMarksTombstonesSyncedAndRetainsUntilRetentionWindow();
        verifySuccessfulSyncCleansOnlyExpiredSyncedTombstones();
        verifyPullingRemoteCleanupHardDeletesAbsentLocalTombstone();
        verifyConcurrentLocalEditBeforeMergedUploadPreservesLocalDataAndMetadata();
        verifyConcurrentLocalEditBeforeMergedApplyPreservesLocalDataAndMetadata();
        verifyUploadFailurePreservesLocalDataAndMetadata();
        verifyUnresolvedConflictsPersistAndBlockFurtherUploads();
        verifyConflictDisplayModelShowsRequiredCompleteComparisonFields();
        verifyDisplayModelAndDialogDoNotOfferMissingSideChoices();
        verifyChoosingLocalOrRemoteResolvesCompleteItemVersion();
        verifyMissingSideResolutionIsRejectedAndConflictStaysUnresolved();
        verifyRemainingUnresolvedConflictBlocksResumeUpload();
        verifyAllResolvedConflictsProduceAndUploadResolvedDocument();
        verifyResolvedConflictsPreservePendingNonConflictingRemoteChanges();
        verifyLocalEditToPendingNonConflictBeforeResumeBlocksStaleUpload();
        verifyLocalEditAfterConflictCaptureBeforeResumeDoesNotUploadStaleResolution();
        verifyLocalEditDuringResolvedConflictUploadDoesNotApplyOrRecordStaleResolution();
        verifyUnsafeResolvedConflictMarkerRequiresConfirmationWithoutUpload();
        verifyConfiguredDisplayNameSourcesLocalEditMetadata();
        verifySyncMenuAndConflictUiSources();
    }

    private static void verifySuccessfulNonConflictingSyncAppliesAndRecordsAfterUpload() {
        SyncItem baseMilk = item("sync-milk", "Milk", 1, false, 10L, "base-device");
        SyncItem baseBread = item("sync-bread", "Bread", 1, false, 11L, "base-device");
        SyncItem localMilk = item("sync-milk", "Oat milk", 1, false, 20L, "phone");
        SyncItem remoteBread = item("sync-bread", "Bread", 2, false, 30L, "tablet");
        SyncDocument baseline = document(baseMilk, baseBread);
        SyncDocument local = document(localMilk, baseBread);
        SyncDocument remote = document(baseMilk, remoteBread);
        SyncDocument merged = document(remoteBread, localMilk);
        RecordingTransport transport = new RecordingTransport();
        transport.respond(200, "\"v2\"", null);
        transport.respond(200, "\"v2\"", json(remote));
        transport.respond(204, "\"v3\"", null);
        RecordingLocalStore localStore = new RecordingLocalStore(local);
        RecordingMetadataStore metadata = new RecordingMetadataStore(baseline, "\"v1\"");

        WebDavSyncResult result = controller(transport, localStore, metadata).synchronize(1000L);

        assertEquals(WebDavSyncResult.Status.UPDATED_REMOTE, result.getStatus(), "successful merge status");
        assertEquals(list("HEAD", "GET", "PUT"), transport.methods(), "successful merge request sequence");
        assertEquals("\"v2\"", transport.request(2).getHeader("If-Match"), "merged upload marker");
        assertDocumentEquals(merged, parse(transport.request(2).getBody()), "merged upload body");
        assertDocumentEquals(merged, localStore.currentDocument, "merged document applied locally");
        assertDocumentEquals(merged, metadata.loadBaselineDocument(), "merged baseline recorded after upload");
        assertEquals("\"v3\"", metadata.loadRemoteVersionMarker(), "new marker recorded after upload");
        assertEquals(0, metadata.loadConflicts().size(), "successful sync clears conflicts");
        assertDoesNotContain(SECRET, result.getMessage(), "sync success message");
        assertDoesNotContain(SECRET, transport.request(2).toString(), "safe upload request string");
    }

    private static void verifySuccessfulSyncMarksTombstonesSyncedAndRetainsUntilRetentionWindow() {
        SyncItem live = item("sync-live-retain", "Milk", 1, false, 10L, "phone");
        SyncItem tombstone = item("sync-delete-retain", "Bread", 1, true, 20L, "phone");
        SyncDocument local = document(live, tombstone);
        RecordingTransport transport = new RecordingTransport();
        transport.respond(404, null, null);
        transport.respond(201, "\"created\"", null);
        RecordingLocalStore localStore = new RecordingLocalStore(local);
        RecordingMetadataStore metadata = new RecordingMetadataStore(null, null);

        WebDavSyncResult result = controller(transport, localStore, metadata).synchronize(1010L);

        assertEquals(WebDavSyncResult.Status.CREATED_REMOTE, result.getStatus(),
                "tombstone mark sync status");
        assertEquals(true, localStore.deletedSyncedAt("sync-delete-retain") != null,
                "successful sync marks tombstone included");
        assertEquals(true, containsId(localStore.currentDocument, "sync-delete-retain"),
                "fresh synced tombstone retained locally");
        assertEquals(true, containsId(metadata.loadBaselineDocument(), "sync-delete-retain"),
                "fresh synced tombstone retained in baseline");
    }

    private static void verifySuccessfulSyncCleansOnlyExpiredSyncedTombstones() {
        SyncItem live = item("sync-live-clean", "Milk", 1, false, 10L, "phone");
        SyncItem expired = item("sync-delete-expired", "Bread", 1, true, 20L, "phone");
        SyncItem fresh = item("sync-delete-fresh", "Tea", 1, true, 30L, "phone");
        SyncDocument local = document(expired, fresh, live);
        RecordingTransport transport = new RecordingTransport();
        transport.respond(404, null, null);
        transport.respond(201, "\"created\"", null);
        RecordingLocalStore localStore = new RecordingLocalStore(local);
        localStore.setDeletedSyncedAt("sync-delete-expired", Long.valueOf(1L));
        RecordingMetadataStore metadata = new RecordingMetadataStore(null, null);

        WebDavSyncResult result = controller(transport, localStore, metadata).synchronize(1020L);

        assertEquals(WebDavSyncResult.Status.CREATED_REMOTE, result.getStatus(),
                "tombstone cleanup sync status");
        assertEquals(true, containsId(parse(transport.request(1).getBody()), "sync-delete-expired"),
                "expired tombstone included before cleanup upload");
        assertEquals(false, containsId(localStore.currentDocument, "sync-delete-expired"),
                "expired synced tombstone cleaned locally after upload");
        assertEquals(true, containsId(localStore.currentDocument, "sync-delete-fresh"),
                "fresh tombstone retained locally after upload");
        assertEquals(true, localStore.deletedSyncedAt("sync-delete-fresh") != null,
                "fresh tombstone marked included after upload");
        assertEquals(true, containsId(metadata.loadBaselineDocument(), "sync-delete-expired"),
                "baseline records successfully uploaded expired tombstone");
    }

    private static void verifyPullingRemoteCleanupHardDeletesAbsentLocalTombstone() {
        SyncItem live = item("sync-live-pull-clean", "Milk", 1, false, 10L, "base-device");
        SyncItem tombstone = item("sync-delete-pull-clean", "Bread", 1, true, 20L, "base-device");
        SyncDocument baseline = document(live, tombstone);
        SyncDocument remoteCleaned = document(live);
        RecordingTransport transport = new RecordingTransport();
        transport.respond(200, "\"v2\"", null);
        transport.respond(200, "\"v2\"", json(remoteCleaned));
        RecordingLocalStore localStore = new RecordingLocalStore(baseline);
        localStore.setDeletedSyncedAt("sync-delete-pull-clean", Long.valueOf(1L));
        RecordingMetadataStore metadata = new RecordingMetadataStore(baseline, "\"v1\"");

        WebDavSyncResult result = controller(transport, localStore, metadata).synchronize(1040L);

        assertEquals(WebDavSyncResult.Status.PULLED_REMOTE, result.getStatus(),
                "remote tombstone cleanup pull status");
        assertEquals(list("HEAD", "GET"), transport.methods(),
                "remote tombstone cleanup request sequence");
        assertEquals(false, containsId(localStore.currentDocument, "sync-delete-pull-clean"),
                "absent synced tombstone hard-deleted during guarded apply");
        assertDocumentEquals(remoteCleaned, localStore.currentDocument,
                "remote cleanup pull leaves local document equal to incoming snapshot");
        assertDocumentEquals(remoteCleaned, metadata.loadBaselineDocument(),
                "remote cleanup pull records cleaned baseline");
        assertEquals("\"v2\"", metadata.loadRemoteVersionMarker(),
                "remote cleanup pull records marker");
    }

    private static void verifyConcurrentLocalEditBeforeMergedUploadPreservesLocalDataAndMetadata() {
        SyncItem baseMilk = item("sync-milk", "Milk", 1, false, 10L, "base-device");
        SyncItem baseBread = item("sync-bread", "Bread", 1, false, 11L, "base-device");
        SyncItem localMilk = item("sync-milk", "Oat milk", 1, false, 20L, "phone");
        SyncItem remoteBread = item("sync-bread", "Bread", 2, false, 30L, "tablet");
        SyncItem concurrentMilk = item("sync-milk", "Almond milk", 1, false, 40L, "phone");
        SyncDocument baseline = document(baseMilk, baseBread);
        SyncDocument local = document(localMilk, baseBread);
        SyncDocument remote = document(baseMilk, remoteBread);
        final SyncDocument concurrentLocal = document(concurrentMilk, baseBread);
        final RecordingLocalStore localStore = new RecordingLocalStore(local);
        RecordingTransport transport = new RecordingTransport();
        transport.respond(200, "\"v2\"", null);
        transport.respondAndThen(200, "\"v2\"", json(remote), new Runnable() {
            @Override
            public void run() {
                localStore.currentDocument = concurrentLocal;
            }
        });
        transport.respond(204, "\"v3\"", null);
        RecordingMetadataStore metadata = new RecordingMetadataStore(baseline, "\"v1\"");

        WebDavSyncResult result = controller(transport, localStore, metadata).synchronize(1050L);

        assertEquals(WebDavSyncResult.Status.LOCAL_CHANGED, result.getStatus(),
                "concurrent edit before upload status");
        assertContains(result.getMessage(), "changed", "concurrent edit message");
        assertDoesNotContain(SECRET, result.getMessage(), "concurrent edit message");
        assertEquals(list("HEAD", "GET"), transport.methods(), "concurrent edit before upload request sequence");
        assertEquals(0, transport.countMethod("PUT"), "concurrent edit before upload blocks stale PUT");
        assertEquals(0, localStore.appliedDocuments.size(), "concurrent edit before upload skips local apply");
        assertDocumentEquals(concurrentLocal, localStore.currentDocument,
                "concurrent edit before upload preserves active local state");
        assertDocumentEquals(baseline, metadata.loadBaselineDocument(),
                "concurrent edit before upload preserves baseline");
        assertEquals("\"v1\"", metadata.loadRemoteVersionMarker(),
                "concurrent edit before upload preserves marker");
        assertEquals(0, metadata.loadConflicts().size(), "concurrent edit before upload keeps prior conflicts");
    }

    private static void verifyConcurrentLocalEditBeforeMergedApplyPreservesLocalDataAndMetadata() {
        SyncItem baseMilk = item("sync-milk", "Milk", 1, false, 10L, "base-device");
        SyncItem baseBread = item("sync-bread", "Bread", 1, false, 11L, "base-device");
        SyncItem localMilk = item("sync-milk", "Oat milk", 1, false, 20L, "phone");
        SyncItem remoteBread = item("sync-bread", "Bread", 2, false, 30L, "tablet");
        SyncItem concurrentMilk = item("sync-milk", "Almond milk", 1, false, 40L, "phone");
        SyncDocument baseline = document(baseMilk, baseBread);
        SyncDocument local = document(localMilk, baseBread);
        SyncDocument remote = document(baseMilk, remoteBread);
        final SyncDocument concurrentLocal = document(concurrentMilk, baseBread);
        final RecordingLocalStore localStore = new RecordingLocalStore(local);
        RecordingTransport transport = new RecordingTransport();
        transport.respond(200, "\"v2\"", null);
        transport.respond(200, "\"v2\"", json(remote));
        transport.respondAndThen(204, "\"v3\"", null, new Runnable() {
            @Override
            public void run() {
                localStore.currentDocument = concurrentLocal;
            }
        });
        RecordingMetadataStore metadata = new RecordingMetadataStore(baseline, "\"v1\"");

        WebDavSyncResult result = controller(transport, localStore, metadata).synchronize(1075L);

        assertEquals(WebDavSyncResult.Status.LOCAL_CHANGED, result.getStatus(),
                "concurrent edit before apply status");
        assertContains(result.getMessage(), "changed", "concurrent edit before apply message");
        assertDoesNotContain(SECRET, result.getMessage(), "concurrent edit before apply message");
        assertEquals(list("HEAD", "GET", "PUT"), transport.methods(),
                "concurrent edit before apply request sequence");
        assertEquals(0, localStore.appliedDocuments.size(), "concurrent edit before apply skips stale apply");
        assertDocumentEquals(concurrentLocal, localStore.currentDocument,
                "concurrent edit before apply preserves active local state");
        assertDocumentEquals(baseline, metadata.loadBaselineDocument(),
                "concurrent edit before apply preserves baseline");
        assertEquals("\"v1\"", metadata.loadRemoteVersionMarker(),
                "concurrent edit before apply preserves marker");
        assertEquals(0, metadata.loadConflicts().size(), "concurrent edit before apply keeps prior conflicts");
    }

    private static void verifyUploadFailurePreservesLocalDataAndMetadata() {
        SyncDocument baseline = document(item("sync-rice", "Rice", 1, false, 10L, "base"));
        SyncDocument local = document(item("sync-rice", "Brown rice", 1, false, 20L, "phone"));
        RecordingTransport transport = new RecordingTransport();
        transport.respond(200, "\"v1\"", null);
        transport.respond(500, null, null);
        RecordingLocalStore localStore = new RecordingLocalStore(local);
        RecordingMetadataStore metadata = new RecordingMetadataStore(baseline, "\"v1\"");

        WebDavSyncResult result = controller(transport, localStore, metadata).synchronize(1100L);

        assertEquals(WebDavSyncResult.Status.SERVER_ERROR, result.getStatus(), "failed upload status");
        assertEquals(list("HEAD", "PUT"), transport.methods(), "failed upload request sequence");
        assertDocumentEquals(local, localStore.currentDocument, "failed upload preserves local state");
        assertDocumentEquals(baseline, metadata.loadBaselineDocument(), "failed upload preserves baseline");
        assertEquals("\"v1\"", metadata.loadRemoteVersionMarker(), "failed upload preserves marker");
        assertEquals(0, metadata.loadConflicts().size(), "failed upload does not create conflicts");
    }

    private static void verifyUnresolvedConflictsPersistAndBlockFurtherUploads() {
        SyncDocument baseline = document(item("sync-tea", "Tea", 1, false, 10L, "base"));
        SyncDocument local = document(item("sync-tea", "Green tea", 1, false, 20L, "phone"));
        SyncDocument remote = document(item("sync-tea", "Black tea", 1, false, 30L, "tablet"));
        RecordingTransport transport = new RecordingTransport();
        transport.respond(200, "\"v2\"", null);
        transport.respond(200, "\"v2\"", json(remote));
        RecordingLocalStore localStore = new RecordingLocalStore(local);
        RecordingMetadataStore metadata = new RecordingMetadataStore(baseline, "\"v1\"");

        WebDavSyncResult result = controller(transport, localStore, metadata).synchronize(1200L);

        assertEquals(WebDavSyncResult.Status.CONFLICTS, result.getStatus(), "conflict sync status");
        assertEquals(0, transport.countMethod("PUT"), "conflict must not upload");
        assertDocumentEquals(local, localStore.currentDocument, "conflict preserves active local state");
        assertDocumentEquals(baseline, metadata.loadBaselineDocument(), "conflict preserves baseline");
        assertEquals("\"v1\"", metadata.loadRemoteVersionMarker(), "conflict preserves old marker");
        assertEquals(1, metadata.loadConflicts().size(), "conflict persisted");

        RecordingTransport blockedTransport = new RecordingTransport();
        WebDavSyncResult blocked = controller(blockedTransport, localStore, metadata).synchronize(1300L);

        assertEquals(WebDavSyncResult.Status.CONFLICTS, blocked.getStatus(), "existing conflict blocks status");
        assertEquals(0, blockedTransport.requests.size(), "existing conflict blocks network before upload");
    }

    private static void verifyConflictDisplayModelShowsRequiredCompleteComparisonFields() {
        SyncConflict conflict = conflict(
                "sync-display",
                item("sync-display", "Coffee", 1, false, 10L, "base"),
                item("sync-display", "Decaf coffee", 2, false, 20L, "phone-user"),
                item("sync-display", "Coffee", 1, true, 30L, "tablet-user"),
                "\"v2\"");

        SyncConflictDisplayModel model = SyncConflictDisplayModel.from(conflict);
        String text = model.toDisplayText();

        assertEquals("sync-display", model.getSyncId(), "display sync_id");
        assertEquals(true, model.getChangedFields().contains("name"), "display changed name field");
        assertEquals(true, model.getChangedFields().contains("status"), "display changed status field");
        assertEquals(true, model.getChangedFields().contains("deleted"), "display changed deleted field");
        assertContains(text, "Changed fields", "display changed fields label");
        assertContains(text, "Local", "display local label");
        assertContains(text, "Remote", "display remote label");
        assertContains(text, "Decaf coffee", "display local complete name");
        assertContains(text, "Coffee", "display remote complete name");
        assertContains(text, "status=2", "display local status value");
        assertContains(text, "status=1", "display remote status value");
        assertContains(text, "deleted=false", "display local deleted state");
        assertContains(text, "deleted=true", "display remote deleted state");
        assertContains(text, "modified_at=20", "display local timestamp");
        assertContains(text, "modified_at=30", "display remote timestamp");
        assertContains(text, "modified_by.name=phone-user", "display local modifier");
        assertContains(text, "modified_by.name=tablet-user", "display remote modifier");
    }

    private static void verifyDisplayModelAndDialogDoNotOfferMissingSideChoices() throws Exception {
        SyncConflict missingLocal = conflict(
                "sync-missing-local",
                item("sync-missing-local", "Coffee", 1, false, 10L, "base"),
                null,
                item("sync-missing-local", "Coffee", 2, false, 30L, "tablet"),
                "\"v2\"");
        SyncConflict missingRemote = conflict(
                "sync-missing-remote",
                item("sync-missing-remote", "Tea", 1, false, 10L, "base"),
                item("sync-missing-remote", "Green tea", 1, false, 20L, "phone"),
                null,
                "\"v2\"");

        SyncConflictDisplayModel localModel = SyncConflictDisplayModel.from(missingLocal);
        SyncConflictDisplayModel remoteModel = SyncConflictDisplayModel.from(missingRemote);
        String conflictDialog = readUtf8(
                "sholi/src/main/java/name/soulayrol/rhaa/sholi/SyncConflictDialogFragment.java");

        assertEquals(false, localModel.canChooseLocal(), "missing local choice unavailable");
        assertEquals(true, localModel.canChooseRemote(), "present remote choice available");
        assertEquals(true, localModel.toDisplayText().contains("Local: missing"),
                "missing local display text");
        assertEquals(true, remoteModel.canChooseLocal(), "present local choice available");
        assertEquals(false, remoteModel.canChooseRemote(), "missing remote choice unavailable");
        assertEquals(true, remoteModel.toDisplayText().contains("Remote: missing"),
                "missing remote display text");
        assertContains(conflictDialog, "canChooseLocal()", "conflict dialog local availability gate");
        assertContains(conflictDialog, "canChooseRemote()", "conflict dialog remote availability gate");
    }

    private static void verifyChoosingLocalOrRemoteResolvesCompleteItemVersion() {
        SyncConflict conflict = conflict(
                "sync-choice",
                item("sync-choice", "Cereal", 1, false, 10L, "base"),
                item("sync-choice", "Granola", 2, false, 20L, "phone"),
                item("sync-choice", "Muesli", 1, true, 30L, "tablet"),
                "\"v2\"");
        RecordingLocalStore localStore = new RecordingLocalStore(document(
                item("sync-choice", "Granola", 2, false, 20L, "phone")));
        RecordingMetadataStore metadata = new RecordingMetadataStore(
                document(conflict.getBaselineItem()), "\"v1\"");
        metadata.replaceConflicts(Collections.singletonList(conflict));
        RecordingConflictUploader uploader = new RecordingConflictUploader("\"v3\"");
        ResolvedConflictSyncService service = new ResolvedConflictSyncService(localStore, metadata, uploader);

        service.choose("sync-choice", ConflictChoice.LOCAL);
        ResolvedConflictSyncService.UploadPlan localPlan = service.prepareUpload();
        assertEquals(ResolvedConflictSyncService.UploadPlan.Status.READY, localPlan.getStatus(),
                "local choice upload readiness");
        assertItemEquals(conflict.getLocalItem(), byId(localPlan.getDocument(), "sync-choice"),
                "local choice complete resolved item");

        metadata.replaceConflicts(Collections.singletonList(conflict));
        service.choose("sync-choice", ConflictChoice.REMOTE);
        ResolvedConflictSyncService.UploadPlan remotePlan = service.prepareUpload();
        assertEquals(ResolvedConflictSyncService.UploadPlan.Status.READY, remotePlan.getStatus(),
                "remote choice upload readiness");
        assertItemEquals(conflict.getRemoteItem(), byId(remotePlan.getDocument(), "sync-choice"),
                "remote choice complete resolved item");
    }

    private static void verifyMissingSideResolutionIsRejectedAndConflictStaysUnresolved() {
        SyncConflict missingLocal = conflict(
                "sync-missing-local-choice",
                item("sync-missing-local-choice", "Coffee", 1, false, 10L, "base"),
                null,
                item("sync-missing-local-choice", "Coffee", 2, false, 30L, "tablet"),
                "\"v2\"");
        RecordingLocalStore localStore = new RecordingLocalStore(document());
        RecordingMetadataStore metadata = new RecordingMetadataStore(document(missingLocal.getBaselineItem()), "\"v1\"");
        metadata.replaceConflicts(Collections.singletonList(missingLocal));
        RecordingConflictUploader uploader = new RecordingConflictUploader("\"v3\"");
        ResolvedConflictSyncService service = new ResolvedConflictSyncService(localStore, metadata, uploader);

        assertMissingSideRejected(service, "sync-missing-local-choice", ConflictChoice.LOCAL,
                "missing local side rejection");
        assertEquals(1, metadata.loadConflicts().size(), "missing local conflict remains recorded");
        assertEquals(SyncConflict.STATUS_UNRESOLVED, metadata.loadConflicts().get(0).getStatus(),
                "missing local conflict remains unresolved");
        assertEquals(ResolvedConflictSyncService.UploadPlan.Status.BLOCKED_UNRESOLVED,
                service.prepareUpload().getStatus(), "missing local rejected upload blocked");
        assertEquals(WebDavSyncResult.Status.CONFLICTS, service.resumeResolvedConflicts().getStatus(),
                "missing local rejected resume blocked");
        assertEquals(0, uploader.uploads.size(), "missing local rejected no upload");

        metadata.replaceConflicts(Collections.singletonList(
                missingLocal.withStatus(SyncConflict.STATUS_RESOLVED_LOCAL)));
        ResolvedConflictSyncService.UploadPlan repairedPlan = service.prepareUpload();
        assertEquals(ResolvedConflictSyncService.UploadPlan.Status.BLOCKED_UNRESOLVED,
                repairedPlan.getStatus(), "stale missing local resolution is repaired and blocked");
        assertEquals(SyncConflict.STATUS_UNRESOLVED, metadata.loadConflicts().get(0).getStatus(),
                "stale missing local resolution becomes unresolved");
        assertEquals(0, uploader.uploads.size(), "stale missing local resolution no upload");

        service.choose("sync-missing-local-choice", ConflictChoice.REMOTE);
        ResolvedConflictSyncService.UploadPlan remotePlan = service.prepareUpload();
        assertEquals(ResolvedConflictSyncService.UploadPlan.Status.READY, remotePlan.getStatus(),
                "present remote side can resolve missing local conflict");
        assertItemEquals(missingLocal.getRemoteItem(), byId(remotePlan.getDocument(), "sync-missing-local-choice"),
                "present remote side selected item");

        SyncConflict missingRemote = conflict(
                "sync-missing-remote-choice",
                item("sync-missing-remote-choice", "Tea", 1, false, 10L, "base"),
                item("sync-missing-remote-choice", "Green tea", 1, false, 20L, "phone"),
                null,
                "\"v2\"");
        localStore = new RecordingLocalStore(document(missingRemote.getLocalItem()));
        metadata = new RecordingMetadataStore(document(missingRemote.getBaselineItem()), "\"v1\"");
        metadata.replaceConflicts(Collections.singletonList(missingRemote));
        service = new ResolvedConflictSyncService(localStore, metadata, uploader);

        assertMissingSideRejected(service, "sync-missing-remote-choice", ConflictChoice.REMOTE,
                "missing remote side rejection");
        assertEquals(SyncConflict.STATUS_UNRESOLVED, metadata.loadConflicts().get(0).getStatus(),
                "missing remote conflict remains unresolved");
        service.choose("sync-missing-remote-choice", ConflictChoice.LOCAL);
        ResolvedConflictSyncService.UploadPlan localPlan = service.prepareUpload();
        assertEquals(ResolvedConflictSyncService.UploadPlan.Status.READY, localPlan.getStatus(),
                "present local side can resolve missing remote conflict");
        assertItemEquals(missingRemote.getLocalItem(), byId(localPlan.getDocument(), "sync-missing-remote-choice"),
                "present local side selected item");
    }

    private static void verifyRemainingUnresolvedConflictBlocksResumeUpload() {
        SyncConflict first = conflict(
                "sync-block-a",
                item("sync-block-a", "Apples", 1, false, 10L, "base"),
                item("sync-block-a", "Green apples", 1, false, 20L, "phone"),
                item("sync-block-a", "Red apples", 1, false, 30L, "tablet"),
                "\"v2\"");
        SyncConflict second = conflict(
                "sync-block-b",
                item("sync-block-b", "Butter", 1, false, 10L, "base"),
                item("sync-block-b", "Butter", 2, false, 20L, "phone"),
                item("sync-block-b", "Butter", 0, false, 30L, "tablet"),
                "\"v2\"");
        RecordingLocalStore localStore = new RecordingLocalStore(document(first.getLocalItem(), second.getLocalItem()));
        RecordingMetadataStore metadata = new RecordingMetadataStore(document(first.getBaselineItem(), second.getBaselineItem()), "\"v1\"");
        metadata.replaceConflicts(list(first, second));
        RecordingConflictUploader uploader = new RecordingConflictUploader("\"v3\"");
        ResolvedConflictSyncService service = new ResolvedConflictSyncService(localStore, metadata, uploader);

        service.choose("sync-block-a", ConflictChoice.LOCAL);
        ResolvedConflictSyncService.UploadPlan plan = service.prepareUpload();
        WebDavSyncResult result = service.resumeResolvedConflicts();

        assertEquals(ResolvedConflictSyncService.UploadPlan.Status.BLOCKED_UNRESOLVED, plan.getStatus(),
                "unresolved conflict plan status");
        assertEquals(WebDavSyncResult.Status.CONFLICTS, result.getStatus(), "unresolved conflict resume status");
        assertEquals(0, uploader.uploads.size(), "unresolved conflict blocks upload call");
        assertDocumentEquals(document(first.getLocalItem(), second.getLocalItem()), localStore.currentDocument,
                "unresolved conflict resume preserves local document");
    }

    private static void verifyAllResolvedConflictsProduceAndUploadResolvedDocument() {
        SyncConflict first = conflict(
                "sync-resume-a",
                item("sync-resume-a", "Apples", 1, false, 10L, "base"),
                item("sync-resume-a", "Green apples", 1, false, 20L, "phone"),
                item("sync-resume-a", "Red apples", 1, false, 30L, "tablet"),
                "\"v2\"");
        SyncConflict second = conflict(
                "sync-resume-b",
                item("sync-resume-b", "Butter", 1, false, 10L, "base"),
                item("sync-resume-b", "Butter", 2, false, 20L, "phone"),
                item("sync-resume-b", "Butter", 0, false, 30L, "tablet"),
                "\"v2\"");
        SyncItem untouched = item("sync-untouched", "Salt", 1, false, 12L, "phone");
        RecordingLocalStore localStore = new RecordingLocalStore(document(
                first.getLocalItem(), second.getLocalItem(), untouched));
        RecordingMetadataStore metadata = new RecordingMetadataStore(
                document(first.getBaselineItem(), second.getBaselineItem(), untouched), "\"v1\"");
        metadata.replaceConflicts(list(first, second));
        RecordingConflictUploader uploader = new RecordingConflictUploader("\"v3\"");
        ResolvedConflictSyncService service = new ResolvedConflictSyncService(localStore, metadata, uploader);

        service.choose("sync-resume-a", ConflictChoice.LOCAL);
        service.choose("sync-resume-b", ConflictChoice.REMOTE);
        ResolvedConflictSyncService.UploadPlan plan = service.prepareUpload();
        WebDavSyncResult result = service.resumeResolvedConflicts();

        assertEquals(ResolvedConflictSyncService.UploadPlan.Status.READY, plan.getStatus(),
                "all resolved upload readiness");
        assertEquals("\"v2\"", plan.getRemoteVersionMarker(), "all resolved safe marker");
        assertItemEquals(first.getLocalItem(), byId(plan.getDocument(), "sync-resume-a"),
                "all resolved local choice");
        assertItemEquals(second.getRemoteItem(), byId(plan.getDocument(), "sync-resume-b"),
                "all resolved remote choice");
        assertItemEquals(untouched, byId(plan.getDocument(), "sync-untouched"),
                "all resolved keeps current non-conflict item");
        assertEquals(WebDavSyncResult.Status.UPDATED_REMOTE, result.getStatus(), "all resolved upload status");
        assertEquals(1, uploader.uploads.size(), "all resolved performs upload");
        assertEquals("\"v2\"", uploader.uploads.get(0).marker, "all resolved upload marker");
        assertDocumentEquals(plan.getDocument(), uploader.uploads.get(0).document, "all resolved upload body");
        assertDocumentEquals(plan.getDocument(), localStore.currentDocument, "all resolved applies local document");
        assertDocumentEquals(plan.getDocument(), metadata.loadBaselineDocument(), "all resolved records baseline");
        assertEquals("\"v3\"", metadata.loadRemoteVersionMarker(), "all resolved records new marker");
        assertEquals(0, metadata.loadConflicts().size(), "all resolved clears conflicts");
    }

    private static void verifyResolvedConflictsPreservePendingNonConflictingRemoteChanges() {
        SyncItem baseA = item("sync-conflict-a", "Milk", 1, false, 10L, "base");
        SyncItem baseB = item("sync-remote-b", "Bread", 1, false, 11L, "base");
        SyncItem localA = item("sync-conflict-a", "Oat milk", 1, false, 20L, "phone");
        SyncItem remoteA = item("sync-conflict-a", "Soy milk", 1, false, 30L, "tablet");
        SyncItem remoteB = item("sync-remote-b", "Bread", 2, false, 40L, "tablet");
        SyncDocument baseline = document(baseA, baseB);
        SyncDocument local = document(localA, baseB);
        SyncDocument remote = document(remoteA, remoteB);
        SyncDocument expectedResolved = document(localA, remoteB);
        RecordingTransport transport = new RecordingTransport();
        transport.respond(200, "\"v2\"", null);
        transport.respond(200, "\"v2\"", json(remote));
        RecordingLocalStore localStore = new RecordingLocalStore(local);
        RecordingMetadataStore metadata = new RecordingMetadataStore(baseline, "\"v1\"");

        WebDavSyncResult conflictResult = controller(transport, localStore, metadata).synchronize(1325L);
        RecordingConflictUploader uploader = new RecordingConflictUploader("\"v3\"");
        ResolvedConflictSyncService service = new ResolvedConflictSyncService(localStore, metadata, uploader);

        assertEquals(WebDavSyncResult.Status.CONFLICTS, conflictResult.getStatus(),
                "mixed conflict capture status");
        assertEquals(1, metadata.loadConflicts().size(), "mixed conflict count");
        service.choose("sync-conflict-a", ConflictChoice.LOCAL);
        ResolvedConflictSyncService.UploadPlan plan = service.prepareUpload();
        WebDavSyncResult result = service.resumeResolvedConflicts();

        assertEquals(ResolvedConflictSyncService.UploadPlan.Status.READY, plan.getStatus(),
                "mixed resolved upload readiness");
        assertItemEquals(localA, byId(plan.getDocument(), "sync-conflict-a"),
                "mixed resolved local conflict choice");
        assertItemEquals(remoteB, byId(plan.getDocument(), "sync-remote-b"),
                "mixed resolved keeps remote-only non-conflict");
        assertEquals(WebDavSyncResult.Status.UPDATED_REMOTE, result.getStatus(),
                "mixed resolved upload status");
        assertEquals(1, uploader.uploads.size(), "mixed resolved performs upload");
        assertDocumentEquals(expectedResolved, uploader.uploads.get(0).document,
                "mixed resolved upload body preserves pending remote change");
        assertDocumentEquals(expectedResolved, localStore.currentDocument,
                "mixed resolved local apply preserves pending remote change");
        assertDocumentEquals(expectedResolved, metadata.loadBaselineDocument(),
                "mixed resolved baseline preserves pending remote change");
        assertEquals("\"v3\"", metadata.loadRemoteVersionMarker(),
                "mixed resolved records new marker");
        assertEquals(0, metadata.loadConflicts().size(), "mixed resolved clears conflicts");
    }

    private static void verifyLocalEditToPendingNonConflictBeforeResumeBlocksStaleUpload() {
        SyncItem baseA = item("sync-stale-a", "Milk", 1, false, 10L, "base");
        SyncItem baseB = item("sync-stale-b", "Bread", 1, false, 11L, "base");
        SyncItem localA = item("sync-stale-a", "Oat milk", 1, false, 20L, "phone");
        SyncItem remoteA = item("sync-stale-a", "Soy milk", 1, false, 30L, "tablet");
        SyncItem remoteB = item("sync-stale-b", "Bread", 2, false, 40L, "tablet");
        SyncItem editedB = item("sync-stale-b", "Sourdough", 1, false, 50L, "phone");
        SyncDocument baseline = document(baseA, baseB);
        SyncDocument local = document(localA, baseB);
        SyncDocument remote = document(remoteA, remoteB);
        SyncDocument editedLocal = document(localA, editedB);
        RecordingTransport transport = new RecordingTransport();
        transport.respond(200, "\"v2\"", null);
        transport.respond(200, "\"v2\"", json(remote));
        RecordingLocalStore localStore = new RecordingLocalStore(local);
        RecordingMetadataStore metadata = new RecordingMetadataStore(baseline, "\"v1\"");

        WebDavSyncResult conflictResult = controller(transport, localStore, metadata).synchronize(1335L);
        RecordingConflictUploader uploader = new RecordingConflictUploader("\"v3\"");
        ResolvedConflictSyncService service = new ResolvedConflictSyncService(localStore, metadata, uploader);

        assertEquals(WebDavSyncResult.Status.CONFLICTS, conflictResult.getStatus(),
                "pending stale capture status");
        service.choose("sync-stale-a", ConflictChoice.LOCAL);
        localStore.currentDocument = editedLocal;
        WebDavSyncResult result = service.resumeResolvedConflicts();

        assertEquals(WebDavSyncResult.Status.LOCAL_CHANGED, result.getStatus(),
                "pending non-conflict stale local edit status");
        assertEquals(0, uploader.uploads.size(), "pending non-conflict stale edit blocks upload");
        assertEquals(0, localStore.appliedDocuments.size(),
                "pending non-conflict stale edit skips local apply");
        assertDocumentEquals(editedLocal, localStore.currentDocument,
                "pending non-conflict stale edit preserves active local state");
        assertDocumentEquals(baseline, metadata.loadBaselineDocument(),
                "pending non-conflict stale edit preserves baseline");
        assertEquals("\"v1\"", metadata.loadRemoteVersionMarker(),
                "pending non-conflict stale edit preserves marker");
        assertEquals(1, metadata.loadConflicts().size(),
                "pending non-conflict stale edit keeps conflict metadata");
    }

    private static void verifyLocalEditAfterConflictCaptureBeforeResumeDoesNotUploadStaleResolution() {
        SyncConflict conflict = conflict(
                "sync-stale-resume",
                item("sync-stale-resume", "Apples", 1, false, 10L, "base"),
                item("sync-stale-resume", "Green apples", 1, false, 20L, "phone"),
                item("sync-stale-resume", "Red apples", 1, false, 30L, "tablet"),
                "\"v2\"");
        SyncDocument baseline = document(conflict.getBaselineItem());
        SyncDocument capturedLocal = document(conflict.getLocalItem());
        SyncDocument editedLocal = document(
                item("sync-stale-resume", "Yellow apples", 1, false, 40L, "phone"));
        RecordingLocalStore localStore = new RecordingLocalStore(capturedLocal);
        RecordingMetadataStore metadata = new RecordingMetadataStore(baseline, "\"v1\"");
        metadata.replaceConflicts(Collections.singletonList(conflict));
        RecordingConflictUploader uploader = new RecordingConflictUploader("\"v3\"");
        ResolvedConflictSyncService service = new ResolvedConflictSyncService(localStore, metadata, uploader);

        service.choose("sync-stale-resume", ConflictChoice.LOCAL);
        localStore.currentDocument = editedLocal;
        WebDavSyncResult result = service.resumeResolvedConflicts();

        assertEquals(WebDavSyncResult.Status.LOCAL_CHANGED, result.getStatus(),
                "stale resolved resume status");
        assertEquals(0, uploader.uploads.size(), "stale resolved resume blocks upload");
        assertEquals(0, localStore.appliedDocuments.size(), "stale resolved resume skips local apply");
        assertDocumentEquals(editedLocal, localStore.currentDocument,
                "stale resolved resume preserves active local edit");
        assertDocumentEquals(baseline, metadata.loadBaselineDocument(),
                "stale resolved resume preserves baseline");
        assertEquals("\"v1\"", metadata.loadRemoteVersionMarker(),
                "stale resolved resume preserves marker");
        assertEquals(1, metadata.loadConflicts().size(),
                "stale resolved resume keeps conflict metadata");
        assertEquals(SyncConflict.STATUS_RESOLVED_LOCAL, metadata.loadConflicts().get(0).getStatus(),
                "stale resolved resume keeps resolved choice for safe retry handling");
    }

    private static void verifyLocalEditDuringResolvedConflictUploadDoesNotApplyOrRecordStaleResolution() {
        SyncConflict conflict = conflict(
                "sync-race-resume",
                item("sync-race-resume", "Tea", 1, false, 10L, "base"),
                item("sync-race-resume", "Green tea", 1, false, 20L, "phone"),
                item("sync-race-resume", "Black tea", 1, false, 30L, "tablet"),
                "\"v2\"");
        SyncDocument baseline = document(conflict.getBaselineItem());
        SyncDocument capturedLocal = document(conflict.getLocalItem());
        final SyncDocument editedLocal = document(
                item("sync-race-resume", "Mint tea", 1, false, 40L, "phone"));
        final RecordingLocalStore localStore = new RecordingLocalStore(capturedLocal);
        RecordingMetadataStore metadata = new RecordingMetadataStore(baseline, "\"v1\"");
        metadata.replaceConflicts(Collections.singletonList(conflict));
        RecordingConflictUploader uploader = new RecordingConflictUploader("\"v3\"", new Runnable() {
            @Override
            public void run() {
                localStore.currentDocument = editedLocal;
            }
        });
        ResolvedConflictSyncService service = new ResolvedConflictSyncService(localStore, metadata, uploader);

        service.choose("sync-race-resume", ConflictChoice.REMOTE);
        WebDavSyncResult result = service.resumeResolvedConflicts();

        assertEquals(WebDavSyncResult.Status.LOCAL_CHANGED, result.getStatus(),
                "resolved resume concurrent edit status");
        assertEquals(1, uploader.uploads.size(), "resolved resume upload already happened");
        assertEquals(0, localStore.appliedDocuments.size(),
                "resolved resume concurrent edit skips stale local apply");
        assertDocumentEquals(editedLocal, localStore.currentDocument,
                "resolved resume concurrent edit preserves active local state");
        assertDocumentEquals(baseline, metadata.loadBaselineDocument(),
                "resolved resume concurrent edit preserves baseline");
        assertEquals("\"v1\"", metadata.loadRemoteVersionMarker(),
                "resolved resume concurrent edit preserves marker");
        assertEquals(1, metadata.loadConflicts().size(),
                "resolved resume concurrent edit keeps conflict metadata");
        assertEquals(SyncConflict.STATUS_RESOLVED_REMOTE, metadata.loadConflicts().get(0).getStatus(),
                "resolved resume concurrent edit keeps resolved choice for safe retry handling");
    }

    private static void verifyUnsafeResolvedConflictMarkerRequiresConfirmationWithoutUpload() {
        SyncConflict missingMarker = conflict(
                "sync-unsafe-a",
                item("sync-unsafe-a", "Tea", 1, false, 10L, "base"),
                item("sync-unsafe-a", "Green tea", 1, false, 20L, "phone"),
                item("sync-unsafe-a", "Black tea", 1, false, 30L, "tablet"),
                null);
        SyncConflict strongMarker = conflict(
                "sync-unsafe-b",
                item("sync-unsafe-b", "Coffee", 1, false, 10L, "base"),
                item("sync-unsafe-b", "Decaf coffee", 1, false, 20L, "phone"),
                item("sync-unsafe-b", "Espresso", 1, false, 30L, "tablet"),
                "\"v2\"");
        RecordingLocalStore localStore = new RecordingLocalStore(document(
                missingMarker.getLocalItem(), strongMarker.getLocalItem()));
        RecordingMetadataStore metadata = new RecordingMetadataStore(
                document(missingMarker.getBaselineItem(), strongMarker.getBaselineItem()), "\"v1\"");
        metadata.replaceConflicts(list(missingMarker, strongMarker));
        RecordingConflictUploader uploader = new RecordingConflictUploader("\"v3\"");
        ResolvedConflictSyncService service = new ResolvedConflictSyncService(localStore, metadata, uploader);

        service.choose("sync-unsafe-a", ConflictChoice.LOCAL);
        service.choose("sync-unsafe-b", ConflictChoice.REMOTE);
        ResolvedConflictSyncService.UploadPlan plan = service.prepareUpload();
        WebDavSyncResult result = service.resumeResolvedConflicts();

        assertEquals(ResolvedConflictSyncService.UploadPlan.Status.CONFIRMATION_REQUIRED, plan.getStatus(),
                "unsafe mixed marker plan status");
        assertEquals(WebDavSyncResult.Status.CONFIRMATION_REQUIRED, result.getStatus(),
                "unsafe mixed marker resume status");
        assertEquals(0, uploader.uploads.size(), "unsafe mixed marker does not upload");
        assertEquals(2, metadata.loadConflicts().size(), "unsafe mixed marker keeps conflicts for later");
    }

    private static void verifyConfiguredDisplayNameSourcesLocalEditMetadata() throws Exception {
        String operations = readUtf8("sholi/src/main/java/name/soulayrol/rhaa/sholi/data/Operations.java");
        String editFragment = readUtf8("sholi/src/main/java/name/soulayrol/rhaa/sholi/EditFragment.java");
        String checkingFragment = readUtf8("sholi/src/main/java/name/soulayrol/rhaa/sholi/CheckingFragment.java");
        String dataOverviewFragment = readUtf8(
                "sholi/src/main/java/name/soulayrol/rhaa/sholi/DataOverviewFragment.java");
        String importFragment = readUtf8("sholi/src/main/java/name/soulayrol/rhaa/sholi/ImportFragment.java");
        String action = readUtf8("sholi/src/main/java/name/soulayrol/rhaa/sholi/data/Action.java");
        String adapter = readUtf8(
                "sholi/src/main/java/name/soulayrol/rhaa/sholi/sync/document/SyncDocumentItemAdapter.java");

        assertContains(operations, "KEY_WEBDAV_DISPLAY_NAME", "operations reads configured display name");
        assertContains(operations, "modifiedByName(Context context)",
                "operations configured modifier helper");
        assertContains(operations, "newItem(Context context", "context-aware item creation");
        assertContains(operations, "touch(Context context", "context-aware touch");
        assertContains(operations, "restore(Context context", "context-aware restore");
        assertContains(operations, "markDeleted(Context context", "context-aware delete");
        assertContains(editFragment, "Operations.touch(getActivity(), item)",
                "edit item updates use display name");
        assertContains(editFragment, "Operations.newItem(getActivity(),",
                "edit item creation uses display name");
        assertContains(editFragment, "modifiedByName(getActivity())",
                "tombstone restore uses display name");
        assertContains(checkingFragment, "Operations.touch(getActivity(), item)",
                "checking toggle uses display name");
        assertContains(dataOverviewFragment, "Operations.markDeleted(getActivity(), item)",
                "bulk delete uses display name");
        assertContains(importFragment, "Operations.restore(getActivity(), existing)",
                "import restore uses display name");
        assertContains(action, "Operations.touch(fragment.getActivity(), item)",
                "bulk checking actions use display name");
        assertContains(adapter, "getModifiedByName()", "export reads item modified_by.name");
    }

    private static void verifySyncMenuAndConflictUiSources() throws Exception {
        String mainMenu = readUtf8("sholi/src/main/res/menu/main.xml");
        String mainActivity = readUtf8("sholi/src/main/java/name/soulayrol/rhaa/sholi/MainActivity.java");
        String strings = readUtf8("sholi/src/main/res/values/strings.xml");
        String conflictDialog = readUtf8(
                "sholi/src/main/java/name/soulayrol/rhaa/sholi/SyncConflictDialogFragment.java");

        assertContains(mainMenu, "action_sync", "main menu sync trigger id");
        assertContains(mainMenu, "action_main_sync", "main menu sync trigger title");
        assertContains(mainActivity, "AndroidWebDavSyncRunner", "main activity sync runner wiring");
        assertContains(mainActivity, "new Thread", "main activity async network work");
        assertContains(mainActivity, "runOnUiThread", "main activity UI status handoff");
        assertContains(mainActivity, "SyncConflictDialogFragment", "main activity conflict UI handoff");
        assertContains(conflictDialog, "choose(", "conflict dialog saves resolution choice");
        assertContains(conflictDialog, "Use local", "conflict dialog local choice text");
        assertContains(conflictDialog, "Use remote", "conflict dialog remote choice text");
        assertContains(strings, "Sync", "sync status strings");
        assertContains(strings, "Changed fields", "conflict changed fields string");
        assertContains(strings, "modified_at", "conflict timestamp string");
        assertContains(strings, "Status", "conflict status string");
        assertContains(strings, "Deleted", "conflict deleted string");
        assertContains(strings, "modified_by.name", "conflict modifier string");
    }

    private static WebDavSyncController controller(
            RecordingTransport transport,
            RecordingLocalStore localStore,
            RecordingMetadataStore metadata) {
        return new WebDavSyncController(
                localStore,
                metadata,
                new StaticEndpointProvider(),
                new WebDavSyncController.DefaultEngineFactory(transport));
    }

    private static SyncConflict conflict(
            String syncId,
            SyncItem baseline,
            SyncItem local,
            SyncItem remote,
            String marker) {
        ArrayList<String> fields = new ArrayList<String>();
        if (local == null || remote == null) {
            fields.add("presence");
        } else {
            if (!equals(local.getName(), remote.getName())) {
                fields.add("name");
            }
            if (local.getStatus() != remote.getStatus()) {
                fields.add("status");
            }
            if (local.isDeleted() != remote.isDeleted()) {
                fields.add("deleted");
            }
        }
        return new SyncConflict(
                syncId,
                baseline,
                local,
                remote,
                marker,
                9000L,
                SyncConflict.STATUS_UNRESOLVED,
                fields);
    }

    private static SyncItem item(
            String syncId, String name, int status, boolean deleted, long modifiedAt, String modifiedBy) {
        return new SyncItem(syncId, name, status, deleted, modifiedAt, new ModifiedBy(modifiedBy, null));
    }

    private static SyncDocument document(SyncItem... items) {
        ArrayList<SyncItem> list = new ArrayList<SyncItem>();
        for (SyncItem item: items) {
            list.add(item);
        }
        return new SyncDocument(list);
    }

    private static String json(SyncDocument document) {
        return SyncDocumentJson.serialize(document);
    }

    private static SyncDocument parse(String json) {
        try {
            return SyncDocumentJson.parse(json);
        } catch (Exception e) {
            throw new AssertionError("Could not parse sync document", e);
        }
    }

    private static SyncItem byId(SyncDocument document, String syncId) {
        for (SyncItem item: document.getItems()) {
            if (syncId.equals(item.getSyncId())) {
                return item;
            }
        }
        throw new AssertionError("Missing sync_id " + syncId);
    }

    private static boolean containsId(SyncDocument document, String syncId) {
        for (SyncItem item: document.getItems()) {
            if (syncId.equals(item.getSyncId())) {
                return true;
            }
        }
        return false;
    }

    private static void assertDocumentEquals(SyncDocument expected, SyncDocument actual, String label) {
        if (expected == null || actual == null) {
            assertEquals(expected, actual, label);
            return;
        }
        assertEquals(json(expected), json(actual), label);
    }

    private static void assertItemEquals(SyncItem expected, SyncItem actual, String label) {
        assertEquals(expected.getSyncId(), actual.getSyncId(), label + " sync_id");
        assertEquals(expected.getName(), actual.getName(), label + " name");
        assertEquals(expected.getStatus(), actual.getStatus(), label + " status");
        assertEquals(expected.isDeleted(), actual.isDeleted(), label + " deleted");
        assertEquals(expected.getModifiedAt(), actual.getModifiedAt(), label + " modified_at");
        assertEquals(expected.getModifiedBy().getName(), actual.getModifiedBy().getName(),
                label + " modified_by.name");
    }

    private static void assertContains(String content, String expected, String label) {
        if (content == null || !content.contains(expected)) {
            throw new AssertionError("Expected " + label + " to contain " + expected);
        }
    }

    private static void assertDoesNotContain(String forbidden, String content, String label) {
        if (content != null && content.contains(forbidden)) {
            throw new AssertionError("The " + label + " leaks secret material");
        }
    }

    private static void assertMissingSideRejected(
            ResolvedConflictSyncService service,
            String syncId,
            ConflictChoice choice,
            String label) {
        try {
            service.choose(syncId, choice);
        } catch (IllegalStateException e) {
            assertContains(e.getMessage(), "missing", label + " message");
            assertDoesNotContain(SECRET, e.getMessage(), label + " message");
            return;
        }
        throw new AssertionError("Expected " + label + " to reject missing snapshot choice");
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Unexpected " + label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static boolean equals(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private static List<String> list(String... values) {
        ArrayList<String> list = new ArrayList<String>();
        for (String value: values) {
            list.add(value);
        }
        return list;
    }

    private static <T> List<T> list(T first, T second) {
        ArrayList<T> list = new ArrayList<T>();
        list.add(first);
        list.add(second);
        return list;
    }

    private static String readUtf8(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), "UTF-8");
    }

    private static final class StaticEndpointProvider implements WebDavSyncController.EndpointProvider {
        @Override
        public ConfiguredWebDavEndpoint requireEndpoint() {
            return new ConfiguredWebDavEndpoint(
                    new WebDavSyncProfile(
                            "https://cloud.example.net/dav/",
                            "alice",
                            "sholi/sync.json",
                            "Alice phone",
                            "alice-phone"),
                    new WebDavCredentials("alice", SECRET));
        }
    }

    private static final class RecordingTransport implements WebDavTransport {
        private final List<WebDavRequest> requests = new ArrayList<WebDavRequest>();
        private final List<ResponseAction> responses = new ArrayList<ResponseAction>();

        void respond(int statusCode, String etag, String body) {
            respondAndThen(statusCode, etag, body, null);
        }

        void respondAndThen(int statusCode, String etag, String body, Runnable afterResponse) {
            Map<String, String> headers = new LinkedHashMap<String, String>();
            if (etag != null) {
                headers.put("ETag", etag);
            }
            responses.add(new ResponseAction(new WebDavResponse(statusCode, headers, body), afterResponse));
        }

        @Override
        public WebDavResponse execute(WebDavRequest request) throws WebDavTransportException {
            requests.add(request);
            if (responses.isEmpty()) {
                throw new AssertionError("No queued WebDAV response for " + request.getMethod());
            }
            ResponseAction response = responses.remove(0);
            if (response.afterResponse != null) {
                response.afterResponse.run();
            }
            return response.response;
        }

        WebDavRequest request(int index) {
            return requests.get(index);
        }

        int countMethod(String method) {
            int count = 0;
            for (WebDavRequest request: requests) {
                if (method.equals(request.getMethod())) {
                    count++;
                }
            }
            return count;
        }

        List<String> methods() {
            ArrayList<String> methods = new ArrayList<String>();
            for (WebDavRequest request: requests) {
                methods.add(request.getMethod());
            }
            return methods;
        }

        private static final class ResponseAction {
            private final WebDavResponse response;
            private final Runnable afterResponse;

            ResponseAction(WebDavResponse response, Runnable afterResponse) {
                this.response = response;
                this.afterResponse = afterResponse;
            }
        }
    }

    private static final class RecordingLocalStore implements LocalSyncDocumentStore {
        private SyncDocument currentDocument;
        private final List<SyncDocument> appliedDocuments = new ArrayList<SyncDocument>();
        private final Map<String, Long> deletedSyncedAtBySyncId = new LinkedHashMap<String, Long>();

        RecordingLocalStore(SyncDocument currentDocument) {
            this.currentDocument = currentDocument;
        }

        @Override
        public SyncDocument loadCurrentDocument() {
            return currentDocument;
        }

        @Override
        public boolean isCurrentDocument(SyncDocument expectedDocument) {
            return json(currentDocument).equals(json(expectedDocument));
        }

        @Override
        public void applyDocument(SyncDocument document) {
            appliedDocuments.add(document);
            applyFullSnapshot(document, null, System.currentTimeMillis());
        }

        @Override
        public boolean applyDocumentIfCurrent(SyncDocument expectedDocument, SyncDocument document) {
            if (!isCurrentDocument(expectedDocument)) {
                return false;
            }
            appliedDocuments.add(document);
            applyFullSnapshot(document, expectedDocument, System.currentTimeMillis());
            return true;
        }

        private void applyFullSnapshot(SyncDocument document, SyncDocument expectedDocument, long now) {
            LinkedHashMap<String, SyncItem> retainedBySyncId = new LinkedHashMap<String, SyncItem>();
            LinkedHashMap<String, SyncItem> incomingBySyncId = new LinkedHashMap<String, SyncItem>();
            for (SyncItem syncItem: document.getItems()) {
                incomingBySyncId.put(syncItem.getSyncId(), syncItem);
                deletedSyncedAtBySyncId.remove(syncItem.getSyncId());
            }
            for (SyncItem syncItem: document.getItems()) {
                retainedBySyncId.put(syncItem.getSyncId(), syncItem);
            }
            for (SyncItem localItem: currentDocument.getItems()) {
                if (incomingBySyncId.containsKey(localItem.getSyncId())) {
                    continue;
                }
                if (shouldHardDeleteAbsentTombstone(localItem, expectedDocument, now)) {
                    deletedSyncedAtBySyncId.remove(localItem.getSyncId());
                    continue;
                }
                retainedBySyncId.put(localItem.getSyncId(), localItem);
            }
            currentDocument = new SyncDocument(new ArrayList<SyncItem>(retainedBySyncId.values()));
        }

        private boolean shouldHardDeleteAbsentTombstone(
                SyncItem localItem, SyncDocument expectedDocument, long now) {
            if (!localItem.isDeleted()) {
                return false;
            }
            Long deletedSyncedAt = deletedSyncedAtBySyncId.get(localItem.getSyncId());
            if (deletedSyncedAt != null
                    && now - deletedSyncedAt.longValue()
                    >= name.soulayrol.rhaa.sholi.sync.items.ItemSyncMetadata.TOMBSTONE_RETENTION_MILLIS) {
                return true;
            }
            return deletedSyncedAt != null
                    && expectedDocument != null
                    && containsDeletedId(expectedDocument, localItem.getSyncId());
        }

        private static boolean containsDeletedId(SyncDocument document, String syncId) {
            for (SyncItem item: document.getItems()) {
                if (syncId.equals(item.getSyncId()) && item.isDeleted()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean markDeletedSyncedAndCleanupIfCurrent(SyncDocument expectedDocument, long now) {
            if (!isCurrentDocument(expectedDocument)) {
                return false;
            }
            ArrayList<SyncItem> retained = new ArrayList<SyncItem>();
            for (SyncItem item: currentDocument.getItems()) {
                if (!item.isDeleted()) {
                    retained.add(item);
                    continue;
                }
                Long deletedSyncedAt = deletedSyncedAtBySyncId.get(item.getSyncId());
                if (deletedSyncedAt != null
                        && now - deletedSyncedAt.longValue()
                        >= name.soulayrol.rhaa.sholi.sync.items.ItemSyncMetadata.TOMBSTONE_RETENTION_MILLIS) {
                    deletedSyncedAtBySyncId.remove(item.getSyncId());
                    continue;
                }
                if (deletedSyncedAt == null) {
                    deletedSyncedAtBySyncId.put(item.getSyncId(), Long.valueOf(now));
                }
                retained.add(item);
            }
            currentDocument = new SyncDocument(retained);
            return true;
        }

        void setDeletedSyncedAt(String syncId, Long deletedSyncedAt) {
            deletedSyncedAtBySyncId.put(syncId, deletedSyncedAt);
        }

        Long deletedSyncedAt(String syncId) {
            return deletedSyncedAtBySyncId.get(syncId);
        }
    }

    private static final class RecordingMetadataStore implements SyncMetadataStore {
        private SyncDocument baselineDocument;
        private String remoteVersionMarker;
        private List<SyncConflict> conflicts = new ArrayList<SyncConflict>();
        private SyncDocument pendingMergedDocument;
        private SyncDocument pendingLocalDocument;

        RecordingMetadataStore(SyncDocument baselineDocument, String remoteVersionMarker) {
            this.baselineDocument = baselineDocument;
            this.remoteVersionMarker = remoteVersionMarker;
        }

        @Override
        public void runInTransaction(Runnable mutation) {
            mutation.run();
        }

        @Override
        public SyncDocument loadBaselineDocument() {
            return baselineDocument;
        }

        @Override
        public String loadRemoteVersionMarker() {
            return remoteVersionMarker;
        }

        @Override
        public void saveBaselineDocument(SyncDocument document) {
            baselineDocument = document;
        }

        @Override
        public void saveRemoteVersionMarker(String remoteVersionMarker) {
            this.remoteVersionMarker = remoteVersionMarker;
        }

        @Override
        public List<SyncConflict> loadConflicts() {
            return new ArrayList<SyncConflict>(conflicts);
        }

        @Override
        public SyncDocument loadPendingMergedDocument() {
            return pendingMergedDocument;
        }

        @Override
        public SyncDocument loadPendingLocalDocument() {
            return pendingLocalDocument;
        }

        @Override
        public void replaceConflicts(List<SyncConflict> conflicts) {
            this.conflicts = new ArrayList<SyncConflict>(conflicts);
        }

        @Override
        public void replacePendingConflictState(
                List<SyncConflict> conflicts,
                SyncDocument pendingMergedDocument,
                SyncDocument pendingLocalDocument) {
            this.conflicts = new ArrayList<SyncConflict>(conflicts);
            this.pendingMergedDocument = pendingMergedDocument;
            this.pendingLocalDocument = pendingLocalDocument;
        }

        @Override
        public void clearConflicts() {
            conflicts = new ArrayList<SyncConflict>();
            pendingMergedDocument = null;
            pendingLocalDocument = null;
        }
    }

    private static final class RecordingConflictUploader
            implements ResolvedConflictSyncService.ConflictUploader {
        private final String successMarker;
        private final Runnable afterUpload;
        private final List<Upload> uploads = new ArrayList<Upload>();

        RecordingConflictUploader(String successMarker) {
            this(successMarker, null);
        }

        RecordingConflictUploader(String successMarker, Runnable afterUpload) {
            this.successMarker = successMarker;
            this.afterUpload = afterUpload;
        }

        @Override
        public WebDavPutResult uploadResolvedDocument(SyncDocument document, String remoteVersionMarker) {
            uploads.add(new Upload(document, remoteVersionMarker));
            if (afterUpload != null) {
                afterUpload.run();
            }
            return WebDavPutResult.success(
                    name.soulayrol.rhaa.sholi.sync.webdav.WebDavEtag.classify(successMarker),
                    204);
        }

    }

    private static final class Upload {
        private final SyncDocument document;
        private final String marker;

        Upload(SyncDocument document, String marker) {
            this.document = document;
            this.marker = marker;
        }
    }
}
