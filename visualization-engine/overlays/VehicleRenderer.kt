package ai.prism.visualization.overlays

import ai.prism.visualization.model.Point2
import ai.prism.visualization.rendering.DisplayLevel
import ai.prism.visualization.rendering.PartRole
import ai.prism.visualization.rendering.RiskIcon
import ai.prism.visualization.rendering.RiskStyle
import ai.prism.visualization.rendering.SceneVehicle
import ai.prism.visualization.rendering.ShapeOp
import ai.prism.visualization.rendering.ShapePart
import ai.prism.visualization.rendering.VehicleBodyType
import ai.prism.visualization.rendering.VehicleGeometry
import ai.prism.visualization.rendering.VehicleShapes
import ai.prism.visualization.rendering.ViewportTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 차량 한 대를 탑뷰 자동차 그래픽(승용차·SUV·대형 화물차)으로 그린다.
 *
 * - 차체 색: 자차 = 파란색, 다른 차량 = 회색(등급과 무관). UNKNOWN 은 반투명 회색.
 * - 등급 표현: WATCH = 노란색 외곽선, WARNING = 빨간색 외곽선(+ 같은 색의 은은한 테두리 띠),
 *   UNKNOWN = 점선 외곽선 + "?" 기호. 색 외에 아이콘(! / !! / ?)과 선 모양으로도 구분한다.
 * - 모든 도형 점(곡선 제어점 포함)이 heading 회전과 [ViewportTransform] 을 거치므로
 *   계약의 위치·크기·방향이 그대로 유지된다(화면 폭 보정은 [VehicleGeometry.displayWidthM]).
 *
 * 위험 영역·예측 궤적을 차량 위에 겹치기 위해 차체([drawVehicleBody])와
 * 아이콘·라벨([drawVehicleAnnotations])을 나눠 그린다. [drawVehicle] 은 둘을 연달아 호출한다.
 */
object VehicleRenderer {

    private val EGO_BODY = Color(RiskStyle.EGO.fill)
    private val VEHICLE_BODY = Color(RiskStyle.NORMAL.fill)
    private val GLASS_DARK = Color(0xFF10141A)
    private val LIGHT = Color(0xFFE8EEF5)

    /** 기존 API 호환: 차체 + 아이콘·라벨을 한 번에 그린다. */
    fun DrawScope.drawVehicle(v: SceneVehicle, t: ViewportTransform, textMeasurer: TextMeasurer?, showId: Boolean) {
        drawVehicleBody(v, t)
        drawVehicleAnnotations(v, t, textMeasurer, showId)
    }

    /**
     * 그리기 패스. 같은 색으로 칠하는 부품은 Path 하나로 합쳐 그리기 호출 수를 줄인다
     * (사이드미러는 차체와 같은 색으로 차체 패스에 포함). 차량당 최대 5회.
     */
    private val PASSES: List<Pair<PartRole, Set<PartRole>>> = listOf(
        PartRole.BODY to setOf(PartRole.MIRROR, PartRole.BODY),
        PartRole.ROOF to setOf(PartRole.ROOF),
        PartRole.GLASS to setOf(PartRole.GLASS),
        PartRole.DETAIL_LINE to setOf(PartRole.DETAIL_LINE),
        PartRole.LIGHT to setOf(PartRole.LIGHT),
    )

    /** 화면상 차량 길이가 이보다 작으면 지붕·디테일·등을 생략(LOD). */
    private const val DETAIL_MIN_LENGTH_PX = 40f

    /**
     * 차종별 정규화 좌표(a, b) Path 캐시. 앱 실행 중 한 번만 만든다.
     * 매 프레임 Path 를 새로 만들지 않고 차량마다 행렬만 바꿔 그리므로, 사각형 렌더러 대비 비용 증가를 줄인다.
     */
    private val shapeCache: Map<VehicleBodyType, List<Pair<PartRole, Path>>> by lazy {
        VehicleBodyType.entries.associateWith { type ->
            val parts = VehicleShapes.of(type)
            PASSES.mapNotNull { (pass, roles) ->
                val ofPass = parts.filter { it.role in roles }
                if (ofPass.isEmpty()) null else pass to Path().apply { ofPass.forEach { appendNormalized(it) } }
            }
        }
    }

