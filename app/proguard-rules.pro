# ProGuard rules for NaviVeylin

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }

# Keep JNI native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep libosmscout-client-java JNI bridge
-keep class com.framstag.libosmscout.client.** { *; }

# java.net.http is provided by core-library-desugaring on API < 33
-dontwarn java.net.http.HttpClient
-dontwarn java.net.http.HttpRequest$Builder
-dontwarn java.net.http.HttpRequest
-dontwarn java.net.http.HttpResponse$BodyHandler
-dontwarn java.net.http.HttpResponse$BodyHandlers
-dontwarn java.net.http.HttpResponse
