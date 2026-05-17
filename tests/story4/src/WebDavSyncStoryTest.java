package story4;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import name.soulayrol.rhaa.sholi.sync.document.ModifiedBy;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocument;
import name.soulayrol.rhaa.sholi.sync.document.SyncDocumentJson;
import name.soulayrol.rhaa.sholi.sync.document.SyncItem;
import name.soulayrol.rhaa.sholi.sync.merge.SyncConflict;
import name.soulayrol.rhaa.sholi.sync.merge.SyncMetadataStore;
import name.soulayrol.rhaa.sholi.sync.webdav.LocalSyncDocumentStore;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavClient;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavEtag;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavMetadataResult;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavRequest;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavResponse;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavSyncEngine;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavSyncResult;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavTransport;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavTransportException;

public final class WebDavSyncStoryTest {

    private static final String REMOTE_URL = "https://cloud.example.net/sholi/sync.json";

    private WebDavSyncStoryTest() {
    }

    public static void run() throws Exception {
        verifyCreateMissingRemoteUsesIfNoneMatch();
        verifyUpdateExistingUsesIfMatchWithLastStrongEtag();
        verifyEtagMismatchDownloadsRemoteInsteadOfOverwrite();
        verifyPreconditionFailureDownloadsAndRetriesAfterNonConflictingMerge();
        verifyPreconditionFailureStopsOnConflicts();
        verifyWeakAndMissingEtagsUseBaselineCompareAndNeverBlindOverwrite();
        verifyMissingSafeMarkerRequiresConfirmation();
        verifyPullOnlyRecordsBaselineAndFailurePreservesMetadata();
        verifySecretMaterialIsRedactedFromRequestsAndResults();
        verifyFragmentSecretMaterialIsRedactedFromRequestsResultsAndErrors();
        verifyAuthorizationAndBroadAuthMaterialIsRedacted();
        verifyDisagreedHeadAndGetEtagsRequireConfirmationBeforeUpload();
        verifySecondPreconditionFailureStopsAfterSingleRetry();
    }

    private static void verifyCreateMissingRemoteUsesIfNoneMatch() {
        SyncDocument local = document(item("sync-milk", "Milk", 1, false, 10L, "phone"));
        RecordingTransport transport = new RecordingTransport();
        transport.respond(404, null, null);
        transport.respond(201, "\"created\"", "");
        RecordingMetadataStore metadata = new RecordingMetadataStore();
        RecordingLocalStore localStore = new RecordingLocalStore(local);

        WebDavSyncResult result = engine(transport, localStore, metadata).synchronize(1000L);

        assertEquals(
                WebDavSyncResult.Status.CREATED_REMOTE,
                result.getStatus(),
                "missing remote create status");
        assertEquals("HEAD", transport.request(0).getMethod(), "metadata probe method");
        assertEquals("PUT", transport.request(1).getMethod(), "create method");
        assertEquals("*", transport.request(1).getHeader("If-None-Match"), "conditional create header");
        assertEquals(null, transport.request(1).getHeader("If-Match"), "create must not use If-Match");
        assertEquals(json(local), transport.request(1).getBody(), "created remote body");
        assertDocumentEquals(local, metadata.loadBaselineDocument(), "created baseline");
        assertEquals("\"created\"", metadata.loadRemoteVersionMarker(), "created remote marker");
    }

    private static void verifyUpdateExistingUsesIfMatchWithLastStrongEtag() {
        SyncDocument baseline = document(item("sync-milk", "Milk", 1, false, 10L, "base"));
        SyncDocument local = document(item("sync-milk", "Oat milk", 1, false, 20L, "phone"));
        RecordingTransport transport = new RecordingTransport();
        transport.respond(200, "\"v1\"", null);
        transport.respond(204, "\"v2\"", null);
        RecordingMetadataStore metadata = new RecordingMetadataStore(baseline, "\"v1\"");
        RecordingLocalStore localStore = new RecordingLocalStore(local);

        WebDavSyncResult result = engine(transport, localStore, metadata).synchronize(1100L);

        assertEquals(WebDavSyncResult.Status.UPDATED_REMOTE, result.getStatus(), "update status");
        assertEquals(2, transport.requests.size(), "direct update request count");
        WebDavRequest put = transport.request(1);
        assertEquals("PUT", put.getMethod(), "update method");
        assertEquals("\"v1\"", put.getHeader("If-Match"), "conditional update marker");
        assertEquals(null, put.getHeader("If-None-Match"), "update must not use create header");
        assertEquals(json(local), put.getBody(), "updated remote body");
        assertDocumentEquals(local, metadata.loadBaselineDocument(), "updated baseline");
        assertEquals("\"v2\"", metadata.loadRemoteVersionMarker(), "updated marker");
    }

