import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.JavaExec
import java.util.zip.ZipFile

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

        buildTypes.release.proguard {
            configurationFiles.from(project.file("compose-desktop.pro"))
        }

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "BillMinder4PC"
            packageVersion = "0.2.1"
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

val releaseProguardDirectory = layout.buildDirectory.dir("compose/tmp/main-release/proguard")

val verifyReleaseDatabaseImplementation = tasks.register("verifyReleaseDatabaseImplementation") {
    group = "verification"
    description = "Checks that ProGuard kept Room's generated database implementation."
    dependsOn("proguardReleaseJars")
    inputs.dir(releaseProguardDirectory)

    doLast {
        val expectedEntry =
            "com/sysadmindoc/billminder4pc/data/BillDatabase_Impl.class"
        val releaseJars = inputs.files.files.filter { it.extension == "jar" }
        val implementationPresent = releaseJars.any { jarFile ->
            ZipFile(jarFile).use { jar -> jar.getEntry(expectedEntry) != null }
        }

        check(implementationPresent) {
            "Release packaging removed $expectedEntry; update compose-desktop.pro before shipping."
        }
    }
}

val verifyReleaseDatabaseRuntime = tasks.register<JavaExec>("verifyReleaseDatabaseRuntime") {
    group = "verification"
    description = "Opens and queries a temporary Room database with the optimized release runtime."
    dependsOn(verifyReleaseDatabaseImplementation)
    classpath = fileTree(releaseProguardDirectory) { include("*.jar") }
    mainClass.set("com.sysadmindoc.billminder4pc.data.ReleasePackageSmokeKt")
    args(layout.buildDirectory.dir("tmp/release-database-smoke").get().asFile.absolutePath)
}

tasks.configureEach {
    if (name == "packageReleaseMsi") {
        dependsOn(verifyReleaseDatabaseRuntime)
    }
}
