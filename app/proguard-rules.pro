# Chaquopy ProGuard rules
-keep class com.chaquo.python.** { *; }

# Media3 ProGuard rules
-keep class androidx.media3.** { *; }

# Room ProGuard rules
-keep class * extends androidx.room.RoomDatabase

# Google Mobile Ads SDK rules
-keep public class com.google.android.gms.ads.** {
    public *;
}
-keep public class com.google.ads.** {
    public *;
}
