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
        versionCode = 23
        versionName = "4.1"

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


    // strip unneeded files from APK
    packaging {
        // remove common license/metadata files that bloat APK
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module",
                "META-INF/versions/**",
                "DebugProbesKt.bin",
                // BouncyCastle metadata
                "META-INF/maven/**",
                "META-INF/proguard/**"
            )
        }
        // Keep only essential JNI libs
        jniLibs {
            useLegacyPackaging = false
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
        viewBinding = true
        buildConfig = true
    }

    flavorDimensions += "version"
    productFlavors {
        create("full") {
            dimension = "version"
            buildConfigField("boolean", "HAS_DAEMON", "true")
        }
        create("fdroid") {
            dimension = "version"
            applicationIdSuffix = ".fdroid" // İsteğe bağlı: Paket adına .fdroid ekler
            versionNameSuffix = "-fdroid"
            buildConfigField("boolean", "HAS_DAEMON", "false")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
            val appName = "ShizuWall"
            val version = versionName
            val type = name
            output.outputFileName = "$appName-$version-$type.apk"
        }
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
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation ("dev.rikka.shizuku:api:$shizuku_version")
    implementation ("dev.rikka.shizuku:provider:$shizuku_version")

    "fullImplementation" ("com.github.MuntashirAkon:libadb-android:3.1.1")
    "fullImplementation" ("org.conscrypt:conscrypt-android:2.5.3")

    // Required for generating a self-signed certificate for ADB-over-WiFi TLS.
    "fullImplementation" ("org.bouncycastle:bcprov-jdk15to18:1.81")
    "fullImplementation" ("org.bouncycastle:bcpkix-jdk15to18:1.81")
}