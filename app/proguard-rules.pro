# Keep kotlinx.serialization & Room generated models safe under R8
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class app.aurora.launcher.**$$serializer { *; }
-keepclassmembers class app.aurora.launcher.** { *** Companion; }
-keepclasseswithmembers class app.aurora.launcher.** { kotlinx.serialization.KSerializer serializer(...); }
