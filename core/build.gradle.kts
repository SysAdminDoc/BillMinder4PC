plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// :core stays free of platform dependencies on purpose. Everything here is pure Kotlin plus
// java.time, which keeps the cycle math testable without a database, a UI, or an OS. The one
// exception is room3-common, which is annotations only and carries no runtime behaviour, so the
// model classes can be declared once instead of duplicated into a mapping layer in :data.
dependencies {
    api(libs.room.common)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
}
