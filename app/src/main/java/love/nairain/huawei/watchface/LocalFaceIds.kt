package love.nairain.huawei.watchface

import java.io.File

/** 只读识别早期随机导入的表盘，不分配新 ID，不删除旧记录。 */
internal class LocalFaceIds(private val directory: File) {
    fun legacyIds(): Set<String> = directory.listFiles().orEmpty()
        .filter { it.isFile && it.name.matches(Regex("[1-9][0-9]{8}\\.reserved")) }
        .map { it.name.removeSuffix(".reserved") }.toSet()
}
