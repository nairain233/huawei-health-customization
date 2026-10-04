package love.nairain.huawei.hook.symbols

/** 三版共有的稳定页面入口；混淆目标由扫描结果提供。 */
data class HuaweiHealthHookPoints(
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
    val bottomBase: String,
    val bottomView: String,
) {
    companion object {
        val ANCHORS = HuaweiHealthHookPoints(
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
            mineListManager = "",
            mineGridAdapter = "com.huawei.ui.main.stories.userprofile.activity.PersonalGridAdapter",
            bottomBase = "com.huawei.uikit.phone.hwbottomnavigationview.widget.HwBottomNavigationView",
            bottomView = "com.huawei.ui.commonui.scrollview.HealthBottomView",
        )
    }
}
