package org.zstack.test.integration.kvm.vm

import org.zstack.sdk.GetVmConsolePasswordAction
import org.zstack.sdk.SetVmConsolePasswordAction
import org.zstack.sdk.VmInstanceInventory
import org.zstack.test.integration.kvm.Env
import org.zstack.test.integration.kvm.KvmTestSuite
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.testlib.VmSpec

/**
 * Created by Camile on 2017/3/13.
 *
 * base on BAT test : TestVmConsolePasswordOnKvm
 */
class VmConsoleCase extends SubCase {
    EnvSpec env

    def DOC = """

"""

    @Override
    void setup() {
        useSpring(KvmTestSuite.springSpec)
    }

    @Override
    void environment() {
        env = Env.oneVmBasicEnv()
    }

    @Override
    void test() {
        env.create {
            testSetConsolePasswordSuccess()
            testSetConsolePasswordFailure()
        }
    }

    void testSetConsolePasswordSuccess() {
        VmSpec spec = env.specByName("vm")
        def action = new SetVmConsolePasswordAction()
        action.uuid = spec.inventory.uuid
        action.consolePassword = "password2"
        action.sessionId = adminSession()
        SetVmConsolePasswordAction.Result res = action.call()
        assert res.error != null

    }

    void testSetConsolePasswordFailure() {
        VmSpec spec = env.specByName("vm")
        VmInstanceInventory inv = setVmConsolePassword {
            uuid = spec.inventory.uuid
            consolePassword = "123456789"
            sessionId = adminSession()
        }
        def action  = new GetVmConsolePasswordAction()
        action.uuid = spec.inventory.uuid
        action.sessionId = adminSession()
        GetVmConsolePasswordAction.Result res = action.call()
        assert res.value.getPassword() == "123456789"

    }

    @Override
    void clean() {
        env.delete()
    }
}
