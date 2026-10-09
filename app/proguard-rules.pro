# 保留 JNI 方法（native 层通过名字查找，混淆会破坏）
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留 JNI 调用的入口类
-keep class com.example.localai.engine.** { *; }