    private static void verifyEtagMismatchDownloadsRemoteInsteadOfOverwrite() {
        SyncDocument baseline = document(item("sync-milk", "Milk", 1, false, 10L, "base"));
        SyncDocument local = document(item("sync-milk", "Oat milk", 1, false, 20L, "phone"));
        SyncDocument remote = document(item("sync-milk", "Soy milk", 1, false, 30L, "tablet"));
        RecordingTransport transport = new RecordingTransport();
        transport.respond(200, "\"v2\"", null);
        transport.respond(200, "\"v2\"", json(remote));
        RecordingMetadataStore metadata = new RecordingMetadataStore(baseline, "\"v1\"");
        RecordingLocalStore localStore = new RecordingLocalStore(local);

        WebDavSyncResult result = engine(transport, localStore, metadata).synchronize(1200L);

        assertEquals(WebDavSyncResult.Status.CONFLICTS, result.getStatus(), "mismatched etag conflict status");
        assertEquals("HEAD", transport.request(0).getMethod(), "mismatch metadata probe");
        assertEquals("GET", transport.request(1).getMethod(), "mismatch downloads remote");
        assertEquals(0, transport.countMethod("PUT"), "mismatch must not overwrite before merge");
        assertEquals(1, metadata.loadConflicts().size(), "mismatch conflict persisted");
        assertDocumentEquals(baseline, metadata.loadBaselineDocument(), "mismatch preserves baseline");
        assertEquals("\"v1\"", metadata.loadRemoteVersionMarker(), "mismatch preserves marker");
    }

    private static void verifyPreconditionFailureDownloadsAndRetriesAfterNonConflictingMerge() {
        SyncItem baseA = item("sync-a", "Milk", 1, false, 10L, "base");
        SyncItem baseB = item("sync-b", "Bread", 1, false, 11L, "base");
        SyncItem localA = item("sync-a", "Oat milk", 1, false, 20L, "phone");
        SyncItem remoteB = item("sync-b", "Bread", 2, false, 30L, "tablet");
        SyncDocument baseline = document(baseA, baseB);
        SyncDocument local = document(localA, baseB);
        SyncDocument remote = document(baseA, remoteB);
        SyncDocument merged = document(localA, remoteB);
        RecordingTransport transport = new RecordingTransport();
        transport.respond(200, "\"v1\"", null);
        transport.respond(412, null, null);
        transport.respond(200, "\"v2\"", json(remote));
        transport.respond(204, "\"v3\"", null);
        RecordingMetadataStore metadata = new RecordingMetadataStore(baseline, "\"v1\"");
        RecordingLocalStore localStore = new RecordingLocalStore(local);

        WebDavSyncResult result = engine(transport, localStore, metadata).synchronize(1300L);

        assertEquals(WebDavSyncResult.Status.UPDATED_REMOTE, result.getStatus(), "retry update status");
        assertEquals(list("HEAD", "PUT", "GET", "PUT"), transport.methods(), "retry request sequence");
        assertEquals("\"v1\"", transport.request(1).getHeader("If-Match"), "first upload marker");
        assertEquals("\"v2\"", transport.request(3).getHeader("If-Match"), "retry upload marker");
        assertEquals(json(merged), transport.request(3).getBody(), "retry merged upload body");
        assertDocumentEquals(merged, localStore.currentDocument, "retry applies merged local document");
        assertDocumentEquals(merged, metadata.loadBaselineDocument(), "retry records merged baseline");
        assertEquals("\"v3\"", metadata.loadRemoteVersionMarker(), "retry records new marker");
    }

