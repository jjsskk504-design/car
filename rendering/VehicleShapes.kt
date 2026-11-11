package ai.prism.visualization.rendering

/**
 * 탑뷰 자동차 그래픽(직접 제작한 벡터 도형, 외부 이미지·폰트 아이콘 미사용).
 *
 * 좌표는 차량 기준 정규화 좌표다.
 *  - a: 길이 방향, 앞 = +0.5, 뒤 = -0.5   (× length_m)
 *  - b: 폭 방향,  왼쪽 = +0.5, 오른쪽 = -0.5 (× 화면용 폭, [VehicleGeometry.displayWidthM])
 * 렌더러가 heading 회전과 화면 변환을 적용하므로 위치·크기·방향은 계약 값 그대로 유지된다.
 * 사이드미러만 차체 폭 밖(|b| ≤ 0.62)으로 나간다.
 */
enum class VehicleBodyType {
    SEDAN, SUV, TRUCK;

    companion object {
        /**
         * VehicleFrame v1 에 차종 필드가 없어 치수로 추정한다(계약 확인 요청 문서의 vehicle_class 제안 참고).
         * 길이 7 m 이상 = 대형 화물차, 길이 5.15 m 이상 또는 폭 2.05 m 이상 = SUV, 나머지 = 승용차.
         * (팀 시뮬레이션 엔진의 일반 승용차가 5.0 × 2.0 m 이므로 그보다 큰 차량만 SUV 로 본다)
         */
        fun classify(lengthM: Float, widthM: Float): VehicleBodyType = when {
            lengthM >= 7f -> TRUCK
            lengthM >= 5.15f || widthM >= 2.05f -> SUV
            else -> SEDAN
        }
    }
}

sealed interface ShapeOp {
    data class Move(val a: Float, val b: Float) : ShapeOp
    data class Line(val a: Float, val b: Float) : ShapeOp
    data class Quad(val ca: Float, val cb: Float, val a: Float, val b: Float) : ShapeOp
    data object Close : ShapeOp
}

/**
 * 부품 역할에 따라 렌더러가 색을 정한다. 모든 부품은 채우기(fill) 도형이다.
 * (선은 차량 크기 행렬로 변환되면 두께가 불균일해지므로 DETAIL_LINE 도 얇은 사각형으로 정의)
 */
enum class PartRole { BODY, MIRROR, GLASS, ROOF, LIGHT, DETAIL_LINE }

data class ShapePart(val role: PartRole, val ops: List<ShapeOp>)

object VehicleShapes {

    private class Builder {
        val parts = ArrayList<ShapePart>()
        private var ops = ArrayList<ShapeOp>()
        fun m(a: Float, b: Float) { ops.add(ShapeOp.Move(a, b)) }
        fun l(a: Float, b: Float) { ops.add(ShapeOp.Line(a, b)) }
        fun q(ca: Float, cb: Float, a: Float, b: Float) { ops.add(ShapeOp.Quad(ca, cb, a, b)) }
        fun z() { ops.add(ShapeOp.Close) }
        fun part(role: PartRole, block: Builder.() -> Unit) {
            ops = ArrayList(); block(); parts.add(ShapePart(role, ops))
        }
        /** 좌우 대칭 부품(b 부호 반전) 두 개를 만든다. */
        fun mirrored(role: PartRole, block: Builder.(sign: Float) -> Unit) {
            part(role) { block(1f) }
            part(role) { block(-1f) }
        }
        /** 길이 위치 a 에서 폭 방향(b1→b2)으로 가로지르는 얇은 띠(반두께 th, 길이 비율). */
        fun barAcross(a: Float, b1: Float, b2: Float, th: Float) { m(a + th, b1); l(a + th, b2); l(a - th, b2); l(a - th, b1); z() }
        /** 폭 위치 b 에서 길이 방향(a1→a2)으로 이어지는 얇은 띠(반두께 th, 폭 비율). */
        fun barAlong(b: Float, a1: Float, a2: Float, th: Float) { m(a1, b + th); l(a2, b + th); l(a2, b - th); l(a1, b - th); z() }
    }

    private fun build(block: Builder.() -> Unit): List<ShapePart> = Builder().apply(block).parts

