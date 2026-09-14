package love.nairain.huawei

import androidx.annotation.StringRes
import androidx.test.platform.app.InstrumentationRegistry

internal fun resourceString(@StringRes id: Int, vararg formatArgs: Any): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *formatArgs)