    private static void verifyPreconditionFailureStopsOnConflicts() {
        SyncDocument baseline = document(item("sync-milk", "Milk", 1, false, 10L, "base"));
        SyncDocument local = document(item("sync-milk", "Oat milk", 1, false, 20L, "phone"));
        SyncDocument remote = document(item("sync-milk", "Soy milk", 1, false, 30L, "tablet"));
        RecordingTransport transport = new RecordingTransport();
        transport.respond(200, "\"v1\"", null);
        transport.respond(412, null, null);
        transport.respond(200, "\"v2\"", json(remote));
        RecordingMetadataStore metadata = new RecordingMetadataStore(baseline, "\"v1\"");
        RecordingLocalStore localStore = new RecordingLocalStore(local);

        WebDavSyncResult result = engine(transport, localStore, metadata).synchronize(1400L);

        assertEquals(WebDavSyncResult.Status.CONFLICTS, result.getStatus(), "412 conflict status");
        assertEquals(list("HEAD", "PUT", "GET"), transport.methods(), "412 conflict request sequence");
        assertEquals(1, metadata.loadConflicts().size(), "412 conflict persisted");
        assertDocumentEquals(local, localStore.currentDocument, "412 conflict preserves local document");
        assertDocumentEquals(baseline, metadata.loadBaselineDocument(), "412 conflict preserves baseline");
        assertEquals("\"v1\"", metadata.loadRemoteVersionMarker(), "412 conflict preserves marker");
    }

    private static void verifyWeakAndMissingEtagsUseBaselineCompareAndNeverBlindOverwrite() {
        assertEquals(true, WebDavEtag.classify("\"strong\"").isStrong(), "strong etag classification");
        assertEquals(false, WebDavEtag.classify("W/\"weak\"").isStrong(), "weak etag classification");
        assertEquals(false, WebDavEtag.classify(null).isStrong(), "missing etag classification");

        SyncDocument baseline = document(item("sync-tea", "Tea", 1, false, 10L, "base"));
        SyncDocument local = document(item("sync-tea", "Green tea", 1, false, 20L, "phone"));
        RecordingTransport weakTransport = new RecordingTransport();
        weakTransport.respond(200, "W/\"weak-1\"", null);
        weakTransport.respond(200, "W/\"weak-1\"", json(baseline));
        RecordingMetadataStore weakMetadata = new RecordingMetadataStore(baseline, "W/\"weak-1\"");

        WebDavSyncResult weakResult = engine(
                weakTransport,
                new RecordingLocalStore(local),
                weakMetadata).synchronize(1500L);

        assertEquals(
                WebDavSyncResult.Status.CONFIRMATION_REQUIRED,
                weakResult.getStatus(),
                "weak etag changed local requires confirmation");
        assertEquals(list("HEAD", "GET"), weakTransport.methods(), "weak etag baseline compare path");
        assertEquals(0, weakTransport.countMethod("PUT"), "weak etag must not blind overwrite");
        assertDocumentEquals(baseline, weakMetadata.loadBaselineDocument(), "weak etag preserves baseline");

        SyncDocument remoteChanged = document(item("sync-tea", "Tea", 2, false, 30L, "tablet"));
        RecordingTransport missingTransport = new RecordingTransport();
        missingTransport.respond(200, null, null);
        missingTransport.respond(200, null, json(remoteChanged));
        RecordingMetadataStore missingMetadata = new RecordingMetadataStore(baseline, null);
        RecordingLocalStore missingLocalStore = new RecordingLocalStore(baseline);

        WebDavSyncResult missingResult = engine(
                missingTransport,
                missingLocalStore,
                missingMetadata).synchronize(1600L);

        assertEquals(
                WebDavSyncResult.Status.PULLED_REMOTE,
                missingResult.getStatus(),
                "missing etag remote-only pull status");
        assertEquals(list("HEAD", "GET"), missingTransport.methods(), "missing etag baseline compare path");
        assertEquals(0, missingTransport.countMethod("PUT"), "missing etag remote-only pull must not upload");
        assertDocumentEquals(remoteChanged, missingLocalStore.currentDocument, "missing etag applies remote pull");
        assertDocumentEquals(remoteChanged, missingMetadata.loadBaselineDocument(), "missing etag records pull baseline");
        assertEquals(null, missingMetadata.loadRemoteVersionMarker(), "missing etag stores no unsafe marker");
    }

