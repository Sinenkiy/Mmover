plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.divinegames.mmover"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.divinegames.mmover"
        minSdk = 24
        targetSdk = 37
        versionCode = 300
        versionName = "3.00"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("reviewTest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".reviewtest"
            versionNameSuffix = "-review-test"
            matchingFallbacks += listOf("debug")
        }
        release {
            isMinifyEnabled = true
            // Enables resource shrinking.
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("reviewDiagnostic") {
            initWith(getByName("release"))
            // Same package/versionCode as release: sign with the existing app key in Android Studio.
            // Keep SDK exception names readable, without exposing production data through run-as.
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            versionNameSuffix = "-review-diagnostic"
            matchingFallbacks += listOf("release")
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
    bundle {
        language {
            // Keep all languages available for the in-app language selector.
            // AGP still marks this documented setting as incubating.
            @Suppress("UnstableApiUsage")
            enableSplit = false
        }
    }
}

// AGP does not create host tests for this custom release-derived variant by default.
@Suppress("UnstableApiUsage")
androidComponents {
    beforeVariants(selector().withBuildType("reviewDiagnostic")) { variant ->
        variant.hostTests.getValue(com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE).enable = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
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
    implementation(libs.rustore.review)

    // Тестовые библиотеки
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}