package com.prism.vehicle.replay

/** 재생 위치 상태. 순수 값 객체라 UI·테스트 어디서든 같은 규칙으로 움직인다. */
data class ReplayClock(
    val positionMs: Long = 0,
    val durationMs: Long,
    val playing: Boolean = false,
    val speed: Float = 1f,
) {
    init { require(speed in SPEEDS) { "speed must be one of $SPEEDS" } }

    val finished: Boolean get() = positionMs >= durationMs

    /** 실제 경과시간(ms)만큼 진행. 끝에 닿으면 멈춘다. */
    fun advance(realElapsedMs: Long): ReplayClock {
        if (!playing || realElapsedMs <= 0) return this
        val next = (positionMs + realElapsedMs * speed).toLong().coerceAtMost(durationMs)
        return copy(positionMs = next, playing = next < durationMs)
    }

    fun play() = copy(playing = true, positionMs = if (finished) 0 else positionMs)
    fun pause() = copy(playing = false)
    fun seek(toMs: Long) = copy(positionMs = toMs.coerceIn(0, durationMs))
    fun withSpeed(s: Float) = copy(speed = s)

    companion object {
        val SPEEDS = listOf(0.5f, 1f, 2f)
    }
}
