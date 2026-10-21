plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)
}

// release signing only where a keystore is given (the release workflow); local release builds stay unsigned
val keystoreFile = System.getenv("ANDROID_KEYSTORE_FILE")

android {
    namespace = "de.bavaco.gt7dashboard"
    compileSdk = 37

    if (keystoreFile != null) {
        signingConfigs.create("release") {
            storeFile = file(keystoreFile)
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }

    defaultConfig {
        applicationId = "de.bavaco.gt7dashboard"
        minSdk = 26
        targetSdk = 36
        versionName = providers.gradleProperty("versionName").getOrElse("0.3.0")
        versionCode = providers.gradleProperty("versionCode").getOrElse("1").toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(compose.runtime)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sqldelight.android.driver)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
