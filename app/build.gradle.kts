plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinxParcelize)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

kotlin {
}

android {
    namespace = "com.example.app"
    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildToolsVersion.get()

    signingConfigs {
    }

    defaultConfig {
        applicationId = "com.example.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        // reduces apk sizes by not including unsupported languages
        resourceConfigurations += setOf("en")

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = libs.versions.jvmTarget.get()

        freeCompilerArgs = listOf(
                "-opt-in=kotlinx.coroutines.FlowPreview",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
//                "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
//                "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
//                "-opt-in=androidx.compose.material.ExperimentalMaterialApi",
//                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
//                "-opt-in=androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi",
//                "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
//                "-opt-in=androidx.compose.ui.text.ExperimentalTextApi",
//                "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
//                "-opt-in=com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi",
        )
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/versions/9/previous-compilation-data.bin"
        }
    }

    lint {
        baseline = file("lint-baseline.xml")
    }

    // We use the .filamat extension for materials compiled with matc
    // Telling aapt to not compress them allows to load them efficiently
    androidResources {
        noCompress += listOf("filamat", "ktx")
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugarJdkLibs)
    implementation(platform(libs.composeBom))

    implementation(libs.hiltAndroid)
    ksp(libs.hiltAndroidCompiler)

    implementation(libs.bundles.kotlinx)
    implementation(libs.bundles.google)
    implementation(libs.bundles.androidx)
    implementation(libs.bundles.androidxApp)
    implementation(libs.bundles.androidxCompose)
    lintChecks(libs.composeLintChecks)
    implementation(libs.bundles.filament)
    implementation(libs.slf4jSimple)

    implementation(libs.arCore)
    implementation(libs.arrowFx)

    testImplementation(libs.bundles.test)
}
