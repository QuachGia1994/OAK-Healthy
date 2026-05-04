# Bảo vệ các Model khỏi bị Obfuscation (Xáo trộn tên) để Room/Gson hoạt động đúng
-keep class com.example.supplementtracker.domain.model.** { *; }
-keep class com.example.supplementtracker.data.local.** { *; }

# Giữ lại các annotation cần thiết
-keepattributes *Annotation*, Signature, InnerClasses

# Hỗ trợ Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.android.HandlerContext {
    private final android.os.Handler handler;
}
