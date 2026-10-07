package love.nairain.huawei.watchface

import org.junit.Assert.*
import org.junit.Test

class NativeLocalInstallSessionTest {
    private fun session(id: String = "123456789", version: String = "3.7.2") =
        NativeLocalInstallSession(LocalInstallState(id, version), "/private/job/unpacked/com.huawei.watchface")

    private fun signing(session: NativeLocalInstallSession) {
        assertTrue(session.cache(byteArrayOf(1)))
        session.payloadReturned()
        assertTrue(session.beginSignature())
    }

    private fun applying(session: NativeLocalInstallSession) {
        signing(session)
        session.signatureReturned()
        assertTrue(session.beginContinuation())
        assertTrue(session.apply(true))
    }

    private fun transferring(session: NativeLocalInstallSession) {
        applying(session)
        assertTrue(session.scheduleTransfer())
        session.continuationReturned()
        assertTrue(session.beginTransfer())
        session.transferReturned()
    }

    @Test fun nativeTransferStartsWithout105AndSecondApplyIsUnsigned() {
        val session = session()
        applying(session)
        assertTrue(session.permitsOperation(true))
        assertFalse(session.permitsOperation(false))
        assertTrue(session.scheduleTransfer()) // 原生紧接首次 apply，不等待 105。
        assertFalse(session.scheduleTransfer()) // 迟到 105 不启动第二次传输。
        session.continuationReturned()
        assertTrue(session.beginTransfer())
        session.transferReturned()
        assertFalse(session.apply(false))
        assertTrue(session.transferred()) // 107；仅授权原生第二次 apply。
        assertTrue(session.apply(false))
        assertFalse(session.permitsOperation(true))
        assertTrue(session.permitsOperation(false))
        assertFalse(session.apply(false))
        assertTrue(session.applied())
        assertFalse(session.state.verify(mapOf(session.state.id to "old")))
        assertTrue(session.state.verify(mapOf(session.state.id to session.state.version)))
        assertTrue(session.canRelease())
    }

    @Test fun emptyOrInvalidDecodedPayloadNeverAllowsCommands() {
        listOf(null, "invalid", byteArrayOf()).forEach {
            val session = session()
            assertFalse(session.cache(it))
            assertFalse(session.beginSignature())
            assertFalse(session.apply(true))
            assertFalse(session.scheduleTransfer())
        }
    }

    @Test fun cancelBeforePayloadReturnsRetainsFilesUntilWorkerExits() {
        val session = session()
        session.cancel()
        assertFalse(session.cache(byteArrayOf(1)))
        assertFalse(session.canRelease())
        session.payloadReturned()
        assertTrue(session.canRelease())
    }

    @Test fun cancelledQueuedSignatureIsSkippedBeforeCleanup() {
        val session = session()
        assertTrue(session.cache(byteArrayOf(1)))
        session.payloadReturned()
        session.cancel()
        assertFalse(session.canRelease())
        assertFalse(session.beginSignature())
        assertTrue(session.canRelease())
    }

    @Test fun signatureReturningAfterCancelCannotResumeInstallation() {
        val session = session()
        signing(session)
        session.cancel()
        assertFalse(session.canRelease())
        session.signatureReturned()
        assertFalse(session.canRelease()) // 主线程续接尚未出队。
        assertFalse(session.beginContinuation())
        assertFalse(session.apply(true))
        assertTrue(session.canRelease())
    }

    @Test fun confirmedStopAlsoWaitsForQueuedTransferToBeSkipped() {
        val session = session()
        applying(session)
        session.scheduleTransfer()
        session.cancel()
        session.stopResponse(20003, session.taskId)
        assertFalse(session.canRelease())
        session.continuationReturned()
        assertFalse(session.canRelease())
        assertFalse(session.beginTransfer())
        assertTrue(session.canRelease())
    }

    @Test fun failedOrUnrelatedStopResponseNeverReleasesActiveTransfer() {
        val session = session()
        transferring(session)
        session.cancel()
        session.stopResponse(20003, "other_3.7.2")
        session.stopResponse(20004, session.taskId)
        session.stopResponse(20003, null)
        assertFalse(session.canRelease())
        assertFalse(session.apply(false))
        session.stopResponse(20003, session.taskId)
        assertTrue(session.canRelease())
    }

    @Test fun transferredButCancelledTaskCannotApplyOrClaimSuccess() {
        val session = session()
        transferring(session)
        session.cancel()
        assertFalse(session.transferred())
        assertTrue(session.canRelease())
        assertFalse(session.apply(false))
        assertFalse(session.applied())
        assertFalse(session.state.verify(mapOf(session.state.id to session.state.version)))
    }

    @Test fun cancelDuringNativeTransferDispatchDefersStopAndCleanup() {
        val session = session()
        applying(session)
        session.scheduleTransfer()
        session.continuationReturned()
        assertTrue(session.beginTransfer())
        session.cancel()
        assertFalse(session.canRequestStop())
        assertFalse(session.canRelease())
        session.transferReturned()
        assertTrue(session.canRequestStop())
        assertFalse(session.canRelease())
        session.stopResponse(20003, session.taskId)
        assertTrue(session.canRelease())
    }

    @Test fun progressAndDuplicateCallbacksCannotClaimSuccess() {
        val session = session()
        transferring(session)
        assertFalse(session.applied()) // 即使 UI 进度为 100，也没有第二次应用成功。
        assertFalse(session.state.verify(mapOf(session.state.id to session.state.version)))
        assertTrue(session.transferred())
        assertFalse(session.transferred())
        assertTrue(session.apply(false))
        session.cancel() // 传输已结束，但第二次应用失败。
        assertFalse(session.applied())
        assertTrue(session.canRelease())
    }