    /** 패스별 색(자차/일반 × 역할)을 미리 계산. */
    private fun partColors(base: Color): Map<PartRole, Color> = mapOf(
        PartRole.BODY to base,
        PartRole.ROOF to lerp(base, Color.White, 0.16f),
        PartRole.GLASS to lerp(base, GLASS_DARK, 0.68f),
        PartRole.DETAIL_LINE to lerp(base, Color.Black, 0.45f),
        PartRole.LIGHT to LIGHT.copy(alpha = 0.85f),
    )
    private val EGO_COLORS by lazy { partColors(EGO_BODY) }
    private val VEHICLE_COLORS by lazy { partColors(VEHICLE_BODY) }

    private fun Path.appendNormalized(part: ShapePart) {
        for (op in part.ops) when (op) {
            is ShapeOp.Move -> moveTo(op.a, op.b)
            is ShapeOp.Line -> lineTo(op.a, op.b)
            is ShapeOp.Quad -> quadraticTo(op.ca, op.cb, op.a, op.b)
            ShapeOp.Close -> close()
        }
    }

    private val matrix = Matrix()

    // ---------------------------------------------------------------- 스프라이트 캐시
    /** 미러 포함 최대 폭(정규화 b 의 절댓값). */
    private const val MAX_B = 0.62f
    private const val SPRITE_PAD = 2f

    private data class SpriteKey(
        val type: VehicleBodyType, val ego: Boolean, val alphaPct: Int,
        val detailed: Boolean, val lenPx: Int, val widPx: Int,
    )

