# R8/ProGuard rules for the OpenFTBA Android release APK.
#
# Compose Multiplatform + AndroidX libraries ship their own consumer rules, so this file only
# adds project-specific keeps. The biggest one is kotlinx.serialization: settings are persisted
# as JSON (AndroidSettingsStore), and R8 will strip the synthetic `Companion`/`serializer()`
# accessors without the rules below.

# kotlinx.serialization — official rules from
# https://github.com/Kotlin/kotlinx.serialization#android
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Silence warnings for the Compose MP / Kotlin stdlib internals R8 doesn't know how to verify.
-dontwarn org.jetbrains.compose.**
-dontwarn kotlinx.serialization.**
