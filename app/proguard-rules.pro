# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.animelib.app.**$$serializer { *; }
-keepclassmembers class com.animelib.app.** { *** Companion; }
-keepclasseswithmembers class com.animelib.app.** { kotlinx.serialization.KSerializer serializer(...); }
