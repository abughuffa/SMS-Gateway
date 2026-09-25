plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.example.smsgateway"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.smsgateway"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }




    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        dataBinding = false
    }
}

// JVM target for Kotlin is derived from compileOptions automatically in AGP 9.
// Only declare this block if you need extra compiler options.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    // Ktor 3.6.0 — CIO engine
    implementation("io.ktor:ktor-server-cio:3.6.0")
    implementation("io.ktor:ktor-server-core:3.6.0")
    implementation("io.ktor:ktor-server-content-negotiation:3.6.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.6.0")
    implementation("io.ktor:ktor-server-status-pages:3.6.0")
    implementation("io.ktor:ktor-server-call-logging:3.6.0")

    // Coroutines + Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("androidx.viewpager2:viewpager2:1.1.0")
}
