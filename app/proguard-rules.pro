# --- Android & Compose ---
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }

# --- Hilt / Dagger ---
-keep class uk.co.fireburn.raiform.RaiFormApp { *; }
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclassmembers class * {
    @dagger.hilt.android.qualifiers.ApplicationContext *;
    @javax.inject.Inject <init>(...);
}

# --- Room ---
-keep class androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.dao.Dao
-dontwarn androidx.room.paging.**

# --- Firebase ---
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# --- Kotlin Serialization ---
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class uk.co.fireburn.raiform.domain.model.** {
    <init>(...);
    *** Companion;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- MPAndroidChart ---
-keep class com.github.mikephil.charting.** { *; }

# --- WorkManager ---
-keep class androidx.work.** { *; }

# --- Glance (Widgets) ---
-keep class androidx.glance.** { *; }
