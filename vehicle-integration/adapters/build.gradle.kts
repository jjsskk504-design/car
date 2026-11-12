plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(17) }

// 01·02 엔진은 각 팀 폴더의 소스를 그대로 컴파일한다(복사·수정 없음). 05 의 AGP/Kotlin 버전으로 빌드된다.
sourceSets {
    main {
        kotlin.srcDir("../../prediction-engine/src/main/kotlin")
        kotlin.srcDir("../../engine02-kotlin/src/main/kotlin")
    }
}

dependencies {
    api(project(":replay"))
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
}

// ./gradlew :adapters:importSim03  -> simulation-engine/samples 를 app assets 의 sim_* 시나리오로 변환
tasks.register<JavaExec>("importSim03") {
    group = "prism"
    description = "03 simulation-engine samples 를 05 앱 assets(scenarios/sim_*) 로 가져온다."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.prism.vehicle.adapters.ImportSim03Kt")
    args(file("../../simulation-engine/samples").path, file("../app/src/main/assets").path)
}
