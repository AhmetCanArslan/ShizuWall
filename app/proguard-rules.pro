

-optimizationpasses 5
-allowaccessmodification
-dontpreverify
-repackageclasses ''

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

-keep class * extends android.app.Application { *; }
-keep class * extends android.app.Activity { *; }
-keep class * extends android.app.Service { *; }
-keep class * extends android.content.BroadcastReceiver { *; }
-keep class * extends android.content.ContentProvider { *; }

-keepnames class androidx.lifecycle.** { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

-keepclassmembers class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator CREATOR;
}

-keep class rikka.shizuku.** { *; }
-keepclassmembers class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

-keep class com.arslan.shizuwall.shizuku.ShizuWallUserService { *; }
-keep interface com.arslan.shizuwall.shizuku.IShizuWallUserService { *; }
-keep class com.arslan.shizuwall.shizuku.IShizuWallUserService$* { *; }

-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**

-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

-dontwarn kotlin.**
-dontnote kotlin.**

-dontnote **