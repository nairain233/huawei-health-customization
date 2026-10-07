package love.nairain.huawei.watchface

import java.net.URI

/** 仅接受真机已核验的商店页面和本页面的一次随机入口令牌。 */
internal object LocalFacePagePolicy {
    fun acceptsPage(url: String?): Boolean = try {
        val uri = URI(url.orEmpty())
        uri.scheme == "https" && uri.host == "h5hosting-drcn.dbankcdn.cn" &&
            uri.port == -1 && uri.userInfo == null && uri.path == "/cch5/health/watchFace/index.html"
    } catch (_: java.net.URISyntaxException) { false }

    fun acceptsAction(url: String?, token: String): Boolean =
        token.isNotBlank() && url == "huawei-local-watchface://choose?token=$token"
}
