plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // não aplique kotlin-android para ficar Java puro
}

android {
    namespace = "com.example.filamenttestjava"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.filamenttestjava"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    androidResources {
        noCompress += listOf("filamat")
    }

    // Se quiser ActivityMainBinding, descomente:
    // buildFeatures { viewBinding = true }
}



dependencies {
    implementation("com.google.android.material:material:1.12.0") // ou mais recente

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation(files("libs/filament-utils-v1.63.1-android.aar"))
    implementation(files("libs/filamat-v1.63.1-android.aar"))
    implementation(files("libs/filament-v1.63.1-android.aar"))
    implementation(files("libs/gltfio-v1.63.1-android.aar"))
    implementation(libs.core.ktx)
}



