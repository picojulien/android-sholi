package name.soulayrol.rhaa.sholi.sync.webdav;

public interface WebDavTransport {

    WebDavResponse execute(WebDavRequest request) throws WebDavTransportException;
}
