# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

#===============================================================================
# OPTIMIZATION SETTINGS
#===============================================================================
# Enable aggressive optimizations
-optimizationpasses 5
-allowaccessmodification
-dontpreverify
-repackageclasses ''

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

#===============================================================================
# APPLICATION COMPONENTS
#===============================================================================
# Keep application/components referenced from manifest
-keep class * extends android.app.Application { *; }
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.content.ContentProvider { *; }

#===============================================================================
# ANDROIDX / MATERIAL
#===============================================================================
# Keep classes used by AndroidX / material reflection (adjust if you add more libs)
-keepnames class androidx.lifecycle.** { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
# Keep classes annotated with @Keep
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Keep parcelable creators (common)
-keepclassmembers class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator CREATOR;
}

#===============================================================================
# CONSCRYPT (TLS Provider for LADB)
#===============================================================================
# Keep only the public Conscrypt API used at runtime via Security.insertProviderAt
-keep class org.conscrypt.Conscrypt {
    public static org.conscrypt.OpenSSLProvider newProvider();
}
-keep class org.conscrypt.OpenSSLProvider { public <init>(); }

# Keep JNI native methods
-keepclasseswithmembernames class org.conscrypt.** {
    native <methods>;
}

# Don't warn about internal Conscrypt classes
-dontwarn org.conscrypt.**
-dontwarn dalvik.system.**

#===============================================================================
# BOUNCYCASTLE (Certificate Generation for LADB)
#===============================================================================
# BouncyCastle is large - only keep what we absolutely need for X509 cert generation
# The app uses: X500Name, JcaX509v3CertificateBuilder, JcaX509CertificateConverter,
# JcaContentSignerBuilder, and BouncyCastleProvider

# Core provider - must keep for Security.addProvider
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider {
    public <init>();
    public static final java.lang.String PROVIDER_NAME;
}

# Certificate builder classes (accessed directly in code)
-keep class org.bouncycastle.asn1.x500.X500Name { public *; }
-keep class org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder { public *; }
-keep class org.bouncycastle.cert.jcajce.JcaX509CertificateConverter { public *; }
-keep class org.bouncycastle.operator.jcajce.JcaContentSignerBuilder { public *; }

# Don't warn about unused BouncyCastle classes - we're intentionally stripping them
-dontwarn org.bouncycastle.**
-dontnote org.bouncycastle.**

#===============================================================================
# LIBADB-ANDROID (ADB over WiFi)
#===============================================================================
# Keep ADB connection classes - accessed via reflection in some places
-keep class io.github.muntashirakon.adb.AdbConnection { *; }
-keep class io.github.muntashirakon.adb.AdbStream { *; }
-keep class io.github.muntashirakon.adb.PairingConnectionCtx { *; }
-keep class io.github.muntashirakon.adb.AdbPairingRequiredException { *; }
-dontwarn io.github.muntashirakon.adb.**

#===============================================================================
# SHIZUKU
#===============================================================================
# Keep Shizuku API classes
-keep class rikka.shizuku.** { *; }
-keepclassmembers class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

#===============================================================================
# JSR305 annotations
#===============================================================================
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**

#===============================================================================
# KOTLIN COROUTINES
#===============================================================================
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

#===============================================================================
# REMOVE UNUSED CODE
#===============================================================================
# Strip kotlin metadata to save space
-dontwarn kotlin.**
-dontnote kotlin.**

# Remove R8 debugging info
-dontnote **