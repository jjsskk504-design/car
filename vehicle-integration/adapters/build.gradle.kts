plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":replay"))
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
}
