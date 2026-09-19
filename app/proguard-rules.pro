# Keep OBSProfile fields for Gson serialization
-keep class com.kongjjj.obscontroller.OBSProfile { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
