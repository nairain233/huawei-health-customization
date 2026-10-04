package fixtures;

/** 在真实 APK 查询中注入额外内容候选，验证歧义不会误选。 */
public final class ScanCollision {
    public String getCardName() {
        return Boolean.getBoolean("ring") ? "SCUI_TwoModelCardData" : "MyGroupCardData";
    }
}
