import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Android TV app: a separate, remote-driven app over the shared :composeApp module. It plays on
// the TV itself (the Sendspin local player) and never controls another player.
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "io.music_assistant.client.tv"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Its own id, so it installs next to the phone app.
        applicationId = "io.music_assistant.client.tv"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Debug-signed until the app gets its own release key.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildToolsVersion = "36.0.0"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(project(":nav"))
    implementation(project(":sendspin-kmp"))
    implementation(project(":ui-utils"))
    implementation(project(":shared-icons"))
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.components.resources)
    implementation(libs.material)
    implementation(libs.androidx.tv.material)
    implementation(libs.material.icons.core)
    implementation(libs.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.material3.viewmodel)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.androidx.media)
    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.kermit)
    // QR codes of the sign-in screen (encoder only).
    implementation("com.google.zxing:core:3.5.3")
    // For the JSON types of the shared API (kotlinx-serialization-json, which this brings along).
    implementation(libs.ktor.client.json)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
}
