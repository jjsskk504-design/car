package ai.prism.visualization.rendering

import ai.prism.visualization.model.RiskLevel

/** 화면에 표시하는 등급. EGO 는 자차 전용. */
enum class DisplayLevel { EGO, NORMAL, WATCH, WARNING, UNKNOWN }

/** 색상만으로 위험을 전달하지 않도록 아이콘·선 모양·텍스트를 함께 정의한다. */
enum class RiskIcon { NONE, TRIANGLE, TRIANGLE_FILLED, QUESTION }

data class RiskStyle(
    val level: DisplayLevel,
    /** 0xAARRGGBB */
    val fill: Long,
    val stroke: Long,
    val strokeWidthDp: Float,
    val dashed: Boolean,
    val icon: RiskIcon,
    /** 스크린리더·범례·상태칩에 쓰는 한국어 라벨. */
    val label: String,
    /** 아이콘 안 글자 */
    val glyph: String,
) {
    companion object {
        val EGO = RiskStyle(DisplayLevel.EGO, 0xFF3B82F6, 0xFFBFD7FF, 1.5f, false, RiskIcon.NONE, "내 차량", "")
        val NORMAL = RiskStyle(DisplayLevel.NORMAL, 0xFF8A919C, 0xFF5D636C, 1f, false, RiskIcon.NONE, "정상", "")
        val WATCH = RiskStyle(DisplayLevel.WATCH, 0xFFF2C230, 0xFFFFE08A, 2.5f, false, RiskIcon.TRIANGLE, "주의", "!")
        val WARNING = RiskStyle(DisplayLevel.WARNING, 0xFFE5484D, 0xFFFFFFFF, 3f, false, RiskIcon.TRIANGLE_FILLED, "경고", "!!")
        /** 판단 보류: 파란 점선 + ?. 자차(진한 파란 실선 채움)와 구분되도록 밝은 하늘색 점선만 쓴다. */
        val UNKNOWN = RiskStyle(DisplayLevel.UNKNOWN, 0x3360A5FA, 0xFF7DB8FF, 2f, true, RiskIcon.QUESTION, "판단 보류", "?")

        fun of(level: DisplayLevel): RiskStyle = when (level) {
            DisplayLevel.EGO -> EGO
            DisplayLevel.NORMAL -> NORMAL
            DisplayLevel.WATCH -> WATCH
            DisplayLevel.WARNING -> WARNING
            DisplayLevel.UNKNOWN -> UNKNOWN
        }

        fun of(level: RiskLevel): RiskStyle = of(level.toDisplay())
    }
}

fun RiskLevel.toDisplay(): DisplayLevel = when (this) {
    RiskLevel.NORMAL -> DisplayLevel.NORMAL
    RiskLevel.WATCH -> DisplayLevel.WATCH
    RiskLevel.WARNING -> DisplayLevel.WARNING
    RiskLevel.UNKNOWN -> DisplayLevel.UNKNOWN
}

/** 강조 우선순위. UNKNOWN 은 경고보다 낮게 두어 "판단 보류"가 경고를 덮어쓰지 않게 한다. */
val DisplayLevel.severity: Int
    get() = when (this) {
        DisplayLevel.EGO, DisplayLevel.NORMAL -> 0
        DisplayLevel.UNKNOWN -> 1
        DisplayLevel.WATCH -> 2
        DisplayLevel.WARNING -> 3
    }
