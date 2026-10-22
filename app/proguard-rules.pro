# General attributes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,DescriptorSignature

# Keep data transfer objects for Kotlinx Serialization and networking
-keep class com.adauto.marvel.core.network.model.** { *; }
-keepclassmembers class com.adauto.marvel.core.network.model.** { *; }
-keep class com.adauto.marvel.core.model.** { *; }
-keepclassmembers class com.adauto.marvel.core.model.** { *; }

# Kotlinx Serialization companion objects
-keepclassmembers class * {
    public static final ** Companion;
}

# Keep Room entities and DAOs
-keep class com.adauto.marvel.core.database.entity.** { *; }
-keepclassmembers class com.adauto.marvel.core.database.entity.** { *; }
-keep class * extends androidx.room.RoomDatabase

# OkHttp & Retrofit rules
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# Skydoves Sandwich
-dontwarn com.skydoves.sandwich.**
-keep class com.skydoves.sandwich.** { *; }
