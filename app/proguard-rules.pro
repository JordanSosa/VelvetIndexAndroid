# The @JavascriptInterface bridge is called reflectively from JS.
-keepclassmembers class com.velvetindex.privateapp.MainActivity$Bridge {
    public *;
}
-keep class com.velvetindex.privateapp.MainActivity$Bridge { *; }
-keep class com.velvetindex.privateapp.MediaServer { *; }
-dontwarn org.json.**
