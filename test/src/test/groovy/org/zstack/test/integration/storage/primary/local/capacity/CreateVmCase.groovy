package org.zstack.test.integration.storage.primary.local.capacity

import org.springframework.http.HttpEntity
import org.zstack.compute.vm.VmGlobalConfig
import org.zstack.core.db.DatabaseFacade
import org.zstack.core.db.Q
import org.zstack.header.vm.VmInstanceDeletionPolicyManager.VmInstanceDeletionPolicy
import org.zstack.header.vm.VmInstanceState
import org.zstack.header.vm.VmInstanceVO
import org.zstack.sdk.*
import org.zstack.storage.primary.local.LocalStorageHostRefVO
import org.zstack.storage.primary.local.LocalStorageHostRefVO_
import org.zstack.storage.primary.local.LocalStorageKvmBackend
import org.zstack.test.integration.storage.StorageTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.data.SizeUnit
import org.zstack.utils.gson.JSONObjectUtil

/**
 * Created by lining on 2017/4/21.
 */
class CreateVmCase extends SubCase {
    EnvSpec env

    @Override
    void clean() {
        env.delete()
    }

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

            sftpBackupStorage {
                name = "sftp"
                url = "/sftp"
                username = "root"
                password = "password"
                hostname = "localhost"

                image {
                    name = "image1"
                    url  = "http://zstack.org/download/test.qcow2"
                }

                image {
                    name = "test-iso"
                    url  = "http://zstack.org/download/test.iso"
                }
            }

