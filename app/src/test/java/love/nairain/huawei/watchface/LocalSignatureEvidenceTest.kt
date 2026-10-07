package love.nairain.huawei.watchface

import org.junit.Assert.*
import org.junit.Test

class LocalSignatureEvidenceTest {
    @Test fun signatureMetadataComparedWithoutReturningPrivateFields() {
        val hash = LocalSignatureEvidence.digest(byteArrayOf(1, 2))
        val raw = """{"result":{"content":{"hitopId":"123456789","version":"3.7.2","status":0,"watchContentHash":"$hash","huid":"private-account","deviceId":"private-device"},"contentSign":"private-signature"}}"""
        val result = LocalSignatureEvidence.inspect(raw, "123456789", "3.7.2", hash)
        assertEquals("match", result.identity)
        assertEquals("match", result.hash)
        assertEquals(0, result.status)
        assertFalse(result.toString().contains("private"))
        assertFalse(result.toString().contains(hash))
        assertEquals("mismatch", LocalSignatureEvidence.inspect(raw, "987654321", "3.7.2", hash).identity)
        assertEquals("mismatch", LocalSignatureEvidence.inspect(raw, "123456789", "3.7.2", "0".repeat(64)).hash)
    }

    @Test fun absentMalformedAndPlaceholderSignatureMetadataRemainDistinct() {
        assertEquals("missing", LocalSignatureEvidence.inspect("invalid", "id", "version", null).identity)
        assertEquals("missing", LocalSignatureEvidence.inspect("{}", "id", "version", null).hash)
        assertEquals("zero", LocalSignatureEvidence.inspect("""{"result":{"content":{"watchContentHash":"0000"}}}""", "id", "version", null).hash)
        assertEquals("unrecognized", LocalSignatureEvidence.inspect("""{"result":{"content":{"watchContentHash":"unexpected"}}}""", "id", "version", null).hash)
    }
}
