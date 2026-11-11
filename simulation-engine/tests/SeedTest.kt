package com.prism.simulation

import com.prism.simulation.exporters.readJsonl
import com.prism.simulation.generators.load
import com.prism.simulation.generators.run
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SeedTest {
    private val dropout = ROOT.resolve("scenarios/sensor_dropout.yaml") // 노이즈 포함 시나리오

    @Test fun sameSeedSameOutput() = assertEquals(run(load(dropout)).frames, run(load(dropout)).frames)

    @Test fun seedChangesNoise() {
        val a = load(dropout)
        assertNotEquals(run(a).frames, run(a.copy(seed = a.seed + 1)).frames)
    }

    /** samples/ 의 파일이 현재 코드·seed로 그대로 재생성되는지 (오래된 샘플 방지). */
    @Test fun committedSamplesAreReproducible() = RESULTS.forEach { (name, r) ->
        assertEquals(readJsonl(ROOT.resolve("samples").resolve(r.manifest.s("file"))), r.frames, name)
        assertEquals(readJsonl(ROOT.resolve("samples").resolve(r.manifest.s("ego_pose_file"))), r.poses, name)
    }
}
