package com.prism.simulation.generators

import com.prism.simulation.exporters.str
import com.prism.simulation.exporters.writeOutputs
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.system.exitProcess

/** 사용: ./gradlew run --args="scenarios -o out"  (시나리오 YAML 파일 또는 폴더, 기본 출력 out/) */
fun main(args: Array<String>) {
    val inputs = mutableListOf<String>()
    var out = "out"
    var i = 0
    while (i < args.size) {
        if (args[i] == "-o" || args[i] == "--out") out = args.getOrNull(++i) ?: usage() else inputs += args[i]
        i++
    }
    if (inputs.isEmpty()) usage()
    val paths = inputs.map { Path.of(it) }.flatMap { p ->
        if (p.isDirectory()) Files.list(p).use { s -> s.filter { it.extension == "yaml" }.sorted().toList() } else listOf(p)
    }
    val results = paths.map { p ->
        run(load(p)).also { r ->
            println("%-15s frames=%-4d collisions=%d".format(
                r.manifest.str("scenario"), r.frames.size, (r.manifest["observed_collisions"] as List<*>).size,
            ))
        }
    }
    println("-> ${writeOutputs(Path.of(out), results)}")
}

private fun usage(): Nothing {
    System.err.println("사용: generators <시나리오.yaml|폴더>... [-o 출력폴더]")
    exitProcess(2)
}
