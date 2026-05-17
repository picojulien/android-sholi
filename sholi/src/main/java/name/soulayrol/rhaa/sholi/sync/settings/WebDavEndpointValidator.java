package name.soulayrol.rhaa.sholi.sync.settings;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import name.soulayrol.rhaa.sholi.sync.credentials.WebDavCredentials;
import name.soulayrol.rhaa.sholi.sync.credentials.WebDavSyncProfile;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavRequest;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavResponse;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavTransport;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavTransportException;

public final class WebDavEndpointValidator {

    public static final String PROBE_BODY = "sholi-webdav-connection-test\n";

    private static final char[] BASE64 =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    private final WebDavTransport transport;

    public WebDavEndpointValidator(WebDavTransport transport) {
        if (transport == null) {
            throw new IllegalArgumentException("transport must not be null");
        }
        this.transport = transport;
    }

    public WebDavEndpointValidationResult validate(
            WebDavSyncProfile profile,
            WebDavCredentials credentials,
            boolean allowInsecureUrl) {
        if (profile == null) {
            return failure(WebDavEndpointValidationResult.Status.INVALID_URL,
                    "Enter a valid WebDAV URL before testing the connection");
        }
        if (credentials == null) {
            return failure(WebDavEndpointValidationResult.Status.MISSING_CREDENTIALS,
                    "Enter WebDAV credentials before testing the connection");
        }

        ConfiguredWebDavEndpoint endpoint;
        try {
            endpoint = new ConfiguredWebDavEndpoint(profile, credentials);
        } catch (IllegalArgumentException e) {
            String message = e.getMessage() == null ? "Enter valid WebDAV settings" : e.getMessage();
            if (message.toLowerCase(Locale.US).contains("remote file path")) {
                return failure(WebDavEndpointValidationResult.Status.INVALID_REMOTE_PATH, message);
            }
            return failure(WebDavEndpointValidationResult.Status.INVALID_URL, message);
        }

        String scheme = ConfiguredWebDavEndpoint.requireHttpUri(profile.getUrl()).getScheme();
        if ("http".equalsIgnoreCase(scheme) && !allowInsecureUrl) {
            return WebDavEndpointValidationResult.warning(
                    WebDavEndpointValidationResult.Status.INSECURE_URL_REQUIRES_CONFIRMATION,
                    "Non-HTTPS WebDAV URLs can expose credentials. Use HTTPS or confirm that you trust this connection.");
        }

        LinkedHashMap<String, String> authHeaders = authorizationHeaders(credentials);
        ProbeResponse parent = request(
                "PROPFIND",
                endpoint.getParentCollectionUrl(),
                headersWith(authHeaders, "Depth", "0"),
                null);
        if (parent.error != null) {
            return parent.error;
        }
        WebDavEndpointValidationResult parentResult = validateParentCollection(parent.response);
        if (parentResult != null) {
            return parentResult;
        }

        String probeUrl = endpoint.buildProbeUrl();
        LinkedHashMap<String, String> putHeaders = headersWith(authHeaders, "If-None-Match", "*");
        putHeaders.put("Content-Type", "text/plain; charset=utf-8");
        ProbeResponse put = request("PUT", probeUrl, putHeaders, PROBE_BODY);
        if (put.error != null) {
            return put.error;
        }
        WebDavEndpointValidationResult putResult = validateWrite(put.response);
        if (putResult != null) {
            return putResult;
        }

        ProbeResponse get = request("GET", probeUrl, authHeaders, null);
        if (get.error != null) {
            cleanupProbe(probeUrl, authHeaders);
            return get.error;
        }
        WebDavEndpointValidationResult readResult = validateRead(get.response);
        if (readResult != null) {
            cleanupProbe(probeUrl, authHeaders);
            return readResult;
        }

        ProbeResponse delete = request("DELETE", probeUrl, authHeaders, null);
        if (delete.error != null) {
            return delete.error;
        }
        if (!isSuccess(delete.response.getStatusCode())) {
            return failure(WebDavEndpointValidationResult.Status.READ_WRITE_FAILED,
                    "WebDAV read/write probe succeeded but the temporary test file could not be removed");
        }

        return WebDavEndpointValidationResult.success("WebDAV connection test succeeded");
    }

    private WebDavEndpointValidationResult validateParentCollection(WebDavResponse response) {
        int statusCode = response.getStatusCode();
        if (statusCode == 207) {
            return null;
        }
        if (statusCode == 401 || statusCode == 403) {
            return failure(WebDavEndpointValidationResult.Status.AUTHENTICATION_FAILED,
                    "WebDAV authentication failed. Check the username and password or app-specific token.");
        }
        if (statusCode == 404 || statusCode == 409) {
            return failure(WebDavEndpointValidationResult.Status.MISSING_COLLECTION,
                    "The remote WebDAV collection for the configured path was not found.");
        }
        if (statusCode == 405 || statusCode == 501) {
            return failure(WebDavEndpointValidationResult.Status.UNSUPPORTED_WEBDAV,
                    "The server does not appear to support required WebDAV PROPFIND behavior.");
        }
        if (isSuccess(statusCode)) {
            return failure(WebDavEndpointValidationResult.Status.UNSUPPORTED_WEBDAV,
                    "The server answered, but did not provide WebDAV collection metadata.");
        }
        return failure(WebDavEndpointValidationResult.Status.SERVER_ERROR,
                "WebDAV server returned HTTP " + statusCode + " while checking the remote collection.");
    }

