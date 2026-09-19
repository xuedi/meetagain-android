plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val appVersion = "0.1.0"

android {
    namespace = "org.meetagain.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.meetagain.app"
        minSdk = 26
        targetSdk = 36
        versionName = appVersion
        versionCode = appVersion.split(".").map(String::toInt).let { (major, minor, patch) ->
            major * 10000 + minor * 100 + patch
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    buildFeatures {
        compose = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkReleaseBuilds = true
        lintConfig = file("lint.xml")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)

    debugImplementation(libs.compose.ui.tooling)
}
