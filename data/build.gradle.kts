plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
}

// The androidx.room3 Gradle plugin only wires itself into the Android and Kotlin Multiplatform
// plugins, so on a plain JVM module the schema location goes to the KSP processor directly.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    api(project(":core"))
    api(libs.room.runtime)
    implementation(libs.sqlite.bundled)
    implementation(libs.kotlinx.coroutines.core)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}
