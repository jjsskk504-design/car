// PRISM visualization-engine: Android Library 모듈
// 소스 폴더는 팀 산출물 규격(model/ rendering/ overlays/ ui/ replay/ tests/)을 그대로 따른다.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

val composeBom = "2026.02.01"

android {
    namespace = "ai.prism.visualization"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs("model", "rendering", "overlays", "ui", "replay")
        }
        getByName("test") {
            kotlin.srcDirs("tests/unit")
        }
        getByName("androidTest") {
            kotlin.srcDirs("tests/android")
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:$composeBom"))
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // org.json 은 Android 런타임에 포함되어 있다. JVM 단위 테스트에서만 실제 구현을 추가한다.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    androidTestImplementation(platform("androidx.compose:compose-bom:$composeBom"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
