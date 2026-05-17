package story3;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import name.soulayrol.rhaa.sholi.sync.credentials.CredentialSafeLogger;
import name.soulayrol.rhaa.sholi.sync.credentials.CredentialSafeText;
import name.soulayrol.rhaa.sholi.sync.credentials.CredentialStore;
import name.soulayrol.rhaa.sholi.sync.credentials.SyncCredentialProvider;
import name.soulayrol.rhaa.sholi.sync.credentials.SyncExportFormatter;
import name.soulayrol.rhaa.sholi.sync.credentials.SyncJsonSerializer;
import name.soulayrol.rhaa.sholi.sync.credentials.WebDavCredentials;
import name.soulayrol.rhaa.sholi.sync.credentials.WebDavSyncProfile;
import name.soulayrol.rhaa.sholi.sync.settings.ConfiguredWebDavEndpoint;
import name.soulayrol.rhaa.sholi.sync.settings.KeyValueWebDavSyncProfileStore;
import name.soulayrol.rhaa.sholi.sync.settings.WebDavEndpointValidationResult;
import name.soulayrol.rhaa.sholi.sync.settings.WebDavEndpointValidator;
import name.soulayrol.rhaa.sholi.sync.settings.WebDavSyncProfileLoader;
import name.soulayrol.rhaa.sholi.sync.settings.WebDavSyncProfileStore;
import name.soulayrol.rhaa.sholi.sync.settings.WebDavSyncSettingsRepository;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavRequest;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavResponse;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavTransport;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavTransportException;

public final class WebDavSettingsStoryTest {

    private static final String SECRET = "story3-super-secret-token";
    private static final String OPAQUE_SECRET = "opaque-value-12345";

    private WebDavSettingsStoryTest() {
    }

    public static void run() throws Exception {
        verifyNonSecretProfileAndSecretAreStoredSeparately();
        verifyPasswordTokenNeverLeaksIntoSafeText();
        verifyAndroidManifestAllowsNetworkAccess();
        verifyAndroidSettingsExposeRedactedWebDavPreferences();
        verifySecretBearingUrlsAreRejectedAndSummariesAreSafe();
        verifyValidEndpointSucceedsWithSafeProbesOnly();
        verifyEndpointFailuresAreClearAndSafe();
        verifyInvalidUrlAndPathFailWithoutRemoteOrMetadataMutation();
        verifyHttpEndpointRequiresExplicitConfirmation();
        verifyCurrentProfileUsernameWinsOverStoredCredentialUsername();
        verifyHttpTransportDoesNotFollowRedirectsAutomatically();
    }

    private static void verifyNonSecretProfileAndSecretAreStoredSeparately() {
        RecordingKeyValueStore values = new RecordingKeyValueStore();
        KeyValueWebDavSyncProfileStore profileStore = new KeyValueWebDavSyncProfileStore(values);
        RecordingCredentialStore credentialStore = new RecordingCredentialStore();
        WebDavSyncSettingsRepository repository =
                new WebDavSyncSettingsRepository(profileStore, credentialStore);
        WebDavSyncProfile profile = profile();

        repository.save(profile, SECRET);

        assertEquals(profile.getUrl(), values.values.get(KeyValueWebDavSyncProfileStore.KEY_URL), "stored URL");
        assertEquals(profile.getUsername(), values.values.get(KeyValueWebDavSyncProfileStore.KEY_USERNAME), "stored username");
        assertEquals(profile.getRemotePath(), values.values.get(KeyValueWebDavSyncProfileStore.KEY_REMOTE_PATH), "stored remote path");
        assertEquals(profile.getDisplayName(), values.values.get(KeyValueWebDavSyncProfileStore.KEY_DISPLAY_NAME), "stored display name");
        assertEquals(profile.getClientId(), values.values.get(KeyValueWebDavSyncProfileStore.KEY_CLIENT_ID), "stored client id");
        assertEquals(SECRET, credentialStore.saved.getPasswordOrToken(), "credential store secret");
        assertDoesNotContain(SECRET, values.values.toString(), "non-secret profile preferences");

        WebDavSyncProfile loaded = profileStore.load();
        assertEquals(profile.getUrl(), loaded.getUrl(), "loaded URL");
        assertEquals(profile.getRemotePath(), loaded.getRemotePath(), "loaded remote path");
        assertEquals(profile.getDisplayName(), loaded.getDisplayName(), "loaded display name");
    }

