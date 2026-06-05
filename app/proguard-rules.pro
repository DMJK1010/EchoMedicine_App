# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# ============================================================
# Room
# ============================================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keep class * implements androidx.room.RoomDatabase$Callback
-dontwarn androidx.room.paging.**

# Keep Room-generated Impl classes
-keep class *_Impl { *; }

# ============================================================
# Hilt / Dagger
# ============================================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.Module class *
-keep @dagger.hilt.InstallIn class *

# ============================================================
# Kotlin Coroutines
# ============================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ============================================================
# App-specific keep rules
# ============================================================
# Keep data classes used in Room entities
-keep class com.echomedicine.app.data.db.entity.** { *; }
-keep class com.echomedicine.app.domain.model.** { *; }

# Keep Bluetooth service for foreground service
-keep class com.echomedicine.app.service.BluetoothForegroundService { *; }

# Keep Application class
-keep class com.echomedicine.app.EchoMedicineApp { *; }
