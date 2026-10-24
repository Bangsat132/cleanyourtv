package com.gathin.weather.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

object WeatherPrefs {
    private const val PREF_API_KEY = "api_key"
    private const val PREF_LOCATION_MODE = "location_mode"
    private const val PREF_MANUAL_CITY = "manual_city"
    private const val PREF_UNITS = "units"
    private const val PREF_REFRESH_INTERVAL = "refresh_interval"
    private const val PREF_LAST_LAT = "last_lat"
    private const val PREF_LAST_LON = "last_lon"
    private const val PREF_LAST_CITY = "last_city"
    private const val PREF_LAST_COUNTRY = "last_country"
    private const val PREF_CACHED_WEATHER = "cached_weather"
    private const val PREF_CACHED_TIMESTAMP = "cached_timestamp"

    enum class LocationMode(val key: String) {
        GPS("gps"),
        IP("ip"),
        MANUAL("manual")
    }

    enum class Units(val key: String, val label: String) {
        METRIC("metric", "°C"),
        IMPERIAL("imperial", "°F")
    }

    enum class RefreshInterval(val minutes: Int, val key: String) {
        MIN_5(5, "5"),
        MIN_15(15, "15"),
        MIN_30(30, "30"),
        MIN_60(60, "60")
    }

    fun getApiKey(context: Context): String? {
        return getPrefs(context).getString(PREF_API_KEY, null)
    }

    fun setApiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(PREF_API_KEY, key).apply()
    }

    fun getLocationMode(context: Context): LocationMode {
        val key = getPrefs(context).getString(PREF_LOCATION_MODE, LocationMode.GPS.key)
        return LocationMode.values().firstOrNull { it.key == key } ?: LocationMode.GPS
    }

    fun setLocationMode(context: Context, mode: LocationMode) {
        getPrefs(context).edit().putString(PREF_LOCATION_MODE, mode.key).apply()
    }

    fun getManualCity(context: Context): String? {
        return getPrefs(context).getString(PREF_MANUAL_CITY, null)
    }

    fun setManualCity(context: Context, city: String) {
        getPrefs(context).edit().putString(PREF_MANUAL_CITY, city).apply()
    }

    fun getUnits(context: Context): Units {
        val key = getPrefs(context).getString(PREF_UNITS, Units.METRIC.key)
        return Units.values().firstOrNull { it.key == key } ?: Units.METRIC
    }

    fun setUnits(context: Context, units: Units) {
        getPrefs(context).edit().putString(PREF_UNITS, units.key).apply()
    }

    fun getRefreshInterval(context: Context): RefreshInterval {
        val key = getPrefs(context).getString(PREF_REFRESH_INTERVAL, RefreshInterval.MIN_5.key)
        return RefreshInterval.values().firstOrNull { it.key == key } ?: RefreshInterval.MIN_5
    }

    fun setRefreshInterval(context: Context, interval: RefreshInterval) {
        getPrefs(context).edit().putString(PREF_REFRESH_INTERVAL, interval.key).apply()
    }

    fun getLastLocation(context: Context): Pair<Double, Double>? {
        val prefs = getPrefs(context)
        val lat = prefs.getFloat(PREF_LAST_LAT, Float.NaN)
        val lon = prefs.getFloat(PREF_LAST_LON, Float.NaN)
        return if (lat.isNaN() || lon.isNaN()) null else Pair(lat.toDouble(), lon.toDouble())
    }

    fun setLastLocation(context: Context, lat: Double, lon: Double) {
        getPrefs(context).edit()
            .putFloat(PREF_LAST_LAT, lat.toFloat())
            .putFloat(PREF_LAST_LON, lon.toFloat())
            .apply()
    }

    fun getLastCity(context: Context): Pair<String, String>? {
        val prefs = getPrefs(context)
        val city = prefs.getString(PREF_LAST_CITY, null)
        val country = prefs.getString(PREF_LAST_COUNTRY, null)
        return if (city != null && country != null) Pair(city, country) else null
    }

    fun setLastCity(context: Context, city: String, country: String) {
        getPrefs(context).edit()
            .putString(PREF_LAST_CITY, city)
            .putString(PREF_LAST_COUNTRY, country)
            .apply()
    }

    fun getCachedWeather(context: Context): String? {
        val prefs = getPrefs(context)
        val timestamp = prefs.getLong(PREF_CACHED_TIMESTAMP, 0)
        val weather = prefs.getString(PREF_CACHED_WEATHER, null)
        if (weather != null && (System.currentTimeMillis() - timestamp) < 10 * 60 * 1000) {
            return weather
        }
        return null
    }

    fun setCachedWeather(context: Context, weatherJson: String) {
        getPrefs(context).edit()
            .putString(PREF_CACHED_WEATHER, weatherJson)
            .putLong(PREF_CACHED_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    private fun getPrefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)
}