    private static void verifyPasswordTokenNeverLeaksIntoSafeText() {
        WebDavCredentials credentials = new WebDavCredentials("alice", SECRET);
        WebDavSyncProfile profile = new WebDavSyncProfile(
                "https://alice:" + SECRET + "@cloud.example.net/remote.php/dav/files/alice"
                        + "?access_token=" + SECRET + "#auth-token/" + SECRET,
                "alice",
                "sholi/sync.json",
                "Alice Phone",
                "client-a");

        assertDoesNotContain(SECRET, SyncJsonSerializer.serialize(profile), "sync profile JSON");
        assertDoesNotContain(SECRET, SyncExportFormatter.export(profile), "sync profile export");
        assertDoesNotContain(SECRET, CredentialSafeLogger.syncConfigured(profile, credentials), "configured log");
        assertDoesNotContain(SECRET, CredentialSafeLogger.authenticationError(profile, credentials), "auth error");
        assertDoesNotContain(OPAQUE_SECRET,
                CredentialSafeText.url("https://cloud.example.net/#authorization=" + OPAQUE_SECRET),
                "authorization fragment URL");
        assertDoesNotContain(SECRET, credentials.toString(), "credentials string");
    }

    private static void verifyAndroidSettingsExposeRedactedWebDavPreferences() throws Exception {
        String preferences = readUtf8("sholi/src/main/res/xml/preferences.xml");
        assertContains(preferences, "android:key=\"pref_webdav_url\"", "WebDAV URL preference");
        assertContains(preferences, "android:key=\"pref_webdav_username\"", "WebDAV username preference");
        assertContains(preferences, "android:key=\"pref_webdav_password_token\"", "WebDAV token preference");
        assertContains(preferences, "android:key=\"pref_webdav_remote_path\"", "WebDAV remote path preference");
        assertContains(preferences, "android:key=\"pref_webdav_display_name\"", "WebDAV display name preference");
        assertContains(preferences, "android:key=\"pref_webdav_test_connection\"", "WebDAV test action");
        assertContains(preferences, "android:persistent=\"false\"", "non-persistent token preference");

        String strings = readUtf8("sholi/src/main/res/values/strings.xml").toLowerCase(Locale.US);
        assertContains(strings, "app-specific token", "token recommendation copy");
        assertContains(strings, "non-https", "non-HTTPS warning copy");

        String fragment = readUtf8("sholi/src/main/java/name/soulayrol/rhaa/sholi/SettingsFragment.java");
        assertContains(fragment, "ProductionCredentialStores.webDav", "encrypted credential store wiring");
        assertContains(fragment, "KEY_WEBDAV_PASSWORD_TOKEN", "password preference handling");
        assertContains(fragment, "settings_webdav_token_summary_redacted", "redacted token summary");
        assertContains(fragment, "new Thread(new Runnable()", "background WebDAV validation");
        assertContains(fragment, "runOnUiThread(new Runnable()", "main-thread WebDAV validation result");
        assertContains(fragment, "CredentialSafeText.message", "sanitized WebDAV result messages");
        assertContains(fragment, "CredentialSafeText.url", "sanitized WebDAV URL summaries");
        assertDoesNotContain(
                "setSummary(sharedPreferences.getString(SettingsActivity.KEY_WEBDAV_PASSWORD_TOKEN",
                fragment,
                "password summary source");
    }

    private static void verifyAndroidManifestAllowsNetworkAccess() throws Exception {
        String manifest = readUtf8("sholi/src/main/AndroidManifest.xml");
        assertContains(manifest, "android.permission.INTERNET", "network permission");
    }

