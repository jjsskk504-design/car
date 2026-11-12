package ai.prism.visualization.rendering

import ai.prism.visualization.model.Point2

/**
 * 볼록 다각형 겹침 판정(분리축 정리, SAT). risk_zones 는 차량 박스 형태의 볼록 다각형이라 정확하다.
 * 오목한 다각형이 오면 볼록 껍질 기준으로 보수적(겹친다고 볼 가능성이 큼)으로 판정된다.
 */
object ConvexOverlap {
    fun overlaps(a: List<Point2>, b: List<Point2>): Boolean {
        if (a.size < 3 || b.size < 3) return false
        return !hasSeparatingAxis(a, b) && !hasSeparatingAxis(b, a)
    }

    private fun hasSeparatingAxis(p: List<Point2>, q: List<Point2>): Boolean {
        for (i in p.indices) {
            val p1 = p[i]; val p2 = p[(i + 1) % p.size]
            val nx = -(p2.y - p1.y); val ny = p2.x - p1.x
            if (nx == 0f && ny == 0f) continue
            var minP = Float.MAX_VALUE; var maxP = -Float.MAX_VALUE
            for (v in p) { val d = v.x * nx + v.y * ny; if (d < minP) minP = d; if (d > maxP) maxP = d }
            var minQ = Float.MAX_VALUE; var maxQ = -Float.MAX_VALUE
            for (v in q) { val d = v.x * nx + v.y * ny; if (d < minQ) minQ = d; if (d > maxQ) maxQ = d }
            if (maxP < minQ || maxQ < minP) return true
        }
        return false
    }
}
