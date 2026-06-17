package name.soulayrol.rhaa.sholi.sync.webdav;

public final class WebDavPutResult {

    public enum Status {
        SUCCESS,
        PRECONDITION_FAILED,
        AUTH_ERROR,
        NETWORK_ERROR,
        SERVER_ERROR
    }

    private final Status status;
    private final WebDavEtag etag;
    private final int statusCode;
    private final String message;

    private WebDavPutResult(Status status, WebDavEtag etag, int statusCode, String message) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.status = status;
        this.etag = etag;
        this.statusCode = statusCode;
        this.message = WebDavSafeText.message(message);
    }

    public static WebDavPutResult success(WebDavEtag etag, int statusCode) {
        return new WebDavPutResult(Status.SUCCESS, requireEtag(etag), statusCode, null);
    }

    public static WebDavPutResult preconditionFailed(int statusCode) {
        return new WebDavPutResult(
                Status.PRECONDITION_FAILED,
                WebDavEtag.classify(null),
                statusCode,
                "WebDAV precondition failed");
    }

    public static WebDavPutResult error(Status status, int statusCode, String message) {
        if (status == Status.SUCCESS || status == Status.PRECONDITION_FAILED) {
            throw new IllegalArgumentException("error status required");
        }
        return new WebDavPutResult(status, WebDavEtag.classify(null), statusCode, message);
    }

    public Status getStatus() {
        return status;
    }

    public WebDavEtag getEtag() {
        return etag;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "WebDavPutResult{status=" + status
                + ", etag=" + etag
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