            zone {
                name = "zone"
                description = "test"

                cluster {
                    name = "cluster"
                    hypervisorType = "KVM"

                    kvm {
                        name = "kvm"
                        managementIp = "localhost"
                        username = "root"
                        password = "password"
                    }

                    attachPrimaryStorage("local")
                    attachL2Network("l2")
                }

                localPrimaryStorage {
                    name = "local"
                    url = "/local_ps"
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

                attachBackupStorage("sftp")
            }

            vm {
                name = "test-vm"
                useInstanceOffering("instanceOffering")
                useImage("image1")
                useL3Networks("l3")
                useRootDiskOffering("diskOffering")
                useHost("kvm")
            }
        }

    }

    @Override
    void test() {
        env.create {
            testExpungeVmByImageReconnectCheckCapacity()
        }
    }

    void testExpungeVmByImageReconnectCheckCapacity() {
        DatabaseFacade dbf = bean(DatabaseFacade.class)

        VmGlobalConfig.VM_DELETION_POLICY.updateValue(VmInstanceDeletionPolicy.Delay.toString())
        PrimaryStorageInventory ps = env.inventoryByName("local")
        ClusterInventory cluster = env.inventoryByName("cluster")
        ImageInventory image = env.inventoryByName("image1")
        DiskOfferingInventory diskOffering = env.inventoryByName("diskOffering")
        InstanceOfferingInventory instanceOffering = env.inventoryByName("instanceOffering")
        VmInstanceInventory vm = env.inventoryByName("test-vm")

        LocalStorageHostRefVO beforeRefVO = Q.New(LocalStorageHostRefVO.class)
                .eq(LocalStorageHostRefVO_.hostUuid, vm.hostUuid).find()

        GetPrimaryStorageCapacityResult beforeCapacityResult = getPrimaryStorageCapacity {
            primaryStorageUuids = [ps.uuid]
        }


        int spend = 1000000000
        boolean checked = false
        env.simulator(LocalStorageKvmBackend.INIT_PATH) { HttpEntity<String> e, EnvSpec spec ->
            LocalStorageKvmBackend.InitCmd cmd = JSONObjectUtil.toObject(e.body,LocalStorageKvmBackend.InitCmd.class)

            LocalStorageHostRefVO refVO = Q.New(LocalStorageHostRefVO.class)
                    .eq(LocalStorageHostRefVO_.hostUuid, cmd.hostUuid).find()

            def rsp = new LocalStorageKvmBackend.AgentResponse()
            rsp.totalCapacity = refVO.totalPhysicalCapacity
            if(cmd.hostUuid == vm.hostUuid){
                rsp.availableCapacity = refVO.totalPhysicalCapacity - spend
            }else{
                rsp.availableCapacity = refVO.availablePhysicalCapacity
            }
            checked = true
            return rsp
        }
        VmInstanceInventory newVm = createVmInstance {
            name = "newVm"
            instanceOfferingUuid = vm.instanceOfferingUuid
            imageUuid = vm.imageUuid
            l3NetworkUuids = [vm.defaultL3NetworkUuid]
        }
        // assert checked

        return
        reconnectHost {
            uuid = vm.hostUuid
        }
        reconnectPrimaryStorage {
            uuid = ps.uuid
        }
        GetPrimaryStorageCapacityResult afterCapacityResult = getPrimaryStorageCapacity {
            primaryStorageUuids = [ps.uuid]
        }
        LocalStorageHostRefVO afterRefVO = Q.New(LocalStorageHostRefVO.class)
                .eq(LocalStorageHostRefVO_.hostUuid, vm.hostUuid).find()
        assert checked
        assert beforeCapacityResult.availablePhysicalCapacity ==  afterCapacityResult.availablePhysicalCapacity + spend
        //assert beforeCapacityResult.availableCapacity > afterCapacityResult.availableCapacity
        assert beforeRefVO.availablePhysicalCapacity == afterRefVO.availablePhysicalCapacity + spend


        destroyVmInstance {
            uuid = vm.uuid
        }
        VmInstanceVO vmvo = dbFindByUuid(vm.uuid, VmInstanceVO.class)
        assert vmvo.state == VmInstanceState.Destroyed

        def delete_bits_path_is_invoked = false
        env.simulator(LocalStorageKvmBackend.DELETE_BITS_PATH) { HttpEntity<String> e, EnvSpec spec ->
            delete_bits_path_is_invoked = true
            LocalStorageHostRefVO refVO = Q.New(LocalStorageHostRefVO.class)
                    .eq(LocalStorageHostRefVO_.hostUuid, vm.hostUuid).find()
            def rsp = new LocalStorageKvmBackend.AgentResponse()
            rsp.totalCapacity = refVO.totalPhysicalCapacity
            rsp.availableCapacity = refVO.availablePhysicalCapacity + spend
            return rsp
        }
        expungeVmInstance {
            uuid = vmSpec.inventory.uuid
        }
        assert delete_bits_path_is_invoked

        //GetPrimaryStorageCapacityResult capacityResult = getPrimaryStorageCapacity {
        //    primaryStorageUuids = [ps.uuid]
        //}
        //assert beforeCapacityResult.availableCapacity < capacityResult.availableCapacity
        //assert beforeCapacityResult.availablePhysicalCapacity < capacityResult.availablePhysicalCapacity

        env.simulator(LocalStorageKvmBackend.INIT_PATH) { HttpEntity<String> e, EnvSpec spec ->
            LocalStorageKvmBackend.InitCmd cmd = JSONObjectUtil.toObject(e.body,LocalStorageKvmBackend.InitCmd.class)

            LocalStorageHostRefVO refVO = Q.New(LocalStorageHostRefVO.class)
                    .eq(LocalStorageHostRefVO_.hostUuid, cmd.hostUuid).find()

            def rsp = new LocalStorageKvmBackend.AgentResponse()
            rsp.totalCapacity = refVO.totalPhysicalCapacity
            rsp.availableCapacity = refVO.availablePhysicalCapacity
            return rsp
        }
        reconnectPrimaryStorage {
            uuid = ps.uuid
        }
        afterCapacityResult = getPrimaryStorageCapacity {
            primaryStorageUuids = [ps.uuid]
        }

        assert beforeCapacityResult.availablePhysicalCapacity == afterCapacityResult.availablePhysicalCapacity
        assert beforeCapacityResult.availableCapacity == afterCapacityResult.availableCapacity
    }
}