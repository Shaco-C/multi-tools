import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val releaseSigningFile = rootProject.file("keystore.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.exists()) {
        releaseSigningFile.inputStream().use(::load)
    }
}

android {
    namespace = "com.pockettoolbox"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.pockettoolbox"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningFile.exists()) {
            create("release") {
                storeFile = file(requireNotNull(releaseSigning.getProperty("storeFile")))
                storePassword = requireNotNull(releaseSigning.getProperty("storePassword"))
                keyAlias = requireNotNull(releaseSigning.getProperty("keyAlias"))
                keyPassword = requireNotNull(releaseSigning.getProperty("keyPassword"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:backup"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(project(":feature:electricity"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
