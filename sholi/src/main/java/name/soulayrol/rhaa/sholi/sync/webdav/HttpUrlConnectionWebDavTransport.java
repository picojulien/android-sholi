package name.soulayrol.rhaa.sholi.sync.webdav;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HttpUrlConnectionWebDavTransport implements WebDavTransport {

    private static final int TIMEOUT_MILLIS = 15000;

    @Override
    public WebDavResponse execute(WebDavRequest request) throws WebDavTransportException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(request.getUrl()).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            setRequestMethod(connection, request.getMethod());
            for (Map.Entry<String, String> header: request.getHeaders().entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            if (request.getBody() != null) {
                byte[] body = request.getBody().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(body.length);
                OutputStream outputStream = connection.getOutputStream();
                try {
                    outputStream.write(body);
                } finally {
                    outputStream.close();
                }
            }
            int statusCode = connection.getResponseCode();
            return new WebDavResponse(statusCode, responseHeaders(connection), responseBody(connection));
        } catch (SocketTimeoutException e) {
            throw new WebDavTransportException("WebDAV connection timed out", e);
        } catch (IOException e) {
            throw new WebDavTransportException("Network error while contacting WebDAV server", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void setRequestMethod(HttpURLConnection connection, String method)
            throws ProtocolException {
        try {
            connection.setRequestMethod(method);
        } catch (ProtocolException e) {
            forceRequestMethod(connection, method, e);
        }
    }

    private static void forceRequestMethod(
            HttpURLConnection connection,
            String method,
            ProtocolException original) throws ProtocolException {
        Class<?> type = connection.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("method");
                field.setAccessible(true);
                field.set(connection, method);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                break;
            } catch (RuntimeException e) {
                break;
            }
        }
        throw original;
    }

    private static Map<String, String> responseHeaders(HttpURLConnection connection) {
        LinkedHashMap<String, String> headers = new LinkedHashMap<String, String>();
        for (Map.Entry<String, List<String>> entry: connection.getHeaderFields().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                headers.put(entry.getKey(), entry.getValue().get(0));
            }
        }
        return headers;
    }

    private static String responseBody(HttpURLConnection connection) throws IOException {
        InputStream inputStream;
        try {
            inputStream = connection.getInputStream();
        } catch (IOException e) {
            inputStream = connection.getErrorStream();
        }
        if (inputStream == null) {
            return null;
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = inputStream.read(chunk)) >= 0) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            inputStream.close();
        }
    }
}