    private static void verifySecretBearingUrlsAreRejectedAndSummariesAreSafe() {
        RecordingKeyValueStore values = new RecordingKeyValueStore();
        final KeyValueWebDavSyncProfileStore profileStore = new KeyValueWebDavSyncProfileStore(values);

        final WebDavSyncProfile userInfoProfile = profileWithUrl(
                "https://alice:" + SECRET + "@cloud.example.net/remote.php/dav/files/alice/");
        assertThrowsIllegalArgument(new ThrowingRunnable() {
            @Override
            public void run() {
                profileStore.save(userInfoProfile);
            }
        }, "URL user-info");
        assertEquals(0, values.values.size(), "user-info URL must not be stored");
        assertDoesNotContain(SECRET, CredentialSafeText.url(userInfoProfile.getUrl()), "user-info URL summary");

        final WebDavSyncProfile queryProfile = profileWithUrl(
                "https://cloud.example.net/remote.php/dav/files/alice/?access_token=" + SECRET);
        assertThrowsIllegalArgument(new ThrowingRunnable() {
            @Override
            public void run() {
                profileStore.save(queryProfile);
            }
        }, "sensitive query URL");
        assertEquals(0, values.values.size(), "sensitive query URL must not be stored");
        assertDoesNotContain(SECRET, CredentialSafeText.url(queryProfile.getUrl()), "query URL summary");

        final WebDavSyncProfile authorizationQueryProfile = profileWithUrl(
                "https://cloud.example.net/remote.php/dav/files/alice/?authorization=" + OPAQUE_SECRET);
        assertThrowsIllegalArgument(new ThrowingRunnable() {
            @Override
            public void run() {
                profileStore.save(authorizationQueryProfile);
            }
        }, "authorization query URL");
        assertEquals(0, values.values.size(), "authorization query URL must not be stored");
        assertDoesNotContain(OPAQUE_SECRET,
                CredentialSafeText.url(authorizationQueryProfile.getUrl()),
                "authorization query URL summary");

        final WebDavSyncProfile fragmentProfile = profileWithUrl(
                "https://cloud.example.net/remote.php/dav/files/alice/#auth-token=" + SECRET);
        assertThrowsIllegalArgument(new ThrowingRunnable() {
            @Override
            public void run() {
                profileStore.save(fragmentProfile);
            }
        }, "sensitive fragment URL");
        assertEquals(0, values.values.size(), "sensitive fragment URL must not be stored");
        assertDoesNotContain(SECRET, CredentialSafeText.url(fragmentProfile.getUrl()), "fragment URL summary");

        values.values.put(KeyValueWebDavSyncProfileStore.KEY_URL, queryProfile.getUrl());
        values.values.put(KeyValueWebDavSyncProfileStore.KEY_USERNAME, profile().getUsername());
        values.values.put(KeyValueWebDavSyncProfileStore.KEY_REMOTE_PATH, profile().getRemotePath());
        values.values.put(KeyValueWebDavSyncProfileStore.KEY_DISPLAY_NAME, profile().getDisplayName());
        assertEquals(null, profileStore.load(), "unsafe stored URL must not be loaded");
        assertEquals(null,
                values.values.get(KeyValueWebDavSyncProfileStore.KEY_URL),
                "unsafe stored URL must be removed");
    }

    private static void verifyValidEndpointSucceedsWithSafeProbesOnly() {
        RecordingTransport transport = new RecordingTransport();
        transport.respond(207, header("DAV", "1, 2"), "<multistatus />");
        transport.respond(201, null, "");
        transport.respond(200, null, WebDavEndpointValidator.PROBE_BODY);
        transport.respond(204, null, "");
        WebDavEndpointValidator validator = new WebDavEndpointValidator(transport);
        MutationSentinel mutationSentinel = new MutationSentinel();

        WebDavEndpointValidationResult result = validator.validate(profile(), credentials(), false);

        assertEquals(WebDavEndpointValidationResult.Status.SUCCESS, result.getStatus(), "success status");
        assertContains(result.getMessage(), "succeeded", "success message");
        assertEquals(list("PROPFIND", "PUT", "GET", "DELETE"), transport.methods(), "safe probe request sequence");
        assertEquals("0", transport.request(0).getHeader("Depth"), "PROPFIND depth");
        assertContains(transport.request(0).getUrl(), "/sholi/", "parent collection probe URL");
        assertEquals(null, transport.findRequestTo(remoteFileUrl()), "remote sync JSON must not be touched");
        assertEquals(0, transport.countBodiesContaining("schema_version"), "validator must not write sync JSON");
        assertEquals(false, mutationSentinel.mutated, "list and sync metadata mutation sentinel");
        assertDoesNotContain(SECRET, transport.requests.toString(), "request log redaction");
    }

