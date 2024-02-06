import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
    alias(libs.plugins.kotlinxParcelize) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

subprojects {
    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            freeCompilerArgs = freeCompilerArgs + "-Xcontext-receivers"

            if (project.findProperty("hackernews.enableComposeCompilerReports") == "true") {
                // force tasks to rerun so that metrics are generated
                outputs.upToDateWhen { false }
                freeCompilerArgs = freeCompilerArgs +
                        "-P=plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${project.projectDir.absolutePath}/build/compose_metrics/" +
                        "-P=plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=${project.projectDir.absolutePath}/build/compose_metrics/"
            }
        }
    }
}

//buildscript {
//    ext {
//        kotlin_version = "1.5.31"
//    }
//    repositories {
//        google()
//        mavenCentral()
//        maven { url "https://plugins.gradle.org/m2/" }
//        maven { url "https://dl.bintray.com/arrow-kt/arrow-kt/" }
//    }
//    dependencies {
//        classpath "com.android.tools.build:gradle:7.0.3"
//        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version"
//        classpath "org.jetbrains.kotlin:kotlin-android-extensions:$kotlin_version"
//        classpath "com.google.gms:google-services:4.3.10"
//        classpath "com.google.firebase:perf-plugin:1.4.0"
//        classpath "com.google.firebase:firebase-crashlytics-gradle:2.8.0"
//        classpath "androidx.navigation:navigation-safe-args-gradle-plugin:2.3.5"
//    }
//}
//
//allprojects {
//    repositories {
//        google()
//        mavenCentral()
//    }
//}
//
//task clean(type: Delete) {
//    delete rootProject.buildDir
//}
