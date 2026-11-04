# kotlinx.serialization: keep serializers for our models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class in.s123dive.oneremote.**$$serializer { *; }
-keepclassmembers class in.s123dive.oneremote.** { *** Companion; }
-keepclasseswithmembers class in.s123dive.oneremote.** { kotlinx.serialization.KSerializer serializer(...); }
# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# OkHttp optional TLS providers
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
# Keep enum names used in Intent extras (Role / Source / DeviceType are looked up by name)
-keepclassmembers enum in.s123dive.oneremote.** { *; }
