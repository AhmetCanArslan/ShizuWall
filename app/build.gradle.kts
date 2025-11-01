plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.arslan.shizuwall"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.arslan.shizuwall"
        minSdk = 30
        targetSdk = 36
        versionCode = 2
        versionName = "2.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true                         // enable R8 shrinking/obfuscation/optimization
            isShrinkResources = true                       // remove unused resources
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // keep debuggable off in release
            isDebuggable = false
        }
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    // generate per-ABI APKs to avoid shipping unused native libs in a single fat APK
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    // strip unneeded files from APK
    packagingOptions {
        // remove common license/metadata files that bloat APK
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/DEPENDENCIES"
            )
        }
    }

    // optional: limit locales/resources if you only need specific ones
    // defaultConfig { resConfigs("en") }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        aidl = true 
    }
}

val shizuku_version = "13.1.5"
dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.cardview)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation ("dev.rikka.shizuku:api:$shizuku_version")
    implementation ("dev.rikka.shizuku:provider:$shizuku_version")
}