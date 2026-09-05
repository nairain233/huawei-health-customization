package love.nairain.huawei.hook.resolver

import love.nairain.huawei.config.SettingsKeys

class RowKeyResolver {
    fun resolve(resourceName: String?, resourceId: Int? = null): String? =
        resourceName?.let(RESOURCE_NAMES::get) ?: resourceId?.let(RESOURCE_IDS::get)

    companion object {
        private val RESOURCE_NAMES = mapOf(
            "IDS_user_profile_achieve_my_reward" to SettingsKeys.MINE_MEDALS,
            "IDS_hwh_me_achieve_report" to SettingsKeys.MINE_ACHIEVEMENTS,
            "IDS_user_profile_health_show_my_data" to SettingsKeys.MINE_DATA,
            "sug_home_my_own_plans" to SettingsKeys.MINE_COURSES,
            "IDS_title_course_plan" to SettingsKeys.MINE_COURSES,
            "IDS_activity_social_my_activities" to SettingsKeys.MINE_ACTIVITIES,
            "IDS_hwh_my_route" to SettingsKeys.MINE_ROUTES,
            "IDS_startup_set_user_info" to SettingsKeys.MINE_PROFILE,
            "IDS_exhibition_info" to SettingsKeys.MINE_PROFILE,
            "IDS_hwh_home_healthshop_featured_order_management" to SettingsKeys.MINE_ORDERS,
            "IDS_my_assets" to SettingsKeys.MINE_ASSETS,
            "IDS_my_award_my_award" to SettingsKeys.MINE_ASSETS,
            "IDS_ihealth_labs" to SettingsKeys.MINE_IHEALTH,
            "IDS_main_btn_state_settings" to SettingsKeys.MINE_SETTINGS,
            "IDS_hwh_privacy_center" to SettingsKeys.MINE_PRIVACY,
            "IDS_hw_personal_cetenr_help_customer_service" to SettingsKeys.MINE_HELP,
            "IDS_hwh_health_vo2max_help" to SettingsKeys.MINE_HELP,
            "IDS_user_profile_questions_suggestions" to SettingsKeys.MINE_FEEDBACK,
            "IDS_user_profile_questions_suggestions_bata" to SettingsKeys.MINE_FEEDBACK,
            "IDS_hw_show_setting_detection_updates" to SettingsKeys.MINE_UPDATE,
            "IDS_settings_about" to SettingsKeys.MINE_ABOUT,
        )

        /** 17.0.7.310 运行时资源名被压缩为数字，因此保留该版本核验后的 R.string ID。 */
        private val RESOURCE_IDS = mapOf(
            0x7f0211b2 to SettingsKeys.MINE_MEDALS,
            0x7f021581 to SettingsKeys.MINE_ACHIEVEMENTS,
            0x7f0211b4 to SettingsKeys.MINE_DATA,
            0x7f02002b to SettingsKeys.MINE_COURSES,
            0x7f022360 to SettingsKeys.MINE_COURSES,
            0x7f021310 to SettingsKeys.MINE_ACTIVITIES,
            0x7f02050e to SettingsKeys.MINE_ROUTES,
            0x7f021188 to SettingsKeys.MINE_PROFILE,
            0x7f02363a to SettingsKeys.MINE_PROFILE,
            0x7f0213a8 to SettingsKeys.MINE_ORDERS,
            0x7f021f25 to SettingsKeys.MINE_ASSETS,
            0x7f020288 to SettingsKeys.MINE_ASSETS,
            0x7f0217a6 to SettingsKeys.MINE_IHEALTH,
            0x7f02111c to SettingsKeys.MINE_SETTINGS,
            0x7f021b4d to SettingsKeys.MINE_PRIVACY,
            0x7f0219ab to SettingsKeys.MINE_HELP,
            0x7f0213c0 to SettingsKeys.MINE_HELP,
            0x7f0212c4 to SettingsKeys.MINE_FEEDBACK,
            0x7f021b99 to SettingsKeys.MINE_FEEDBACK,
            0x7f020091 to SettingsKeys.MINE_UPDATE,
            0x7f021150 to SettingsKeys.MINE_ABOUT,
        )
    }
}

class BottomTabKeyResolver {
    fun resolve(resourceName: String?): String? = resourceName?.let(RESOURCE_NAMES::get)

