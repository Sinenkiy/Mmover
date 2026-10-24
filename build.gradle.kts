buildscript {
    dependencies {
        // Use the current Kotlin compiler with AGP's built-in Kotlin support.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
}
