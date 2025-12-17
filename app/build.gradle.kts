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
        versionCode = 16
        versionName = "3.2"

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
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation ("dev.rikka.shizuku:api:$shizuku_version")
    implementation ("dev.rikka.shizuku:provider:$shizuku_version")

    implementation ("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation ("org.conscrypt:conscrypt-android:2.5.3")
    implementation ("androidx.security:security-crypto:1.1.0")

    // Required for generating a self-signed certificate for ADB-over-WiFi TLS.
    implementation("org.bouncycastle:bcprov-jdk15to18:1.81")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")
}