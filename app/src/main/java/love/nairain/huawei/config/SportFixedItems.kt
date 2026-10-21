package love.nairain.huawei.config

import androidx.annotation.StringRes
import love.nairain.huawei.R

enum class SportItemKind { CONTROL, SECTION, ENTRY, GROUP, PLAN, GUIDE }

data class SportPage(val id: Int, @param:StringRes val title: Int)
data class SportItem(
    val id: String,
    val pageId: Int,
    @param:StringRes val title: Int = 0,
    val hostTitle: String = "",
    val legacyKey: String? = null,
    val parentKey: String? = null,
    val kind: SportItemKind = SportItemKind.CONTROL,
    val resources: Set<String> = emptySet(),
) {
    companion object {
        fun fixed(page: Int, key: String, parent: String? = null) = SportItem("$page/fixed/$key", page, parentKey = parent)
    }
}

/** 17.0.8.300 实机清单；未知内容放行，不再由宿主内容生成设置项。 */
object SportFixedItems {
    val pages = listOf(
        SportPage(500001, R.string.sport_page_recommend),
        SportPage(2, R.string.sport_page_plan),
        SportPage(258, R.string.sport_page_run),
        SportPage(137, R.string.sport_page_yoga),
        SportPage(10001, R.string.sport_page_fitness),
        SportPage(257, R.string.sport_page_walk),
        SportPage(259, R.string.sport_page_cycle),
        SportPage(286, R.string.sport_page_golf),
        SportPage(283, R.string.sport_page_rope),
    )
    val pageIds = pages.map { it.id }
    private val entries = listOf(
        SportItem("500001/fixed/hide.sport.banner", 500001, R.string.sport_fixed_500001_hide_sport_banner, "Banner", "hide.sport.banner", null, SportItemKind.CONTROL, setOf("view_sport_banner_root", "view_pager_sport_banner")),
        SportItem("500001/fixed/hide.sport.quick_entries", 500001, R.string.sport_fixed_500001_hide_sport_quick_entries, "快捷入口整组", "hide.sport.quick_entries", null, SportItemKind.GROUP, setOf("layout_marketing_grid")),
        SportItem("500001/fixed/hide.sport.latest", 500001, R.string.sport_fixed_500001_hide_sport_latest, "最新上线", "hide.sport.latest", null, SportItemKind.SECTION, setOf()),
        SportItem("500001/fixed/hide.sport.today", 500001, R.string.sport_fixed_500001_hide_sport_today, "今日动一动", "hide.sport.today", null, SportItemKind.SECTION, setOf()),
        SportItem("500001/fixed/hide.sport.more_courses", 500001, R.string.sport_fixed_500001_hide_sport_more_courses, "更多好课", "hide.sport.more_courses", null, SportItemKind.SECTION, setOf()),
        SportItem("500001/fixed/hide.sport.coaches", 500001, R.string.sport_fixed_500001_hide_sport_coaches, "明星教练", "hide.sport.coaches", null, SportItemKind.SECTION, setOf()),
        SportItem("500001/fixed/hide.sport.entry.golf", 500001, R.string.sport_fixed_500001_hide_sport_entry_golf, "高尔夫课", "hide.sport.entry.golf", "hide.sport.quick_entries", SportItemKind.ENTRY, setOf()),
        SportItem("500001/fixed/aerobic_rope", 500001, R.string.sport_fixed_500001_aerobic_rope, "有氧跳绳", null, "hide.sport.quick_entries", SportItemKind.ENTRY, setOf()),
        SportItem("500001/fixed/fitness_test", 500001, R.string.sport_fixed_500001_fitness_test, "达标自测", null, "hide.sport.quick_entries", SportItemKind.ENTRY, setOf()),
        SportItem("500001/fixed/hide.sport.entry.walk_run", 500001, R.string.sport_fixed_500001_hide_sport_entry_walk_run, "跑步健走", "hide.sport.entry.walk_run", "hide.sport.quick_entries", SportItemKind.ENTRY, setOf()),
        SportItem("500001/fixed/hide.sport.entry.traditional", 500001, R.string.sport_fixed_500001_hide_sport_entry_traditional, "古法养生", "hide.sport.entry.traditional", "hide.sport.quick_entries", SportItemKind.ENTRY, setOf()),
        SportItem("500001/fixed/hide.sport.entry.muscle", 500001, R.string.sport_fixed_500001_hide_sport_entry_muscle, "增肌塑形", "hide.sport.entry.muscle", "hide.sport.quick_entries", SportItemKind.ENTRY, setOf()),
        SportItem("500001/fixed/fat_burning", 500001, R.string.sport_fixed_500001_fat_burning, "燃脂瘦身", null, "hide.sport.quick_entries", SportItemKind.ENTRY, setOf()),
        SportItem("500001/fixed/hide.sport.entry.master_yoga", 500001, R.string.sport_fixed_500001_hide_sport_entry_master_yoga, "大师瑜伽", "hide.sport.entry.master_yoga", "hide.sport.quick_entries", SportItemKind.ENTRY, setOf()),
        SportItem("500001/fixed/hide.sport.entry.dance", 500001, R.string.sport_fixed_500001_hide_sport_entry_dance, "热汗舞蹈", "hide.sport.entry.dance", "hide.sport.quick_entries", SportItemKind.ENTRY, setOf()),
        SportItem("500001/fixed/hide.sport.entry.muay_thai", 500001, R.string.sport_fixed_500001_hide_sport_entry_muay_thai, "泰拳搏击", "hide.sport.entry.muay_thai", "hide.sport.quick_entries", SportItemKind.ENTRY, setOf()),
        SportItem("500001/fixed/hide.sport.entry.pilates", 500001, R.string.sport_fixed_500001_hide_sport_entry_pilates, "普拉提课", "hide.sport.entry.pilates", "hide.sport.quick_entries", SportItemKind.ENTRY, setOf()),
        SportItem("500001/fixed/hide.sport.entry.stretch", 500001, R.string.sport_fixed_500001_hide_sport_entry_stretch, "拉伸放松", "hide.sport.entry.stretch", "hide.sport.quick_entries", SportItemKind.ENTRY, setOf()),
        SportItem("500001/fixed/hide.sport.entry.cycling", 500001, R.string.sport_fixed_500001_hide_sport_entry_cycling, "骑行课程", "hide.sport.entry.cycling", "hide.sport.quick_entries", SportItemKind.ENTRY, setOf()),
        SportItem("500001/fixed/hide.sport.entry.blue_star", 500001, R.string.sport_fixed_500001_hide_sport_entry_blue_star, "蓝星环游", "hide.sport.entry.blue_star", "hide.sport.quick_entries", SportItemKind.ENTRY, setOf()),
        SportItem("500001/fixed/hide.sport.entry.run_power", 500001, R.string.sport_fixed_500001_hide_sport_entry_run_power, "跑力提升", "hide.sport.entry.run_power", "hide.sport.quick_entries", SportItemKind.ENTRY, setOf()),
        SportItem("2/fixed/hide.sport.plan.cards", 2, R.string.sport_fixed_2_hide_sport_plan_cards, "计划卡片整组", "hide.sport.plan.cards", null, SportItemKind.CONTROL, setOf("plan_resource_slot")),
        SportItem("2/fixed/hide.sport.plan.weight", 2, R.string.sport_fixed_2_hide_sport_plan_weight, "智能体重管理", "hide.sport.plan.weight", "hide.sport.plan.cards", SportItemKind.PLAN, setOf()),
        SportItem("2/fixed/hide.sport.plan.training", 2, R.string.sport_fixed_2_hide_sport_plan_training, "智能训练计划", "hide.sport.plan.training", "hide.sport.plan.cards", SportItemKind.PLAN, setOf()),
        SportItem("258/fixed/hide.sport.run.summary", 258, R.string.sport_fixed_258_hide_sport_run_summary, "累计概览", "hide.sport.run.summary", null, SportItemKind.CONTROL, setOf("normal_view")),
        SportItem("258/fixed/hide.sport.run.warmup", 258, R.string.sport_fixed_258_hide_sport_run_warmup, "热身", "hide.sport.run.warmup", null, SportItemKind.CONTROL, setOf("section_img_sport_entrance_warmup")),
        SportItem("258/fixed/hide.sport.run.begin", 258, R.string.sport_fixed_258_hide_sport_run_begin, "开始按钮", "hide.sport.run.begin", null, SportItemKind.CONTROL, setOf("section_img_sport_entrance_begin")),
        SportItem("258/fixed/goal", 258, R.string.sport_fixed_258_goal, "设置目标", null, null, SportItemKind.CONTROL, setOf("goal_card_button")),
        SportItem("258/fixed/hide.sport.run.route", 258, R.string.sport_fixed_258_hide_sport_run_route, "路线", "hide.sport.run.route", null, SportItemKind.CONTROL, setOf("right_top_layout")),
        SportItem("257/fixed/hide.sport.run.summary", 257, R.string.sport_fixed_257_hide_sport_run_summary, "累计概览", null, null, SportItemKind.CONTROL, setOf("normal_view")),
        SportItem("257/fixed/hide.sport.run.warmup", 257, R.string.sport_fixed_257_hide_sport_run_warmup, "热身", null, null, SportItemKind.CONTROL, setOf("section_img_sport_entrance_warmup")),
        SportItem("257/fixed/hide.sport.run.begin", 257, R.string.sport_fixed_257_hide_sport_run_begin, "开始按钮", null, null, SportItemKind.CONTROL, setOf("section_img_sport_entrance_begin")),
        SportItem("257/fixed/goal", 257, R.string.sport_fixed_257_goal, "设置目标", null, null, SportItemKind.CONTROL, setOf("goal_card_button")),
        SportItem("259/fixed/hide.sport.run.summary", 259, R.string.sport_fixed_259_hide_sport_run_summary, "累计概览", null, null, SportItemKind.CONTROL, setOf("normal_view")),
        SportItem("259/fixed/hide.sport.run.warmup", 259, R.string.sport_fixed_259_hide_sport_run_warmup, "热身", null, null, SportItemKind.CONTROL, setOf("section_img_sport_entrance_warmup")),
        SportItem("259/fixed/hide.sport.run.begin", 259, R.string.sport_fixed_259_hide_sport_run_begin, "开始按钮", null, null, SportItemKind.CONTROL, setOf("section_img_sport_entrance_begin")),
        SportItem("259/fixed/goal", 259, R.string.sport_fixed_259_goal, "设置目标", null, null, SportItemKind.CONTROL, setOf("goal_card_button")),
        SportItem("259/fixed/hide.sport.run.route", 259, R.string.sport_fixed_259_hide_sport_run_route, "路线", null, null, SportItemKind.CONTROL, setOf("right_top_layout")),
        SportItem("283/fixed/hide.sport.run.summary", 283, R.string.sport_fixed_283_hide_sport_run_summary, "累计概览", null, null, SportItemKind.CONTROL, setOf("normal_view")),
        SportItem("283/fixed/hide.sport.run.warmup", 283, R.string.sport_fixed_283_hide_sport_run_warmup, "热身", null, null, SportItemKind.CONTROL, setOf("section_img_sport_entrance_warmup")),
        SportItem("283/fixed/hide.sport.run.begin", 283, R.string.sport_fixed_283_hide_sport_run_begin, "开始按钮", null, null, SportItemKind.CONTROL, setOf("section_img_sport_entrance_begin")),
        SportItem("283/fixed/goal", 283, R.string.sport_fixed_283_goal, "自由跳", null, null, SportItemKind.CONTROL, setOf("goal_card_button")),
        SportItem("258/fixed/hide.sport.run.music", 258, R.string.sport_fixed_258_hide_sport_run_music, "音乐跑", "hide.sport.run.music", null, SportItemKind.CONTROL, setOf("section_img_sport_entrance_music")),
        SportItem("258/fixed/hide.sport.run.training", 258, R.string.sport_fixed_258_hide_sport_run_training, "跑步必练", "hide.sport.run.training", null, SportItemKind.SECTION, setOf()),
        SportItem("258/fixed/hide.sport.quick_entries", 258, R.string.sport_fixed_258_hide_sport_quick_entries, "快捷入口整组", null, null, SportItemKind.GROUP, setOf("layout_marketing_grid")),
        SportItem("258/fixed/all_running", 258, R.string.sport_fixed_258_all_running, "全部跑步课", null, "hide.sport.quick_entries", SportItemKind.ENTRY, setOf()),
        SportItem("258/fixed/running_stretch", 258, R.string.sport_fixed_258_running_stretch, "跑步拉伸", null, "hide.sport.quick_entries", SportItemKind.ENTRY, setOf()),
        SportItem("258/fixed/smart_training", 258, R.string.sport_fixed_258_smart_training, "智能训练", null, "hide.sport.quick_entries", SportItemKind.ENTRY, setOf()),
        SportItem("258/fixed/running_burn", 258, R.string.sport_fixed_258_running_burn, "跑步燃脂", null, "hide.sport.quick_entries", SportItemKind.ENTRY, setOf()),
        SportItem("258/fixed/injury_prevention", 258, R.string.sport_fixed_258_injury_prevention, "损伤防护", null, "hide.sport.quick_entries", SportItemKind.ENTRY, setOf()),
        SportItem("137/fixed/my_yoga", 137, R.string.sport_fixed_137_my_yoga, "我的瑜伽课程", null, null, SportItemKind.SECTION, setOf()),
        SportItem("137/fixed/relax", 137, R.string.sport_fixed_137_relax, "即刻舒缓放松", null, null, SportItemKind.SECTION, setOf()),
        SportItem("137/fixed/awaken", 137, R.string.sport_fixed_137_awaken, "唤醒身体活力", null, null, SportItemKind.SECTION, setOf()),
        SportItem("137/fixed/sleep", 137, R.string.sport_fixed_137_sleep, "练完睡个好觉", null, null, SportItemKind.SECTION, setOf()),
        SportItem("137/fixed/shape", 137, R.string.sport_fixed_137_shape, "塑形气质提升", null, null, SportItemKind.SECTION, setOf()),
        SportItem("137/fixed/burn", 137, R.string.sport_fixed_137_burn, "热辣高效瘦身", null, null, SportItemKind.SECTION, setOf()),
        SportItem("137/fixed/choose_course", 137, R.string.sport_fixed_137_choose_course, "去选课", null, "my_yoga", SportItemKind.CONTROL, setOf("no_data_button")),
        SportItem("10001/fixed/hide.sport.banner", 10001, R.string.sport_fixed_10001_hide_sport_banner, "Banner", "hide.sport.banner", null, SportItemKind.CONTROL, setOf("view_sport_banner_root")),
        SportItem("10001/fixed/hide.sport.fitness.summary", 10001, R.string.sport_fixed_10001_hide_sport_fitness_summary, "运动时长概览", "hide.sport.fitness.summary", null, SportItemKind.CONTROL, setOf("normal_view_fitness")),
        SportItem("10001/fixed/all_courses", 10001, R.string.sport_fixed_10001_all_courses, "全部课程", null, "hide.sport.fitness.summary", SportItemKind.CONTROL, setOf("common_card_button_fitness")),
        SportItem("10001/fixed/hide.sport.fitness.my_courses", 10001, R.string.sport_fixed_10001_hide_sport_fitness_my_courses, "我的健身课程", "hide.sport.fitness.my_courses", null, SportItemKind.SECTION, setOf()),
        SportItem("10001/fixed/hide.sport.fitness.weekly_plan", 10001, R.string.sport_fixed_10001_hide_sport_fitness_weekly_plan, "一周五练计划", "hide.sport.fitness.weekly_plan", null, SportItemKind.SECTION, setOf()),
        SportItem("10001/fixed/choose_course", 10001, R.string.sport_fixed_10001_choose_course, "去选课", null, "hide.sport.fitness.my_courses", SportItemKind.CONTROL, setOf("no_data_button")),
        SportItem("10001/fixed/fat_loss_plans", 10001, R.string.sport_fixed_10001_fat_loss_plans, "热门减脂计划", null, null, SportItemKind.SECTION, setOf()),
        SportItem("10001/fixed/recommended", 10001, R.string.sport_fixed_10001_recommended, "推荐好课", null, null, SportItemKind.SECTION, setOf()),
        SportItem("10001/fixed/fitness_plans", 10001, R.string.sport_fixed_10001_fitness_plans, "健身计划", null, null, SportItemKind.SECTION, setOf()),
        SportItem("10001/fixed/exercise_library", 10001, R.string.sport_fixed_10001_exercise_library, "动作库", null, null, SportItemKind.SECTION, setOf()),
        SportItem("257/fixed/warmup_stretch", 257, R.string.sport_fixed_257_warmup_stretch, "热身拉伸专区", null, null, SportItemKind.SECTION, setOf()),
        SportItem("259/fixed/cycling_essentials", 259, R.string.sport_fixed_259_cycling_essentials, "骑行必备", null, null, SportItemKind.SECTION, setOf()),
        SportItem("259/fixed/advanced", 259, R.string.sport_fixed_259_advanced, "进阶专区", null, null, SportItemKind.SECTION, setOf()),
        SportItem("286/fixed/course_map", 286, R.string.sport_fixed_286_course_map, "球场地图", null, null, SportItemKind.CONTROL, setOf("right_top_button")),
        SportItem("286/fixed/guide", 286, R.string.sport_fixed_286_guide, "高尔夫球场模式使用指南", null, null, SportItemKind.GUIDE, setOf()),
        SportItem("286/fixed/pro_tips", 286, R.string.sport_fixed_286_pro_tips, "职业球手的高尔夫秘籍", null, null, SportItemKind.SECTION, setOf()),
        SportItem("283/fixed/rope_essentials", 283, R.string.sport_fixed_283_rope_essentials, "跳绳必备", null, null, SportItemKind.SECTION, setOf()),
        SportItem("283/fixed/rope_challenges", 283, R.string.sport_fixed_283_rope_challenges, "跳绳挑战", null, null, SportItemKind.SECTION, setOf()),
    )
    private val byId = entries.associateBy { it.id }
    fun items(page: Int): List<SportItem> = entries.filter { it.pageId == page }
    fun item(id: String): SportItem? = byId[id]
    val resourceNames = entries.flatMap { it.resources }.toSet() + setOf(
        "header_title", "hwsubheader_title_left", "item_title", "item_two_landscape_title",
        "item_quick_entry_title", "item_quick_entry_root_layout", "series_course_layout",
        "layout_marketing_grid", "section_root_view", "item_two_landscape_layout",
        "section1_1list_01_no_data_view",
        "banner_root", "section16_9card_01", "recycle_item", "view_pager_banner_cant_loop", "view_pager_banner",
    )
}
