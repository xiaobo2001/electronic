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
#-renamesourcefileattribute SourceFile# ===== R8 瘦身保留规则 =====
-dontwarn com.tencent.mmkv.**
-keep class com.tencent.mmkv.** { *; }
-keep class com.skydoves.balloon.** { *; }
-keep class com.xiaobo.fireworks.** { public *; }
# 本地离线 TTS：sherpa-onnx 靠 JNI 按类名反查 Java 方法，混淆后 JNI 调用会崩
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keep class kotlin.** { *; }
-dontwarn kotlin.**
-keep class com.xiaobo.electronic.FireworkWallpaperService { *; }
-keep class com.xiaobo.electronic.ScreenRecorderService { *; }
-keepclassmembers class * implements android.os.Parcelable { public static final ** CREATOR; }
-keepnames class org.json.**
