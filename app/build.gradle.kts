plugins {
    id("com.android.application")
}

android {
    namespace = "com.unlock.islandwhitelist"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.unlock.islandwhitelist"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 没有正式签名时用 debug 签名，保证 APK 可直接安装
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
}
