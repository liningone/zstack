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
import org.zstack.utils.data.SizeUnit

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
        env = env {
            instanceOffering {
                name = "instanceOffering"
                memory = SizeUnit.GIGABYTE.toByte(8)
                cpu = 4
            }
            diskOffering {
                name = "diskOffering"
                diskSize = SizeUnit.GIGABYTE.toByte(20)
            }
            zone{
                name = "zone"
                cluster {
                    name = "test-cluster"
                    hypervisorType = "KVM"

                    kvm {
                        name = "ceph-mon"
                        managementIp = "localhost"
                        username = "root"
                        password = "password"
                        usedMem = 1000
                        totalCpu = 10
                    }
                    kvm {
                        name = "host"
                        username = "root"
                        password = "password"
                        usedMem = 1000
                        totalCpu = 10
                    }

                    attachPrimaryStorage("ceph-pri")
                    attachL2Network("l2")
                }
                l2NoVlanNetwork {
                    name = "l2"
                    physicalInterface = "eth0"

                    l3Network {
                        name = "l3"



                        ip {
                            startIp = "192.168.100.10"
                            endIp = "192.168.100.100"
                            netmask = "255.255.255.0"
                            gateway = "192.168.100.1"
                        }
                    }
                }

                cephPrimaryStorage {
                    name="ceph-pri"
                    description="Test"
                    totalCapacity = SizeUnit.GIGABYTE.toByte(100)
                    availableCapacity= SizeUnit.GIGABYTE.toByte(100)
                    url="ceph://pri"
                    fsid="7ff218d9-f525-435f-8a40-3618d1772a64"
                    monUrls=["root:password@localhost/?monPort=7777"]

                }

                attachBackupStorage("ceph-bk")
            }

            cephBackupStorage {
                name="ceph-bk"
                description="Test"
                totalCapacity = SizeUnit.GIGABYTE.toByte(100)
                availableCapacity= SizeUnit.GIGABYTE.toByte(100)
                url = "/bk"
                fsid ="7ff218d9-f525-435f-8a40-3618d1772a64"
                monUrls = ["root:password@localhost/?monPort=7777"]
            }
        }
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