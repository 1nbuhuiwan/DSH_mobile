# ZXing 核心：保留反射/序列化用到的类
-keep class com.google.zxing.** { *; }

# 保留下划线方法名（Kotlin 生成的同步方法）
-keepclassmembers class * {
    *** get*();
}

# DataBinding / ViewBinding 生成的类名
-keep class com.dsh.mobile.databinding.** { *; }

# 关键：保留给网页 JS 调用的桥方法（addJavascriptInterface 按方法名反射调用，
# R8 混淆会把 vibrate/toast/promptForText 改名，导致网页端失效）
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.dsh.mobile.MainActivity$DSHBridge { *; }
