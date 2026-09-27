package love.nairain.huawei.config

import android.content.SharedPreferences
import androidx.annotation.StringRes
import love.nairain.huawei.R

data class SettingDefinition(
    val key: String,
    @param:StringRes val title: Int,
    val defaultValue: Boolean = false,
)

data class SettingGroup(
    val id: String,
    @param:StringRes val title: Int? = null,
    val settings: List<SettingDefinition>,
)

enum class SettingsCategory(@get:StringRes val title: Int) {
    HEALTH(R.string.settings_health_title),
    SPORT(R.string.settings_sport_title),
    DEVICE(R.string.settings_device_title),
    MINE(R.string.settings_mine_title),
    BOTTOM(R.string.settings_bottom_title),
}

object SettingsCatalog {
    val general = listOf(
        SettingDefinition(SettingsKeys.ENABLED, R.string.settings_enabled),
        SettingDefinition(SettingsKeys.HIDE_LAUNCHER_ICON, R.string.settings_hide_launcher_icon),
    )

    val healthGroups = listOf(
        group(
            id = "top",
            title = R.string.settings_group_page_top,
            SettingsKeys.HEALTH_SEARCH to R.string.settings_health_search,
            SettingsKeys.HEALTH_MORE to R.string.settings_more_menu,
        ),
        group(
            id = "health-cards",
            title = R.string.settings_group_health_cards,
            SettingsKeys.HEALTH_ACTIVITY_RINGS to R.string.settings_health_activity_rings,
            SettingsKeys.HEALTH_QUICK_ENTRIES to R.string.settings_health_quick_entries,
            SettingsKeys.HEALTH_EDIT_CARDS to R.string.settings_health_edit_cards,
        ),
        group(
            id = "recommendations",
            title = R.string.settings_group_recommended_content,
            SettingsKeys.HEALTH_TODAY to R.string.settings_health_today,
            SettingsKeys.HEALTH_INSIGHTS to R.string.settings_health_insights,
            SettingsKeys.HEALTH_HEADLINES to R.string.settings_health_headlines,
            SettingsKeys.HEALTH_TIPS to R.string.settings_health_tips,
        ),
    )
    val health = healthGroups.flatMap { it.settings }

    val sportGroups = listOf(
        group(
            id = "top",
            title = R.string.settings_group_page_top,
            SettingsKeys.SPORT_CATEGORY_BAR to R.string.settings_sport_category_bar,
            SettingsKeys.SPORT_SEARCH to R.string.settings_search,
            SettingsKeys.SPORT_MORE to R.string.settings_more_menu,
            SettingsKeys.SPORT_BANNER to R.string.settings_sport_banner,
        ),
        group(
            id = "quick-entries",
            title = R.string.settings_group_quick_entries,
            SettingsKeys.SPORT_QUICK_ENTRIES to R.string.settings_sport_quick_entries,
        ),
        group(
            id = "course-content",
            title = R.string.settings_group_sport_recommend,
            SettingsKeys.SPORT_LATEST to R.string.settings_sport_latest,
            SettingsKeys.SPORT_ENJOY to R.string.settings_sport_enjoy,
            SettingsKeys.SPORT_TODAY to R.string.settings_sport_today,
            SettingsKeys.SPORT_MORE_COURSES to R.string.settings_sport_more_courses,
            SettingsKeys.SPORT_COACHES to R.string.settings_sport_coaches,
        ),
        group(
            id = "plan-content",
            title = R.string.settings_group_sport_plan,
            SettingsKeys.SPORT_PLAN_CARDS to R.string.settings_sport_plan_cards,
            SettingsKeys.SPORT_PLAN_WEIGHT to R.string.settings_sport_plan_weight,
            SettingsKeys.SPORT_PLAN_TRAINING to R.string.settings_sport_plan_training,
        ),
        group(
            id = "run-content",
            title = R.string.settings_group_sport_run,
            SettingsKeys.SPORT_RUN_SUMMARY to R.string.settings_sport_run_summary,
            SettingsKeys.SPORT_RUN_ROUTE to R.string.settings_sport_run_route,
            SettingsKeys.SPORT_RUN_TRAINING to R.string.settings_sport_run_training,
            SettingsKeys.SPORT_RUN_WARMUP to R.string.settings_sport_run_warmup,
            SettingsKeys.SPORT_RUN_BEGIN to R.string.settings_sport_run_begin,
            SettingsKeys.SPORT_RUN_MUSIC to R.string.settings_sport_run_music,
        ),
        group(
            id = "yoga-content",
            title = R.string.settings_group_sport_yoga,
            SettingsKeys.SPORT_YOGA_SUMMARY to R.string.settings_sport_yoga_summary,
            SettingsKeys.SPORT_YOGA_COURSES to R.string.settings_sport_yoga_courses,
        ),
        group(
            id = "fitness-content",
            title = R.string.settings_group_sport_fitness,
            SettingsKeys.SPORT_FITNESS_SUMMARY to R.string.settings_sport_fitness_summary,
            SettingsKeys.SPORT_MY_COURSES to R.string.settings_sport_my_courses,
            SettingsKeys.SPORT_WEEKLY_PLAN to R.string.settings_sport_weekly_plan,
        ),
    )
    val sportQuickEntryGroups = listOf(
        group(
            id = "quick-entry-items",
            title = null,
            SettingsKeys.SPORT_STRETCH to R.string.settings_sport_stretch,
            SettingsKeys.SPORT_TRADITIONAL to R.string.settings_sport_traditional,
            SettingsKeys.SPORT_CYCLING to R.string.settings_sport_cycling,
            SettingsKeys.SPORT_GOLF to R.string.settings_sport_golf,
            SettingsKeys.SPORT_DANCE to R.string.settings_sport_dance,
            SettingsKeys.SPORT_PILATES to R.string.settings_sport_pilates,
            SettingsKeys.SPORT_MASTER_YOGA to R.string.settings_sport_master_yoga,
            SettingsKeys.SPORT_MUSCLE to R.string.settings_sport_muscle,
            SettingsKeys.SPORT_RUN_POWER to R.string.settings_sport_run_power,
            SettingsKeys.SPORT_BLUE_STAR to R.string.settings_sport_blue_star,
            SettingsKeys.SPORT_WALK_RUN to R.string.settings_sport_walk_run,
            SettingsKeys.SPORT_MUAY_THAI to R.string.settings_sport_muay_thai,
        ),
    )
    val sportQuickEntries = sportQuickEntryGroups.flatMap { it.settings }
    val sport = sportGroups.flatMap { it.settings } + sportQuickEntries

