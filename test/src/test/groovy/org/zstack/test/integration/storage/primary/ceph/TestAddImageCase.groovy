package org.zstack.test.integration.storage.primary.ceph

import junit.framework.Assert
import org.zstack.header.image.ImageConstant
import org.zstack.header.volume.VolumeConstant
import org.zstack.sdk.BackupStorageInventory
import org.zstack.sdk.ImageInventory
import org.zstack.sdk.VmInstanceInventory
import org.zstack.sdk.ZoneInventory
import org.zstack.test.integration.storage.CephEnv
import org.zstack.test.integration.storage.StorageTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase

/**
 * Created by heathhose on 17-3-22.
 */
class TestAddImageCase extends SubCase{
    def description = """
        1. use ceph for primary storage and backup storage
        2. create a vm
        3. create an image from the vm's root volume
        confirm the volume created successfully
    """

    EnvSpec env

    @Override
    void setup() {
        useSpring(StorageTest.springSpec)
    }

    @Override
    void environment() {
        env = CephEnv.CephStorageOneVmEnv()
    }

    @Override
    void test() {
        env.create {
            createImageFromRootVolume()
        }
    }

    void createImageFromRootVolume(){
        BackupStorageInventory bs = env.inventoryByName("ceph-bk")
        ZoneInventory zone  = env.inventoryByName("zone")


        def imageName = "large-image"
        def thread = Thread.start {
            addImage {
                name = imageName
                url = "http://my-site/foo.iso"
                backupStorageUuids = [bs.uuid]
                format = ImageConstant.ISO_FORMAT_STRING
            }
        }

        addImage {
            name = "asdf"
            url = "ceph://bak-t-9b278a2c62fb4ed281b1695a6ebdbc8c/335b922a93cd4c529a08c63e431f2345"
            backupStorageUuids = [bs.uuid]
            format = ImageConstant.ISO_FORMAT_STRING
        }

    }
    
    @Override
    void clean() {
        env.delete()
    }
}