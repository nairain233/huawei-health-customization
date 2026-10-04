package love.nairain.huawei.watchface

import org.junit.Assert.*
import org.junit.Test

class LocalImportFailureTest {
    private fun state() = LocalInstallState("123456789", "1.0.0")
    @Test fun unidentifiedSignatureFailureClosesWaitingTask() {
        assertTrue(LocalImportFailure.missingIdentityError(state(), true, false, true, 100007, "null_null"))
        assertTrue(LocalImportFailure.missingIdentityError(state(), true, false, true, 100014, null))
        assertTrue(LocalImportFailure.signatureRejected(100007))
        assertFalse(LocalImportFailure.signatureRejected(-3))
    }
    @Test fun unidentifiedSuccessNeverAdvances() {
        assertFalse(LocalImportFailure.missingIdentityError(state(), true, false, true, 0, "null_null"))
    }
    @Test fun unrelatedOrUnownedReportsPassThrough() {
        assertFalse(LocalImportFailure.missingIdentityError(null, true, false, true, 100007, "null_null"))
        assertFalse(LocalImportFailure.missingIdentityError(state(), false, false, true, 100007, "null_null"))
        assertFalse(LocalImportFailure.missingIdentityError(state(), true, false, true, 100007, "other_1.0.0"))
        assertFalse(LocalImportFailure.missingIdentityError(state(), true, false, false, 100007, "null_null"))
    }
    @Test fun cancelledFailedAndTransferringTasksIgnoreLateError() {
        val state = state()
        assertFalse(LocalImportFailure.missingIdentityError(state, true, true, true, 100007, "null_null"))
        state.readyToTransfer()
        assertFalse(LocalImportFailure.missingIdentityError(state, true, false, true, 100007, "null_null"))
        state.transferred()
        assertTrue(LocalImportFailure.missingIdentityError(state, true, false, true, 100007, "null_null"))
        state.fail()
        assertFalse(LocalImportFailure.missingIdentityError(state, true, false, true, 100007, "null_null"))
    }
    @Test fun signatureResponseCodeIsNotInferredFromNonemptyText() {
        assertEquals(0, LocalImportFailure.signatureResult("{\"resultcode\":\"0\"}"))
        assertEquals(500, LocalImportFailure.signatureResult("{\"resultcode\":500}"))
        assertNull(LocalImportFailure.signatureResult("nonempty"))
        assertNull(LocalImportFailure.signatureResult("{\"result\":{}}"))
    }
    @Test fun deadlineReportsTheCurrentStageInsteadOfGenericFailure() {
        val stages = listOf(LocalImportFailure.Stage.READING, LocalImportFailure.Stage.DEVICE_LIST,
            LocalImportFailure.Stage.SIGNATURE, LocalImportFailure.Stage.APPLYING,
            LocalImportFailure.Stage.TRANSFERRING, LocalImportFailure.Stage.VERIFYING)
        stages.forEach { stage ->
            assertNull(LocalImportFailure.timeoutStage(stage, 60_000, 60_000, 0, true))
            assertEquals(stage, LocalImportFailure.timeoutStage(stage, 60_001, 60_000, 0, true))
        }
    }
    @Test fun confirmationDoesNotExpireButActiveTransferHasHardLimit() {
        assertNull(LocalImportFailure.timeoutStage(LocalImportFailure.Stage.CONFIRMING, 1_000_000, Long.MAX_VALUE, 0, false))
        assertEquals(LocalImportFailure.Stage.TRANSFERRING,
            LocalImportFailure.timeoutStage(LocalImportFailure.Stage.TRANSFERRING, 900_001, 950_000, 0, true))
    }
    @Test fun onlyVerifiedPageCanOpenPickerWithExactToken() {
        assertTrue(LocalFacePagePolicy.acceptsPage("https://h5hosting-drcn.dbankcdn.cn/cch5/health/watchFace/index.html#/index"))
        assertFalse(LocalFacePagePolicy.acceptsPage("https://h5hosting-drcn.dbankcdn.cn.attacker.test/cch5/health/watchFace/index.html"))
        assertFalse(LocalFacePagePolicy.acceptsPage("http://h5hosting-drcn.dbankcdn.cn/cch5/health/watchFace/index.html"))
        assertFalse(LocalFacePagePolicy.acceptsPage("https://h5hosting-drcn.dbankcdn.cn/other.html"))
        assertTrue(LocalFacePagePolicy.acceptsAction("huawei-local-watchface://choose?token=token", "token"))
        assertFalse(LocalFacePagePolicy.acceptsAction("huawei-local-watchface://choose?token=token&path=anything", "token"))
        assertFalse(LocalFacePagePolicy.acceptsAction("huawei-local-watchface://choose?token=old", "new"))
    }
}
