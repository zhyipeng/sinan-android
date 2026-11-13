plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.sinan.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.sinan.launcher"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0-dev"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:search"))
}
