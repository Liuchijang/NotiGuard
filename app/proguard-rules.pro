# Shizuku user-service class must survive shrinking (loaded reflectively by Shizuku).
-keep class com.local.notiguard.shizuku.** { *; }
-keep class rikka.shizuku.** { *; }