    private static void verifyMissingSafeMarkerRequiresConfirmation() {
        SyncDocument local = document(item("sync-coffee", "Coffee", 1, false, 10L, "phone"));
        SyncDocument remote = document(item("sync-coffee", "Decaf coffee", 1, false, 20L, "tablet"));
        RecordingTransport transport = new RecordingTransport();
        transport.respond(200, null, null);
        transport.respond(200, null, json(remote));
        RecordingMetadataStore metadata = new RecordingMetadataStore(null, null);
        RecordingLocalStore localStore = new RecordingLocalStore(local);

        WebDavSyncResult result = engine(transport, localStore, metadata).synchronize(1700L);

        assertEquals(
                WebDavSyncResult.Status.CONFIRMATION_REQUIRED,
                result.getStatus(),
                "missing safe marker confirmation status");
        assertEquals(list("HEAD", "GET"), transport.methods(), "missing marker request sequence");
        assertEquals(0, transport.countMethod("PUT"), "missing marker must not replace remote");
        assertEquals(null, metadata.loadBaselineDocument(), "missing marker preserves absent baseline");
        assertEquals(null, metadata.loadRemoteVersionMarker(), "missing marker preserves absent marker");
        assertDocumentEquals(local, localStore.currentDocument, "missing marker preserves local document");
    }

    private static void verifyPullOnlyRecordsBaselineAndFailurePreservesMetadata() {
        SyncDocument baseline = document(item("sync-rice", "Rice", 1, false, 10L, "base"));
        SyncDocument remote = document(item("sync-rice", "Brown rice", 1, false, 20L, "tablet"));
        RecordingTransport pullTransport = new RecordingTransport();
        pullTransport.respond(200, "\"v2\"", null);
        pullTransport.respond(200, "\"v2\"", json(remote));
        RecordingMetadataStore pullMetadata = new RecordingMetadataStore(baseline, "\"v1\"");
        RecordingLocalStore pullLocalStore = new RecordingLocalStore(baseline);

        WebDavSyncResult pullResult = engine(
                pullTransport,
                pullLocalStore,
                pullMetadata).synchronize(1800L);

        assertEquals(WebDavSyncResult.Status.PULLED_REMOTE, pullResult.getStatus(), "pull-only status");
        assertEquals(0, pullTransport.countMethod("PUT"), "pull-only must not upload");
        assertDocumentEquals(remote, pullLocalStore.currentDocument, "pull-only applies remote document");
        assertDocumentEquals(remote, pullMetadata.loadBaselineDocument(), "pull-only records new baseline");
        assertEquals("\"v2\"", pullMetadata.loadRemoteVersionMarker(), "pull-only records new marker");

        SyncDocument local = document(item("sync-rice", "Wild rice", 1, false, 30L, "phone"));
        RecordingTransport failureTransport = new RecordingTransport();
        failureTransport.respond(200, "\"v2\"", null);
        failureTransport.respond(500, null, null);
        RecordingMetadataStore failureMetadata = new RecordingMetadataStore(baseline, "\"v2\"");
        RecordingLocalStore failureLocalStore = new RecordingLocalStore(local);

        WebDavSyncResult failureResult = engine(
                failureTransport,
                failureLocalStore,
                failureMetadata).synchronize(1900L);

        assertEquals(WebDavSyncResult.Status.SERVER_ERROR, failureResult.getStatus(), "failed upload status");
        assertDocumentEquals(local, failureLocalStore.currentDocument, "failed upload preserves local document");
        assertDocumentEquals(baseline, failureMetadata.loadBaselineDocument(), "failed upload preserves baseline");
        assertEquals("\"v2\"", failureMetadata.loadRemoteVersionMarker(), "failed upload preserves marker");
    }

    private static void verifySecretMaterialIsRedactedFromRequestsAndResults() {
        String secret = "ultra-secret";
        Map<String, String> authHeaders = new LinkedHashMap<String, String>();
        authHeaders.put("Authorization", "Bearer " + secret);
        WebDavRequest request = new WebDavRequest(
                "GET",
                "https://alice:" + secret + "@cloud.example.net/sync.json?access_token=" + secret,
                authHeaders,
                null);

        assertDoesNotContain(secret, request.toString(), "request string");
        assertDoesNotContain("Bearer " + secret, request.toString(), "authorization header string");

        WebDavTransportException exception = new WebDavTransportException(
                "Authorization: Bearer " + secret);
        assertDoesNotContain(secret, exception.toString(), "transport exception string");

        RecordingTransport transport = new RecordingTransport();
        transport.fail(new WebDavTransportException("Authorization: Bearer " + secret));
        WebDavClient client = new WebDavClient(transport, authHeaders);

        WebDavMetadataResult result = client.head(
                "https://cloud.example.net/sync.json?password=" + secret);

        assertEquals(WebDavMetadataResult.Status.NETWORK_ERROR, result.getStatus(), "network error status");
        assertDoesNotContain(secret, result.getMessage(), "network error message");
        assertDoesNotContain(secret, result.toString(), "network error result string");
        assertDoesNotContain(secret, transport.request(0).toString(), "sent request string");
    }

