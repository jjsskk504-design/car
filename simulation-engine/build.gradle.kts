import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.0.21"
    application
}

group = "com.prism"
version = "0.2.0"

repositories { mavenCentral() }

dependencies {
    implementation("org.yaml:snakeyaml:2.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
}

// 03 프롬프트가 지정한 폴더 구조(adapters/generators/exporters/visualization/tests)를 그대로 소스 폴더로 쓴다
sourceSets {
    main { kotlin.srcDirs("adapters", "generators", "exporters", "visualization") }
    test { kotlin.srcDirs("tests") }
}

java { targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

application { mainClass.set("com.prism.simulation.generators.MainKt") }

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

// ./gradlew generate  -> scenarios/*.yaml 7종을 samples/ 로
tasks.register<JavaExec>("generate") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.prism.simulation.generators.MainKt")
    args("scenarios", "-o", "samples")
}

// ./gradlew replay  -> samples/ 를 docs/replay/*.png, *.gif 로
tasks.register<JavaExec>("replay") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.prism.simulation.visualization.ReplayKt")
    args("samples", "docs/replay")
}
