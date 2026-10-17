plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.overlayblock"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.overlayblock"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("debugLocal") {
            storeFile = file("../keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugLocal")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Versions pinned to what is available in the local offline artifact cache.
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.0")
    implementation("com.google.android.material:material:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Force transitive versions that exist in the offline cache (Gradle resolves
    // to the highest requested version, so these pins keep everything resolvable).
    constraints {
        // Pin transitive versions to what exists in the offline artifact cache.
        implementation("androidx.core:core:1.9.0")
        implementation("androidx.core:core-ktx:1.9.0")
        implementation("androidx.activity:activity:1.6.0")
        implementation("androidx.activity:activity-ktx:1.6.0")
        implementation("androidx.arch.core:core-runtime:2.1.0")
        implementation("androidx.arch.core:core-common:2.1.0")
        implementation("androidx.lifecycle:lifecycle-viewmodel:2.5.1")
        implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.5.1")
        implementation("androidx.lifecycle:lifecycle-livedata-core:2.5.1")
        implementation("androidx.lifecycle:lifecycle-runtime:2.5.1")
        implementation("androidx.lifecycle:lifecycle-common:2.5.1")
        implementation("androidx.transition:transition:1.4.1")
        implementation("androidx.viewpager2:viewpager2:1.1.0-beta02")
        implementation("androidx.fragment:fragment:1.5.4")
        implementation("androidx.recyclerview:recyclerview:1.3.2")
        implementation("androidx.drawerlayout:drawerlayout:1.1.1")
        implementation("androidx.customview:customview:1.1.0")
        implementation("androidx.annotation:annotation:1.3.0")
        implementation("androidx.annotation:annotation-experimental:1.3.0")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.1")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.1")
    }
}
