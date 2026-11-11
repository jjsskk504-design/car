package com.prism.simulation.visualization

import com.prism.simulation.exporters.readJsonl
import com.prism.simulation.exporters.str
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.FileImageOutputStream

/*
 * 생성된 VehicleFrame JSONL 을 2D 로 재생한다 (자차 기준 탑뷰, Java2D 만 사용).
 * 사용: ./gradlew replay  (= samples -> docs/replay)
 *   -> <name>.png (시간별 스냅샷), <name>.gif (재생 영상)
 */

private val SNAP_S = listOf(0, 3, 5, 7, 9, 12)
private const val FRAME_STRIDE = 2 // GIF 용량 절감: 100ms 프레임 중 2개마다 1장 (= 5fps 실시간)
private const val X_MIN = -9.0
private const val X_MAX = 9.0
private const val Y_MIN = -45.0
private const val Y_MAX = 75.0
private const val HEADER = 46

/** t_ms 시점에 진행 중이거나 막 발생한(1초 이내) 강제 이벤트 표시용 문자열. */
fun activeEvents(m: JsonObject, tMs: Int): List<String> = m.getValue("event_annotation").jsonArray.map { it.jsonObject }.filter { e ->
    val t = e.getValue("t_ms").jsonPrimitive.int
    val end = e["t_end_ms"]?.jsonPrimitive?.int ?: (t + 1000)
    tMs in t until end
}.map { "[forced] ${it.str("type")} ${it.str("vehicle_id")}" }

/** 프레임 하나를 (ox, oy) 위치의 패널에 그린다. scale = 화면 px / m. */
fun draw(g: Graphics2D, frame: JsonObject, active: List<String>, ox: Int, oy: Int, scale: Double) {
    val w = ((X_MAX - X_MIN) * scale).toInt()
    val h = ((Y_MAX - Y_MIN) * scale).toInt()
    fun sx(x: Double) = ox + (x - X_MIN) * scale
    fun sy(y: Double) = oy + HEADER + (Y_MAX - y) * scale // 화면은 아래로 갈수록 y 증가
    g.color = Color.WHITE
    g.fillRect(ox, oy + HEADER, w, h)
    g.color = Color(0xBB, 0xBB, 0xBB)
    g.stroke = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(4f, 4f), 0f)
    for (x in listOf(-6.0, -2.0, 2.0, 6.0)) g.drawLine(sx(x).toInt(), oy + HEADER, sx(x).toInt(), oy + HEADER + h) // 4m 차선 경계 (자차가 차선 중앙이라는 근사)
    g.stroke = BasicStroke(1f)
    val clip = g.clip
    g.clipRect(ox, oy + HEADER, w, h)
    val egoId = frame.str("ego_id")
    for (v in frame.getValue("vehicles").jsonArray.map { it.jsonObject }) {
        fun d(k: String) = v.getValue(k).jsonPrimitive.double
        val len = d("length_m") * scale
        val wid = d("width_m") * scale
        val shape = AffineTransform().apply {
            translate(sx(d("x")), sy(d("y")))
            rotate(-d("heading_rad")) // 화면 y 가 뒤집혀 있으므로 반시계 각도를 음수로
        }.createTransformedShape(Rectangle2D.Double(-len / 2, -wid / 2, len, wid))
        g.color = if (v.str("id") == egoId) Color(0x2B, 0x6C, 0xB0) else Color(0x99, 0x99, 0x99)
        g.fill(shape)
        g.color = Color.BLACK
        g.draw(shape)
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 9)
        g.drawString(v.str("id"), (sx(d("x")) + 1.4 * scale).toFloat(), sy(d("y")).toFloat())
    }
    g.clip = clip
    g.color = Color.BLACK
    g.drawRect(ox, oy + HEADER, w, h)
    g.font = Font(Font.SANS_SERIF, Font.PLAIN, 10)
    val title = listOf("t=%.1fs".format(frame.getValue("timestamp_ms").jsonPrimitive.int / 1000.0)) + active
    title.forEachIndexed { i, line -> g.drawString(line, ox, oy + 12 + i * 11) }
}

