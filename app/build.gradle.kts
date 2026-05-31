import javax.inject.Inject
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.process.ExecOperations

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
        versionCode = 31
        versionName = "4.5"

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
            // RB don't strip .so — keep the Maven bytes so F-Droid's server
            // (NDK present) can't re-strip and change ELF .shstrtab, breaking
            // byte-for-byte reproducibility (libconscrypt_jni.so, libspake2.so)
            keepDebugSymbols += "**/*.so"
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

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
            val appName = "ShizuWall"
            val version = versionName
            val type = buildType.name
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
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation ("dev.rikka.shizuku:api:$shizuku_version")
    implementation ("dev.rikka.shizuku:provider:$shizuku_version")

    implementation ("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation ("org.conscrypt:conscrypt-android:2.5.3")

    // Required for generating a self-signed certificate for ADB-over-WiFi TLS.
    implementation ("org.bouncycastle:bcprov-jdk15to18:1.81")
    implementation ("org.bouncycastle:bcpkix-jdk15to18:1.81")
}

// ---------------------------------------------------------------------------
// On-device daemon (DEX), compiled from source as part of the normal build.
//
// SystemDaemon.java is a plain Java class that runs on the
// device under the shell UID via app_process. It is compiled to a DEX and
// shipped as the daemon.bin asset. Compiling it here instead of committing
// a prebuilt binary or running a manual script lets reproducible build
// servers (F-Droid) produce it from source during assemble.
// ---------------------------------------------------------------------------
abstract class CompileDaemonDexTask : DefaultTask() {
    @get:InputFile
    abstract val javaSource: RegularFileProperty

    @get:Internal
    abstract val sdkDirectory: DirectoryProperty

    @get:Input
    abstract val buildToolsVersion: Property<String>

    @get:Input
    abstract val compileSdkVersion: Property<Int>

    @get:Input
    abstract val minApi: Property<Int>

    @get:Internal
    abstract val workDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val execOps: ExecOperations

    @get:Inject
    abstract val fsOps: FileSystemOperations

    @get:Inject
    abstract val archiveOps: ArchiveOperations

    @TaskAction
    fun compile() {
        val sdk = sdkDirectory.get().asFile
        val d8 = File(sdk, "build-tools/${buildToolsVersion.get()}/d8")
        val androidJar = File(sdk, "platforms/android-${compileSdkVersion.get()}/android.jar")
        require(d8.exists()) { "d8 not found at $d8 (install the matching build-tools)" }
        require(androidJar.exists()) { "android.jar not found at $androidJar" }

        val work = workDir.get().asFile
        fsOps.delete { delete(work) }
        val classesDir = File(work, "classes").apply { mkdirs() }

        execOps.exec {
            commandLine(
                "javac", "--release", "11",
                "-d", classesDir.absolutePath,
                "-classpath", androidJar.absolutePath,
                javaSource.get().asFile.absolutePath
            )
        }

        val classFiles = classesDir.walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .map { it.absolutePath }
            .toList()
        require(classFiles.isNotEmpty()) { "javac produced no .class files for the daemon" }

        val dexZip = File(work, "daemon.zip")
        execOps.exec {
            commandLine(
                buildList {
                    add(d8.absolutePath)
                    add("--min-api"); add(minApi.get().toString())
                    add("--output"); add(dexZip.absolutePath)
                    addAll(classFiles)
                    add("--lib"); add(androidJar.absolutePath)
                }
            )
        }

        val out = outputDir.get().asFile
        fsOps.delete { delete(out) }
        out.mkdirs()
        fsOps.copy {
            from(archiveOps.zipTree(dexZip)) {
                include("classes.dex")
                rename("classes.dex", "daemon.bin")
            }
            into(out)
        }
    }
}

val compileDaemonDex = tasks.register<CompileDaemonDexTask>("compileDaemonDex") {
    javaSource.set(layout.projectDirectory.file("src/main/java/com/arslan/shizuwall/daemon/SystemDaemon.java"))
    sdkDirectory.set(layout.dir(provider { android.sdkDirectory }))
    buildToolsVersion.set(provider { android.buildToolsVersion })
    compileSdkVersion.set(provider { android.compileSdk!! })
    minApi.set(provider { android.defaultConfig.minSdk!! })
    workDir.set(layout.buildDirectory.dir("intermediates/daemonDex"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            compileDaemonDex,
            CompileDaemonDexTask::outputDir
        )
    }
}