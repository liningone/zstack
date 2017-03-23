package org.zstack.storage.primary.local;

import java.io.Serializable;

public class CompositePrimaryKeyForLocalStorageResourceRefVO implements Serializable {
    private String resourceUuid;
    private String primaryStorageUuid;

    public String getResourceUuid() {
        return resourceUuid;
    }

    public void setResourceUuid(String resourceUuid) {
        this.resourceUuid = resourceUuid;
    }

    public String getPrimaryStorageUuid() {
        return primaryStorageUuid;
    }

    public void setPrimaryStorageUuid(String primaryStorageUuid) {
        this.primaryStorageUuid = primaryStorageUuid;
    }
}
