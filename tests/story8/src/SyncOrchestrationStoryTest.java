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
import name.soulayrol.rhaa.sholi.sync.orchestration.ResolvedConflictAwareSyncRunner;
import name.soulayrol.rhaa.sholi.sync.orchestration.ResolvedConflictSyncService;
import name.soulayrol.rhaa.sholi.sync.orchestration.ConflictSideSelection;
import name.soulayrol.rhaa.sholi.sync.orchestration.ConflictListSelection;
import name.soulayrol.rhaa.sholi.sync.orchestration.SyncConflictDisplayModel;
import name.soulayrol.rhaa.sholi.sync.orchestration.WebDavSyncController;
import name.soulayrol.rhaa.sholi.sync.settings.ConfiguredWebDavEndpoint;
import name.soulayrol.rhaa.sholi.sync.webdav.LocalSyncDocumentStore;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavEtag;
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
        verifyConflictSideSelectionBehavior();
        verifyConflictListSelectionCompletenessAndStableChoices();
        verifyDisplayModelAndDialogDoNotOfferMissingSideChoices();
        verifyChoosingLocalOrRemoteResolvesCompleteItemVersion();
        verifyMissingSideResolutionIsRejectedAndConflictStaysUnresolved();
        verifyBatchConflictChoicesAreAtomicWhenLaterChoiceInvalid();
        verifyRemainingUnresolvedConflictBlocksResumeUpload();
        verifyAllResolvedConflictsProduceAndUploadResolvedDocument();
        verifyResolvedConflictsPreservePendingNonConflictingRemoteChanges();
        verifyInitialUnsafeConflictsPreservePendingRemoteOnlyItemsOnResume();
        verifyLocalEditToPendingNonConflictBeforeResumeBlocksStaleUpload();
        verifyLocalEditAfterConflictCaptureBeforeResumeDoesNotUploadStaleResolution();
        verifyLocalEditDuringResolvedConflictUploadDoesNotApplyOrRecordStaleResolution();
        verifyUnsafeResolvedConflictMarkerRequiresConfirmationWithoutUpload();
        verifyResolvedConflictPreconditionMessageIncludesEtags();
        verifyResolvedConflictGzipMarkerUsesCanonicalUploadAndDiagnostics();
        verifyStoredChoicesUseStaleAwareFallbacks();
        verifyStaleResolvedPreconditionFallsBackToNormalSync();
        verifyStaleResolvedLocalChangedFallsBackToNormalSync();
        verifySuccessfulResolvedResumeDoesNotRunNormalSync();
        verifyUnresolvedConflictsDoNotRunResolvedResume();
        verifyResolvedUploadClearsConflictsBeforeSubsequentSync();
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
        assertEquals(false, model.isLocalNew(), "older local side not marked new");
        assertEquals(true, model.isRemoteNew(), "newer remote side marked new");

        SyncConflict localOnly = conflict(
                "sync-local-only",
                item("sync-local-only", "Tea", 1, false, 10L, "base"),
                item("sync-local-only", "Green tea", 1, false, 20L, "phone"),
                null,
                "\"v2\"");
        SyncConflict sameAge = conflict(
                "sync-same-age",
                item("sync-same-age", "Juice", 1, false, 10L, "base"),
                item("sync-same-age", "Apple juice", 1, false, 40L, "phone"),
                item("sync-same-age", "Orange juice", 1, false, 40L, "tablet"),
                "\"v2\"");

        assertEquals(true, SyncConflictDisplayModel.from(localOnly).isLocalNew(),
                "only present local side marked new");
        assertEquals(false, SyncConflictDisplayModel.from(localOnly).isRemoteNew(),
                "missing remote side not marked new");
        assertEquals(false, SyncConflictDisplayModel.from(sameAge).isLocalNew(),
                "equal local timestamp not marked new");
        assertEquals(false, SyncConflictDisplayModel.from(sameAge).isRemoteNew(),
                "equal remote timestamp not marked new");
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

    private static void verifyConflictSideSelectionBehavior() {
        ConflictSideSelection bothSides = new ConflictSideSelection(true, true);

        assertEquals(true, bothSides.canChooseLocal(), "both sides local available");
        assertEquals(true, bothSides.canChooseRemote(), "both sides remote available");
        assertEquals(false, bothSides.hasSelection(), "both sides start without selection");
        assertEquals(null, bothSides.getSelectedChoice(), "both sides initial selected choice");

        assertEquals(true, bothSides.selectLocal(), "local tap selection accepted");
        assertEquals(ConflictChoice.LOCAL, bothSides.getSelectedChoice(), "local tap selected local");
        assertEquals(true, bothSides.hasSelection(), "local tap creates selection");
        assertEquals(true, bothSides.selectRemote(), "remote tap selection accepted");
        assertEquals(ConflictChoice.REMOTE, bothSides.getSelectedChoice(), "remote tap selected remote");

        ConflictSideSelection swipes = new ConflictSideSelection(true, true);
        assertEquals(true, swipes.selectFromSwipeDelta(-42.0f), "swipe left accepted");
        assertEquals(ConflictChoice.LOCAL, swipes.getSelectedChoice(), "swipe left selects local");
        assertEquals(true, swipes.selectFromSwipeDelta(42.0f), "swipe right accepted");
        assertEquals(ConflictChoice.REMOTE, swipes.getSelectedChoice(), "swipe right selects remote");
        assertEquals(false, swipes.selectFromSwipeDelta(0.0f), "zero swipe ignored");
        assertEquals(ConflictChoice.REMOTE, swipes.getSelectedChoice(), "ignored swipe preserves selection");

        ConflictSideSelection localOnly = new ConflictSideSelection(true, false);
        assertEquals(true, localOnly.hasSelection(), "local-only conflict auto-selects present side");
        assertEquals(ConflictChoice.LOCAL, localOnly.getSelectedChoice(), "local-only selects local");
        assertEquals(false, localOnly.selectRemote(), "missing remote tap rejected");
        assertEquals(ConflictChoice.LOCAL, localOnly.getSelectedChoice(), "rejected remote keeps local selected");
        assertEquals(false, localOnly.selectFromSwipeDelta(42.0f), "swipe toward missing remote rejected");
        assertEquals(ConflictChoice.LOCAL, localOnly.getSelectedChoice(), "missing remote swipe keeps local selected");

        ConflictSideSelection remoteOnly = new ConflictSideSelection(false, true);
        assertEquals(true, remoteOnly.hasSelection(), "remote-only conflict auto-selects present side");
        assertEquals(ConflictChoice.REMOTE, remoteOnly.getSelectedChoice(), "remote-only selects remote");
        assertEquals(false, remoteOnly.selectLocal(), "missing local tap rejected");
        assertEquals(ConflictChoice.REMOTE, remoteOnly.getSelectedChoice(), "rejected local keeps remote selected");
        assertEquals(false, remoteOnly.selectFromSwipeDelta(-42.0f), "swipe toward missing local rejected");
        assertEquals(ConflictChoice.REMOTE, remoteOnly.getSelectedChoice(), "missing local swipe keeps remote selected");

        ConflictSideSelection neitherSide = new ConflictSideSelection(false, false);
        assertEquals(false, neitherSide.hasSelection(), "no-side conflict starts without selection");
        assertEquals(false, neitherSide.selectLocal(), "no-side local tap rejected");
        assertEquals(false, neitherSide.selectRemote(), "no-side remote tap rejected");
        assertEquals(false, neitherSide.selectFromSwipeDelta(-42.0f), "no-side swipe rejected");
        assertEquals(null, neitherSide.getSelectedChoice(), "no-side rejected choices keep no selection");
    }

    private static void verifyConflictListSelectionCompletenessAndStableChoices() {
        SyncConflict first = conflict(
                "sync-list-a",
                item("sync-list-a", "Apples", 1, false, 10L, "base"),
                item("sync-list-a", "Green apples", 1, false, 20L, "phone"),
                item("sync-list-a", "Red apples", 1, false, 30L, "tablet"),
                "\"v2\"");
        SyncConflict second = conflict(
                "sync-list-b",
                item("sync-list-b", "Butter", 1, false, 10L, "base"),
                item("sync-list-b", "Butter", 2, false, 20L, "phone"),
                item("sync-list-b", "Butter", 0, false, 30L, "tablet"),
                "\"v2\"");
        SyncConflict localOnly = conflict(
                "sync-list-local-only",
                item("sync-list-local-only", "Coffee", 1, false, 10L, "base"),
                item("sync-list-local-only", "Decaf coffee", 1, false, 20L, "phone"),
                null,
                "\"v2\"");
        SyncConflict remoteOnly = conflict(
                "sync-list-remote-only",
                item("sync-list-remote-only", "Tea", 1, false, 10L, "base"),
                null,
                item("sync-list-remote-only", "Black tea", 1, false, 30L, "tablet"),
                "\"v2\"");

        ConflictListSelection selection = ConflictListSelection.fromConflicts(
                list(first, second, localOnly, remoteOnly));

        assertEquals(false, selection.isComplete(), "multi-conflict starts incomplete");
        assertEquals(ConflictChoice.LOCAL, selection.getSelectedChoice("sync-list-local-only"),
                "local-only row auto-selects local");
        assertEquals(ConflictChoice.REMOTE, selection.getSelectedChoice("sync-list-remote-only"),
                "remote-only row auto-selects remote");
        assertEquals(false, selection.select("sync-list-local-only", ConflictChoice.REMOTE),
                "missing remote side rejected in list");
        assertEquals(ConflictChoice.LOCAL, selection.getSelectedChoice("sync-list-local-only"),
                "rejected missing side keeps auto-selected local");
        assertEquals(false, selection.select("sync-list-missing", ConflictChoice.LOCAL),
                "unknown conflict row rejected");

        assertEquals(true, selection.select("sync-list-a", ConflictChoice.LOCAL),
                "first row local tap accepted");
        assertEquals(false, selection.isComplete(), "one unresolved row keeps confirm disabled");
        assertEquals(true, selection.selectFromSwipeDelta("sync-list-b", 42.0f),
                "second row swipe right accepted");
        assertEquals(ConflictChoice.REMOTE, selection.getSelectedChoice("sync-list-b"),
                "swipe right selects remote in list row");
        assertEquals(true, selection.isComplete(), "all rows selected enables confirm");

        List<ConflictListSelection.SelectedChoice> choices = selection.choices();
        assertEquals(4, choices.size(), "complete list choice count");
        assertSelectedChoice(choices.get(0), "sync-list-a", ConflictChoice.LOCAL,
                "first stable choice");
        assertSelectedChoice(choices.get(1), "sync-list-b", ConflictChoice.REMOTE,
                "second stable choice");
        assertSelectedChoice(choices.get(2), "sync-list-local-only", ConflictChoice.LOCAL,
                "third stable auto-choice");
        assertSelectedChoice(choices.get(3), "sync-list-remote-only", ConflictChoice.REMOTE,
                "fourth stable auto-choice");
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

    private static void verifyBatchConflictChoicesAreAtomicWhenLaterChoiceInvalid() {
        SyncConflict first = conflict(
                "sync-batch-valid",
                item("sync-batch-valid", "Apples", 1, false, 10L, "base"),
                item("sync-batch-valid", "Green apples", 1, false, 20L, "phone"),
                item("sync-batch-valid", "Red apples", 1, false, 30L, "tablet"),
                "\"v2\"");
        SyncConflict second = conflict(
                "sync-batch-invalid",
                item("sync-batch-invalid", "Tea", 1, false, 10L, "base"),
                null,
                item("sync-batch-invalid", "Black tea", 1, false, 30L, "tablet"),
                "\"v2\"");
        RecordingLocalStore localStore = new RecordingLocalStore(document(first.getLocalItem()));
        RecordingMetadataStore metadata = new RecordingMetadataStore(
                document(first.getBaselineItem(), second.getBaselineItem()), "\"v1\"");
        metadata.replaceConflicts(list(first, second));
        RecordingConflictUploader uploader = new RecordingConflictUploader("\"v3\"");
        ResolvedConflictSyncService service = new ResolvedConflictSyncService(localStore, metadata, uploader);

        boolean rejected = false;
        try {
            service.chooseAll(list(
                    new ConflictListSelection.SelectedChoice("sync-batch-valid", ConflictChoice.LOCAL),
                    new ConflictListSelection.SelectedChoice("sync-batch-invalid", ConflictChoice.LOCAL)));
        } catch (IllegalStateException e) {
            rejected = true;
            assertContains(e.getMessage(), "missing", "batch invalid choice message");
            assertDoesNotContain(SECRET, e.getMessage(), "batch invalid choice message");
        }

        List<SyncConflict> conflicts = metadata.loadConflicts();
        assertEquals(true, rejected, "batch invalid choice rejected");
        assertEquals(2, conflicts.size(), "batch invalid conflict count unchanged");
        assertEquals("sync-batch-valid", conflicts.get(0).getSyncId(),
                "batch invalid first stable order");
        assertEquals("sync-batch-invalid", conflicts.get(1).getSyncId(),
                "batch invalid second stable order");
        assertEquals(SyncConflict.STATUS_UNRESOLVED, conflicts.get(0).getStatus(),
                "batch invalid leaves earlier valid choice unresolved");
        assertEquals(SyncConflict.STATUS_UNRESOLVED, conflicts.get(1).getStatus(),
                "batch invalid leaves later invalid choice unresolved");
        assertEquals(0, uploader.uploads.size(), "batch invalid does not upload");
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

    private static void verifyInitialUnsafeConflictsPreservePendingRemoteOnlyItemsOnResume() {
        SyncItem localA = item("sync-initial-a", "Oat milk", 1, false, 20L, "phone");
        SyncItem remoteA = item("sync-initial-a", "Soy milk", 1, false, 30L, "tablet");
        SyncItem localOnly = item("sync-initial-local", "Crackers", 1, false, 25L, "phone");
        SyncItem remoteOnly = item("sync-initial-remote", "Bread", 2, false, 40L, "tablet");
        SyncDocument local = document(localA, localOnly);
        SyncDocument remote = document(remoteA, remoteOnly);
        SyncDocument expectedResolved = document(localOnly, remoteOnly, localA);
        RecordingTransport transport = new RecordingTransport();
        transport.respond(200, "\"head-v2\"", null);
        transport.respond(200, "\"get-v1\"", json(remote));
        RecordingLocalStore localStore = new RecordingLocalStore(local);
        RecordingMetadataStore metadata = new RecordingMetadataStore(null, null);

        WebDavSyncResult conflictResult = controller(transport, localStore, metadata).synchronize(1340L);
        RecordingConflictUploader uploader = new RecordingConflictUploader("\"v3\"");
        ResolvedConflictSyncService service = new ResolvedConflictSyncService(localStore, metadata, uploader);

        assertEquals(WebDavSyncResult.Status.CONFLICTS, conflictResult.getStatus(),
                "initial unsafe conflict capture status");
        assertEquals(list("HEAD", "GET"), transport.methods(),
                "initial unsafe conflict capture request sequence");
        assertEquals(0, transport.countMethod("PUT"), "initial unsafe conflict capture does not upload");
        assertDocumentEquals(document(localOnly, remoteOnly), metadata.loadPendingMergedDocument(),
                "initial unsafe conflict pending merged keeps remote-only item");
        assertDocumentEquals(local, metadata.loadPendingLocalDocument(),
                "initial unsafe conflict records expected local document");

        service.choose("sync-initial-a", ConflictChoice.LOCAL);
        ResolvedConflictSyncService.UploadPlan plan = service.prepareUpload();
        WebDavSyncResult result = service.resumeResolvedConflicts();

        assertEquals(ResolvedConflictSyncService.UploadPlan.Status.READY, plan.getStatus(),
                "initial unsafe resolved upload readiness");
        assertEquals("\"get-v1\"", plan.getRemoteVersionMarker(),
                "initial unsafe resolved upload marker from downloaded snapshot");
        assertItemEquals(remoteOnly, byId(plan.getDocument(), "sync-initial-remote"),
                "initial unsafe resolved preserves pending remote-only item");
        assertEquals(WebDavSyncResult.Status.UPDATED_REMOTE, result.getStatus(),
                "initial unsafe resolved upload status");
        assertEquals(1, uploader.uploads.size(), "initial unsafe resolved performs upload");
        assertDocumentEquals(expectedResolved, uploader.uploads.get(0).document,
                "initial unsafe resolved upload body preserves pending remote-only item");
        assertDocumentEquals(expectedResolved, localStore.currentDocument,
                "initial unsafe resolved local apply preserves pending remote-only item");
        assertDocumentEquals(expectedResolved, metadata.loadBaselineDocument(),
                "initial unsafe resolved baseline preserves pending remote-only item");
        assertEquals("\"v3\"", metadata.loadRemoteVersionMarker(),
                "initial unsafe resolved records new marker");
        assertEquals(0, metadata.loadConflicts().size(), "initial unsafe resolved clears conflicts");
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

    private static void verifyResolvedConflictPreconditionMessageIncludesEtags() {
        assertResolvedConflictPreconditionMessageIncludesEtags(
                "\"v3\"",
                "response ETag: \"v3\"",
                "resolved precondition with response etag");
        assertResolvedConflictPreconditionMessageIncludesEtags(
                null,
                "response ETag: <missing>",
                "resolved precondition missing response etag");
    }

    private static void assertResolvedConflictPreconditionMessageIncludesEtags(
            String responseEtag,
            String expectedResponseMessage,
            String label) {
        SyncConflict conflict = conflict(
                "sync-resolved-etag",
                item("sync-resolved-etag", "Apples", 1, false, 10L, "base"),
                item("sync-resolved-etag", "Green apples", 1, false, 20L, "phone"),
                item("sync-resolved-etag", "Red apples", 1, false, 30L, "tablet"),
                "\"v2\"");
        RecordingLocalStore localStore = new RecordingLocalStore(document(conflict.getLocalItem()));
        RecordingMetadataStore metadata = new RecordingMetadataStore(
                document(conflict.getBaselineItem()), "\"v1\"");
        metadata.replaceConflicts(Collections.singletonList(conflict));
        FixedConflictUploader uploader = new FixedConflictUploader(
                WebDavPutResult.preconditionFailed(WebDavEtag.classify(responseEtag), 412));
        ResolvedConflictSyncService service = new ResolvedConflictSyncService(localStore, metadata, uploader);

        service.choose("sync-resolved-etag", ConflictChoice.LOCAL);
        WebDavSyncResult result = service.resumeResolvedConflicts();

        assertEquals(WebDavSyncResult.Status.PRECONDITION_FAILED, result.getStatus(), label + " status");
        assertEquals(1, uploader.uploads.size(), label + " upload count");
        assertEquals("\"v2\"", uploader.uploads.get(0).marker, label + " expected upload marker");
        assertContains(result.getMessage(), "expected ETag: \"v2\"", label + " expected marker message");
        assertContains(result.getMessage(), expectedResponseMessage, label + " response marker message");
        assertDoesNotContain(SECRET, result.getMessage(), label + " message");
    }

    private static void verifyResolvedConflictGzipMarkerUsesCanonicalUploadAndDiagnostics() {
        SyncConflict conflict = conflict(
                "sync-resolved-gzip-etag",
                item("sync-resolved-gzip-etag", "Coffee", 1, false, 10L, "base"),
                item("sync-resolved-gzip-etag", "Decaf coffee", 1, false, 20L, "phone"),
                item("sync-resolved-gzip-etag", "Espresso", 1, false, 30L, "tablet"),
                "\"v2-gzip\"");
        RecordingLocalStore localStore = new RecordingLocalStore(document(conflict.getLocalItem()));
        RecordingMetadataStore metadata = new RecordingMetadataStore(
                document(conflict.getBaselineItem()), "\"v1\"");
        metadata.replaceConflicts(Collections.singletonList(conflict));
        FixedConflictUploader uploader = new FixedConflictUploader(
                WebDavPutResult.preconditionFailed(WebDavEtag.classify("\"v4-gzip\""), 412));
        ResolvedConflictSyncService service = new ResolvedConflictSyncService(localStore, metadata, uploader);

        service.choose("sync-resolved-gzip-etag", ConflictChoice.LOCAL);
        ResolvedConflictSyncService.UploadPlan plan = service.prepareUpload();
        WebDavSyncResult result = service.resumeResolvedConflicts();

        assertEquals(ResolvedConflictSyncService.UploadPlan.Status.READY, plan.getStatus(),
                "resolved gzip plan status");
        assertEquals("\"v2\"", plan.getRemoteVersionMarker(), "resolved gzip plan marker");
        assertEquals(WebDavSyncResult.Status.PRECONDITION_FAILED, result.getStatus(),
                "resolved gzip 412 status");
        assertEquals(1, uploader.uploads.size(), "resolved gzip upload count");
        assertEquals("\"v2\"", uploader.uploads.get(0).marker, "resolved gzip upload marker");
        assertContains(result.getMessage(), "expected ETag: \"v2\"",
                "resolved gzip expected marker diagnostic");
        assertDoesNotContain("\"v2-gzip\"", result.getMessage(),
                "resolved gzip diagnostics omit raw expected marker");
    }

    private static void verifyStoredChoicesUseStaleAwareFallbacks() {
        verifyStoredChoicesStalePreconditionFallsBackToFreshConflicts();
        verifyStoredChoicesStaleLocalChangedFallsBackToFreshConflicts();
    }

    private static void verifyStoredChoicesStalePreconditionFallsBackToFreshConflicts() {
        SyncConflict stale = conflict(
                "sync-stored-precondition",
                item("sync-stored-precondition", "Apples", 1, false, 10L, "base"),
                item("sync-stored-precondition", "Green apples", 1, false, 20L, "phone"),
                item("sync-stored-precondition", "Red apples", 1, false, 30L, "tablet"),
                "\"v2\"");
        final SyncConflict fresh = conflict(
                "sync-stored-precondition-fresh",
                item("sync-stored-precondition-fresh", "Tea", 1, false, 10L, "base"),
                item("sync-stored-precondition-fresh", "Green tea", 1, false, 40L, "phone"),
                item("sync-stored-precondition-fresh", "Black tea", 1, false, 50L, "tablet"),
                "\"v4\"");
        RecordingLocalStore localStore = new RecordingLocalStore(document(stale.getLocalItem()));
        final RecordingMetadataStore metadata = new RecordingMetadataStore(
                document(stale.getBaselineItem()), "\"v1\"");
        metadata.replaceConflicts(Collections.singletonList(stale));
        final FixedConflictUploader uploader = new FixedConflictUploader(
                WebDavPutResult.preconditionFailed(WebDavEtag.classify("\"v6\""), 412));
        final ResolvedConflictSyncService service = new ResolvedConflictSyncService(localStore, metadata, uploader);

        service.chooseAll(Collections.singletonList(
                new ConflictListSelection.SelectedChoice("sync-stored-precondition", ConflictChoice.LOCAL)));
        final int[] normalCalls = new int[] {0};
        ResolvedConflictAwareSyncRunner runner = new ResolvedConflictAwareSyncRunner(
                metadata,
                new ResolvedConflictAwareSyncRunner.NormalSyncOperation() {
                    @Override
                    public WebDavSyncResult synchronize() {
                        normalCalls[0]++;
                        metadata.replaceConflicts(Collections.singletonList(fresh));
                        return WebDavSyncResult.conflicts(metadata.loadConflicts());
                    }
                },
                new ResolvedConflictAwareSyncRunner.ResolvedConflictResumeOperation() {
                    @Override
                    public WebDavSyncResult resumeResolvedConflicts() {
                        return service.resumeResolvedConflicts();
                    }
                });

        WebDavSyncResult result = runner.synchronizeOrResume();

        assertEquals(1, uploader.uploads.size(),
                "stored precondition attempts resolved upload before fallback");
        assertEquals(1, normalCalls[0], "stored precondition runs normal sync fallback");
        assertEquals(WebDavSyncResult.Status.CONFLICTS, result.getStatus(),
                "stored precondition fallback returns conflicts");
        assertContains(result.getMessage(), "expected ETag: \"v2\"",
                "stored precondition preserves expected etag diagnostics");
        assertContains(result.getMessage(), "response ETag: \"v6\"",
                "stored precondition preserves response etag diagnostics");
        assertContains(result.getMessage(), "synchronization stopped with fresh conflicts",
                "stored precondition explains fresh conflicts after fallback");
        assertDoesNotContain(SECRET, result.getMessage(),
                "stored precondition fallback conflict message");
        assertEquals("sync-stored-precondition-fresh", result.getConflicts().get(0).getSyncId(),
                "stored precondition exposes fresh conflict");
        assertEquals(SyncConflict.STATUS_UNRESOLVED, metadata.loadConflicts().get(0).getStatus(),
                "stored precondition stores fresh unresolved conflict");
    }

    private static void verifyStoredChoicesStaleLocalChangedFallsBackToFreshConflicts() {
        SyncConflict stale = conflict(
                "sync-stored-local-changed",
                item("sync-stored-local-changed", "Milk", 1, false, 10L, "base"),
                item("sync-stored-local-changed", "Oat milk", 1, false, 20L, "phone"),
                item("sync-stored-local-changed", "Soy milk", 1, false, 30L, "tablet"),
                "\"v2\"");
        final SyncConflict fresh = conflict(
                "sync-stored-local-changed-fresh",
                item("sync-stored-local-changed-fresh", "Coffee", 1, false, 10L, "base"),
                item("sync-stored-local-changed-fresh", "Decaf", 1, false, 40L, "phone"),
                item("sync-stored-local-changed-fresh", "Espresso", 1, false, 50L, "tablet"),
                "\"v5\"");
        RecordingLocalStore localStore = new RecordingLocalStore(document(stale.getLocalItem()));
        final RecordingMetadataStore metadata = new RecordingMetadataStore(
                document(stale.getBaselineItem()), "\"v1\"");
        metadata.replaceConflicts(Collections.singletonList(stale));
        final FixedConflictUploader uploader = new FixedConflictUploader(
                WebDavPutResult.success(
                        name.soulayrol.rhaa.sholi.sync.webdav.WebDavEtag.classify("\"v3\""),
                        204));
        final ResolvedConflictSyncService service = new ResolvedConflictSyncService(localStore, metadata, uploader);

        service.chooseAll(Collections.singletonList(
                new ConflictListSelection.SelectedChoice("sync-stored-local-changed", ConflictChoice.REMOTE)));
        localStore.currentDocument = document(
                item("sync-stored-local-changed", "Almond milk", 1, false, 60L, "phone"));
        final int[] normalCalls = new int[] {0};
        ResolvedConflictAwareSyncRunner runner = new ResolvedConflictAwareSyncRunner(
                metadata,
                new ResolvedConflictAwareSyncRunner.NormalSyncOperation() {
                    @Override
                    public WebDavSyncResult synchronize() {
                        normalCalls[0]++;
                        metadata.replaceConflicts(Collections.singletonList(fresh));
                        return WebDavSyncResult.conflicts(metadata.loadConflicts());
                    }
                },
                new ResolvedConflictAwareSyncRunner.ResolvedConflictResumeOperation() {
                    @Override
                    public WebDavSyncResult resumeResolvedConflicts() {
                        return service.resumeResolvedConflicts();
                    }
                });

        WebDavSyncResult result = runner.synchronizeOrResume();

        assertEquals(0, uploader.uploads.size(),
                "stored local changed skips stale resolved upload before fallback");
        assertEquals(1, normalCalls[0], "stored local changed runs normal sync fallback");
        assertEquals(WebDavSyncResult.Status.CONFLICTS, result.getStatus(),
                "stored local changed fallback returns conflicts");
        assertContains(result.getMessage(), "Local items changed during resolved conflict synchronization",
                "stored local changed preserves stale diagnostic");
        assertContains(result.getMessage(), "synchronization stopped with fresh conflicts",
                "stored local changed explains fresh conflicts after fallback");
        assertEquals("sync-stored-local-changed-fresh", result.getConflicts().get(0).getSyncId(),
                "stored local changed exposes fresh conflict");
        assertEquals(SyncConflict.STATUS_UNRESOLVED, metadata.loadConflicts().get(0).getStatus(),
                "stored local changed stores fresh unresolved conflict");
    }

    private static void verifyStaleResolvedPreconditionFallsBackToNormalSync() {
        SyncConflict staleResolved = conflict(
                "sync-stale-precondition",
                item("sync-stale-precondition", "Apples", 1, false, 10L, "base"),
                item("sync-stale-precondition", "Green apples", 1, false, 20L, "phone"),
                item("sync-stale-precondition", "Red apples", 1, false, 30L, "tablet"),
                "\"v2\"").withStatus(SyncConflict.STATUS_RESOLVED_LOCAL);
        final SyncConflict freshUnresolved = conflict(
                "sync-fresh-precondition",
                item("sync-fresh-precondition", "Tea", 1, false, 10L, "base"),
                item("sync-fresh-precondition", "Green tea", 1, false, 40L, "phone"),
                item("sync-fresh-precondition", "Black tea", 1, false, 50L, "tablet"),
                "\"v4\"");
        final RecordingMetadataStore metadata = new RecordingMetadataStore(
                document(staleResolved.getBaselineItem()), "\"v1\"");
        metadata.replaceConflicts(Collections.singletonList(staleResolved));
        final int[] normalCalls = new int[] {0};
        final int[] resumeCalls = new int[] {0};
        ResolvedConflictAwareSyncRunner runner = new ResolvedConflictAwareSyncRunner(
                metadata,
                new ResolvedConflictAwareSyncRunner.NormalSyncOperation() {
                    @Override
                    public WebDavSyncResult synchronize() {
                        normalCalls[0]++;
                        metadata.replaceConflicts(Collections.singletonList(freshUnresolved));
                        return WebDavSyncResult.conflicts(metadata.loadConflicts());
                    }
                },
                new ResolvedConflictAwareSyncRunner.ResolvedConflictResumeOperation() {
                    @Override
                    public WebDavSyncResult resumeResolvedConflicts() {
                        resumeCalls[0]++;
                        return WebDavSyncResult.status(
                                WebDavSyncResult.Status.PRECONDITION_FAILED,
                                "Remote sync document changed before resolved conflicts could upload");
                    }
                });

        WebDavSyncResult result = runner.synchronizeOrResume();

        assertEquals(1, resumeCalls[0], "stale precondition resume call count");
        assertEquals(1, normalCalls[0], "stale precondition normal sync fallback count");
        assertEquals(WebDavSyncResult.Status.CONFLICTS, result.getStatus(),
                "stale precondition fallback returns normal sync result");
        assertContains(result.getMessage(), "Remote sync document changed before resolved conflicts could upload",
                "stale precondition fallback preserves stale message");
        assertContains(result.getMessage(), "synchronization stopped with fresh conflicts",
                "stale precondition fallback explains fresh conflicts");
        assertEquals("sync-fresh-precondition", result.getConflicts().get(0).getSyncId(),
                "stale precondition exposes fresh conflict");
        assertEquals(SyncConflict.STATUS_UNRESOLVED, metadata.loadConflicts().get(0).getStatus(),
                "stale precondition stores fresh unresolved conflict");
    }

    private static void verifyStaleResolvedLocalChangedFallsBackToNormalSync() {
        SyncConflict staleResolved = conflict(
                "sync-stale-local-changed",
                item("sync-stale-local-changed", "Milk", 1, false, 10L, "base"),
                item("sync-stale-local-changed", "Oat milk", 1, false, 20L, "phone"),
                item("sync-stale-local-changed", "Soy milk", 1, false, 30L, "tablet"),
                "\"v2\"").withStatus(SyncConflict.STATUS_RESOLVED_REMOTE);
        final SyncConflict freshUnresolved = conflict(
                "sync-fresh-local-changed",
                item("sync-fresh-local-changed", "Coffee", 1, false, 10L, "base"),
                item("sync-fresh-local-changed", "Decaf", 1, false, 40L, "phone"),
                item("sync-fresh-local-changed", "Espresso", 1, false, 50L, "tablet"),
                "\"v5\"");
        final RecordingMetadataStore metadata = new RecordingMetadataStore(
                document(staleResolved.getBaselineItem()), "\"v1\"");
        metadata.replaceConflicts(Collections.singletonList(staleResolved));
        final int[] normalCalls = new int[] {0};
        final int[] resumeCalls = new int[] {0};
        ResolvedConflictAwareSyncRunner runner = new ResolvedConflictAwareSyncRunner(
                metadata,
                new ResolvedConflictAwareSyncRunner.NormalSyncOperation() {
                    @Override
                    public WebDavSyncResult synchronize() {
                        normalCalls[0]++;
                        metadata.replaceConflicts(Collections.singletonList(freshUnresolved));
                        return WebDavSyncResult.conflicts(metadata.loadConflicts());
                    }
                },
                new ResolvedConflictAwareSyncRunner.ResolvedConflictResumeOperation() {
                    @Override
                    public WebDavSyncResult resumeResolvedConflicts() {
                        resumeCalls[0]++;
                        return WebDavSyncResult.status(
                                WebDavSyncResult.Status.LOCAL_CHANGED,
                                "Local items changed during synchronization; please retry");
                    }
                });

        WebDavSyncResult result = runner.synchronizeOrResume();

        assertEquals(1, resumeCalls[0], "stale local changed resume call count");
        assertEquals(1, normalCalls[0], "stale local changed normal sync fallback count");
        assertEquals(WebDavSyncResult.Status.CONFLICTS, result.getStatus(),
                "stale local changed fallback returns normal sync result");
        assertContains(result.getMessage(), "Local items changed during synchronization; please retry",
                "stale local changed fallback preserves stale message");
        assertContains(result.getMessage(), "synchronization stopped with fresh conflicts",
                "stale local changed fallback explains fresh conflicts");
        assertEquals("sync-fresh-local-changed", result.getConflicts().get(0).getSyncId(),
                "stale local changed exposes fresh conflict");
        assertEquals(SyncConflict.STATUS_UNRESOLVED, metadata.loadConflicts().get(0).getStatus(),
                "stale local changed stores fresh unresolved conflict");
    }

    private static void verifySuccessfulResolvedResumeDoesNotRunNormalSync() {
        SyncConflict resolved = conflict(
                "sync-resolved-success",
                item("sync-resolved-success", "Butter", 1, false, 10L, "base"),
                item("sync-resolved-success", "Butter", 2, false, 20L, "phone"),
                item("sync-resolved-success", "Butter", 0, false, 30L, "tablet"),
                "\"v2\"").withStatus(SyncConflict.STATUS_RESOLVED_LOCAL);
        RecordingMetadataStore metadata = new RecordingMetadataStore(
                document(resolved.getBaselineItem()), "\"v1\"");
        metadata.replaceConflicts(Collections.singletonList(resolved));
        final int[] normalCalls = new int[] {0};
        final int[] resumeCalls = new int[] {0};
        ResolvedConflictAwareSyncRunner runner = new ResolvedConflictAwareSyncRunner(
                metadata,
                new ResolvedConflictAwareSyncRunner.NormalSyncOperation() {
                    @Override
                    public WebDavSyncResult synchronize() {
                        normalCalls[0]++;
                        return WebDavSyncResult.status(WebDavSyncResult.Status.UP_TO_DATE,
                                "Normal sync should not run after successful resolved upload");
                    }
                },
                new ResolvedConflictAwareSyncRunner.ResolvedConflictResumeOperation() {
                    @Override
                    public WebDavSyncResult resumeResolvedConflicts() {
                        resumeCalls[0]++;
                        return WebDavSyncResult.status(WebDavSyncResult.Status.UPDATED_REMOTE,
                                "Uploaded resolved synchronization conflicts");
                    }
                });

        WebDavSyncResult result = runner.synchronizeOrResume();

        assertEquals(WebDavSyncResult.Status.UPDATED_REMOTE, result.getStatus(),
                "successful resolved resume result");
        assertEquals(1, resumeCalls[0], "successful resolved resume call count");
        assertEquals(0, normalCalls[0], "successful resolved resume skips normal sync");
    }

    private static void verifyUnresolvedConflictsDoNotRunResolvedResume() {
        SyncConflict unresolved = conflict(
                "sync-unresolved-orchestration",
                item("sync-unresolved-orchestration", "Crackers", 1, false, 10L, "base"),
                item("sync-unresolved-orchestration", "Rice crackers", 1, false, 20L, "phone"),
                item("sync-unresolved-orchestration", "Saltines", 1, false, 30L, "tablet"),
                "\"v2\"");
        final RecordingMetadataStore metadata = new RecordingMetadataStore(
                document(unresolved.getBaselineItem()), "\"v1\"");
        metadata.replaceConflicts(Collections.singletonList(unresolved));
        final int[] resumeCalls = new int[] {0};
        ResolvedConflictAwareSyncRunner runner = new ResolvedConflictAwareSyncRunner(
                metadata,
                new ResolvedConflictAwareSyncRunner.NormalSyncOperation() {
                    @Override
                    public WebDavSyncResult synchronize() {
                        return WebDavSyncResult.conflicts(metadata.loadConflicts());
                    }
                },
                new ResolvedConflictAwareSyncRunner.ResolvedConflictResumeOperation() {
                    @Override
                    public WebDavSyncResult resumeResolvedConflicts() {
                        resumeCalls[0]++;
                        return WebDavSyncResult.status(WebDavSyncResult.Status.UPDATED_REMOTE,
                                "Resolved resume should not run while conflicts are unresolved");
                    }
                });

        WebDavSyncResult result = runner.synchronizeOrResume();

        assertEquals(WebDavSyncResult.Status.CONFLICTS, result.getStatus(),
                "unresolved orchestration result");
        assertEquals("sync-unresolved-orchestration", result.getConflicts().get(0).getSyncId(),
                "unresolved orchestration conflict remains visible");
        assertEquals(0, resumeCalls[0], "unresolved orchestration skips resolved resume");
    }

    private static void verifyResolvedUploadClearsConflictsBeforeSubsequentSync() {
        SyncItem baselineTea = item("sync-clear-repeat", "Tea", 1, false, 10L, "base");
        SyncItem localTea = item("sync-clear-repeat", "Green tea", 1, false, 20L, "phone");
        SyncItem remoteTea = item("sync-clear-repeat", "Black tea", 1, false, 30L, "tablet");
        SyncDocument baseline = document(baselineTea);
        SyncDocument local = document(localTea);
        SyncDocument remote = document(remoteTea);
        RecordingTransport conflictTransport = new RecordingTransport();
        conflictTransport.respond(200, "\"v2\"", null);
        conflictTransport.respond(200, "\"v2\"", json(remote));
        RecordingLocalStore localStore = new RecordingLocalStore(local);
        RecordingMetadataStore metadata = new RecordingMetadataStore(baseline, "\"v1\"");

        WebDavSyncResult conflictResult = controller(conflictTransport, localStore, metadata).synchronize(1425L);
        RecordingConflictUploader uploader = new RecordingConflictUploader("\"v3\"");
        ResolvedConflictSyncService service = new ResolvedConflictSyncService(localStore, metadata, uploader);

        assertEquals(WebDavSyncResult.Status.CONFLICTS, conflictResult.getStatus(),
                "clear-repeat conflict capture status");
        assertEquals(1, metadata.loadConflicts().size(), "clear-repeat captured one conflict");

        service.choose("sync-clear-repeat", ConflictChoice.LOCAL);
        WebDavSyncResult resolvedResult = service.resumeResolvedConflicts();

        assertEquals(WebDavSyncResult.Status.UPDATED_REMOTE, resolvedResult.getStatus(),
                "clear-repeat resolved upload status");
        assertEquals(1, uploader.uploads.size(), "clear-repeat uploads resolved document once");
        assertEquals("\"v2\"", uploader.uploads.get(0).marker,
                "clear-repeat resolved upload uses conflict marker");
        assertDocumentEquals(document(localTea), metadata.loadBaselineDocument(),
                "clear-repeat records resolved baseline");
        assertEquals("\"v3\"", metadata.loadRemoteVersionMarker(),
                "clear-repeat records resolved marker");
        assertEquals(0, metadata.loadConflicts().size(),
                "clear-repeat clears conflicts after successful upload");

        RecordingTransport followUpTransport = new RecordingTransport();
        followUpTransport.respond(200, "\"v3\"", null);
        WebDavSyncResult followUpResult = controller(followUpTransport, localStore, metadata).synchronize(1430L);

        assertEquals(WebDavSyncResult.Status.UP_TO_DATE, followUpResult.getStatus(),
                "clear-repeat subsequent sync does not reopen conflict");
        assertEquals(list("HEAD"), followUpTransport.methods(),
                "clear-repeat subsequent sync only checks matching marker");
        assertEquals(0, metadata.loadConflicts().size(),
                "clear-repeat subsequent sync leaves conflict store empty");
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
        String autoSyncController = readUtf8(
                "sholi/src/main/java/name/soulayrol/rhaa/sholi/ForegroundWebDavAutoSyncController.java");
        String abstractListFragment = readUtf8(
                "sholi/src/main/java/name/soulayrol/rhaa/sholi/AbstractListFragment.java");
        String strings = readUtf8("sholi/src/main/res/values/strings.xml");
        String conflictDialog = readUtf8(
                "sholi/src/main/java/name/soulayrol/rhaa/sholi/SyncConflictDialogFragment.java");
        String syncResult = readUtf8(
                "sholi/src/main/java/name/soulayrol/rhaa/sholi/sync/webdav/WebDavSyncResult.java");
        String conflictLayout = readUtf8("sholi/src/main/res/layout/sync_conflict_dialog.xml");
        String conflictRowLayout = readUtf8("sholi/src/main/res/layout/sync_conflict_row.xml");
        String itemAdapter = readUtf8(
                "sholi/src/main/java/name/soulayrol/rhaa/sholi/data/ItemLazyListAdapter.java");
        String lazyListAdapter = readUtf8(
                "sholi/src/main/java/name/soulayrol/rhaa/sholi/data/AbstractLazyListAdapter.java");
        String itemStyler = readUtf8(
                "sholi/src/main/java/name/soulayrol/rhaa/sholi/data/ItemViewStyler.java");

        assertContains(mainMenu, "action_sync", "main menu sync trigger id");
        assertContains(mainMenu, "action_main_sync", "main menu sync trigger title");
        assertContains(mainActivity, "AndroidWebDavSyncRunner", "main activity sync runner wiring");
        assertContains(mainActivity, "new Thread", "main activity async network work");
        assertContains(mainActivity, "runOnUiThread", "main activity UI status handoff");
        assertContains(autoSyncController, "public final class ForegroundWebDavAutoSyncController",
                "foreground automatic sync controller exists");
        assertContains(autoSyncController, "FOREGROUND_SYNC_INTERVAL_SECONDS = 30L",
                "foreground automatic sync uses named 30s interval constant");
        assertContains(autoSyncController, "scheduleAtFixedRate",
                "foreground automatic sync schedules periodic polling");
        assertContains(autoSyncController, "delegate.startAutomaticSynchronization()",
                "foreground automatic sync delegates ticks to shared synchronization path");
        assertContains(autoSyncController, "scheduledFuture.cancel(false)",
                "foreground automatic sync cancels future ticks without interrupting work");
        assertContains(mainActivity, "new ForegroundWebDavAutoSyncController",
                "main activity owns foreground automatic sync controller");
        assertContains(mainActivity, "protected void onResume()", "main activity resume lifecycle hook");
        assertContains(mainActivity, "startForegroundAutoSyncIfMainListVisible();",
                "main activity starts foreground automatic sync on resume/open");
        assertContains(mainActivity, "protected void onPause()", "main activity pause lifecycle hook");
        assertContains(mainActivity, "stopForegroundAutoSync();",
                "main activity stops foreground automatic sync on pause");
        assertContains(mainActivity, "protected void onDestroy()", "main activity destroy lifecycle hook");
        assertContains(mainActivity, "shutdownForegroundAutoSync();",
                "main activity shuts down foreground automatic sync controller on destroy");
        assertContains(mainActivity, "startSynchronization(SynchronizationTrigger.MANUAL)",
                "manual sync routes through shared synchronization path");
        assertContains(mainActivity, "startSynchronization(SynchronizationTrigger.AUTOMATIC)",
                "automatic sync routes through shared synchronization path");
        assertContains(mainActivity, "private boolean tryAcquireSynchronizationSlot()",
                "manual and automatic sync share no-overlap guard");
        assertContains(mainActivity, "synchronizationInProgress",
                "shared synchronization path records in-flight work");
        assertContains(mainActivity, "new AndroidWebDavSyncRunner(MainActivity.this).synchronizeOrResume()",
                "shared synchronization path reuses Android WebDAV runner");
        assertContains(mainActivity, "onAutomaticSynchronizationResult",
                "main activity has automatic result policy");
        assertContains(mainActivity, "isSilentAutomaticResult(result)",
                "automatic up-to-date results are handled silently");
        assertContains(mainActivity, "WebDavSyncResult.Status.UP_TO_DATE",
                "automatic result policy recognizes up-to-date status");
        assertContains(mainActivity, "if (!SyncConflictDialogFragment.isShowing(getFragmentManager()))",
                "automatic conflicts avoid duplicate conflict dialogs");
        assertContains(mainActivity, "AUTOMATIC_ERROR_NOTIFICATION_INTERVAL_MILLIS",
                "automatic errors have rate-limit interval hook");
        assertContains(mainActivity, "shouldShowAutomaticError(result",
                "automatic repeated errors are suppressed or rate-limited");
        assertContains(mainActivity, "lastAutomaticErrorStatus",
                "automatic repeated error suppression tracks previous status");
        assertContains(mainActivity, "SyncConflictDialogFragment", "main activity conflict UI handoff");
        assertContains(mainActivity,
                "SyncConflictDialogFragment.showNext(getFragmentManager(), conflictMessage(result))",
                "main activity passes conflict diagnostics to dialog");
        assertContains(conflictDialog, "public static boolean isShowing(FragmentManager fragmentManager)",
                "conflict dialog exposes duplicate-dialog detection");
        assertContains(abstractListFragment, "public void refreshList()",
                "list fragments expose explicit refresh API");
        assertContains(abstractListFragment, "if (!isAdded() || getActivity() == null",
                "list refresh skips detached fragments");
        assertContains(abstractListFragment, "_session == null || _adapter == null",
                "list refresh skips destroyed fragments");
        assertContains(abstractListFragment, "_session.clear();",
                "list refresh clears cached DAO session entities");
        assertContains(abstractListFragment, "_adapter.setLazyList(createList(getActivity()))",
                "list refresh reloads lazy list from database");
        assertContains(syncResult, "status == Status.PULLED_REMOTE",
                "sync success includes pulled remote changes");
        assertContains(mainActivity, "if (result.isSuccess())",
                "main activity refreshes after every successful sync status");
        assertContains(mainActivity, "refreshVisibleList();",
                "main activity refreshes visible list after successful sync");
        assertContains(mainActivity, "fragment.isAdded()",
                "main activity skips detached visible list refresh targets");
        assertContains(mainActivity, "fragment.getActivity() == this",
                "main activity refreshes only fragments attached to itself");
        assertContains(mainActivity, "Toast.makeText(this, R.string.sync_conflicts_found",
                "main activity conflict toast stays generic");
        assertDoesNotContain(mainActivity, "Toast.makeText(this, conflictMessage(result)",
                "main activity does not rely on full diagnostic toast");
        assertContains(conflictDialog, "chooseAll(", "conflict dialog saves resolution choices atomically");
        assertContains(conflictDialog, "ARG_DIAGNOSTIC_MESSAGE",
                "conflict dialog stores diagnostic argument key");
        assertContains(conflictDialog,
                "public static void showNext(FragmentManager fragmentManager, String diagnosticMessage)",
                "conflict dialog overload accepts diagnostic message");
        assertContains(conflictDialog, "setArguments(arguments)",
                "conflict dialog passes diagnostic bundle");
        assertContains(conflictDialog, "diagnosticMessage()",
                "conflict dialog reads diagnostic argument");
        assertContains(conflictDialog, "R.layout.sync_conflict_dialog", "conflict dialog custom view");
        assertContains(conflictDialog, "setView(view)", "conflict dialog installs custom view");
        assertContains(conflictDialog, "R.id.sync_conflict_diagnostic_message",
                "conflict dialog binds diagnostic message view");
        assertContains(conflictDialog, "R.string.sync_conflict_diagnostic_label",
                "conflict dialog labels diagnostic message");
        assertContains(conflictDialog, "setVisibility(View.GONE)",
                "conflict dialog hides empty diagnostic panel");
        assertContains(conflictDialog, "setPositiveButton(R.string.sync_conflict_use_selection",
                "conflict dialog confirms selected choice");
        assertContains(conflictDialog, "confirmButton.setOnClickListener",
                "conflict dialog waits for positive confirmation");
        assertContains(conflictDialog, "ConflictListSelection.fromConflicts(conflicts)",
                "conflict dialog tracks all unresolved conflict selections");
        assertContains(conflictDialog, "for (SyncConflict conflict: conflicts)",
                "conflict dialog builds a row for every unresolved conflict");
        assertContains(conflictDialog, "R.layout.sync_conflict_row",
                "conflict dialog inflates reusable conflict rows");
        assertContains(conflictDialog, "confirmButton.setEnabled(selection != null && selection.isComplete())",
                "conflict dialog disables confirm until all rows are selected");
        assertContains(conflictDialog, "runner.chooseAll(choices)",
                "conflict dialog resolves confirmed choices in one batch");
        assertContains(conflictDialog, "runner.synchronizeOrResume()",
                "conflict dialog immediately resumes stale-aware sync");
        assertContains(conflictDialog, "new Thread",
                "conflict dialog runs immediate upload off the UI thread");
        assertContains(conflictDialog, "runOnUiThread",
                "conflict dialog returns immediate upload result to UI thread");
        assertContains(conflictDialog, "WebDavSyncResult.Status.CONFLICTS",
                "conflict dialog reopens conflicts returned by immediate upload");
        assertContains(conflictDialog, "showNext(activity.getFragmentManager(), conflictMessage(activity, result))",
                "conflict dialog reopens conflicts with diagnostic message");
        assertContains(conflictDialog, "if (result.isSuccess())",
                "conflict dialog refreshes after successful resolved-conflict sync");
        assertContains(conflictDialog, "refreshVisibleList(activity);",
                "conflict dialog refreshes visible list after successful resolved-conflict upload");
        assertContains(conflictDialog, "fragment.isAdded()",
                "conflict dialog skips detached visible list refresh targets");
        assertContains(conflictDialog, "fragment.getActivity() == activity",
                "conflict dialog refreshes only fragments attached to the callback activity");
        assertContains(conflictDialog, "Toast.makeText(activity, R.string.sync_conflicts_found",
                "conflict dialog conflict toast stays generic");
        assertDoesNotContain(conflictDialog, "Toast.makeText(activity, conflictMessage(activity, result)",
                "conflict dialog does not rely on full diagnostic toast");
        assertContains(conflictDialog, "ConflictListSelection",
                "conflict dialog delegates list selection behavior");
        assertContains(conflictDialog, "GestureDetector", "conflict dialog swipe detector");
        assertContains(conflictDialog, "onFling", "conflict dialog swipe handling");
        assertContains(conflictDialog, "selection.selectFromSwipeDelta(syncId, dx)",
                "swipe delta selects side through controller");
        assertContains(conflictDialog, "action == MotionEvent.ACTION_UP && swipeHandled[0]",
                "handled swipe consumes column click on release");
        assertContains(conflictDialog, "bindConflictSide", "conflict dialog binds local and remote sides");
        assertContains(conflictDialog, "ItemViewStyler.bindName",
                "conflict dialog reuses shared item styling");
        assertContains(itemAdapter, "ItemViewStyler.bindName",
                "list adapter reuses shared item styling");
        assertContains(lazyListAdapter, "if (_lazyList != null)",
                "lazy list adapter closes previous lazy list only when present");
        assertContains(lazyListAdapter, "_lazyList.close();",
                "lazy list adapter retains previous lazy list close behavior");
        assertContains(itemStyler, "Checkable.OFF_LIST", "shared styler off-list item styling");
        assertContains(itemStyler, "Color.GRAY", "shared styler off-list item color");
        assertContains(itemStyler, "Checkable.UNCHECKED", "shared styler unchecked item styling");
        assertContains(itemStyler, "Color.GREEN", "shared styler unchecked item color");
        assertContains(itemStyler, "Checkable.CHECKED", "shared styler checked item styling");
        assertContains(itemStyler, "Paint.STRIKE_THRU_TEXT_FLAG",
                "shared styler checked item strike-through");
        assertContains(itemStyler, "Color.WHITE", "shared styler checked item color");
        assertContains(conflictLayout, "ScrollView", "conflict layout scrolls all conflict rows");
        assertContains(conflictLayout, "@+id/sync_conflict_diagnostic_message",
                "conflict layout includes diagnostic panel");
        assertContains(conflictLayout, "@string/sync_conflict_diagnostic_label",
                "conflict layout labels diagnostic panel");
        assertContains(conflictLayout, "android:singleLine=\"false\"",
                "conflict layout allows multiline diagnostics");
        assertContains(conflictLayout, "android:scrollHorizontally=\"false\"",
                "conflict layout wraps long ETag diagnostics");
        assertDoesNotContain(conflictLayout, "ellipsize",
                "conflict layout does not truncate diagnostics");
        assertContains(conflictLayout, "@+id/sync_conflict_list",
                "conflict layout hosts all conflict rows");
        assertContains(conflictRowLayout, "@+id/sync_conflict_local_column",
                "conflict row layout local column id");
        assertContains(conflictRowLayout, "@+id/sync_conflict_remote_column",
                "conflict row layout remote column id");
        assertContains(conflictRowLayout, "@+id/sync_conflict_local_new",
                "conflict row layout local new indicator");
        assertContains(conflictRowLayout, "@+id/sync_conflict_remote_new",
                "conflict row layout remote new indicator");
        assertContains(conflictRowLayout, "@layout/list_item", "conflict row layout reuses app item row");
        assertContains(conflictRowLayout, "sync_conflict_side_background",
                "conflict row layout has selected-column highlight drawable");
        assertContains(strings, "Sync", "sync status strings");
        assertContains(strings, "Local", "conflict local label string");
        assertContains(strings, "Remote", "conflict remote label string");
        assertContains(strings, "🆕 new", "conflict new indicator string");
        assertContains(strings, "Use selection", "conflict confirm string");
        assertContains(strings, "sync_conflict_diagnostic_label", "conflict diagnostic label string");
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

    private static void assertSelectedChoice(
            ConflictListSelection.SelectedChoice actual,
            String syncId,
            ConflictChoice choice,
            String label) {
        assertEquals(syncId, actual.getSyncId(), label + " sync_id");
        assertEquals(choice, actual.getChoice(), label + " choice");
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

    private static <T> List<T> list(T first, T second, T third, T fourth) {
        ArrayList<T> list = new ArrayList<T>();
        list.add(first);
        list.add(second);
        list.add(third);
        list.add(fourth);
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

    private static final class FixedConflictUploader
            implements ResolvedConflictSyncService.ConflictUploader {
        private final WebDavPutResult result;
        private final List<Upload> uploads = new ArrayList<Upload>();

        FixedConflictUploader(WebDavPutResult result) {
            this.result = result;
        }

        @Override
        public WebDavPutResult uploadResolvedDocument(SyncDocument document, String remoteVersionMarker) {
            uploads.add(new Upload(document, remoteVersionMarker));
            return result;
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
