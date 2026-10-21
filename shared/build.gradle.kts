plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(17)
    jvm("desktop")
    android {
        namespace = "de.stiller.gt7.shared"
        compileSdk = 37
        minSdk = 26
        // cars.csv and maker.csv are compose resources
        androidResources { enable = true }
        withHostTestBuilder {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(libs.ktor.network)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.sqldelight.coroutines.extensions)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        // Skia runtime for rendering the README screenshots (ReadmeScreenshots)
        val desktopTest by getting { dependencies { implementation(compose.desktop.currentOs) } }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.sqldelight.sqlite.driver)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.jna)
            }
        }
    }
}

sqldelight {
    databases {
        create("Gt7Database") {
            packageName.set("de.stiller.gt7.db")
        }
    }
}
