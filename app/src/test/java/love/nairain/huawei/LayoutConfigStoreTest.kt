package love.nairain.huawei

import android.content.SharedPreferences
import love.nairain.huawei.config.LayoutConfigStore
import love.nairain.huawei.config.LayoutWriteOutcome
import love.nairain.huawei.config.SettingsCatalog
import love.nairain.huawei.config.SettingsKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutConfigStoreTest {
    private val store = LayoutConfigStore()

    @Test
    fun loadCommitsSchemaAndMissingDefaults() {
        val preferences = ScriptedPreferences(mapOf(SettingsKeys.ENABLED to true))

        val result = store.load(preferences)

        assertEquals(LayoutWriteOutcome.SUCCESS, result.defaultWriteOutcome)
        assertTrue(result.values.getValue(SettingsKeys.ENABLED))
        assertEquals(SettingsKeys.CURRENT_SCHEMA_VERSION, preferences.all[SettingsKeys.SCHEMA_VERSION])
        SettingsCatalog.defaults.keys.forEach { key ->
            assertEquals(result.values.getValue(key), preferences.all[key])
        }
    }

    @Test
    fun falseDefaultCommitRestoresOriginalValuesAndAbsence() {
        val initial = mapOf(SettingsKeys.ENABLED to true, "unknown" to "kept")
        val preferences = ScriptedPreferences(
            initial,
            CommitAction.RETURN_FALSE,
            CommitAction.RETURN_TRUE,
        )

        val result = store.load(preferences)

        assertEquals(LayoutWriteOutcome.FAILED_RESTORED, result.defaultWriteOutcome)
        assertEquals(initial, preferences.all)
        assertFalse(preferences.contains(SettingsKeys.SCHEMA_VERSION))
        assertFalse(preferences.contains(SettingsKeys.HEALTH_SEARCH))
    }

    @Test
    fun exceptionalDefaultCommitAlsoRollsBackPrecisely() {
        val initial = mapOf(
            SettingsKeys.SCHEMA_VERSION to 1,
            SettingsKeys.HIDE_LAUNCHER_ICON to true,
        )
        val preferences = ScriptedPreferences(
            initial,
            CommitAction.THROW,
            CommitAction.RETURN_TRUE,
        )

        val result = store.load(preferences)

        assertEquals(LayoutWriteOutcome.FAILED_RESTORED, result.defaultWriteOutcome)
        assertEquals(initial, preferences.all)
    }

    @Test
    fun failedRollbackReportsUncertainState() {
        val preferences = ScriptedPreferences(
            emptyMap(),
            CommitAction.RETURN_FALSE,
            CommitAction.RETURN_FALSE,
        )

        val result = store.load(preferences)

        assertEquals(LayoutWriteOutcome.FAILED_UNCERTAIN, result.defaultWriteOutcome)
    }

    @Test
    fun saveCommitsNormalizedDifferenceAndMissingDefaults() {
        val preferences = ScriptedPreferences(
            mapOf(
                SettingsKeys.SCHEMA_VERSION to SettingsKeys.CURRENT_SCHEMA_VERSION,
                SettingsKeys.ENABLED to true,
            ),
        )

        val result = store.save(
            preferences,
            SettingsCatalog.defaults + (SettingsKeys.ENABLED to true),
            SettingsKeys.HEALTH_SEARCH,
            true,
        )

        assertEquals(LayoutWriteOutcome.SUCCESS, result.outcome)
        assertTrue(result.changed)
        assertTrue(result.values.getValue(SettingsKeys.HEALTH_SEARCH))
        SettingsCatalog.defaults.keys.forEach { key -> assertTrue(preferences.contains(key)) }
    }

    @Test
    fun failedSaveRestoresExistingAndAbsentKeys() {
        val initial = completePreferences() + (SettingsKeys.HEALTH_SEARCH to false)
        val preferences = ScriptedPreferences(
            initial,
            CommitAction.RETURN_FALSE,
            CommitAction.RETURN_TRUE,
        )

        val result = store.save(
            preferences,
            SettingsCatalog.defaults,
            SettingsKeys.HEALTH_SEARCH,
            true,
        )

        assertEquals(LayoutWriteOutcome.FAILED_RESTORED, result.outcome)
        assertEquals(initial, preferences.all)
        assertFalse(result.values.getValue(SettingsKeys.HEALTH_SEARCH))
    }

    @Test
    fun unknownAndUnchangedWritesAreIgnored() {
        val initial = completePreferences()
        val preferences = ScriptedPreferences(initial, CommitAction.THROW)

        val unknown = store.save(preferences, SettingsCatalog.defaults, "unknown", true)
        val unchanged = store.save(
            preferences,
            SettingsCatalog.defaults,
            SettingsKeys.HEALTH_SEARCH,
            false,
        )

        assertFalse(unknown.changed)
        assertFalse(unchanged.changed)
        assertEquals(initial, preferences.all)
    }

    @Test
    fun hidingEveryBottomTabStillKeepsHealthVisible() {
        val current = SettingsCatalog.defaults + SettingsCatalog.bottom.associate { it.key to true } +
            (SettingsKeys.BOTTOM_HEALTH to false)
        val preferences = ScriptedPreferences(completePreferences(current))

        val result = store.save(
            preferences,
            current,
            SettingsKeys.BOTTOM_HEALTH,
            true,
        )

        assertEquals(LayoutWriteOutcome.SUCCESS, result.outcome)
        assertFalse(result.changed)
        assertFalse(result.values.getValue(SettingsKeys.BOTTOM_HEALTH))
    }
}

internal enum class CommitAction {
    RETURN_TRUE,
    RETURN_FALSE,
    THROW,
}

internal fun completePreferences(
    values: Map<String, Boolean> = SettingsCatalog.defaults,
): Map<String, Any?> = buildMap {
    put(SettingsKeys.SCHEMA_VERSION, SettingsKeys.CURRENT_SCHEMA_VERSION)
    putAll(SettingsCatalog.defaults)
    putAll(values)
}

internal class ScriptedPreferences(
    initial: Map<String, Any?> = emptyMap(),
    vararg commitActions: CommitAction,
) : SharedPreferences {
    private val values = initial.toMutableMap()
    private val actions = ArrayDeque(commitActions.toList())

    override fun getAll(): Map<String, *> = values.toMap()
    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        values[key] as? Set<String> ?: defValues
    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val updates = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearRequested = false

        override fun putString(key: String, value: String?) = update(key, value)
        override fun putStringSet(key: String, values: Set<String>?) = update(key, values?.toSet())
        override fun putInt(key: String, value: Int) = update(key, value)
        override fun putLong(key: String, value: Long) = update(key, value)
        override fun putFloat(key: String, value: Float) = update(key, value)
        override fun putBoolean(key: String, value: Boolean) = update(key, value)
        override fun remove(key: String): SharedPreferences.Editor = apply {
            updates.remove(key)
            removals += key
        }
        override fun clear(): SharedPreferences.Editor = apply { clearRequested = true }

        override fun commit(): Boolean {
            applyChanges()
            return when (actions.removeFirstOrNull() ?: CommitAction.RETURN_TRUE) {
                CommitAction.RETURN_TRUE -> true
                CommitAction.RETURN_FALSE -> false
                CommitAction.THROW -> throw IllegalStateException("scripted commit failure")
            }
        }

        override fun apply() {
            applyChanges()
        }

        private fun applyChanges() {
            if (clearRequested) values.clear()
            removals.forEach(values::remove)
            values.putAll(updates)
        }

        private fun update(key: String, value: Any?): SharedPreferences.Editor = apply {
            removals -= key
            updates[key] = value
        }
    }
}
