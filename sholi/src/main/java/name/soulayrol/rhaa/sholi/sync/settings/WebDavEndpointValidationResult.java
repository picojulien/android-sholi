package name.soulayrol.rhaa.sholi.sync.settings;

import name.soulayrol.rhaa.sholi.sync.credentials.CredentialSafeText;

public final class WebDavEndpointValidationResult {

    public enum Status {
        SUCCESS,
        INSECURE_URL_REQUIRES_CONFIRMATION,
        INVALID_URL,
        INVALID_REMOTE_PATH,
        MISSING_CREDENTIALS,
        AUTHENTICATION_FAILED,
        MISSING_COLLECTION,
        WRITE_DENIED,
        TIMEOUT,
        UNSUPPORTED_WEBDAV,
        READ_WRITE_FAILED,
        NETWORK_ERROR,
        SERVER_ERROR
    }

    private final Status status;
    private final String message;
    private final boolean warning;

    private WebDavEndpointValidationResult(Status status, String message, boolean warning) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.status = status;
        this.message = CredentialSafeText.message(message);
        this.warning = warning;
    }

    public static WebDavEndpointValidationResult success(String message) {
        return new WebDavEndpointValidationResult(Status.SUCCESS, message, false);
    }

    public static WebDavEndpointValidationResult warning(Status status, String message) {
        return new WebDavEndpointValidationResult(status, message, true);
    }

    public static WebDavEndpointValidationResult failure(Status status, String message) {
        return new WebDavEndpointValidationResult(status, message, false);
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public boolean isWarning() {
        return warning;
    }

    @Override
    public String toString() {
        return "WebDavEndpointValidationResult{status=" + status
                + ", warning=" + warning
                + ", message='" + CredentialSafeText.message(message) + "'}";
    }
}
