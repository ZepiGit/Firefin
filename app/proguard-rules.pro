# Keep Jellyfin DTO field names accessed reflectively-free; org.json is runtime
# reflection-free, so nothing model-specific is needed. Keep crash reporting of
# Kotlin coroutines stack traces intact.
-keepattributes SourceFile,LineNumberTable,InnerClasses,EnclosingMethod
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
# Conscrypt is installed as a runtime security provider on API 22.
-keep class org.conscrypt.** { *; }
-keepnames class org.conscrypt.**
