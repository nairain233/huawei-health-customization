package love.nairain.huawei.scan

import love.nairain.huawei.config.SettingsKeys as K
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class ScanCacheTest {
    @Test fun oldCacheMissingNewFixedResourcesMustRescanWithoutRuleBump() {
        assertThrows(IllegalArgumentException::class.java) {
            ScanCache.verifyResources(resolution.copy(groups = setOf("sport.fixed"))) { name, _ -> if (name == "banner_root") 123 else 0 }
        }
        ScanCache.verifyResources(resolution) { name, _ -> if (name == "banner_root") 123 else 0 }
        assertEquals(8, ScanProtocol.RULES)
    }
    private val resolution = LayoutResolution(emptySet(), emptySet(), emptyMap(), setOf("Lsample/Target;->run()V"), "manager",
        ScanProtocol.keys.associateWith { "unresolved" }, issues = listOf("mine.badges.grid", "mine.badges.kaka")
            .associate { "$it:${love.nairain.huawei.config.SettingsKeys.MINE_BADGES}" to "entry_missing" } +
            listOf("sport.identity", "sport.navigation", "sport.discovery", "sport.quick.discovery", "sport.fixed").associateWith { "entry_missing" } +
            mapOf(
                "$EDIT_CARDS_SOURCE:${K.HEALTH_EDIT_CARDS}" to "entry_missing",
                "$MY_WATCH_SOURCE:${K.DEVICE_MY_WATCH}" to "entry_missing",
            ))

    @Test fun oldCacheMustHaveCheckedBothAdditionalMineBadgePaths() {
        listOf("mine.badges.grid", "mine.badges.kaka").forEach { source ->
            val missing = "$source:${K.MINE_BADGES}"
            val old = resolution.copy(issues = resolution.issues - missing)
            var verified = false
            assertTrue(runCatching {
                ScanCache.decode(ScanCache.encode("apk1", "", old), "apk1", "") { verified = true }
            }.isFailure)
            assertFalse(verified)
        }
        assertEquals(8, ScanProtocol.RULES)
        assertEquals(98, ScanProtocol.keys.size)
    }

    @Test fun cacheWithPreviousEditAndWatchRulesRequiresRescan() {
        val previous = mapOf(
            "$EDIT_CARDS_SOURCE:${K.HEALTH_EDIT_CARDS}" to "health.edit-cards:${K.HEALTH_EDIT_CARDS}",
            "$MY_WATCH_SOURCE:${K.DEVICE_MY_WATCH}" to "device.new.arkui:${K.DEVICE_MY_WATCH}",
        )
        previous.forEach { (current, old) ->
            val stale = resolution.copy(issues = resolution.issues - current + (old to "entry_missing"))
            assertThrows(IllegalArgumentException::class.java) {
                ScanCache.decode(ScanCache.encode("apk1", "", stale), "apk1", "") {
                    fail("旧缓存应在复核描述符前失效")
                }
            }
        }
    }

    @Test fun oldCacheMustHaveCheckedSportPathsBeforeMemberVerification() {
        listOf("sport.identity", "sport.navigation", "sport.discovery", "sport.quick.discovery", "sport.fixed").forEach { missing ->
            var verified = false
            val old = resolution.copy(issues = resolution.issues - missing)
            assertThrows(IllegalArgumentException::class.java) {
                ScanCache.decode(ScanCache.encode("apk", "", old), "apk", "") { verified = true }
            }
            assertFalse(verified)
        }
    }
    @Test fun old98ChoiceCacheIsRejectedWithoutChangingRuleVersion() {
        val old = resolution.copy(failures = resolution.failures - love.nairain.huawei.config.SettingsKeys.HEALTH_PROMOTION +
            ("hide.health.more_menu" to "unresolved"))
        assertEquals(98, old.failures.size)
        var verified = false
        assertThrows(IllegalArgumentException::class.java) {
            ScanCache.decode(ScanCache.encode("same-apk", "", old), "same-apk", "") { verified = true }
        }
        assertFalse(verified)
        assertEquals(8, ScanProtocol.RULES)
    }
    @Test fun cacheWithoutPromotionChoiceRequiresRescanBeforeReflection() {
        val old = resolution.copy(failures = resolution.failures - love.nairain.huawei.config.SettingsKeys.HEALTH_PROMOTION)
        var verified = false
        assertThrows(IllegalArgumentException::class.java) {
            ScanCache.decode(ScanCache.encode("same-apk", "", old), "same-apk", "") { verified = true }
        }
        assertFalse(verified)
        assertEquals(resolution, ScanCache.decode(ScanCache.encode("same-apk", "", resolution), "same-apk", "") {})
    }
    @Test fun cachedNegativeResultsAvoidRescanningUntilIdentityOrRequestChanges() {
        val raw = ScanCache.encode("apk1", "request1", resolution)
        val checked = mutableListOf<String>()
        assertEquals(resolution, ScanCache.decode(raw, "apk1", "request1", checked::add))
        assertEquals(resolution.descriptors.toList(), checked)
        assertTrue(runCatching { ScanCache.decode(raw, "apk2", "request1") {} }.isFailure)
        assertTrue(runCatching { ScanCache.decode(raw, "apk1", "request2") {} }.isFailure)
    }

    @Test fun corruptOrUnresolvableCacheIsRejected() {
        val raw = ScanCache.encode("apk1", "request1", resolution)
        val damaged = JSONObject(raw).put("resolution", resolution.encode().replace("manager", "broken")).toString()
        assertTrue(runCatching { ScanCache.decode(damaged, "apk1", "request1") {} }.isFailure)
        assertTrue(runCatching { ScanCache.decode("{", "apk1", "request1") {} }.isFailure)
        assertTrue(runCatching { ScanCache.decode(raw, "apk1", "request1") { throw NoSuchMethodException() } }.isFailure)
    }

    @Test fun currentMembershipContainerMissingFromOldCacheRequiresRescan() {
        val old = resolution.copy(resourceIds = mapOf("id/vip_layout" to 10))
        val current = mapOf("id/vip_layout" to 10, "id/member_benefit_container" to 20)
        val lookup: (String, String) -> Int = { name, kind -> current["$kind/$name"] ?: 0 }
        assertTrue(runCatching { ScanCache.verifyResources(old, lookup) }.isFailure)
        ScanCache.verifyResources(old.copy(resourceIds = current), lookup)
    }

    @Test fun hostsWithoutMembershipContainerKeepLegacyCacheAndChangedResourcesAreRejected() {
        val legacy = resolution.copy(resourceIds = mapOf("id/vip_layout" to 10))
        ScanCache.verifyResources(legacy) { name, kind -> if ("$kind/$name" == "id/vip_layout") 10 else 0 }
        assertTrue(runCatching {
            ScanCache.verifyResources(legacy) { name, kind -> if ("$kind/$name" == "id/vip_layout") 11 else 0 }
        }.isFailure)
    }

    @Test fun rule8CacheMissingNewBadgeChoicesIsRejectedBeforeSymbolVerification() {
        val old = resolution.copy(failures = resolution.failures - setOf(
            love.nairain.huawei.config.SettingsKeys.MINE_BADGES,
            love.nairain.huawei.config.SettingsKeys.BOTTOM_BADGES,
        ))
        var verified = false
        assertTrue(runCatching {
            ScanCache.decode(ScanCache.encode("apk1", "request1", old), "apk1", "request1") { verified = true }
        }.isFailure)
        assertFalse(verified)
        assertEquals(8, ScanProtocol.RULES)
    }

    @Test fun rule7CacheCannotBeUsedAfterStructuralRefactor() {
        val apk = kotlin.io.path.createTempFile().toFile()
        try {
            apk.writeText("same APK content")
            val previous = ApkIdentity.fingerprint("17.0.8.300", 1700008300L, 10, listOf(apk), rules = 7)
            val current = ApkIdentity.fingerprint("17.0.8.300", 1700008300L, 10, listOf(apk))
            assertNotEquals(previous, current)
            val raw = ScanCache.encode(previous, "", resolution)
            assertTrue(runCatching { ScanCache.decode(raw, current, "") {} }.isFailure)
        } finally {
            apk.delete()
        }
    }
}
