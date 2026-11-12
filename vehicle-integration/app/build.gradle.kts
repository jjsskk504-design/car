plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.prism.vehicle.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.prism.vehicle.app"
        minSdk = 28
        targetSdk = 34 // Pleos Connect 에뮬레이터 = Android 14 (API 34)
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 서명 키는 저장소에 두지 않는다. 서명 없는 release 는 설치 불가 → 시연·제출은 debug APK 사용.
        }
    }

    buildFeatures { compose = true }

    // 04 visualization-engine 소스를 그대로 컴파일한다(복사·수정 없음). 05 의 AGP/Compose 버전으로 빌드된다.
    sourceSets.getByName("main") {
        java.srcDirs(listOf("model", "rendering", "overlays", "ui", "replay").map { "../../visualization-engine/$it" })
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":adapters"))
    implementation(project(":sdk"))

    implementation(platform(libs.compose.bom))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
