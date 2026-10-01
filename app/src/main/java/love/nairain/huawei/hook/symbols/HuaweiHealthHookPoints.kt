package love.nairain.huawei.hook.symbols

/** 仅包含 17.0.7.320 静态分析确认过的符号，不提供未知版本兜底。 */
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
    val newDeviceFragment: String,
    val arkuiDeviceFragment: String,
    val vmallFragment: String,
    val mineFragment: String,
    val mineListManager: String,
    val mineGridAdapter: String,
    val mineMarketingCallback: String,
    val bottomBase: String,
    val bottomView: String,
) {
    companion object {
        val V17_0_7_320 = HuaweiHealthHookPoints(
            versionName = "17.0.7.320",
            versionCode = 1700007320L,
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
            newDeviceFragment = "com.huawei.ui.homehealth.device.NewDeviceFragment",
            arkuiDeviceFragment = "com.huawei.ui.homehealth.devicearkui.ArkuiDeviceFragment",
            vmallFragment = "com.huawei.ui.homehealth.device.VMallFragment",
            mineFragment = "com.huawei.ui.main.stories.userprofile.activity.PersonalCenterFragment",
            mineListManager = "wsl",
            mineGridAdapter = "com.huawei.ui.main.stories.userprofile.activity.PersonalGridAdapter",
            mineMarketingCallback = "com.huawei.ui.main.stories.userprofile.activity.PersonalCenterRecyclerViewAdapter\$b\$4",
            bottomBase = "com.huawei.uikit.phone.hwbottomnavigationview.widget.HwBottomNavigationView",
            bottomView = "com.huawei.ui.commonui.scrollview.HealthBottomView",
        )
    }
}