    private static void verifyEndpointFailuresAreClearAndSafe() {
        assertFailure(
                responses(response(401, null, "")),
                WebDavEndpointValidationResult.Status.AUTHENTICATION_FAILED,
                "authentication");
        assertFailure(
                responses(response(404, null, "")),
                WebDavEndpointValidationResult.Status.MISSING_COLLECTION,
                "collection");
        assertFailure(
                responses(response(405, null, "")),
                WebDavEndpointValidationResult.Status.UNSUPPORTED_WEBDAV,
                "WebDAV");
        assertFailure(
                responses(response(207, header("DAV", "1, 2"), ""), response(403, null, "")),
                WebDavEndpointValidationResult.Status.WRITE_DENIED,
                "write");

        RecordingTransport timeoutTransport = new RecordingTransport();
        timeoutTransport.fail(new WebDavTransportException(
                "timeout while using password=" + SECRET + " against https://cloud.example.net"));
        WebDavEndpointValidationResult timeout = new WebDavEndpointValidator(timeoutTransport)
                .validate(profile(), credentials(), false);
        assertEquals(WebDavEndpointValidationResult.Status.TIMEOUT, timeout.getStatus(), "timeout status");
        assertContains(timeout.getMessage(), "timed out", "timeout message");
        assertDoesNotContain(SECRET, timeout.toString(), "timeout result");
    }

    private static void verifyInvalidUrlAndPathFailWithoutRemoteOrMetadataMutation() {
        RecordingTransport transport = new RecordingTransport();
        WebDavEndpointValidator validator = new WebDavEndpointValidator(transport);
        MutationSentinel mutationSentinel = new MutationSentinel();

        WebDavEndpointValidationResult invalidUrl = validator.validate(
                new WebDavSyncProfile("not a url", "alice", "sholi/sync.json", "Alice", "client-a"),
                credentials(),
                false);
        assertEquals(WebDavEndpointValidationResult.Status.INVALID_URL, invalidUrl.getStatus(), "invalid URL status");
        assertContains(invalidUrl.getMessage(), "valid WebDAV URL", "invalid URL message");
        assertEquals(0, transport.requests.size(), "invalid URL must not contact remote");

        WebDavEndpointValidationResult invalidPath = validator.validate(
                new WebDavSyncProfile(
                        "https://cloud.example.net/remote.php/dav/files/alice/",
                        "alice",
                        "../sync.json",
                        "Alice",
                        "client-a"),
                credentials(),
                false);
        assertEquals(WebDavEndpointValidationResult.Status.INVALID_REMOTE_PATH, invalidPath.getStatus(), "invalid path status");
        assertContains(invalidPath.getMessage(), "remote file path", "invalid path message");
        assertEquals(0, transport.requests.size(), "invalid path must not contact remote");
        assertEquals(false, mutationSentinel.mutated, "invalid settings mutation sentinel");
    }