    companion object {
        private val RESOURCE_NAMES = mapOf(
            "IDS_hw_show_main_home_page_health" to SettingsKeys.BOTTOM_HEALTH,
            "IDS_hw_show_main_home_page_sport" to SettingsKeys.BOTTOM_SPORT,
            "IDS_hw_show_main_home_page_discover" to SettingsKeys.BOTTOM_MEMBER,
            "IDS_vip" to SettingsKeys.BOTTOM_MEMBER,
            "IDS_device_title_use" to SettingsKeys.BOTTOM_DEVICE,
            "IDS_hw_show_main_home_page_mine" to SettingsKeys.BOTTOM_MINE,
        )
    }
}

object HealthContentKeyResolver {
    private val topCards = mapOf(
        "SCUI_TwoModelCardData" to SettingsKeys.HEALTH_ACTIVITY_RINGS,
        "OperationCardData" to SettingsKeys.HEALTH_TODAY,
        "HealthInsightsCardData" to SettingsKeys.HEALTH_INSIGHTS,
        "HealthHeadLinesCardData" to SettingsKeys.HEALTH_HEADLINES,
        "OperaMsgCardData" to SettingsKeys.HEALTH_TIPS,
        "SCUI_DialogCardData" to SettingsKeys.HEALTH_TIPS,
    )
    private val healthCards = mapOf(
        "SPORTS_CARD_KEY_NEW" to SettingsKeys.HEALTH_CARD_SPORT,
        "HEARTRATE_CARD_KAY_NEW" to SettingsKeys.HEALTH_CARD_HEART,
        "SLEEP_CARD_KEY_NEW" to SettingsKeys.HEALTH_CARD_SLEEP,
        "WEIGHT_CARD_KEY_NEW" to SettingsKeys.HEALTH_CARD_WEIGHT,
        "STRESS_CARD_KEY_NEW" to SettingsKeys.HEALTH_CARD_STRESS,
        "BLOODOXYGEN_CARD_KEY_NEW" to SettingsKeys.HEALTH_CARD_SPO2,
        "BLOODSUGAR_CARD_KEY_NEW" to SettingsKeys.HEALTH_CARD_GLUCOSE,
        "BLOODPRESSURE_CARD_KEY_NEW" to SettingsKeys.HEALTH_CARD_PRESSURE,
        "TEMPERATURE_CARD_KEY_NEW" to SettingsKeys.HEALTH_CARD_TEMPERATURE,
        "PHYSIOLOGICAL_CYCLE_CARD_KEY_NEW" to SettingsKeys.HEALTH_CARD_CYCLE,
    )
    private val menuTitles = mapOf(
        "AI音乐空间" to SettingsKeys.HEALTH_AI_MUSIC,
        "AI助眠音乐空间" to SettingsKeys.HEALTH_AI_MUSIC,
        "健康管理" to SettingsKeys.HEALTH_MANAGEMENT,
        "智能减重" to SettingsKeys.HEALTH_WEIGHT_LOSS,
        "智能减脂" to SettingsKeys.HEALTH_WEIGHT_LOSS,
        "智能训练" to SettingsKeys.HEALTH_SMART_TRAINING,
        "助眠音乐" to SettingsKeys.HEALTH_SLEEP_MUSIC,
    )

    fun topCard(cardName: String?): String? = cardName?.let(topCards::get)
    fun healthCard(cardId: String?): String? = cardId?.let(healthCards::get)

    fun quickEntry(dynamicDataId: String?, linkValue: String?, title: String?): String? {
        val stable = listOfNotNull(dynamicDataId, linkValue).joinToString("|").lowercase()
        return when {
            "ai-sleep" in stable || "sleepmusic" in stable -> SettingsKeys.HEALTH_AI_MUSIC
            "healthmanage" in stable || "health-management" in stable -> SettingsKeys.HEALTH_MANAGEMENT
            "weight" in stable && "plan" in stable -> SettingsKeys.HEALTH_WEIGHT_LOSS
            "training" in stable && "plan" in stable -> SettingsKeys.HEALTH_SMART_TRAINING
            "pagetypeid=7" in stable -> SettingsKeys.HEALTH_SLEEP_MUSIC
            else -> title?.let(menuTitles::get)
        }
    }
}

object SportContentKeyResolver {
    private val titles = mapOf(
        "拉伸放松" to SettingsKeys.SPORT_STRETCH,
        "舒展放松" to SettingsKeys.SPORT_STRETCH,
        "古法养生" to SettingsKeys.SPORT_TRADITIONAL,
        "骑行课程" to SettingsKeys.SPORT_CYCLING,
        "高尔夫课" to SettingsKeys.SPORT_GOLF,
        "热汗舞蹈" to SettingsKeys.SPORT_DANCE,
        "普拉提课" to SettingsKeys.SPORT_PILATES,
        "畅享运动" to SettingsKeys.SPORT_ENJOY,
        "今日动一动" to SettingsKeys.SPORT_TODAY,
        "更多好课" to SettingsKeys.SPORT_MORE_COURSES,
        "明星教练" to SettingsKeys.SPORT_COACHES,
    )

