# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.TypeConverters class * { *; }
-keep class com.example.supplementtracker.data.local.*Entity { *; }

# Giữ lại các annotation cần thiết
-keepattributes *Annotation*, Signature, InnerClasses

# Hỗ trợ Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.android.HandlerContext {
    private final android.os.Handler handler;
}