    private static void verifyHttpEndpointRequiresExplicitConfirmation() {
        RecordingTransport transport = new RecordingTransport();
        WebDavEndpointValidator validator = new WebDavEndpointValidator(transport);
        WebDavSyncProfile insecure = new WebDavSyncProfile(
                "http://cloud.example.net/remote.php/dav/files/alice/",
                "alice",
                "sholi/sync.json",
                "Alice",
                "client-a");

        WebDavEndpointValidationResult warning = validator.validate(insecure, credentials(), false);
        assertEquals(
                WebDavEndpointValidationResult.Status.INSECURE_URL_REQUIRES_CONFIRMATION,
                warning.getStatus(),
                "non-HTTPS warning status");
        assertEquals(true, warning.isWarning(), "non-HTTPS warning flag");
        assertContains(warning.getMessage(), "HTTPS", "non-HTTPS warning message");
        assertEquals(0, transport.requests.size(), "non-HTTPS warning must wait for confirmation");

        transport.respond(207, header("DAV", "1, 2"), "<multistatus />");
        transport.respond(201, null, "");
        transport.respond(200, null, WebDavEndpointValidator.PROBE_BODY);
        transport.respond(204, null, "");
        WebDavEndpointValidationResult confirmed = validator.validate(insecure, credentials(), true);
        assertEquals(WebDavEndpointValidationResult.Status.SUCCESS, confirmed.getStatus(), "confirmed non-HTTPS status");
    }

    private static void verifyCurrentProfileUsernameWinsOverStoredCredentialUsername() {
        RecordingKeyValueStore values = new RecordingKeyValueStore();
        WebDavSyncProfileStore profileStore = new KeyValueWebDavSyncProfileStore(values);
        RecordingCredentialStore credentialStore = new RecordingCredentialStore();
        profileStore.save(profile());
        credentialStore.save(new WebDavCredentials("old-alice", SECRET));

        WebDavSyncProfileLoader loader = new WebDavSyncProfileLoader(
                profileStore,
                new SyncCredentialProvider(credentialStore));
        ConfiguredWebDavEndpoint endpoint = loader.requireEndpoint();

        assertEquals(profile().getUrl(), endpoint.getProfile().getUrl(), "loaded endpoint URL");
        assertEquals(profile().getRemotePath(), endpoint.getProfile().getRemotePath(), "loaded endpoint path");
        assertEquals(remoteFileUrl(), endpoint.getRemoteFileUrl(), "loaded remote sync URL");
        assertEquals(profile().getUsername(), endpoint.getCredentials().getUsername(), "loaded endpoint credential username");
        assertEquals(SECRET, endpoint.getCredentials().getPasswordOrToken(), "loaded endpoint credential");
        assertEquals(1, credentialStore.loadCount, "loader uses credential provider");

        RecordingTransport transport = new RecordingTransport();
        transport.respond(207, header("DAV", "1, 2"), "<multistatus />");
        transport.respond(201, null, "");
        transport.respond(200, null, WebDavEndpointValidator.PROBE_BODY);
        transport.respond(204, null, "");
        WebDavEndpointValidationResult result = new WebDavEndpointValidator(transport)
                .validate(profile(), new WebDavCredentials("old-alice", SECRET), false);
        assertEquals(WebDavEndpointValidationResult.Status.SUCCESS, result.getStatus(), "username drift validation status");
        assertEquals(expectedBasicAuth(profile().getUsername()),
                transport.request(0).getHeader("Authorization"),
                "validator credential username");
    }

    private static void verifyHttpTransportDoesNotFollowRedirectsAutomatically() throws Exception {
        String transport = readUtf8(
                "sholi/src/main/java/name/soulayrol/rhaa/sholi/sync/webdav/HttpUrlConnectionWebDavTransport.java");
        assertContains(transport, "setInstanceFollowRedirects(false)", "redirect safety");
    }

    private static void assertFailure(
            List<Object> responses,
            WebDavEndpointValidationResult.Status expectedStatus,
            String expectedMessagePart) {
        RecordingTransport transport = new RecordingTransport();
        for (Object response: responses) {
            transport.responses.add(response);
        }
        WebDavEndpointValidationResult result = new WebDavEndpointValidator(transport)
                .validate(profile(), credentials(), false);
        assertEquals(expectedStatus, result.getStatus(), expectedStatus + " status");
        assertContains(result.getMessage(), expectedMessagePart, expectedStatus + " message");
        assertDoesNotContain(SECRET, result.getMessage(), expectedStatus + " message secret safety");
        assertDoesNotContain(SECRET, result.toString(), expectedStatus + " result secret safety");
    }

