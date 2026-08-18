plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.principal.school2"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.principal.school2"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            // 测试分发:使用 debug 签名,保证 CI 产物可直接安装
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // 纯平台 API,无第三方依赖,构建最稳定
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
}
