package org.zstack.test.integration.core.gc

import org.zstack.core.Platform
import org.zstack.core.cloudbus.EventFacade
import org.zstack.core.db.DatabaseFacade
import org.zstack.core.db.SQL
import org.zstack.core.errorcode.ErrorFacade
import org.zstack.core.gc.*
import org.zstack.testlib.SubCase

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Created by xing5 on 2017/3/1.
 */
class EventBasedGarbageCollectorCase extends SubCase {
    static final String EVENT_PATH = "/test/gc"
    static final String EVENT_PATH2 = "/test/gc2"
    static final String EVENT_PATH3 = "/test/gc3"


    DatabaseFacade dbf
    EventFacade evtf
    ErrorFacade errf
    GarbageCollectorManagerImpl gcMgr

    String adminSessionUuid

    static class Context {
        String text
    }

    static enum EventBasedGCInDbBehavior {
        SUCCESS,
        FAIL,
        CANCEL
    }

    class EventBasedGC1 extends EventBasedGarbageCollector {
        Closure trigger = { true }
        Closure testLogic

        @Override
        protected void setup() {
            onEvent(EVENT_PATH, { token, data ->
                return trigger()
            })
        }


        @Override
        protected void triggerNow(GCCompletion completion) {
            System.out.println("lining123EventBasedGC1")
            if(testLogic == null ){
                System.out.println("null")
            }else{
                System.out.println("not null")
            }
            testLogic(completion)
        }
    }

    static class EventBasedGCInDb extends EventBasedGarbageCollector {
        Closure trigger = { true }

        Closure testLogicForJobLoadedFromDb

        @GC
        String name
        @GC
        String description
        @GC
        Context context

        @Override
        protected void setup() {
            onEvent(EVENT_PATH) { tokens, data ->
                return trigger()
            }
        }

        void saveToDatabase() {
            saveToDb()
        }

        @Override
        protected void triggerNow(GCCompletion completion) {

            System.out.println("lining123")
            if(testLogicForJobLoadedFromDb == null ){
                System.out.println("null")
            }else{
                System.out.println("not null")
            }
            EventBasedGCInDbBehavior ret = testLogicForJobLoadedFromDb(this)

            if (ret == EventBasedGCInDbBehavior.SUCCESS) {
                completion.success()
            } else if (ret == EventBasedGCInDbBehavior.FAIL) {
                completion.fail(errf.stringToOperationError("on purpose"))
            } else if (ret == EventBasedGCInDbBehavior.CANCEL) {
                completion.cancel()
            } else {
                assert false: "unknown behavior $ret"
            }
        }
    }

    static Closure<EventBasedGCInDbBehavior> testTriggerNowForJobLoadedFromDb

    @Override
    void setup() {
        INCLUDE_CORE_SERVICES = false
    }

    @Override
    void environment() {
        adminSessionUuid = loginAsAdmin().uuid
    }

    void testEventBasedGCSuccess() {
        int count = 0

        CountDownLatch latch = new CountDownLatch(1)

        def gc = new EventBasedGC1()
        gc.NAME = "testEventBasedGCSuccess"

        System.out.println("lining123EventBasedGCaaa")
        gc.testLogic = { GCCompletion completion ->
            count ++
            completion.success()
            latch.countDown()
        }
        System.out.println(gc.testLogic.toString())
        System.out.println("lining123EventBasedGCbbb")
        gc.submit()

        GarbageCollectorVO vo = dbf.findByUuid(gc.uuid, GarbageCollectorVO.class)
        assert vo != null
        assert vo.runnerClass == gc.class.name
        assert vo.context != null
        assert vo.status == GCStatus.Idle
        assert vo.managementNodeUuid == Platform.getManagementServerId()

        // trigger the GC
        evtf.fire(EVENT_PATH, "trigger it")
        latch.await(10, TimeUnit.SECONDS)

        assert count == 1
        assert dbFindByUuid(gc.uuid, GarbageCollectorVO.class).status == GCStatus.Done

        // trigger again, confirm the event is no longer hooked
        evtf.fire(EVENT_PATH, "trigger it")

        assert retryInSecs {
            return {
                assert count == 1
            }
        }
    }

    void testLoadedOrphanJobCancel() {
        // create GC job just in the database
        def gc = new EventBasedGCInDb()
        gc.name = "test"
        gc.NAME = "testLoadedOrphanJobSuccess"
        gc.description = "description"
        gc.context = new Context()
        gc.context.text = "something"
        gc.saveToDatabase()

        // make the Job as an orphan
        GarbageCollectorVO vo = dbf.findByUuid(gc.uuid, GarbageCollectorVO.class)
        vo.setManagementNodeUuid(null)
        dbf.update(vo)

        System.out.println("lining123aaaa")
        gc.testLogicForJobLoadedFromDb = { return EventBasedGCInDbBehavior.CANCEL }
        System.out.println(gc.testLogicForJobLoadedFromDb.toString())
        System.out.println("lining123bbbb")


        // load orphan jobs
        gcMgr.managementNodeReady()
        evtf.fire(EVENT_PATH, "trigger it")

        assert retryInSecs {
            return {
                assert dbFindByUuid(gc.uuid, GarbageCollectorVO.class).status == GCStatus.Done
            }
        }
    }

    @Override
    void test() {
        dbf = bean(DatabaseFacade.class)
        evtf = bean(EventFacade.class)
        errf = bean(ErrorFacade.class)
        gcMgr = bean(GarbageCollectorManagerImpl.class)

        testEventBasedGCSuccess()
        testLoadedOrphanJobCancel()
    }

    @Override
    void clean() {
        SQL.New(GarbageCollectorVO.class).delete()
    }
}