private fun canvas(w: Int, h: Int): Pair<BufferedImage, Graphics2D> {
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.color = Color(0xF4, 0xF4, 0xF4)
    g.fillRect(0, 0, w, h)
    return img to g
}

private fun snapshots(frames: List<JsonObject>, m: JsonObject, path: Path) {
    val scale = 5.0
    val panelW = ((X_MAX - X_MIN) * scale).toInt() + 40
    val panelH = ((Y_MAX - Y_MIN) * scale).toInt() + HEADER
    val (img, g) = canvas(panelW * SNAP_S.size + 20, panelH + 40)
    val byT = frames.associateBy { it.getValue("timestamp_ms").jsonPrimitive.int }
    g.color = Color.BLACK
    g.font = Font(Font.SANS_SERIF, Font.BOLD, 12)
    g.drawString("${m.str("scenario")} (ego_local_m: x=right, y=forward) - SIMULATION", 20, 18)
    SNAP_S.forEachIndexed { i, s ->
        val t = minOf(s * 1000, m.getValue("duration_ms").jsonPrimitive.int)
        draw(g, byT.getValue(t), activeEvents(m, t), 20 + i * panelW, 26, scale)
    }
    g.dispose()
    ImageIO.write(img, "png", path.toFile())
}

private fun animate(frames: List<JsonObject>, m: JsonObject, path: Path) {
    val scale = 3.5
    val w = ((X_MAX - X_MIN) * scale).toInt() + 120
    val h = ((Y_MAX - Y_MIN) * scale).toInt() + HEADER + 30
    val delayCs = m.getValue("step_ms").jsonPrimitive.int * FRAME_STRIDE / 10 // GIF 지연 단위는 1/100초
    val writer = ImageIO.getImageWritersByFormatName("gif").next()
    Files.deleteIfExists(path)
    FileImageOutputStream(path.toFile()).use { out ->
        writer.output = out
        writer.prepareWriteSequence(null)
        for (f in frames.filterIndexed { i, _ -> i % FRAME_STRIDE == 0 }) {
            val (img, g) = canvas(w, h)
            g.color = Color.BLACK
            g.font = Font(Font.SANS_SERIF, Font.BOLD, 11)
            g.drawString("${m.str("scenario")} - SIMULATION", 8, 14)
            draw(g, f, activeEvents(m, f.getValue("timestamp_ms").jsonPrimitive.int), 8, 22, scale)
            g.dispose()
            val meta = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(img), null)
            val fmt = meta.nativeMetadataFormatName
            val root = meta.getAsTree(fmt) as IIOMetadataNode
            root.appendChild(IIOMetadataNode("GraphicControlExtension").apply {
                setAttribute("disposalMethod", "none"); setAttribute("userInputFlag", "FALSE")
                setAttribute("transparentColorFlag", "FALSE"); setAttribute("delayTime", "$delayCs")
                setAttribute("transparentColorIndex", "0")
            })
            root.appendChild(IIOMetadataNode("ApplicationExtensions").apply {
                appendChild(IIOMetadataNode("ApplicationExtension").apply {
                    setAttribute("applicationID", "NETSCAPE"); setAttribute("authenticationCode", "2.0")
                    userObject = byteArrayOf(1, 0, 0) // 무한 반복
                })
            })
            meta.setFromTree(fmt, root)
            writer.writeToSequence(IIOImage(img, null, meta), null)
        }
        writer.endWriteSequence()
    }
    writer.dispose()
}

fun main(args: Array<String>) {
    val sampleDir = Path.of(args.getOrElse(0) { "samples" })
    val outDir = Path.of(args.getOrElse(1) { "docs/replay" })
    Files.createDirectories(outDir)
    val manifest = Json.parseToJsonElement(Files.readString(sampleDir.resolve("scenario_manifest.json"))).jsonObject
    for (m in manifest.getValue("scenarios").jsonArray.map { it.jsonObject }) {
        val frames = readJsonl(sampleDir.resolve(m.str("file")))
        snapshots(frames, m, outDir.resolve("${m.str("scenario")}.png"))
        animate(frames, m, outDir.resolve("${m.str("scenario")}.gif"))
        println("${outDir.resolve(m.str("scenario"))} (.png, .gif)")
    }
}
