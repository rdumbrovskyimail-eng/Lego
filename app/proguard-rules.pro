-dontobfuscate
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}

# Jetpack Compose
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }

# WorkManager создаёт воркеры по имени класса
-keep class * extends androidx.work.ListenableWorker { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { *; }