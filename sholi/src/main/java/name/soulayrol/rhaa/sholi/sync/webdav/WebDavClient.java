package name.soulayrol.rhaa.sholi.sync.webdav;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WebDavClient {

    private static final Pattern GETETAG_PATTERN = Pattern.compile(
            "<(?:(?:[^:>]+):)?getetag>(.*?)</(?:(?:[^:>]+):)?getetag>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final WebDavTransport transport;
    private final Map<String, String> defaultHeaders;

    public WebDavClient(WebDavTransport transport) {
        this(transport, Collections.<String, String>emptyMap());
    }

    public WebDavClient(WebDavTransport transport, Map<String, String> defaultHeaders) {
        if (transport == null) {
            throw new IllegalArgumentException("transport must not be null");
        }
        this.transport = transport;
        this.defaultHeaders = copyHeaders(defaultHeaders);
    }

    public WebDavMetadataResult head(String url) {
        try {
            return metadataResult(transport.execute(new WebDavRequest("HEAD", url, defaultHeaders, null)));
        } catch (WebDavTransportException e) {
            return WebDavMetadataResult.error(
                    WebDavMetadataResult.Status.NETWORK_ERROR,
                    0,
                    "Network error while contacting WebDAV server");
        }
    }

    public WebDavMetadataResult propfindMetadata(String url) {
        LinkedHashMap<String, String> headers = headersWith("Depth", "0");
        try {
            WebDavResponse response = transport.execute(new WebDavRequest("PROPFIND", url, headers, null));
            if (response.getStatusCode() == 207) {
                return WebDavMetadataResult.present(
                        WebDavEtag.classify(extractGetEtag(response.getBody())),
                        response.getStatusCode());
            }
            return metadataResult(response);
        } catch (WebDavTransportException e) {
            return WebDavMetadataResult.error(
                    WebDavMetadataResult.Status.NETWORK_ERROR,
                    0,
                    "Network error while contacting WebDAV server");
        }
    }

    public WebDavGetResult get(String url) {
        try {
            WebDavResponse response = transport.execute(new WebDavRequest("GET", url, defaultHeaders, null));
            int statusCode = response.getStatusCode();
            if (statusCode == 404) {
                return WebDavGetResult.missing(statusCode);
            }
            if (isSuccess(statusCode)) {
                return WebDavGetResult.present(
                        WebDavEtag.classify(response.getHeader("ETag")),
                        response.getBody(),
                        statusCode);
            }
            return WebDavGetResult.error(toGetErrorStatus(statusCode), statusCode, messageFor(statusCode));
        } catch (WebDavTransportException e) {
            return WebDavGetResult.error(
                    WebDavGetResult.Status.NETWORK_ERROR,
                    0,
                    "Network error while contacting WebDAV server");
        }
    }

    public WebDavPutResult putIfAbsent(String url, String body) {
        LinkedHashMap<String, String> headers = headersWith("If-None-Match", "*");
        headers.put("Content-Type", "application/json; charset=utf-8");
        return put(url, headers, body);
    }

    public WebDavPutResult putIfMatch(String url, String body, String etag) {
        if (!WebDavEtag.classify(etag).isStrong()) {
            throw new IllegalArgumentException("If-Match requires a strong ETag");
        }
        LinkedHashMap<String, String> headers = headersWith("If-Match", etag.trim());
        headers.put("Content-Type", "application/json; charset=utf-8");
        return put(url, headers, body);
    }

    private WebDavPutResult put(String url, Map<String, String> headers, String body) {
        try {
            WebDavResponse response = transport.execute(new WebDavRequest("PUT", url, headers, body));
            int statusCode = response.getStatusCode();
            if (isSuccess(statusCode)) {
                return WebDavPutResult.success(
                        WebDavEtag.classify(response.getHeader("ETag")),
                        statusCode);
            }
            if (statusCode == 412) {
                return WebDavPutResult.preconditionFailed(statusCode);
            }
            return WebDavPutResult.error(toPutErrorStatus(statusCode), statusCode, messageFor(statusCode));
        } catch (WebDavTransportException e) {
            return WebDavPutResult.error(
                    WebDavPutResult.Status.NETWORK_ERROR,
                    0,
                    "Network error while contacting WebDAV server");
        }
    }

    private WebDavMetadataResult metadataResult(WebDavResponse response) {
        int statusCode = response.getStatusCode();
        if (statusCode == 404) {
            return WebDavMetadataResult.missing(statusCode);
        }
        if (isSuccess(statusCode)) {
            return WebDavMetadataResult.present(
                    WebDavEtag.classify(response.getHeader("ETag")),
                    statusCode);
        }
        return WebDavMetadataResult.error(toMetadataErrorStatus(statusCode), statusCode, messageFor(statusCode));
    }

    private LinkedHashMap<String, String> headersWith(String name, String value) {
        LinkedHashMap<String, String> headers = new LinkedHashMap<String, String>(defaultHeaders);
        headers.put(name, value);
        return headers;
    }

    private static String extractGetEtag(String body) {
        if (body == null) {
            return null;
        }
        Matcher matcher = GETETAG_PATTERN.matcher(body);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).trim();
    }

    private static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private static WebDavMetadataResult.Status toMetadataErrorStatus(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return WebDavMetadataResult.Status.AUTH_ERROR;
        }
        return statusCode == 0 ? WebDavMetadataResult.Status.NETWORK_ERROR : WebDavMetadataResult.Status.SERVER_ERROR;
    }

    private static WebDavGetResult.Status toGetErrorStatus(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return WebDavGetResult.Status.AUTH_ERROR;
        }
        return statusCode == 0 ? WebDavGetResult.Status.NETWORK_ERROR : WebDavGetResult.Status.SERVER_ERROR;
    }

    private static WebDavPutResult.Status toPutErrorStatus(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return WebDavPutResult.Status.AUTH_ERROR;
        }
        return statusCode == 0 ? WebDavPutResult.Status.NETWORK_ERROR : WebDavPutResult.Status.SERVER_ERROR;
    }

    private static String messageFor(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return "WebDAV authentication or authorization failed";
        }
        return "WebDAV server returned HTTP " + statusCode;
    }

    private static Map<String, String> copyHeaders(Map<String, String> headers) {
        LinkedHashMap<String, String> copy = new LinkedHashMap<String, String>();
        if (headers != null) {
            copy.putAll(headers);
        }
        return Collections.unmodifiableMap(copy);
    }
}