    private WebDavEndpointValidationResult validateWrite(WebDavResponse response) {
        int statusCode = response.getStatusCode();
        if (isSuccess(statusCode)) {
            return null;
        }
        if (statusCode == 401) {
            return failure(WebDavEndpointValidationResult.Status.AUTHENTICATION_FAILED,
                    "WebDAV authentication failed. Check the username and password or app-specific token.");
        }
        if (statusCode == 403) {
            return failure(WebDavEndpointValidationResult.Status.WRITE_DENIED,
                    "The WebDAV account does not have write permission for the remote collection.");
        }
        if (statusCode == 404 || statusCode == 409) {
            return failure(WebDavEndpointValidationResult.Status.MISSING_COLLECTION,
                    "The remote WebDAV collection for the configured path was not found.");
        }
        if (statusCode == 405 || statusCode == 501) {
            return failure(WebDavEndpointValidationResult.Status.UNSUPPORTED_WEBDAV,
                    "The server does not support WebDAV writes needed for synchronization.");
        }
        return failure(WebDavEndpointValidationResult.Status.SERVER_ERROR,
                "WebDAV server returned HTTP " + statusCode + " while writing a temporary test file.");
    }

    private WebDavEndpointValidationResult validateRead(WebDavResponse response) {
        int statusCode = response.getStatusCode();
        if (isSuccess(statusCode) && PROBE_BODY.equals(response.getBody())) {
            return null;
        }
        if (statusCode == 401 || statusCode == 403) {
            return failure(WebDavEndpointValidationResult.Status.AUTHENTICATION_FAILED,
                    "WebDAV authentication failed while reading the temporary test file.");
        }
        return failure(WebDavEndpointValidationResult.Status.READ_WRITE_FAILED,
                "WebDAV read/write probe failed while reading back the temporary test file.");
    }

    private void cleanupProbe(String probeUrl, Map<String, String> authHeaders) {
        try {
            transport.execute(new WebDavRequest("DELETE", probeUrl, authHeaders, null));
        } catch (WebDavTransportException e) {
            // Best-effort cleanup after a failed probe. The returned failure already explains the problem.
        }
    }

    private ProbeResponse request(
            String method,
            String url,
            Map<String, String> headers,
            String body) {
        try {
            return ProbeResponse.response(transport.execute(new WebDavRequest(method, url, headers, body)));
        } catch (WebDavTransportException e) {
            return ProbeResponse.error(transportFailure(e));
        }
    }

    private WebDavEndpointValidationResult transportFailure(WebDavTransportException e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.US);
        if (message.contains("timeout") || message.contains("timed out")) {
            return failure(WebDavEndpointValidationResult.Status.TIMEOUT,
                    "WebDAV connection timed out while testing the endpoint.");
        }
        return failure(WebDavEndpointValidationResult.Status.NETWORK_ERROR,
                "Network error while testing the WebDAV endpoint.");
    }

    private WebDavEndpointValidationResult failure(
            WebDavEndpointValidationResult.Status status,
            String message) {
        return WebDavEndpointValidationResult.failure(status, message);
    }

    private static LinkedHashMap<String, String> authorizationHeaders(WebDavCredentials credentials) {
        LinkedHashMap<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Authorization", "Basic " + base64(credentials.getUsername() + ":" + credentials.getPasswordOrToken()));
        return headers;
    }

    private static LinkedHashMap<String, String> headersWith(
            Map<String, String> headers,
            String name,
            String value) {
        LinkedHashMap<String, String> copy = new LinkedHashMap<String, String>(headers);
        copy.put(name, value);
        return copy;
    }

    private static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private static String base64(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder builder = new StringBuilder(((bytes.length + 2) / 3) * 4);
        for (int i = 0; i < bytes.length; i += 3) {
            int b0 = bytes[i] & 0xff;
            int b1 = i + 1 < bytes.length ? bytes[i + 1] & 0xff : 0;
            int b2 = i + 2 < bytes.length ? bytes[i + 2] & 0xff : 0;
            builder.append(BASE64[b0 >>> 2]);
            builder.append(BASE64[((b0 & 0x03) << 4) | (b1 >>> 4)]);
            builder.append(i + 1 < bytes.length ? BASE64[((b1 & 0x0f) << 2) | (b2 >>> 6)] : '=');
            builder.append(i + 2 < bytes.length ? BASE64[b2 & 0x3f] : '=');
        }
        return builder.toString();
    }

    private static final class ProbeResponse {
        private final WebDavResponse response;
        private final WebDavEndpointValidationResult error;

        private ProbeResponse(WebDavResponse response, WebDavEndpointValidationResult error) {
            this.response = response;
            this.error = error;
        }

        static ProbeResponse response(WebDavResponse response) {
            return new ProbeResponse(response, null);
        }

        static ProbeResponse error(WebDavEndpointValidationResult error) {
            return new ProbeResponse(null, error);
        }
    }
}
