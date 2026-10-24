# Keep card service for launcher binding
-keep class com.gathin.weather.WeatherCardService { *; }

# Keep WeatherApp for Application subclass
-keep class com.gathin.weather.WeatherApp { *; }

# Keep WeatherRepository and related
-keep class com.gathin.weather.repository.** { *; }
-keep class com.gathin.weather.network.** { *; }
-keep class com.gathin.weather.model.** { *; }
-keep class com.gathin.weather.prefs.** { *; }
-keep class com.gathin.weather.utils.** { *; }

# Keep SettingsActivity
-keep class com.gathin.weather.SettingsActivity { *; }
-keep class com.gathin.weather.SettingsActivity$SettingsFragment { *; }

# Keep BroadcastReceivers
-keep class com.gathin.weather.BootReceiver { *; }
-keep class com.gathin.weather.SyncReceiver { *; }
-keep class com.gathin.weather.WeatherSyncWorker { *; }

# OkHttp/Gson
-keep class okhttp3.** { *; }
-keep class com.google.gson.** { *; }
-keepattributes Signature,Annotation,EnclosingMethod

# Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }