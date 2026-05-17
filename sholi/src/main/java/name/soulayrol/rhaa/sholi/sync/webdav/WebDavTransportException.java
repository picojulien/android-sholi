package name.soulayrol.rhaa.sholi.sync.webdav;

public final class WebDavTransportException extends Exception {

    private static final long serialVersionUID = 1L;

    public WebDavTransportException(String message) {
        super(WebDavSafeText.message(message));
    }

    public WebDavTransportException(String message, Throwable cause) {
        super(WebDavSafeText.message(message), cause);
    }
}
