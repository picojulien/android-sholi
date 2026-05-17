package name.soulayrol.rhaa.sholi.sync.document;

public final class ModifiedBy {

    private final String name;
    private final String clientId;

    public ModifiedBy(String name, String clientId) {
        if (isEmpty(name)) {
            throw new IllegalArgumentException("modified_by.name must not be empty");
        }
        if (clientId != null && clientId.length() == 0) {
            throw new IllegalArgumentException("modified_by.client_id must not be empty when present");
        }
        this.name = name;
        this.clientId = clientId;
    }

    public String getName() {
        return name;
    }

    public String getClientId() {
        return clientId;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }
}