    fun resolve(resourceName: String?, providerName: String?, title: String?): String? {
        val stable = "${resourceName.orEmpty()}|${providerName.orEmpty()}".lowercase()
        return when {
            "banner" in stable -> SettingsKeys.SPORT_BANNER
            "stretch" in stable -> SettingsKeys.SPORT_STRETCH
            "cycling" in stable || "ride" in stable -> SettingsKeys.SPORT_CYCLING
            "golf" in stable -> SettingsKeys.SPORT_GOLF
            "dance" in stable -> SettingsKeys.SPORT_DANCE
            "pilates" in stable -> SettingsKeys.SPORT_PILATES
            "coach" in stable -> SettingsKeys.SPORT_COACHES
            else -> title?.let(titles::get)
        }
    }
}

object DeviceContentKeyResolver {
    val resourceMappings = mapOf(
        "device_global_search_view" to SettingsKeys.DEVICE_SEARCH,
        "hwappbarpattern_layout_menu_icon" to SettingsKeys.DEVICE_MENU,
        "hwappbarpattern_layout_ok_icon" to SettingsKeys.DEVICE_MENU,
        "hwappbarpattern_menu_icon" to SettingsKeys.DEVICE_MENU,
        "hwappbarpattern_ok_icon" to SettingsKeys.DEVICE_MENU,
        "auto_ota_top_layout" to SettingsKeys.DEVICE_AUTO_UPGRADE,
        "nearby_permission_top_layout" to SettingsKeys.DEVICE_NEARBY_PERMISSION,
        "auto_switch_layout" to SettingsKeys.DEVICE_AUTO_SWITCH,
        "rl_share_device" to SettingsKeys.DEVICE_SHARED,
        "device_card_normal" to SettingsKeys.DEVICE_ADD,
        "device_card_list_more_add" to SettingsKeys.DEVICE_LIST,
        "card_device_list" to SettingsKeys.DEVICE_LIST,
        "device_function_card" to SettingsKeys.DEVICE_FUNCTIONS,
        "watchface_card" to SettingsKeys.DEVICE_WATCH_FACES,
        "optimization_card" to SettingsKeys.DEVICE_RECOMMENDED,
        "recommended_layout" to SettingsKeys.DEVICE_RECOMMENDED,
        "marketing_banner" to SettingsKeys.DEVICE_MARKETING,
        "marketing_top_column_layout" to SettingsKeys.DEVICE_MARKETING,
        "general_marketing_layout" to SettingsKeys.DEVICE_MARKETING,
        "marketing_layout" to SettingsKeys.DEVICE_MARKETING,
        "no_device_marketing_layout" to SettingsKeys.DEVICE_MARKETING,
        "device_vmall_card_layout" to SettingsKeys.DEVICE_STORE,
        "vmall_recommended_layout" to SettingsKeys.DEVICE_STORE,
    )

    fun resolve(resourceName: String?): String? = resourceName?.let(resourceMappings::get)
}

object ListFilters {
    fun <T> copyAndFilter(
        source: List<T>,
        keyOf: (T) -> String?,
        config: Map<String, Boolean>,
    ): ArrayList<T> = ArrayList(source.filterNot { item -> keyOf(item)?.let { config[it] == true } == true })

    fun <T> cleanGroups(source: List<T>, isDivider: (T) -> Boolean): ArrayList<T> {
        val result = ArrayList<T>(source.size)
        source.forEach { item ->
            if (!isDivider(item) || result.isNotEmpty() && !isDivider(result.last())) result += item
        }
        while (result.lastOrNull()?.let(isDivider) == true) result.removeAt(result.lastIndex)
        return result
    }

    /** 仅当分组标题后仍有内容时保留标题，并清掉首尾/连续空白分隔项。 */
    fun <T> cleanSections(
        source: List<T>,
        isDivider: (T) -> Boolean,
        isTitledDivider: (T) -> Boolean,
    ): ArrayList<T> {
        val result = ArrayList<T>(source.size)
        val pending = ArrayList<T>()
        source.forEach { item ->
            if (isDivider(item)) {
                pending += item
            } else {
                if (pending.isNotEmpty()) {
                    val titled = pending.lastOrNull(isTitledDivider)
                    if (titled != null) result += titled else if (result.isNotEmpty()) result += pending.last()
                    pending.clear()
                }
                result += item
            }
        }
        return result
    }
}
