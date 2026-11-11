package ai.prism.visualization

import ai.prism.visualization.model.RiskFrame
import ai.prism.visualization.model.VehicleFrame
import ai.prism.visualization.model.VehicleState
import ai.prism.visualization.rendering.PartRole
import ai.prism.visualization.rendering.SceneBuilder
import ai.prism.visualization.rendering.ShapeOp
import ai.prism.visualization.rendering.VehicleBodyType
import ai.prism.visualization.rendering.VehicleShapes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class VehicleShapesTest {

    @Test
    fun classifyByDimensions() {
        assertEquals(VehicleBodyType.SEDAN, VehicleBodyType.classify(4.6f, 1.9f))
        assertEquals(VehicleBodyType.SEDAN, VehicleBodyType.classify(5.0f, 2.0f))   // 팀 시뮬레이션 일반 승용차
        assertEquals(VehicleBodyType.SUV, VehicleBodyType.classify(5.2f, 2.1f))
        assertEquals(VehicleBodyType.SUV, VehicleBodyType.classify(4.9f, 2.06f))
        assertEquals(VehicleBodyType.TRUCK, VehicleBodyType.classify(10f, 2.5f))   // 팀 시뮬레이션 화물차
        assertEquals(VehicleBodyType.TRUCK, VehicleBodyType.classify(12f, 2.5f))
        assertEquals(VehicleBodyType.TRUCK, VehicleBodyType.classify(7f, 2.3f))
    }

    @Test
    fun shapesStayInsideVehicleFootprint_soPositionAndSizeArePreserved() {
        for (type in VehicleBodyType.entries) {
            val parts = VehicleShapes.of(type)
            assertEquals("$type has exactly one body", 1, parts.count { it.role == PartRole.BODY })
            for (part in parts) {
                val pts = part.ops.flatMap { op ->
                    when (op) {
                        is ShapeOp.Move -> listOf(op.a to op.b)
                        is ShapeOp.Line -> listOf(op.a to op.b)
                        is ShapeOp.Quad -> listOf(op.ca to op.cb, op.a to op.b)
                        ShapeOp.Close -> emptyList()
                    }
                }
                // 길이 방향은 차체 길이를 넘지 않음, 폭 방향은 미러만 차체 밖(최대 0.62)
                val maxB = if (part.role == PartRole.MIRROR) 0.62f else 0.5f
                pts.forEach { (a, b) ->
                    assertTrue("$type ${part.role} a=$a", abs(a) <= 0.5f + 1e-6f)
                    assertTrue("$type ${part.role} b=$b", abs(b) <= maxB + 1e-6f)
                }
            }
            // 차체 외곽은 길이·폭 전체를 사용(크기 유지)
            val body = parts.first { it.role == PartRole.BODY }.ops
            val aVals = body.mapNotNull { (it as? ShapeOp.Line)?.a ?: (it as? ShapeOp.Move)?.a ?: (it as? ShapeOp.Quad)?.a }
            val bVals = body.mapNotNull { (it as? ShapeOp.Line)?.b ?: (it as? ShapeOp.Move)?.b ?: (it as? ShapeOp.Quad)?.b }
            assertEquals(0.5f, aVals.max(), 1e-6f); assertEquals(-0.5f, aVals.min(), 1e-6f)
            assertEquals(0.5f, bVals.max(), 1e-6f); assertEquals(-0.5f, bVals.min(), 1e-6f)
        }
    }

    @Test
    fun shapesAreLeftRightSymmetric() {
        for (type in VehicleBodyType.entries) {
            val pts = VehicleShapes.of(type).flatMap { p ->
                p.ops.flatMap { op ->
                    when (op) {
                        is ShapeOp.Move -> listOf(op.a to op.b)
                        is ShapeOp.Line -> listOf(op.a to op.b)
                        is ShapeOp.Quad -> listOf(op.ca to op.cb, op.a to op.b)
                        ShapeOp.Close -> emptyList()
                    }
                }
            }.map { (a, b) -> Math.round(a * 1000) to Math.round(b * 1000) }.toSet()
            pts.forEach { (a, b) -> assertTrue("$type missing mirror of ($a,$b)", (a to -b) in pts) }
        }
    }

    @Test
    fun shapesDifferPerType() {
        assertNotEquals(VehicleShapes.SEDAN, VehicleShapes.SUV)
        assertNotEquals(VehicleShapes.SUV, VehicleShapes.TRUCK)
    }

    @Test
    fun sceneBuilderAssignsBodyType() {
        val fwd = (PI / 2).toFloat()
        fun v(id: String, l: Float, w: Float) = VehicleState(id, 0f, 10f, 0f, 20f, fwd, l, w, "L1")
        val vf = VehicleFrame(0, "ego", listOf(v("ego", 4.6f, 1.9f), v("S", 5.2f, 2.1f), v("T", 12f, 2.5f), v("C", 4.4f, 1.8f)))
        val s = SceneBuilder.build(vf, null, RiskFrame(0, emptyList()))
        val types = s.vehicles.associate { it.id to it.bodyType }
        assertEquals(VehicleBodyType.SEDAN, types["ego"])
        assertEquals(VehicleBodyType.SUV, types["S"])
        assertEquals(VehicleBodyType.TRUCK, types["T"])
        assertEquals(VehicleBodyType.SEDAN, types["C"])
    }
}