    private static WebDavSyncProfile profile() {
        return new WebDavSyncProfile(
                "https://cloud.example.net/remote.php/dav/files/alice/",
                "alice",
                "sholi/sync.json",
                "Alice Phone",
                "client-a");
    }

    private static WebDavSyncProfile profileWithUrl(String url) {
        return new WebDavSyncProfile(
                url,
                profile().getUsername(),
                profile().getRemotePath(),
                profile().getDisplayName(),
                profile().getClientId());
    }

    private static WebDavCredentials credentials() {
        return new WebDavCredentials("alice", SECRET);
    }

    private static String remoteFileUrl() {
        return "https://cloud.example.net/remote.php/dav/files/alice/sholi/sync.json";
    }

    private static List<Object> responses(Object... values) {
        ArrayList<Object> list = new ArrayList<Object>();
        for (Object value: values) {
            list.add(value);
        }
        return list;
    }

    private static WebDavResponse response(int statusCode, Map<String, String> headers, String body) {
        return new WebDavResponse(statusCode, headers, body);
    }

    private static Map<String, String> header(String name, String value) {
        LinkedHashMap<String, String> headers = new LinkedHashMap<String, String>();
        headers.put(name, value);
        return headers;
    }

    private static List<String> list(String... values) {
        ArrayList<String> list = new ArrayList<String>();
        for (String value: values) {
            list.add(value);
        }
        return list;
    }

    private static String expectedBasicAuth(String username) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (username + ":" + SECRET).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void assertContains(String content, String expected, String label) {
        if (content == null || !content.contains(expected)) {
            throw new AssertionError("Expected " + label + " to contain " + expected + ": " + content);
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

    private static void assertThrowsIllegalArgument(ThrowingRunnable runnable, String label) {
        try {
            runnable.run();
        } catch (IllegalArgumentException e) {
            assertDoesNotContain(SECRET, e.getMessage(), label + " exception message");
            return;
        }
        throw new AssertionError("Expected " + label + " to be rejected");
    }

    private static String readUtf8(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), "UTF-8");
    }

    private interface ThrowingRunnable {
        void run();
    }

    private static final class RecordingKeyValueStore implements KeyValueWebDavSyncProfileStore.KeyValueStore {
        private final Map<String, String> values = new LinkedHashMap<String, String>();

        @Override
        public String getString(String key, String defaultValue) {
            String value = values.get(key);
            return value == null ? defaultValue : value;
        }

        @Override
        public void putString(String key, String value) {
            values.put(key, value);
        }

        @Override
        public void remove(String key) {
            values.remove(key);
        }
    }

    private static final class RecordingCredentialStore implements CredentialStore {
        private WebDavCredentials saved;
        private int loadCount;

        @Override
        public void save(WebDavCredentials credentials) {
            saved = credentials;
        }

        @Override
        public WebDavCredentials load() {
            loadCount++;
            return saved;
        }

        @Override
        public void clear() {
            saved = null;
        }
    }

    private static final class RecordingTransport implements WebDavTransport {
        private final List<WebDavRequest> requests = new ArrayList<WebDavRequest>();
        private final List<Object> responses = new ArrayList<Object>();

        void respond(int statusCode, Map<String, String> headers, String body) {
            responses.add(new WebDavResponse(statusCode, headers, body));
        }

        void fail(WebDavTransportException exception) {
            responses.add(exception);
        }

        @Override
        public WebDavResponse execute(WebDavRequest request) throws WebDavTransportException {
            requests.add(request);
            if (responses.isEmpty()) {
                throw new AssertionError("No queued response for " + request.getMethod());
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

        WebDavRequest findRequestTo(String url) {
            for (WebDavRequest request: requests) {
                if (url.equals(request.getUrl())) {
                    return request;
                }
            }
            return null;
        }

        int countBodiesContaining(String value) {
            int count = 0;
            for (WebDavRequest request: requests) {
                if (request.getBody() != null && request.getBody().contains(value)) {
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

    private static final class MutationSentinel {
        private boolean mutated;
    }
}
