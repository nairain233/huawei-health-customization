package love.nairain.huawei.hook.resolver

/** 两份真实 APK 的显示层证据；资源和混淆成员仍须由当前 APK 完整核验。 */
internal object BadgeTargets {
    const val BOTTOM_ITEM = "com.huawei.uikit.hwbottomnavigationview.widget.HwBottomNavigationView\$BottomNavigationItemView"
    const val MINE_HEAD = "com.huawei.ui.main.stories.userprofile.scroll.CustomHeadView"
    const val MINE_ADAPTER = "com.huawei.ui.main.stories.userprofile.activity.PersonalCenterRecyclerViewAdapter"
    const val MINE_GRID_HOLDER = "com.huawei.ui.main.stories.userprofile.card.PersonalGridCardViewHolder"
    const val HOLDER = "androidx.recyclerview.widget.RecyclerView\$ViewHolder"
    val HEADER_RESOURCES = setOf("txt_message_count_new", "rank_red_point")
    val ROW_RESOURCES = setOf("list_content_red_point", "mymedal_red_point", "number_red_dot", "number_red_dot_text")
    val GRID_RESOURCES = setOf("item_channel_red_point", "item_channel_red_point_vertical")
    val KAKA_RESOURCES = setOf("kaka_to_be_collected_text")
}
