import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Подпись release-сборки: keystore.properties — локальный, не в git (см. .gitignore).
// Если файла нет (например, у другого разработчика при сборке из публичных исходников),
// release просто соберётся неподписанным — сборка не падает.
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasSigningConfig = keystorePropertiesFile.exists()
if (hasSigningConfig) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.anipulse.app"
    compileSdk = 35
    buildToolsVersion = "35.0.1"

    defaultConfig {
        applicationId = "com.anipulse.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 28
        versionName = "0.7.1"

        ndk {
            // Нативные библиотеки SQLCipher весят ~5 МБ на каждую архитектуру. x86 и x86_64
            // встречаются только в эмуляторах и единичных Chromebook'ах — на них уходило
            // 11 МБ из 24 МБ APK, которые ни один телефон не использует.
            //
            // Отладочные сборки фильтр не трогает (см. ниже), иначе приложение перестало бы
            // ставиться на эмулятор.
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }

            // SHA-256 сертификата подписи для проверки целостности на старте (см. AppIntegrity).
            // Значение берётся из keystore.properties и в git не попадает. Если его нет,
            // проверка выключается: иначе сборка из публичных исходников не запускалась бы.
            //
            // Строка формата "AA:BB:...", как её печатает keytool -list -v.
            val expectedFingerprint = keystoreProperties.getProperty("releaseCertSha256").orEmpty()
            buildConfigField("String", "RELEASE_CERT_SHA256", "\"$expectedFingerprint\"")
        }
        debug {
            // Поле должно существовать во всех вариантах, иначе BuildConfig не скомпилируется.
            // Пустая строка = проверка целостности отключена (отладочная подпись своя у каждого).
            buildConfigField("String", "RELEASE_CERT_SHA256", "\"\"")
            // Возвращаем x86-архитектуры: эмуляторы почти всегда x86_64, и без них
            // отладочная сборка на них не запустится.
            ndk { abiFilters += listOf("x86", "x86_64") }
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
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation(libs.androidx.navigation.compose)

    implementation(libs.retrofit)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.converter)
    implementation(libs.coil.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)

    implementation(libs.work.runtime)

    implementation(libs.androidx.security.crypto)
    // Шифрование локальной базы. Тянет нативные библиотеки на все ABI (~4 МБ к APK) —
    // цена за то, что история просмотра не читается с устройства в открытом виде.
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.core.splashscreen)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
