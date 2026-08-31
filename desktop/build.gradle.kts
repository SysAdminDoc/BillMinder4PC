import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))

    implementation(compose.desktop.currentOs)
    // material3 stays on the plugin accessor: the standalone org.jetbrains.compose.material3
    // artifact only publishes alphas for this line, so naming it directly downgrades the build.
    implementation(compose.material3)
    // The icons pack is frozen at 1.7.3 upstream, so it is pinned rather than tracked.
    implementation(libs.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
}

compose.desktop {
    application {
        mainClass = "com.sysadmindoc.billminder4pc.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "BillMinder4PC"
            packageVersion = "0.2.0"
            description = "Bill tracking and reminders for Windows"
            copyright = "Copyright 2026 SysAdminDoc"
            vendor = "SysAdminDoc"
            licenseFile.set(rootProject.file("LICENSE"))

            // jdeps misses reflective loads, so the modules Room, SQLite and Swing need are listed here.
            modules("java.sql", "java.naming", "java.management", "jdk.unsupported")

            windows {
                // Toast notifications only display for an app with a Start menu shortcut carrying an
                // AppUserModelID, so menuGroup is load-bearing rather than cosmetic.
                menuGroup = "BillMinder4PC"
                perUserInstall = true
                dirChooser = true
                shortcut = true
                // Regenerating this breaks in-place upgrades for everyone already installed.
                upgradeUuid = "6f3a1c48-2d5e-4b9a-9c17-8e0d4a7b5c31"
            }
        }
    }
}
