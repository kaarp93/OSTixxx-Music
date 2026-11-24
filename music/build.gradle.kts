plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cat.ostixxx.music"
    compileSdk = 34
    defaultConfig {
        applicationId = "cat.ostixxx.music"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        // Identificador de la build: el posa el workflow (BUILD_ID = versió-número de compilació-commit); en local, "local"
        buildConfigField("String", "BUILD_ID", "\"" + (System.getenv("BUILD_ID") ?: "local") + "\"")
    }
    // Sempre la mateixa clau de depuració (debug.keystore, àlies androiddebugkey, contrasenya android),
    // si canviés a cada compilació, Android no deixaria actualitzar.
    signingConfigs {
        create("gbdsmixxx") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("release") { signingConfig = signingConfigs.getByName("gbdsmixxx") }
        getByName("debug") { signingConfig = signingConfigs.getByName("gbdsmixxx") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":consoles"))
    implementation(project(":experimental"))
}
