plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.divinegames.mmover"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.divinegames.mmover"
        minSdk = 24
        targetSdk = 35
        versionCode = 104
        versionName = "1.04"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            // Enables resource shrinking.
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    bundle {
        language {
            enableSplit = false
        }
    }
}

dependencies {
    // Основные библиотеки AndroidX
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Google Mobile Ads SDK (AdMob)
    //implementation(libs.play.services.ads)

    // Yandex SDK
    implementation("com.yandex.android:mobileads:7.17.0") // <-- Используем прямую зависимость для надежности

    // Тестовые библиотеки
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}