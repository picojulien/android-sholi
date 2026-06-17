package name.soulayrol.rhaa.sholi.sync.webdav;

public final class WebDavMetadataResult {

    public enum Status {
        MISSING,
        PRESENT,
        AUTH_ERROR,
        NETWORK_ERROR,
        SERVER_ERROR
    }

    private final Status status;
    private final WebDavEtag etag;
    private final int statusCode;
    private final String message;

    private WebDavMetadataResult(Status status, WebDavEtag etag, int statusCode, String message) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.status = status;
        this.etag = etag;
        this.statusCode = statusCode;
        this.message = WebDavSafeText.message(message);
    }

    public static WebDavMetadataResult missing(int statusCode) {
        return new WebDavMetadataResult(Status.MISSING, WebDavEtag.classify(null), statusCode, null);
    }

    public static WebDavMetadataResult present(WebDavEtag etag, int statusCode) {
        return new WebDavMetadataResult(Status.PRESENT, requireEtag(etag), statusCode, null);
    }

    public static WebDavMetadataResult error(Status status, int statusCode, String message) {
        if (status == Status.MISSING || status == Status.PRESENT) {
            throw new IllegalArgumentException("error status required");
        }
        return new WebDavMetadataResult(status, WebDavEtag.classify(null), statusCode, message);
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
        return "WebDavMetadataResult{status=" + status
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
