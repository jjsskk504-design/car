plugins {
    kotlin("jvm") version "2.2.10"
    kotlin("plugin.serialization") version "2.2.10"
}

repositories { mavenCentral() }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("ai.riskradar:engine02-kotlin")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
}

val teamReplay = layout.projectDirectory.dir("../../vehicle-integration/replay/src/main/kotlin")
val teamContracts = files(
    teamReplay.file("com/prism/vehicle/replay/Contracts.kt"),
    teamReplay.file("com/prism/vehicle/replay/ContractParser.kt"),
)
kotlin.sourceSets.test {
    kotlin.srcDir(teamReplay)
    kotlin.include("com/prism/vehicle/replay/Contracts.kt", "com/prism/vehicle/replay/ContractParser.kt", "**/PrismContractTest.kt")
}

tasks.named("compileTestKotlin") {
    doFirst { teamContracts.forEach { check(it.isFile) { "Missing team contract source: ${it.name}" } } }
}
tasks.test {
    useJUnit()
    testLogging { events("passed", "failed", "skipped") }
}
