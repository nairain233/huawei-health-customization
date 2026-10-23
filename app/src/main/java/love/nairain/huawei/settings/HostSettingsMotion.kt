package love.nairain.huawei.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection

/** 只负责模块页面过渡；复用既有父路由，不引入第二份导航栈。 */
@Composable
internal fun SettingsPageTransition(route: String, modifier: Modifier = Modifier,
    content: @Composable (route: String) -> Unit) {
    val layoutDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    AnimatedContent(targetState = route, modifier = modifier.clipToBounds(), label = "settings-page",
        transitionSpec = {
            val direction = layoutDirection * if (settingsRouteReturnsTo(initialState, targetState)) -1 else 1
            ((slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 5 * direction } + fadeIn(tween(200)))
                togetherWith (slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it / 8 * direction } + fadeOut(tween(160))))
                .using(null)
        }) { displayedRoute ->
        val active = displayedRoute == route
        // 退场页仍参与绘制，但不能接受触摸或进入无障碍遍历。
        val interaction = if (active) Modifier else Modifier.clearAndSetSemantics {}.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
        Box(Modifier.fillMaxSize().then(interaction).testTag("settings.page.$displayedRoute")) {
            content(displayedRoute)
        }
    }
}

/** 返回任意祖先均反向播放，包含运动子页和服务三级页面。 */
internal fun settingsRouteReturnsTo(from: String, to: String): Boolean {
    var ancestor = HostSettingsPageState.parent(from)
    while (ancestor != "close") {
        if (ancestor == to) return true
        ancestor = HostSettingsPageState.parent(ancestor)
    }
    return false
}
