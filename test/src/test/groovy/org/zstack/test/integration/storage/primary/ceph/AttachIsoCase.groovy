package org.zstack.test.integration.storage.primary.ceph

import org.springframework.http.HttpEntity
import org.zstack.core.db.DatabaseFacade
import org.zstack.core.db.Q
import org.zstack.header.storage.primary.ImageCacheVO
import org.zstack.header.storage.primary.ImageCacheVO_
import org.zstack.sdk.AttachIsoToVmInstanceAction
import org.zstack.storage.ceph.primary.CephPrimaryStorageBase
import org.zstack.header.storage.primary.PrimaryStorageState
import org.zstack.header.storage.primary.PrimaryStorageVO
import org.zstack.testlib.BackupStorageSpec
import org.zstack.testlib.ImageSpec
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.PrimaryStorageSpec
import org.zstack.testlib.SubCase
import org.zstack.testlib.VmSpec
import org.zstack.test.integration.storage.CephEnv
import org.zstack.test.integration.storage.StorageTestSuite

import org.zstack.utils.gson.JSONObjectUtil

/**
 * Created by xing5 on 2017/2/27.
 */
class AttachIsoCase extends SubCase {
    EnvSpec env

    @Override
    void setup() {
        useSpring(StorageTestSuite.springSpec)
    }

    @Override
    void environment() {
        env = CephEnv.CephStorageOneVmEnv()
    }

    @Override
    void test() {
        env.create {
            testAttachIsoToDisabledCephStorageWhenIsoExisting()
            testAttachIsoToDisabledCephStorageWhenIsoDeleted()
        }
    }

    @Override
    void clean() {
        env.delete()
    }

    void testAttachIsoToDisabledCephStorageWhenIsoExisting() {
        String vmUuid = (env.specByName("test-vm") as VmSpec).inventory.uuid
        String imageUuid = (env.specByName("test-iso") as ImageSpec).inventory.uuid
        String psUuid = (env.specByName("ceph-pri") as PrimaryStorageSpec).inventory.uuid
        DatabaseFacade dbf = bean(DatabaseFacade.class)

        assert psUuid
        assert vmUuid
        assert imageUuid


        PrimaryStorageVO psvo = dbf.findByUuid(psUuid, PrimaryStorageVO.class)
        psvo.state = PrimaryStorageState.Enabled
        dbf.updateAndRefresh(psvo)
        assert dbf.findByUuid(psUuid, PrimaryStorageVO.class).state == PrimaryStorageState.Enabled

        env.simulator(CephPrimaryStorageBase.CHECK_BITS_PATH) {
            CephPrimaryStorageBase.CheckIsBitsExistingRsp rsp = new CephPrimaryStorageBase.CheckIsBitsExistingRsp()
            rsp.setExisting(true)
            return rsp
        }

        attachIsoToVmInstance {
            isoUuid = imageUuid
            vmInstanceUuid = vmUuid
            sessionId = currentEnvSpec.session.uuid
        }
        detachIsoFromVmInstance {
            vmInstanceUuid = vmUuid
            sessionId = currentEnvSpec.session.uuid
        }

        ImageCacheVO cache = Q.New(ImageCacheVO.class)
                .eq(ImageCacheVO_.primaryStorageUuid, psUuid)
                .eq(ImageCacheVO_.imageUuid, imageUuid)
                .find()


        assert cache != null
        assert cache.installUrl != null

        psvo.state = PrimaryStorageState.Disabled
        dbf.updateAndRefresh(psvo)
        assert dbf.findByUuid(psUuid, PrimaryStorageVO.class).state == PrimaryStorageState.Disabled

        CephPrimaryStorageBase.CheckIsBitsExistingCmd cmd = null


        env.afterSimulator(CephPrimaryStorageBase.CHECK_BITS_PATH) { rsp, HttpEntity<String> e ->
            cmd = JSONObjectUtil.toObject(e.body, CephPrimaryStorageBase.CheckIsBitsExistingCmd.class)
            return rsp
        }
        attachIsoToVmInstance {
            isoUuid = imageUuid
            vmInstanceUuid = vmUuid
            sessionId = currentEnvSpec.session.uuid
        }
        assert cmd != null
        assert cmd.installPath == cache.installUrl
    }

    void testAttachIsoToDisabledCephStorageWhenIsoDeleted(){
        String vmUuid = (env.specByName("test-vm") as VmSpec).inventory.uuid
        String imageUuid = (env.specByName("test-iso") as ImageSpec).inventory.uuid
        String psUuid = (env.specByName("ceph-pri") as PrimaryStorageSpec).inventory.uuid
        BackupStorageSpec backupStorageSpec = env.specByName("ceph-bk") as BackupStorageSpec
        DatabaseFacade dbf = bean(DatabaseFacade.class)

        assert vmUuid
        assert imageUuid
        assert psUuid
        assert backupStorageSpec.inventory.uuid

        detachIsoFromVmInstance {
            vmInstanceUuid = vmUuid
            sessionId = currentEnvSpec.session.uuid
        }

        ImageCacheVO cache = Q.New(ImageCacheVO.class)
                .eq(ImageCacheVO_.primaryStorageUuid, psUuid)
                .eq(ImageCacheVO_.imageUuid, imageUuid)
                .find()

        assert cache != null
        assert cache.installUrl != null

        PrimaryStorageVO  psvo = dbf.findByUuid(psUuid, PrimaryStorageVO.class)
        psvo.state = PrimaryStorageState.Disabled
        dbf.updateAndRefresh(psvo)
        assert dbf.findByUuid(psUuid, PrimaryStorageVO.class).state == PrimaryStorageState.Disabled


        env.simulator(CephPrimaryStorageBase.CHECK_BITS_PATH) {
            CephPrimaryStorageBase.CheckIsBitsExistingRsp rsp = new CephPrimaryStorageBase.CheckIsBitsExistingRsp()
            rsp.setExisting(false)
            return rsp
        }

        AttachIsoToVmInstanceAction a = new AttachIsoToVmInstanceAction()
        a.isoUuid = imageUuid
        a.vmInstanceUuid = vmUuid
        a.sessionId = currentEnvSpec.session.uuid

        AttachIsoToVmInstanceAction.Result res = a.call()
        assert res.error != null

    }
}