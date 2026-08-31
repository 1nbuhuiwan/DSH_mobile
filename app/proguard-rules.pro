# ZXing 核心：保留反射/序列化用到的类
-keep class com.google.zxing.** { *; }

# 保留下划线方法名（Kotlin 生成的同步方法）
-keepclassmembers class * {
    *** get*();
}

# DataBinding / ViewBinding 生成的类名
-keep class com.dsh.mobile.databinding.** { *; }