    val deviceGroups = listOf(
        group(
            id = "top",
            title = R.string.settings_group_page_top,
            SettingsKeys.DEVICE_SEARCH to R.string.settings_search,
            SettingsKeys.DEVICE_MENU to R.string.settings_more_menu,
            SettingsKeys.DEVICE_SWITCHER to R.string.settings_device_switcher,
            SettingsKeys.DEVICE_TAB_DEVICE to R.string.settings_device_tab_device,
            SettingsKeys.DEVICE_TAB_STORE to R.string.settings_device_tab_store,
        ),
        group(
            id = "notices",
            title = R.string.settings_group_device_notices,
            SettingsKeys.DEVICE_AUTO_UPGRADE to R.string.settings_device_auto_upgrade,
            SettingsKeys.DEVICE_NEARBY_PERMISSION to R.string.settings_device_nearby,
            SettingsKeys.DEVICE_AUTO_SWITCH to R.string.settings_device_auto_switch,
            SettingsKeys.DEVICE_SHARED to R.string.settings_device_shared,
        ),
        group(
            id = "my-devices",
            title = R.string.settings_group_my_devices,
            SettingsKeys.DEVICE_ADD to R.string.settings_device_add,
            SettingsKeys.DEVICE_LIST to R.string.settings_device_list,
            SettingsKeys.DEVICE_PRIMARY to R.string.settings_device_primary,
            SettingsKeys.DEVICE_TIPS to R.string.settings_device_tips,
            SettingsKeys.DEVICE_GENERAL_SETTINGS to R.string.settings_device_general_settings,
            SettingsKeys.DEVICE_DISCONNECT_PROTECTION to R.string.settings_device_disconnect_protection,
            SettingsKeys.DEVICE_MY_WATCH to R.string.settings_device_my_watch,
            SettingsKeys.DEVICE_FUNCTIONS to R.string.settings_device_functions,
            SettingsKeys.DEVICE_FEATURES to R.string.settings_device_features,
            SettingsKeys.DEVICE_WATCH_FACES to R.string.settings_device_watch_faces,
        ),
        group(
            id = "store",
            title = R.string.settings_group_recommendations_and_store,
            SettingsKeys.DEVICE_RECOMMENDED to R.string.settings_device_recommended,
            SettingsKeys.DEVICE_MARKETING to R.string.settings_device_marketing,
            SettingsKeys.DEVICE_STORE to R.string.settings_device_store,
        ),
    )
    val device = deviceGroups.flatMap { it.settings }

