package name.soulayrol.rhaa.sholi.sync.items;

public interface SyncTrackedItem {

    Long getId();

    void setId(Long id);

    String getName();

    void setName(String name);

    Integer getStatus();

    void setStatus(Integer status);

    String getSyncId();

    void setSyncId(String syncId);

    Long getModifiedAt();

    void setModifiedAt(Long modifiedAt);

    String getModifiedByName();

    void setModifiedByName(String modifiedByName);

    Boolean getDeleted();

    void setDeleted(Boolean deleted);

    Long getDeletedSyncedAt();

    void setDeletedSyncedAt(Long deletedSyncedAt);
}
