package love.nairain.huawei.hook.symbols

/** 仅包含 17.0.7.310 静态分析确认过的符号，不提供未知版本兜底。 */
data class HuaweiHealthHookPoints(
    val versionName: String,
    val versionCode: Long,
    val homeFragment: String,
    val homeAdapter: String,
    val functionSetHolder: String,
    val sportFragment: String,
    val sportTrigger: String,
    val sportColumnAdapter: String,
    val deviceFragments: List<String>,
    val mineFragment: String,
    val mineListManager: String,
    val mineGridAdapter: String,
    val mineMarketingCallback: String,
    val bottomBase: String,
    val bottomView: String,
) {
    companion object {
        val V17_0_7_310 = HuaweiHealthHookPoints(
            versionName = "17.0.7.310",
            versionCode = 1700007310L,
            homeFragment = "com.huawei.ui.homehealth.HomeFragment",
            homeAdapter = "com.huawei.ui.homehealth.adapter.HomeCardAdapter",
            functionSetHolder = "com.huawei.ui.homehealth.functionsetcard.FunctionSetCardViewHolder",
            sportFragment = "com.huawei.ui.homehealth.runcard.trackfragments.SportEntranceFragment",
            sportTrigger = "com.huawei.ui.homehealth.runcard.trackfragments.SportTabPageResTrigger",
            sportColumnAdapter = "com.huawei.health.marketing.views.ColumnLayoutAdapter",
            deviceFragments = listOf(
                "com.huawei.ui.homehealth.device.DeviceFragment",
                "com.huawei.ui.homehealth.device.CardDeviceFragment",
            ),
            mineFragment = "com.huawei.ui.main.stories.userprofile.activity.PersonalCenterFragment",
            mineListManager = "wst",
            mineGridAdapter = "com.huawei.ui.main.stories.userprofile.activity.PersonalGridAdapter",
            mineMarketingCallback = "com.huawei.ui.main.stories.userprofile.activity.PersonalCenterRecyclerViewAdapter\$c\$4",
            bottomBase = "com.huawei.uikit.phone.hwbottomnavigationview.widget.HwBottomNavigationView",
            bottomView = "com.huawei.ui.commonui.scrollview.HealthBottomView",
        )
    }
}