    private static void verifyFragmentSecretMaterialIsRedactedFromRequestsResultsAndErrors() {
        String secret = "fragment-secret";
        String fragmentUrl = "https://cloud.example.net/sync.json#auth-token/" + secret;
        WebDavRequest request = new WebDavRequest("GET", fragmentUrl, null, null);

        assertDoesNotContain(secret, request.toString(), "fragment request string");

        WebDavSyncResult syncResult = WebDavSyncResult.status(
                WebDavSyncResult.Status.SERVER_ERROR,
                "Remote sync failed at " + fragmentUrl);
        assertDoesNotContain(secret, syncResult.getMessage(), "fragment sync result message");
        assertDoesNotContain(secret, syncResult.toString(), "fragment sync result string");

        WebDavMetadataResult metadataResult = WebDavMetadataResult.error(
                WebDavMetadataResult.Status.SERVER_ERROR,
                500,
                "Metadata failed at " + fragmentUrl);
        assertDoesNotContain(secret, metadataResult.getMessage(), "fragment metadata result message");
        assertDoesNotContain(secret, metadataResult.toString(), "fragment metadata result string");

        WebDavTransportException exception = new WebDavTransportException(
                "Transport failed at " + fragmentUrl);
        assertDoesNotContain(secret, exception.getMessage(), "fragment transport exception message");
        assertDoesNotContain(secret, exception.toString(), "fragment transport exception string");
    }

    private static void verifyAuthorizationAndBroadAuthMaterialIsRedacted() {
        String secret = "authorization-secret";
        WebDavRequest authorizationQuery = new WebDavRequest(
                "GET",
                "https://cloud.example.net/sync.json?authorization=" + secret,
                null,
                null);
        assertDoesNotContain(secret, authorizationQuery.toString(), "authorization query request string");

        WebDavRequest broadAuthQuery = new WebDavRequest(
                "GET",
                "https://cloud.example.net/sync.json?oauth=" + secret,
                null,
                null);
        assertDoesNotContain(secret, broadAuthQuery.toString(), "broad auth query request string");

        WebDavRequest authorizationFragment = new WebDavRequest(
                "GET",
                "https://cloud.example.net/sync.json#authorization=" + secret,
                null,
                null);
        assertDoesNotContain(secret, authorizationFragment.toString(), "authorization fragment request string");
    }

    private static void verifyDisagreedHeadAndGetEtagsRequireConfirmationBeforeUpload() {
        SyncDocument baseline = document(item("sync-flour", "Flour", 1, false, 10L, "base"));
        SyncDocument local = document(item("sync-flour", "Whole flour", 1, false, 20L, "phone"));
        RecordingTransport transport = new RecordingTransport();
        transport.respond(200, "\"v2\"", null);
        transport.respond(200, "\"v1\"", json(baseline));
        transport.respond(204, "\"v3\"", null);
        RecordingMetadataStore metadata = new RecordingMetadataStore(baseline, "\"v1\"");
        RecordingLocalStore localStore = new RecordingLocalStore(local);

        WebDavSyncResult result = engine(transport, localStore, metadata).synchronize(2000L);

        assertEquals(
                WebDavSyncResult.Status.CONFIRMATION_REQUIRED,
                result.getStatus(),
                "HEAD/GET etag disagreement status");
        assertEquals(list("HEAD", "GET"), transport.methods(), "HEAD/GET disagreement request sequence");
        assertEquals(0, transport.countMethod("PUT"), "HEAD/GET disagreement must not blind overwrite");
        assertDocumentEquals(local, localStore.currentDocument, "HEAD/GET disagreement preserves local document");
        assertDocumentEquals(baseline, metadata.loadBaselineDocument(), "HEAD/GET disagreement preserves baseline");
        assertEquals("\"v1\"", metadata.loadRemoteVersionMarker(), "HEAD/GET disagreement preserves marker");
    }

