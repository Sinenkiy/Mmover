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
        versionCode = 201
        versionName = "2.01"
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
        isCoreLibraryDesugaringEnabled = true
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
    implementation(libs.androidx.activity)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.coroutines.android)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    // Основные библиотеки AndroidX
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)


    // Yandex SDK
    implementation(libs.yandex.ads)

    // Тестовые библиотеки
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}