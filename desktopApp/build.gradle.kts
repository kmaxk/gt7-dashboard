import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "de.bavaco.gt7.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "GT7 Dashboard"
            packageVersion = "0.4.1"
            modules("java.sql")
            macOS {
                bundleID = "de.bavaco.gt7dashboard"
                iconFile.set(project.file("icons/app-icon.icns"))
                // macOS rejects bundle versions starting with 0; the release itself is named after packageVersion
                packageVersion = "1.0.0"
                infoPlist {
                    // required for macOS to ask for local network access; on its own it did not make the prompt appear (see README)
                    extraKeysRawXml = """
                        <key>NSLocalNetworkUsageDescription</key>
                        <string>GT7 Dashboard empfängt die Telemetrie deiner PS5 im lokalen Netzwerk.</string>
                    """.trimIndent()
                }
            }
            windows {
                menuGroup = "GT7 Dashboard"
                iconFile.set(project.file("icons/app-icon.ico"))
                shortcut = true
                perUserInstall = true // no admin rights needed
                // fixed so newer MSIs upgrade the installed version instead of installing side by side
                upgradeUuid = "6f1b2c4e-8d3a-4b7e-9c51-2a7e0d4f8b63"
            }
        }
    }
}
