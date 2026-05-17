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
        verifyUploadFailurePreservesLocalDataAndMetadata();
        verifyUnresolvedConflictsPersistAndBlockFurtherUploads();
        verifyConflictDisplayModelShowsRequiredCompleteComparisonFields();
        verifyChoosingLocalOrRemoteResolvesCompleteItemVersion();
        verifyRemainingUnresolvedConflictBlocksResumeUpload();
        verifyAllResolvedConflictsProduceAndUploadResolvedDocument();
        verifyUnsafeResolvedConflictMarkerRequiresConfirmationWithoutUpload();
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
        private final List<Object> responses = new ArrayList<Object>();

        void respond(int statusCode, String etag, String body) {
            Map<String, String> headers = new LinkedHashMap<String, String>();
            if (etag != null) {
                headers.put("ETag", etag);
            }
            responses.add(new WebDavResponse(statusCode, headers, body));
        }

        @Override
        public WebDavResponse execute(WebDavRequest request) throws WebDavTransportException {
            requests.add(request);
            if (responses.isEmpty()) {
                throw new AssertionError("No queued WebDAV response for " + request.getMethod());
            }
            Object response = responses.remove(0);
            if (response instanceof WebDavTransportException) {
                throw (WebDavTransportException) response;
            }
            return (WebDavResponse) response;
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
    }

    private static final class RecordingLocalStore implements LocalSyncDocumentStore {
        private SyncDocument currentDocument;
        private final List<SyncDocument> appliedDocuments = new ArrayList<SyncDocument>();

        RecordingLocalStore(SyncDocument currentDocument) {
            this.currentDocument = currentDocument;
        }

        @Override
        public SyncDocument loadCurrentDocument() {
            return currentDocument;
        }

        @Override
        public void applyDocument(SyncDocument document) {
            appliedDocuments.add(document);
            currentDocument = document;
        }
    }

    private static final class RecordingMetadataStore implements SyncMetadataStore {
        private SyncDocument baselineDocument;
        private String remoteVersionMarker;
        private List<SyncConflict> conflicts = new ArrayList<SyncConflict>();

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
        public void replaceConflicts(List<SyncConflict> conflicts) {
            this.conflicts = new ArrayList<SyncConflict>(conflicts);
        }

        @Override
        public void clearConflicts() {
            conflicts = new ArrayList<SyncConflict>();
        }
    }

    private static final class RecordingConflictUploader
            implements ResolvedConflictSyncService.ConflictUploader {
        private final String successMarker;
        private final List<Upload> uploads = new ArrayList<Upload>();

        RecordingConflictUploader(String successMarker) {
            this.successMarker = successMarker;
        }

        @Override
        public WebDavPutResult uploadResolvedDocument(SyncDocument document, String remoteVersionMarker) {
            uploads.add(new Upload(document, remoteVersionMarker));
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
