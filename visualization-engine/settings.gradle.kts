// 독립 실행용 설정. PRISM 루트 빌드에 포함할 때는 README의 "통합 방법"을 따른다.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.library") version "9.5.1"
        id("com.android.application") version "9.5.1"
        id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "visualization-engine"
include(":demo")
