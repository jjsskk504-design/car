plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ai.prism.visualization.demo"
    compileSdk = 37

    defaultConfig {
        applicationId = "ai.prism.visualization.demo"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
}

dependencies {
    // 독립 빌드(:demo → 부모 ":")와 PRISM 루트 빌드(:visualization-engine:demo → 부모 ":visualization-engine") 모두에서 라이브러리를 가리킨다
    implementation(project(project.parent!!.path))
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

// 성능 측정용: release 최적화(비 debuggable) + 디버그 키 서명. 배포용 서명이 아님.
android {
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }
}
