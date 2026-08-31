plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}

// Every module compiles and runs on the same toolchain. Packaging needs JDK 17+ for jpackage.
subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension> {
            jvmToolchain(21)
        }
    }
    tasks.withType<Test>().configureEach {
        useJUnit()
        testLogging { events("failed", "skipped") }
    }
}