    private static void verifySecondPreconditionFailureStopsAfterSingleRetry() {
        SyncItem baseA = item("sync-apples", "Apples", 1, false, 10L, "base");
        SyncItem baseB = item("sync-butter", "Butter", 1, false, 11L, "base");
        SyncItem localA = item("sync-apples", "Green apples", 1, false, 20L, "phone");
        SyncItem remoteB = item("sync-butter", "Butter", 2, false, 30L, "tablet");
        SyncDocument baseline = document(baseA, baseB);
        SyncDocument local = document(localA, baseB);
        SyncDocument remote = document(baseA, remoteB);
        SyncDocument merged = document(localA, remoteB);
        RecordingTransport transport = new RecordingTransport();
        transport.respond(200, "\"v1\"", null);
        transport.respond(412, null, null);
        transport.respond(200, "\"v2\"", json(remote));
        transport.respond(412, null, null);
        RecordingMetadataStore metadata = new RecordingMetadataStore(baseline, "\"v1\"");
        RecordingLocalStore localStore = new RecordingLocalStore(local);

        WebDavSyncResult result = engine(transport, localStore, metadata).synchronize(2100L);

        assertEquals(
                WebDavSyncResult.Status.PRECONDITION_FAILED,
                result.getStatus(),
                "second precondition failure status");
        assertEquals(list("HEAD", "PUT", "GET", "PUT"), transport.methods(), "second 412 request sequence");
        assertEquals(2, transport.countMethod("PUT"), "second 412 performs one retry only");
        assertEquals("\"v1\"", transport.request(1).getHeader("If-Match"), "second 412 first marker");
        assertEquals("\"v2\"", transport.request(3).getHeader("If-Match"), "second 412 retry marker");
        assertEquals(json(merged), transport.request(3).getBody(), "second 412 retry merged body");
        assertEquals(0, localStore.appliedDocuments.size(), "second 412 must not apply merged document");
        assertDocumentEquals(local, localStore.currentDocument, "second 412 preserves local document");
        assertDocumentEquals(baseline, metadata.loadBaselineDocument(), "second 412 preserves baseline");
        assertEquals("\"v1\"", metadata.loadRemoteVersionMarker(), "second 412 preserves marker");
        assertEquals(0, metadata.loadConflicts().size(), "second 412 does not persist conflicts");
    }

    private static WebDavSyncEngine engine(
            RecordingTransport transport,
            RecordingLocalStore localStore,
            RecordingMetadataStore metadata) {
        return new WebDavSyncEngine(new WebDavClient(transport), REMOTE_URL, localStore, metadata);
    }

    private static SyncItem item(
            String syncId, String name, int status, boolean deleted, long modifiedAt, String modifiedBy) {
        return new SyncItem(syncId, name, status, deleted, modifiedAt, new ModifiedBy(modifiedBy, null));
    }

    private static SyncDocument document(SyncItem... items) {
        List<SyncItem> list = new ArrayList<SyncItem>();
        for (SyncItem item: items) {
            list.add(item);
        }
        return new SyncDocument(list);
    }

    private static String json(SyncDocument document) {
        return SyncDocumentJson.serialize(document);
    }

    private static void assertDocumentEquals(SyncDocument expected, SyncDocument actual, String label) {
        if (expected == null || actual == null) {
            assertEquals(expected, actual, label);
            return;
        }
        assertEquals(json(expected), json(actual), label);
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

    private static List<String> list(String... values) {
        ArrayList<String> list = new ArrayList<String>();
        for (String value: values) {
            list.add(value);
        }
        return list;
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

        void fail(WebDavTransportException exception) {
            responses.add(exception);
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
        public boolean isCurrentDocument(SyncDocument expectedDocument) {
            return json(currentDocument).equals(json(expectedDocument));
        }

        @Override
        public void applyDocument(SyncDocument document) {
            appliedDocuments.add(document);
            currentDocument = document;
        }

        @Override
        public boolean applyDocumentIfCurrent(SyncDocument expectedDocument, SyncDocument document) {
            if (!isCurrentDocument(expectedDocument)) {
                return false;
            }
            applyDocument(document);
            return true;
        }

        @Override
        public boolean markDeletedSyncedAndCleanupIfCurrent(SyncDocument expectedDocument, long now) {
            return isCurrentDocument(expectedDocument);
        }
    }

    private static final class RecordingMetadataStore implements SyncMetadataStore {
        private SyncDocument baselineDocument;
        private String remoteVersionMarker;
        private List<SyncConflict> conflicts = new ArrayList<SyncConflict>();
        private SyncDocument pendingMergedDocument;
        private SyncDocument pendingLocalDocument;

        RecordingMetadataStore() {
        }

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
}
