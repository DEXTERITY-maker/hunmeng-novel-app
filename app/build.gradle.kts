plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hunmeng.novel"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hunmeng.novel"
        minSdk = 24
        targetSdk = 34
        // При каждом обновлении приложения увеличивай оба числа
        versionCode = 9
        versionName = "0.3.5"
    }

    // Один постоянный ключ подписи: без него новые версии не встанут поверх старых.
    // Сам ключ и пароль лежат в секретах GitHub (KEYSTORE_BASE64, KEYSTORE_PASSWORD),
    // сборка в Actions передаёт их сюда через переменные окружения.
    val keystorePath = System.getenv("HUNMENG_KEYSTORE")
    val keystorePass = System.getenv("HUNMENG_KEYSTORE_PASSWORD")
    val hasReleaseKey = !keystorePath.isNullOrEmpty() && !keystorePass.isNullOrEmpty()

    signingConfigs {
        if (hasReleaseKey) {
            create("hunmeng") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePass
                keyAlias = "hunmeng"
                keyPassword = keystorePass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Без ключа (например, при сборке на телефоне) — временная отладочная подпись
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("hunmeng")
                            else signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
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
    testImplementation("junit:junit:4.13.2")
}
