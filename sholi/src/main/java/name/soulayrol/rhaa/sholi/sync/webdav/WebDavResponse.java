package name.soulayrol.rhaa.sholi.sync.webdav;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WebDavResponse {

    private final int statusCode;
    private final Map<String, String> headers;
    private final String body;

    public WebDavResponse(int statusCode, Map<String, String> headers, String body) {
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("statusCode must be a valid HTTP status code");
        }
        this.statusCode = statusCode;
        this.headers = copyHeaders(headers);
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
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
        return "WebDavResponse{statusCode=" + statusCode
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
                if (entry.getKey() == null || entry.getKey().length() == 0) {
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
}
