# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.chitranjan.watchlist.** {
    *** Companion;
}
-keepclasseswithmembers class com.chitranjan.watchlist.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.chitranjan.watchlist.**$$serializer { *; }
