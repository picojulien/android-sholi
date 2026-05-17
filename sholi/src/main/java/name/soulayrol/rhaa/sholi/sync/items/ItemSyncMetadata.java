package name.soulayrol.rhaa.sholi.sync.items;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public final class ItemSyncMetadata {

    public static final String DEFAULT_MODIFIED_BY_NAME = "local";
    public static final long TOMBSTONE_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1000L;

    private static final String SYNC_ID_PREFIX = "sholi:item:v1:";

    private ItemSyncMetadata() {
    }

    public static String canonicalizeName(String name) {
        if (name == null) {
            return "";
        }
        return collapseWhitespace(name).toLowerCase(Locale.US);
    }

    public static String initialSyncIdForName(String name) {
        return sha256Hex(SYNC_ID_PREFIX + canonicalizeName(name));
    }

    public static void initializeNewItem(SyncTrackedItem item, long modifiedAt, String modifiedByName) {
        if (isEmpty(item.getSyncId())) {
            item.setSyncId(initialSyncIdForName(item.getName()));
        }
        item.setDeleted(Boolean.FALSE);
        item.setDeletedSyncedAt(null);
        item.setModifiedAt(modifiedAt);
        item.setModifiedByName(defaultModifiedByName(modifiedByName));
    }

    public static void ensurePresent(SyncTrackedItem item, long modifiedAt, String modifiedByName) {
        if (isEmpty(item.getSyncId())) {
            item.setSyncId(initialSyncIdForName(item.getName()));
        }
        if (item.getModifiedAt() == null) {
            item.setModifiedAt(modifiedAt);
        }
        if (isEmpty(item.getModifiedByName())) {
            item.setModifiedByName(defaultModifiedByName(modifiedByName));
        }
        if (item.getDeleted() == null) {
            item.setDeleted(Boolean.FALSE);
        }
    }

    public static void touch(SyncTrackedItem item, long modifiedAt, String modifiedByName) {
        ensurePresent(item, modifiedAt, modifiedByName);
        item.setModifiedAt(modifiedAt);
        item.setModifiedByName(defaultModifiedByName(modifiedByName));
    }

    public static void markDeleted(SyncTrackedItem item, long modifiedAt, String modifiedByName) {
        touch(item, modifiedAt, modifiedByName);
        item.setDeleted(Boolean.TRUE);
        item.setDeletedSyncedAt(null);
    }

    public static void restore(SyncTrackedItem item, long modifiedAt, String modifiedByName) {
        touch(item, modifiedAt, modifiedByName);
        item.setDeleted(Boolean.FALSE);
        item.setDeletedSyncedAt(null);
    }

    public static void markDeletedSynced(SyncTrackedItem item, long deletedSyncedAt) {
        if (Boolean.TRUE.equals(item.getDeleted())) {
            item.setDeletedSyncedAt(deletedSyncedAt);
        }
    }

    public static boolean isTombstoneReadyForCleanup(SyncTrackedItem item, long now) {
        if (!Boolean.TRUE.equals(item.getDeleted())) {
            return false;
        }
        Long deletedSyncedAt = item.getDeletedSyncedAt();
        return deletedSyncedAt != null && now - deletedSyncedAt >= TOMBSTONE_RETENTION_MILLIS;
    }

    public static boolean shouldRetainTombstone(SyncTrackedItem item, long now) {
        return Boolean.TRUE.equals(item.getDeleted()) && !isTombstoneReadyForCleanup(item, now);
    }

    static String collapseWhitespace(String name) {
        String trimmed = name.trim();
        StringBuilder builder = new StringBuilder(trimmed.length());
        boolean previousWasWhitespace = false;
        for (int i = 0; i < trimmed.length(); ++i) {
            char c = trimmed.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!previousWasWhitespace) {
                    builder.append(' ');
                    previousWasWhitespace = true;
                }
            } else {
                builder.append(c);
                previousWasWhitespace = false;
            }
        }
        return builder.toString();
    }

    static String defaultModifiedByName(String modifiedByName) {
        return isEmpty(modifiedByName) ? DEFAULT_MODIFIED_BY_NAME : modifiedByName;
    }

    static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes("UTF-8"));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b: bytes) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is not available", e);
        }
    }
}