    @Test fun subsequentImportUsesSameNativeSlotButIndependentVersion() {
        val first = session(LocalInstallIdentity.ID)
        val second = session(LocalInstallIdentity.ID, "5.2.9")
        transferring(first)
        first.transferred()
        first.cancel()
        assertTrue(first.canRelease())
        assertEquals(first.state.id, second.state.id)
        assertFalse(second.matches(first.state.id, first.state.version))
        assertFalse(second.matches(second.state.id, first.state.version))
        assertTrue(second.matches("000000001", "5.2.9"))
        assertEquals("000000001_5.2.9", second.taskId)
        applying(second)
        assertTrue(second.permitsOperation(true))
        assertFalse(first.permitsOperation(true))
    }

    @Test fun retiredLocalIdentityDoesNotBlockOrdinaryHostTransfer() {
        val session = session()
        session.cancel()
        assertEquals(NativeLocalInstallSession.TransferRoute.ORIGINAL,
            session.transferRoute(false, false, "/host/download/${session.taskId}/com.huawei.watchface"))
        assertEquals(NativeLocalInstallSession.TransferRoute.SKIP,
            session.transferRoute(false, true, "/host/download/${session.taskId}/com.huawei.watchface"))
        assertEquals(NativeLocalInstallSession.TransferRoute.SKIP,
            session.transferRoute(false, false, session.payloadPath))
    }

    @Test fun activeAndCancelledTransfersKeepTaskIsolation() {
        val session = session()
        assertEquals(NativeLocalInstallSession.TransferRoute.LOCAL, session.transferRoute(true, false, "/host/path"))
        session.cancel()
        assertEquals(NativeLocalInstallSession.TransferRoute.SKIP, session.transferRoute(true, false, "/host/path"))
        assertFalse(session.matches("other", session.state.version))
        assertFalse(session.matches(session.state.id, "other"))
    }

    @Test fun completedTaskCanReturnIdentityOwnershipToOrdinaryHostOperation() {
        val session = session()
        transferring(session)
        assertTrue(session.observesFileCallbacks)
        session.cancel()
        session.reuseByHost()
        assertFalse(session.hostReused)
        assertFalse(session.observesFileCallbacks)
        session.transferred()
        session.reuseByHost()
        assertTrue(session.hostReused)
        assertFalse(session.beginContinuation())
        assertFalse(session.permitsOperation(false))
    }

    @Test fun callbacksAfterConfirmedTransferEndDoNotBelongToFileProgress() {
        val session = session()
        assertFalse(session.observesFileCallbacks)
        transferring(session)
        assertTrue(session.observesFileCallbacks)
        session.transferred()
        assertFalse(session.observesFileCallbacks)
    }

    @Test fun stopAfter107MustFinishBeforeNextImportCanStart() {
        val session = session()
        transferring(session)
        session.transferred()
        session.apply(false)
        session.cancel()
        session.requestStop()
        val callback = Any()
        assertTrue(session.bindStopCallback(callback))
        assertFalse(session.bindStopCallback(Any())) // 后续宿主停止不能替换已发出命令的回调身份。
        assertFalse(session.canRelease())
        assertFalse(session.acceptsStopResponse(Any(), session.taskId))
        assertFalse(session.acceptsStopResponse(callback, "other"))
        assertTrue(session.acceptsStopResponse(callback, session.taskId))
        session.callbackStarted()
        session.stopResponse(20003, session.taskId)
        assertFalse(session.canRelease())
        assertFalse(session.acceptsStopResponse(callback, session.taskId)) // 迟到重复停止不能再次重置宿主。
        session.callbackReturned()
        assertTrue(session.canRelease())
    }

    @Test fun failedStopAfterConfirmed107ReleasesOnlyAfterOriginalCallbackReturns() {
        val session = session()
        transferring(session)
        session.transferred()
        session.apply(false)
        session.cancel()
        session.requestStop()
        val callback = Any()
        session.bindStopCallback(callback)
        session.callbackStarted()
        session.stopResponse(20004, session.taskId)
        assertFalse(session.canRelease())
        assertFalse(session.acceptsStopResponse(callback, session.taskId))
        session.callbackReturned()
        assertTrue(session.canRelease())
        assertFalse(session.applied())
    }

    @Test fun originalInstallCallbackMustReturnBeforeStopAndCleanup() {
        val session = session()
        transferring(session)
        session.transferred()
        session.callbackStarted()
        session.cancel()
        assertFalse(session.canRequestStop())
        assertFalse(session.canRelease())
        session.callbackReturned()
        assertTrue(session.canRequestStop())
        session.requestStop()
        assertFalse(session.canRelease())
    }

    @Test fun uncertainActiveTransferCanAcceptLaterStopConfirmationAfterFailure() {
        val session = session()
        transferring(session)
        session.cancel()
        session.requestStop()
        val callback = Any()
        session.bindStopCallback(callback)
        session.stopResponse(20004, session.taskId)
        assertFalse(session.canRelease())
        assertTrue(session.acceptsStopResponse(callback, session.taskId))
        session.callbackStarted()
        session.stopResponse(20003, session.taskId)
        assertFalse(session.canRelease())
        session.callbackReturned()
        assertTrue(session.canRelease())
        assertFalse(session.acceptsStopResponse(callback, session.taskId))
    }
}