    /** 최근 사용 64개만 유지(뷰포트·차종 조합 수는 보통 10개 미만). */
    private val sprites = object : LinkedHashMap<SpriteKey, ImageBitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SpriteKey, ImageBitmap>?) = size > 64
    }
    private val spriteScope = CanvasDrawScope()

    /**
     * 차종·색·화면 크기별 차량 그림을 한 번만 래스터화한다(외부 이미지 아님: [VehicleShapes] 벡터 도형).
     * 비트맵 좌표: u = 길이 방향(뒤 → 앞), v = 폭 방향(왼쪽 → 오른쪽).
     */
    private fun sprite(key: SpriteKey, colors: Map<PartRole, Color>, alpha: Float): ImageBitmap = sprites.getOrPut(key) {
        val len = key.lenPx.toFloat(); val wid = key.widPx.toFloat()
        val w = ceil(len + 2 * SPRITE_PAD).toInt()
        val h = ceil(2 * MAX_B * wid + 2 * SPRITE_PAD).toInt()
        val img = ImageBitmap(w, h)
        val n = Matrix().apply {
            val v = values
            v[Matrix.ScaleX] = len; v[Matrix.ScaleY] = -wid
            v[Matrix.TranslateX] = SPRITE_PAD + 0.5f * len
            v[Matrix.TranslateY] = SPRITE_PAD + MAX_B * wid
        }
        spriteScope.draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
            withTransform({ transform(n) }) {
                for ((pass, path) in shapeCache.getValue(key.type)) {
                    if (!key.detailed && pass != PartRole.BODY && pass != PartRole.GLASS) continue
                    val c = colors.getValue(pass)
                    drawPath(path, if (alpha == 1f) c else c.copy(alpha = c.alpha * alpha))
                }
            }
        }
        img
    }

    fun DrawScope.drawVehicleBody(v: SceneVehicle, t: ViewportTransform) {
        val colors = if (v.isEgo) EGO_COLORS else VEHICLE_COLORS
        val detailed = v.lengthM * t.pxPerMeterY >= DETAIL_MIN_LENGTH_PX
        val partAlpha = when { v.stale -> 0.45f; v.level == DisplayLevel.UNKNOWN -> 0.35f; else -> 1f }
        // 외곽선·테두리 띠는 화면 좌표 Path 가 필요(선 두께 일정). 일반 차량은 생략해 비용을 줄인다.
        val needsOutline = v.level != DisplayLevel.NORMAL
        val frame = BodyFrame(v, t)
        val bodyPath = if (needsOutline) frame.path(VehicleShapes.of(v.bodyType).first { it.role == PartRole.BODY }) else null

        // 1) 주의·경고 테두리 띠(차체 밖으로만 보이도록 먼저 그림)
        if (bodyPath != null && (v.level == DisplayLevel.WATCH || v.level == DisplayLevel.WARNING)) {
            val risk = Color(RiskStyle.of(v.level).fill)
            drawPath(bodyPath, risk.copy(alpha = 0.32f), style = Stroke(px(if (v.level == DisplayLevel.WARNING) 11f else 9f), join = StrokeJoin.Round))
        }

        // 2) 자동차 그래픽: 스프라이트(직접 만든 벡터 도형을 한 번 래스터화) 1장 + 차량별 affine 행렬
        val lenPx = (v.lengthM * t.pxPerMeterY).roundToInt().coerceAtLeast(4)
        val widPx = (frame.wid * t.pxPerMeterX).roundToInt().coerceAtLeast(3)
        val sprite = sprite(SpriteKey(v.bodyType, v.isEgo, (partAlpha * 100).roundToInt(), detailed, lenPx, widPx), colors, partAlpha)
        frame.loadSpriteMatrix(matrix, lenPx.toFloat(), widPx.toFloat(), SPRITE_PAD)
        withTransform({ transform(matrix) }) { drawImage(sprite) }

        // 3) 외곽선
        if (bodyPath != null) when (v.level) {
            DisplayLevel.EGO -> drawPath(bodyPath, Color(RiskStyle.EGO.stroke), style = Stroke(px(1.5f), join = StrokeJoin.Round))
            DisplayLevel.WATCH -> drawPath(bodyPath, Color(RiskStyle.WATCH.fill), style = Stroke(px(3f), join = StrokeJoin.Round))
            DisplayLevel.WARNING -> drawPath(bodyPath, Color(RiskStyle.WARNING.fill), style = Stroke(px(3.5f), join = StrokeJoin.Round))
            // 판단 보류: 파란 점선. 단, 데이터가 오래된 경우(stale)는 회색 점선으로 따로 표시
            DisplayLevel.UNKNOWN -> drawPath(bodyPath, if (v.stale) STALE_OUTLINE else Color(RiskStyle.UNKNOWN.stroke),
                style = Stroke(px(if (v.stale) 1.5f else 2f), join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(if (v.stale) floatArrayOf(px(3f), px(4f)) else floatArrayOf(px(6f), px(4f)))))
            DisplayLevel.NORMAL -> Unit
        }

        // 4) 원인 차량 표시: 색이 아닌 점선 이중 테두리
        if (v.isSource) {
            val outer = expand(frame.corners, v.center, 0.6f)
            drawPath(polygon(outer, t), Color(RiskStyle.WATCH.fill),
                style = Stroke(px(1.5f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(px(4f), px(3f)))))
        }
    }

    private val STALE_OUTLINE = Color(0xFF8A919C)
    private val CHIP_BG = Color(0xE614171C)

    /**
     * 차량 라벨 칩(차량 오른쪽, 화면 밖으로 나가면 왼쪽). 영역·궤적·연결선보다 위에 그린다.
     * - 주행 모드(showId = false): 위험 차량만 [아이콘] 등급 짧게("경고"), 일반 차량은 표시 안 함 → 간결
     * - 정차 후 상세(showId = true): 모든 차량 ID, 위험 차량은 "ID · 등급"
     * - 자차: "자차", 원인 차량: 아래에 "원인" 칩 추가, 오래된 데이터: "지연"
     * 색·아이콘 모양·글자를 함께 써서 색만으로 등급을 전달하지 않는다.
     */
    fun DrawScope.drawVehicleAnnotations(v: SceneVehicle, t: ViewportTransform, textMeasurer: TextMeasurer?, showId: Boolean) {
        if (textMeasurer == null) {
            // 글자를 못 그리는 환경(일부 오프스크린)에서는 아이콘만
            val style = RiskStyle.of(v.level)
            if (style.icon != RiskIcon.NONE) {
                val c = t.toScreen(v.center)
                with(RiskOverlay) { drawRiskIcon(style, Offset(c.x + px(24f), c.y), null) }
            }
            return
        }
        val w = VehicleGeometry.displayWidthM(v.lengthM, v.widthM, t)
        val corners = VehicleGeometry.corners(v.center.x, v.center.y, v.headingRad, v.lengthM, w)
        val c = t.toScreen(v.center)
        val right = corners.maxOf { t.toScreen(it).x }
        val left = corners.minOf { t.toScreen(it).x }

        if (v.isEgo) {
            val layout = textMeasurer.measure("자차", TextStyle(color = RadarColors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            drawText(layout, topLeft = Offset(left - layout.size.width - px(6f), c.y - layout.size.height / 2f))
            return
        }

        val risky = v.level != DisplayLevel.NORMAL
        // 일반 차량의 ID 는 칩 없이 글자만(차량이 많을 때 그리기 비용을 기존 수준으로 유지)
        if (!risky && !v.stale && showId) {
            val layout = textMeasurer.measure(v.id, TextStyle(color = RadarColors.subtext, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            var x = right + px(6f)
            if (x + layout.size.width > size.width - px(2f)) x = left - px(6f) - layout.size.width
            drawText(layout, topLeft = Offset(x, c.y - layout.size.height / 2f))
            if (v.isSource) drawChip(textMeasurer, "원인", Color(RiskStyle.WATCH.fill), CHIP_BG, Color(RiskStyle.WATCH.fill),
                dashed = true, icon = null, anchorRight = right, anchorLeft = left, centerY = c.y + layout.size.height / 2f + px(14f))
            return
        }
        val text: String? = when {
            v.stale -> if (showId) "${v.id} · 지연" else "지연"
            risky -> if (showId) "${v.id} · ${RiskStyle.of(v.level).label}" else RiskStyle.of(v.level).label
            showId -> v.id
            else -> null
        }
        var chipBottom = c.y
        if (text != null) {
            val style = RiskStyle.of(v.level)
            val showIcon = risky && !v.stale
            val (bg, fg, border) = when {
                v.stale -> Triple(CHIP_BG, RadarColors.subtext, STALE_OUTLINE)
                v.level == DisplayLevel.WARNING -> Triple(Color(style.fill), Color.White, Color(style.fill))
                v.level == DisplayLevel.WATCH -> Triple(CHIP_BG, Color(style.fill), Color(style.fill))
                v.level == DisplayLevel.UNKNOWN -> Triple(CHIP_BG, Color(style.stroke), Color(style.stroke))
                else -> Triple(CHIP_BG, RadarColors.text, Color(0xFF3A414C))
            }
            chipBottom = drawChip(textMeasurer, text, fg, bg, border,
                dashed = v.level == DisplayLevel.UNKNOWN, icon = if (showIcon) style else null,
                anchorRight = right, anchorLeft = left, centerY = c.y)
        }
        if (v.isSource) {
            drawChip(textMeasurer, "원인", Color(RiskStyle.WATCH.fill), CHIP_BG, Color(RiskStyle.WATCH.fill),
                dashed = true, icon = null, anchorRight = right, anchorLeft = left,
                centerY = if (text != null) chipBottom + px(14f) else c.y)
        }
    }

    /** 둥근 칩 하나를 그리고 칩 아래쪽 y 를 돌려준다. */
    private fun DrawScope.drawChip(
        tm: TextMeasurer, text: String, fg: Color, bg: Color, border: Color, dashed: Boolean, icon: RiskStyle?,
        anchorRight: Float, anchorLeft: Float, centerY: Float,
    ): Float {
        val layout = tm.measure(text, TextStyle(color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        val padH = px(6f); val padV = px(3f)
        val iconW = if (icon != null) px(20f) else 0f
        val wChip = layout.size.width + padH * 2 + iconW
        val hChip = layout.size.height + padV * 2
        var x = anchorRight + px(6f)
        if (x + wChip > size.width - px(2f)) x = anchorLeft - px(6f) - wChip
        val top = centerY - hChip / 2f
        val corner = androidx.compose.ui.geometry.CornerRadius(hChip / 2f)
        drawRoundRect(bg, Offset(x, top), androidx.compose.ui.geometry.Size(wChip, hChip), corner)
        drawRoundRect(border, Offset(x, top), androidx.compose.ui.geometry.Size(wChip, hChip), corner,
            style = Stroke(px(1.2f), pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(px(4f), px(3f))) else null))
        if (icon != null) with(RiskOverlay) { drawRiskIcon(icon, Offset(x + padH + px(8f), centerY), tm, radiusDp = 8f) }
        drawText(layout, topLeft = Offset(x + padH + iconW, top + padV))
        return top + hChip
    }

    /** 정규화 도형 → 화면 Path. 차량 heading 회전 + 화면 변환(선형)이라 2차 곡선 제어점도 그대로 변환된다. */
    private class BodyFrame(v: SceneVehicle, private val t: ViewportTransform) {
        private val cx = v.center.x
        private val cy = v.center.y
        private val len = v.lengthM
        val wid = VehicleGeometry.displayWidthM(v.lengthM, v.widthM, t)
        private val ux = cos(v.headingRad)
        private val uy = sin(v.headingRad)
        val corners: List<Point2> = VehicleGeometry.corners(cx, cy, v.headingRad, len, wid)

        private fun sx(a: Float, b: Float): Float = t.toScreen(cx + ux * a * len - uy * b * wid, 0f).x
        private fun sy(a: Float, b: Float): Float = t.toScreen(0f, cy + uy * a * len + ux * b * wid).y

        /**
         * 정규화 좌표 (a, b) → 화면 좌표 affine 행렬. sx/sy 와 같은 변환이다.
         *   X = tx + a·(L·ux·kx) + b·(−W·uy·kx)
         *   Y = ty + a·(−L·uy·ky) + b·(−W·ux·ky)
         */
        /**
         * 스프라이트 비트맵 픽셀 (u, v) → 화면 행렬.
         *   a = (u − pad)/lenPx − 0.5,  b = MAX_B − (v − pad)/widPx  를 [loadMatrix] 의 변환에 대입한 것.
         * 비트맵 해상도와 무관하게 화면 크기는 계약의 길이·폭으로 정확히 맞춰진다.
         */
        fun loadSpriteMatrix(m: Matrix, lenPx: Float, widPx: Float, pad: Float) {
            val kx = t.pxPerMeterX
            val ky = t.pxPerMeterY
            val m00 = len * ux * kx; val m01 = -wid * uy * kx
            val m10 = -len * uy * ky; val m11 = -wid * ux * ky
            val ca = -pad / lenPx - 0.5f
            val cb = MAX_B + pad / widPx
            m.reset()
            val v = m.values
            v[Matrix.ScaleX] = m00 / lenPx
            v[Matrix.SkewX] = -m01 / widPx
            v[Matrix.SkewY] = m10 / lenPx
            v[Matrix.ScaleY] = -m11 / widPx
            v[Matrix.TranslateX] = t.toScreen(cx, 0f).x + m00 * ca + m01 * cb
            v[Matrix.TranslateY] = t.toScreen(0f, cy).y + m10 * ca + m11 * cb
        }

        fun loadMatrix(m: Matrix) {
            val kx = t.pxPerMeterX
            val ky = t.pxPerMeterY
            m.reset()
            val v = m.values
            v[Matrix.ScaleX] = len * ux * kx
            v[Matrix.SkewX] = -wid * uy * kx
            v[Matrix.SkewY] = -len * uy * ky
            v[Matrix.ScaleY] = -wid * ux * ky
            v[Matrix.TranslateX] = t.toScreen(cx, 0f).x
            v[Matrix.TranslateY] = t.toScreen(0f, cy).y
        }

        fun path(part: ShapePart): Path = Path().apply {
            for (op in part.ops) when (op) {
                is ShapeOp.Move -> moveTo(sx(op.a, op.b), sy(op.a, op.b))
                is ShapeOp.Line -> lineTo(sx(op.a, op.b), sy(op.a, op.b))
                is ShapeOp.Quad -> quadraticTo(sx(op.ca, op.cb), sy(op.ca, op.cb), sx(op.a, op.b), sy(op.a, op.b))
                ShapeOp.Close -> close()
            }
        }
    }

    internal fun polygon(points: List<Point2>, t: ViewportTransform): Path = Path().apply {
        points.forEachIndexed { i, p ->
            val s = t.toScreen(p)
            if (i == 0) moveTo(s.x, s.y) else lineTo(s.x, s.y)
        }
        close()
    }

    private fun expand(ps: List<Point2>, c: Point2, m: Float) = ps.map { p ->
        val dx = p.x - c.x; val dy = p.y - c.y
        val len = kotlin.math.hypot(dx, dy).coerceAtLeast(0.01f)
        Point2(p.x + dx / len * m, p.y + dy / len * m)
    }
}
