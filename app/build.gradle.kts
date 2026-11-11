plugins {
    id("com.android.application") version "8.9.2"
    id("org.jetbrains.kotlin.android") version "2.1.10"
}

android {
    namespace = "com.goule.prototype"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.goule.prototype"
        minSdk = 30
        targetSdk = 35
        versionCode = 5
        versionName = "0.3.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