    val mineGroups = listOf(
        group(
            id = "account",
            title = null,
            SettingsKeys.MINE_MESSAGES to R.string.settings_mine_messages,
            SettingsKeys.MINE_ACCOUNT to R.string.settings_mine_account,
            SettingsKeys.MINE_VIP to R.string.settings_mine_vip,
        ),
        group(
            id = "cards-primary",
            title = R.string.settings_group_cards,
            SettingsKeys.MINE_GROUP to R.string.settings_mine_group,
            SettingsKeys.MINE_FAMILY to R.string.settings_mine_family,
            SettingsKeys.MINE_ANNUAL_GOAL to R.string.settings_mine_annual_goal,
            SettingsKeys.MINE_REPORTS to R.string.settings_mine_reports,
            SettingsKeys.MINE_MEDALS to R.string.settings_mine_medals,
            SettingsKeys.MINE_MARKETING to R.string.settings_mine_marketing,
        ),
        group(
            id = "cards-secondary",
            title = R.string.settings_group_data,
            SettingsKeys.MINE_ACHIEVEMENTS to R.string.settings_mine_achievements,
            SettingsKeys.MINE_DATA to R.string.settings_mine_data,
            SettingsKeys.MINE_COURSES to R.string.settings_mine_courses,
            SettingsKeys.MINE_ACTIVITIES to R.string.settings_mine_activities,
            SettingsKeys.MINE_ROUTES to R.string.settings_mine_routes,
            SettingsKeys.MINE_PROFILE to R.string.settings_mine_profile,
        ),
        group(
            id = "other",
            title = R.string.settings_group_other,
            SettingsKeys.MINE_ORDERS to R.string.settings_orders,
            SettingsKeys.MINE_ASSETS to R.string.settings_assets,
            SettingsKeys.MINE_IHEALTH to R.string.settings_mine_ihealth,
        ),
        group(
            id = "settings-support",
            title = R.string.settings_group_settings_support,
            SettingsKeys.MINE_SETTINGS to R.string.settings_settings,
            SettingsKeys.MINE_PRIVACY to R.string.settings_privacy,
            SettingsKeys.MINE_HELP to R.string.settings_help,
            SettingsKeys.MINE_FEEDBACK to R.string.settings_mine_feedback,
            SettingsKeys.MINE_UPDATE to R.string.settings_update,
            SettingsKeys.MINE_ABOUT to R.string.settings_mine_about,
        ),
    )
    val mine = mineGroups.flatMap { it.settings }

    val bottomGroups = listOf(
        group(
            id = "tabs",
            title = null,
            SettingsKeys.BOTTOM_HEALTH to R.string.settings_tab_health,
            SettingsKeys.BOTTOM_SPORT to R.string.settings_tab_sport,
            SettingsKeys.BOTTOM_MEMBER to R.string.settings_tab_member,
            SettingsKeys.BOTTOM_DEVICE to R.string.settings_tab_device,
            SettingsKeys.BOTTOM_MINE to R.string.settings_tab_mine,
        ),
    )
    val bottom = bottomGroups.flatMap { it.settings }

    val groups = linkedMapOf(
        SettingsCategory.HEALTH to healthGroups,
        SettingsCategory.SPORT to sportGroups,
        SettingsCategory.DEVICE to deviceGroups,
        SettingsCategory.MINE to mineGroups,
        SettingsCategory.BOTTOM to bottomGroups,
    )
    val categories = groups.mapValues { (category, categoryGroups) ->
        if (category == SettingsCategory.SPORT) sport else categoryGroups.flatMap { it.settings }
    }
    val all = general + categories.values.flatten()
    val defaults = all.associate { it.key to it.defaultValue }

    fun settingsFor(category: SettingsCategory): List<SettingDefinition> = categories.getValue(category)

    fun groupsFor(category: SettingsCategory): List<SettingGroup> = groups.getValue(category)

    fun read(preferences: SharedPreferences): Map<String, Boolean> = normalizeBottomTabs(
        all.associate { setting ->
            setting.key to runCatching {
                preferences.getBoolean(setting.key, setting.defaultValue)
            }.getOrDefault(setting.defaultValue)
        },
    )

    /** 只描述需要补齐的当前 schema 默认项，不在目录层直接写入配置。 */
    fun missingDefaults(current: Map<String, *>): Map<String, Any> = buildMap {
        if (current[SettingsKeys.SCHEMA_VERSION] != SettingsKeys.CURRENT_SCHEMA_VERSION) {
            put(SettingsKeys.SCHEMA_VERSION, SettingsKeys.CURRENT_SCHEMA_VERSION)
        }
        all.filterNot { current.containsKey(it.key) }
            .forEach { put(it.key, it.defaultValue) }
    }

    fun normalizeBottomTabs(values: Map<String, Boolean>): Map<String, Boolean> {
        val normalized = values.toMutableMap()
        if (bottom.all { normalized[it.key] == true }) {
            normalized[SettingsKeys.BOTTOM_HEALTH] = false
        }
        return normalized.toMap()
    }

    fun normalize(values: Map<String, Boolean>): Map<String, Boolean> = normalizeBottomTabs(
        defaults.mapValues { (key, defaultValue) -> values[key] ?: defaultValue },
    )

    fun normalizeWrite(key: String, value: Boolean, current: Map<String, Boolean>): Map<String, Boolean> {
        val confirmed = normalize(current)
        if (!defaults.containsKey(key)) return normalizeBottomTabs(confirmed)
        return normalizeBottomTabs(confirmed + (key to value))
    }

    fun hasHidden(category: SettingsCategory, values: Map<String, Boolean>): Boolean =
        values[SettingsKeys.ENABLED] == true && settingsFor(category).any { values[it.key] == true }

    private fun definitions(vararg values: Pair<String, Int>) =
        values.map { (key, title) -> SettingDefinition(key, title) }

    private fun group(
        id: String,
        @StringRes title: Int? = null,
        vararg values: Pair<String, Int>,
    ) = SettingGroup(id, title, definitions(*values))
}
