package name.soulayrol.rhaa.sholi.sync.webdav;

public final class WebDavGetResult {

    public enum Status {
        MISSING,
        PRESENT,
        AUTH_ERROR,
        NETWORK_ERROR,
        SERVER_ERROR
    }

    private final Status status;
    private final WebDavEtag etag;
    private final String body;
    private final int statusCode;
    private final String message;

    private WebDavGetResult(
            Status status,
            WebDavEtag etag,
            String body,
            int statusCode,
            String message) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.status = status;
        this.etag = etag;
        this.body = body;
        this.statusCode = statusCode;
        this.message = WebDavSafeText.message(message);
    }

    public static WebDavGetResult missing(int statusCode) {
        return new WebDavGetResult(Status.MISSING, WebDavEtag.classify(null), null, statusCode, null);
    }

    public static WebDavGetResult present(WebDavEtag etag, String body, int statusCode) {
        return new WebDavGetResult(Status.PRESENT, requireEtag(etag), body, statusCode, null);
    }

    public static WebDavGetResult error(Status status, int statusCode, String message) {
        if (status == Status.MISSING || status == Status.PRESENT) {
            throw new IllegalArgumentException("error status required");
        }
        return new WebDavGetResult(status, WebDavEtag.classify(null), null, statusCode, message);
    }

    public Status getStatus() {
        return status;
    }

    public WebDavEtag getEtag() {
        return etag;
    }

    public String getBody() {
        return body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "WebDavGetResult{status=" + status
                + ", etag=" + etag
                + ", bodyChars=" + (body == null ? 0 : body.length())
                + ", statusCode=" + statusCode
                + ", message='" + WebDavSafeText.message(message) + "'}";
    }

    private static WebDavEtag requireEtag(WebDavEtag etag) {
        if (etag == null) {
            throw new IllegalArgumentException("etag must not be null");
        }
        return etag;
    }
}