    /** 승용차: 둥근 앞부분, 긴 보닛, 앞·뒤 유리와 지붕. */
    val SEDAN: List<ShapePart> = build {
        mirrored(PartRole.MIRROR) { s -> m(0.20f, 0.49f * s); l(0.16f, 0.62f * s); l(0.11f, 0.62f * s); l(0.12f, 0.49f * s); z() }
        part(PartRole.BODY) {
            m(0.36f, 0.5f); l(-0.42f, 0.5f); q(-0.5f, 0.5f, -0.5f, 0.36f); l(-0.5f, -0.36f)
            q(-0.5f, -0.5f, -0.42f, -0.5f); l(0.36f, -0.5f); q(0.5f, -0.47f, 0.5f, -0.22f)
            l(0.5f, 0.22f); q(0.5f, 0.47f, 0.36f, 0.5f); z()
        }
        part(PartRole.GLASS) { m(0.29f, 0.40f); q(0.33f, 0f, 0.29f, -0.40f); l(0.10f, -0.35f); l(0.10f, 0.35f); z() }
        part(PartRole.ROOF) { m(0.10f, 0.35f); l(-0.22f, 0.35f); l(-0.22f, -0.35f); l(0.10f, -0.35f); z() }
        part(PartRole.GLASS) { m(-0.22f, 0.34f); l(-0.36f, 0.38f); q(-0.39f, 0f, -0.36f, -0.38f); l(-0.22f, -0.34f); z() }
        mirrored(PartRole.LIGHT) { s -> m(0.44f, 0.44f * s); l(0.49f, 0.36f * s); l(0.495f, 0.22f * s); l(0.46f, 0.26f * s); z() }
    }

    /** SUV: 각진 앞부분, 짧은 보닛, 긴 지붕과 루프랙, 작은 뒷유리. */
    val SUV: List<ShapePart> = build {
        mirrored(PartRole.MIRROR) { s -> m(0.23f, 0.49f * s); l(0.19f, 0.62f * s); l(0.14f, 0.62f * s); l(0.15f, 0.49f * s); z() }
        part(PartRole.BODY) {
            m(0.40f, 0.5f); l(-0.46f, 0.5f); q(-0.5f, 0.5f, -0.5f, 0.44f); l(-0.5f, -0.44f)
            q(-0.5f, -0.5f, -0.46f, -0.5f); l(0.40f, -0.5f); q(0.5f, -0.49f, 0.5f, -0.34f)
            l(0.5f, 0.34f); q(0.5f, 0.49f, 0.40f, 0.5f); z()
        }
        part(PartRole.GLASS) { m(0.31f, 0.41f); q(0.34f, 0f, 0.31f, -0.41f); l(0.16f, -0.37f); l(0.16f, 0.37f); z() }
        part(PartRole.ROOF) { m(0.16f, 0.38f); l(-0.40f, 0.38f); l(-0.40f, -0.38f); l(0.16f, -0.38f); z() }
        part(PartRole.DETAIL_LINE) { barAlong(0.30f, 0.12f, -0.36f, 0.022f); barAlong(-0.30f, 0.12f, -0.36f, 0.022f) }
        part(PartRole.GLASS) { m(-0.40f, 0.36f); l(-0.46f, 0.38f); l(-0.46f, -0.38f); l(-0.40f, -0.36f); z() }
        mirrored(PartRole.LIGHT) { s -> m(0.46f, 0.45f * s); l(0.495f, 0.40f * s); l(0.497f, 0.26f * s); l(0.47f, 0.28f * s); z() }
    }

    /** 대형 화물차: 앞쪽 캡(앞유리·캡 지붕) + 분리선 + 화물칸(패널선). */
    val TRUCK: List<ShapePart> = build {
        mirrored(PartRole.MIRROR) { s -> m(0.45f, 0.49f * s); l(0.43f, 0.62f * s); l(0.40f, 0.62f * s); l(0.41f, 0.49f * s); z() }
        part(PartRole.BODY) {
            m(0.46f, 0.5f); l(-0.5f, 0.5f); l(-0.5f, -0.5f); l(0.46f, -0.5f)
            q(0.5f, -0.5f, 0.5f, -0.42f); l(0.5f, 0.42f); q(0.5f, 0.5f, 0.46f, 0.5f); z()
        }
        part(PartRole.ROOF) { m(0.46f, 0.42f); l(0.33f, 0.42f); l(0.33f, -0.42f); l(0.46f, -0.42f); z() }
        part(PartRole.GLASS) { m(0.495f, 0.40f); l(0.495f, -0.40f); l(0.465f, -0.38f); l(0.465f, 0.38f); z() }
        part(PartRole.ROOF) { m(0.30f, 0.46f); l(-0.48f, 0.46f); l(-0.48f, -0.46f); l(0.30f, -0.46f); z() }
        part(PartRole.DETAIL_LINE) {
            barAcross(0.315f, 0.5f, -0.5f, 0.007f)
            for (a in floatArrayOf(0.10f, -0.10f, -0.30f)) barAcross(a, 0.46f, -0.46f, 0.004f)
        }
        mirrored(PartRole.LIGHT) { s -> m(0.49f, 0.46f * s); l(0.50f, 0.40f * s); l(0.50f, 0.30f * s); l(0.49f, 0.32f * s); z() }
    }

    fun of(type: VehicleBodyType): List<ShapePart> = when (type) {
        VehicleBodyType.SEDAN -> SEDAN
        VehicleBodyType.SUV -> SUV
        VehicleBodyType.TRUCK -> TRUCK
    }
}
