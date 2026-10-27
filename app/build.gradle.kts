plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "pl.ambif.ambify"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "pl.ambif.ambify"
        minSdk = 31
        targetSdk = 37
        versionCode = 7
        versionName = "0.5.1"

        // Fixed home server address; changed only by an app update, never by the user.
        // -Pambify.serverUrl=… builds a separate test app ("AMBiFy TEST", own data) against a test
        // server, installed next to the real app so tests never touch the family's sign-in.
        val testServerUrl = project.findProperty("ambify.serverUrl") as String?
        buildConfigField("String", "SERVER_URL", "\"${testServerUrl ?: "http://192.168.1.122:8090"}\"")
        if (testServerUrl != null) applicationIdSuffix = ".test"
        // The test app offers one-tap sign-in with the test server's demo accounts.
        buildConfigField("boolean", "TEST_LOGIN", (testServerUrl != null).toString())
        manifestPlaceholders["appLabel"] = if (testServerUrl != null) "AMBiFy TEST" else "@string/app_name"
        manifestPlaceholders["appIcon"] = if (testServerUrl != null) "@mipmap/ic_launcher_test" else "@mipmap/ic_launcher"
        manifestPlaceholders["appRoundIcon"] = if (testServerUrl != null) "@mipmap/ic_launcher_test_round" else "@mipmap/ic_launcher_round"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}