package org.zstack.test.integration.storage.primary.local

import org.zstack.test.integration.kvm.Env
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase

/**
 * Created by lining on 2017/3/5.
 */
class LocalStorageCapCase extends SubCase{

    EnvSpec env

    @Override
    void setup() {
        spring {
            sftpBackupStorage()
            localStorage()
            virtualRouter()
            securityGroup()
            kvm()
        }
    }

    @Override
    void environment() {
        env = Env.oneVmBasicEnv()

        env.zone {
            localPrimaryStorage{
                name = "local_2"
                url = "/local_ps_2"
            }
        }
    }

    /**
     * Test Case: 云盘创建失败后，LocalStorageHostRefVO.availableCapacity会自动恢复
     *
     * Test action:
     * 1.Build 2 PS, host
     * 2.Create a 10GB cloud disk，创建失败
     * 3.Check primary storage available capacity
     */
    @Override
    void test() {
        env.create {

        }
    }

    @Override
    void clean() {
        env.delete()
    }
}
