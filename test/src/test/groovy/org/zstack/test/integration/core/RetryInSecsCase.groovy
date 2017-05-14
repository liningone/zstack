package org.zstack.test.integration.core

import org.zstack.testlib.SubCase

/**
 * Created by lining on 2017/5/14.
 */
class RetryInSecsCase extends SubCase {

    @Override
    void clean() {
    }

    @Override
    void setup() {
    }

    @Override
    void environment() {
    }

    @Override
    void test() {
        baseTest()
        retryTimeTest()
    }

    void baseTest(){
        // do nothing, expect true
        assert retryInSecs2(2){
        }

        // return a not boolean object, expect false
        assert !retryInSecs2(2){
            return new Object()
        }

        assert retryInSecs2(2){
            return new Boolean(true)
        }

        assert retryInSecs2(){
            return true
        }

        assert !retryInSecs2(2){
            return false
        }

        retryInSecs2(3){
            assert 1 == 1
            assert 2 == 2
            assert 1000 * 1 == 100 * 10
            assert true == true
            assert null != new Object()
            assert "love you, zstack, best wish for you!" == "love you, zstack, best wish for you!"
        }
    }

    void retryTimeTest(){

        int retryTimes = 3
        long startTime = System.currentTimeMillis()
        assert retryInSecs2(retryTimes){
            return true
        }
        long endTime = System.currentTimeMillis()
        assert endTime - startTime < 1000


        retryTimes = 3
        startTime = System.currentTimeMillis()
        try {
            retryInSecs2(retryTimes){
                assert new Object() == new Object().class
                assert 1 == 2
            }
            assert false
        }catch (Throwable e){
            endTime = System.currentTimeMillis()
            assert endTime - startTime > 1000 * (retryTimes -1)
            assert endTime - startTime < 1000 * (retryTimes)
        }

        retryTimes = 2
        startTime = System.currentTimeMillis()
        assert !retryInSecs2(retryTimes){
            return false
        }
        endTime = System.currentTimeMillis()
        assert endTime - startTime > 1000 * (retryTimes -1)
        assert endTime - startTime > 1000 * (retryTimes)
    }
}
