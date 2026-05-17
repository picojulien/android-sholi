package name.soulayrol.rhaa.sholi.sync.webdav;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class WebDavRequest {

    private final String method;
    private final String url;
    private final Map<String, String> headers;
    private final String body;

    public WebDavRequest(String method, String url, Map<String, String> headers, String body) {
        if (isEmpty(method)) {
            throw new IllegalArgumentException("method must not be empty");
        }
        if (isEmpty(url)) {
            throw new IllegalArgumentException("url must not be empty");
        }
        this.method = method.toUpperCase(Locale.US);
        this.url = url;
        this.headers = copyHeaders(headers);
        this.body = body;
    }

    public String getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getHeader(String name) {
        if (name == null) {
            return null;
        }
        String value = headers.get(name);
        if (value != null) {
            return value;
        }
        for (Map.Entry<String, String> entry: headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    public String getBody() {
        return body;
    }

    @Override
    public String toString() {
        return "WebDavRequest{method='" + method + '\''
                + ", url='" + WebDavSafeText.url(url) + '\''
                + ", headers=" + safeHeaders()
                + ", bodyChars=" + (body == null ? 0 : body.length())
                + '}';
    }

    private Map<String, String> safeHeaders() {
        LinkedHashMap<String, String> safeHeaders = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry: headers.entrySet()) {
            safeHeaders.put(entry.getKey(), WebDavSafeText.headerValue(entry.getKey(), entry.getValue()));
        }
        return safeHeaders;
    }

    private static Map<String, String> copyHeaders(Map<String, String> headers) {
        LinkedHashMap<String, String> copy = new LinkedHashMap<String, String>();
        if (headers != null) {
            for (Map.Entry<String, String> entry: headers.entrySet()) {
                if (isEmpty(entry.getKey())) {
                    throw new IllegalArgumentException("header names must not be empty");
                }
                if (entry.getValue() == null) {
                    throw new IllegalArgumentException("header values must not be null");
                }
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }
}
