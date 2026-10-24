package com.gathin.weather.repository

import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import com.gathin.weather.model.WeatherDisplayData
import com.gathin.weather.model.WeatherResponse
import com.gathin.weather.network.WeatherApi
import com.gathin.weather.prefs.WeatherPrefs
import com.gathin.weather.utils.WeatherUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class WeatherRepository(private val app: Application) {

    private val prefs = WeatherPrefs
    private val scope = CoroutineScope(Dispatchers.IO)

    suspend fun getCurrentWeather(): Result<WeatherDisplayData> {
        val apiKey = prefs.getApiKey(app)
        if (apiKey.isNullOrBlank()) {
            return Result.failure(Exception("No API key"))
        }

        val cached = prefs.getCachedWeather(app)
        if (cached != null) {
            val displayData = WeatherUtils.parseDisplayData(cached, prefs.getUnits(app))
            if (displayData != null) {
                return Result.success(displayData)
            }
        }

        return withContext(Dispatchers.IO) {
            val location = getLocation()
            location?.let { (lat, lon) ->
                val response = WeatherApi.fetchWeather(lat, lon, apiKey, prefs.getUnits(app).key)
                response?.let {
                    val json = WeatherApi.gson.toJson(it)
                    prefs.setCachedWeather(app, json)
                    val displayData = WeatherUtils.parseDisplayData(json, prefs.getUnits(app))
                    displayData?.let { Result.success(it) } ?: Result.failure(Exception("Parse failed"))
                } ?: Result.failure(Exception("API failed"))
            } ?: Result.failure(Exception("No location"))
        }
    }

    private fun getLocation(): Pair<Double, Double>? {
        val mode = prefs.getLocationMode(app)
        return when (mode) {
            WeatherPrefs.LocationMode.GPS -> getGpsLocation()
            WeatherPrefs.LocationMode.IP -> getIpLocation()
            WeatherPrefs.LocationMode.MANUAL -> getManualLocation()
        }
    }

    private fun getGpsLocation(): Pair<Double, Double>? {
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (provider in providers) {
            try {
                val loc = lm.getLastKnownLocation(provider)
                if (loc != null) {
                    prefs.setLastLocation(app, loc.latitude, loc.longitude)
                    return Pair(loc.latitude, loc.longitude)
                }
            } catch (e: SecurityException) {
            }
        }
        return prefs.getLastLocation(app)
    }

    private fun getIpLocation(): Pair<Double, Double>? {
        val ipLoc = WeatherApi.fetchLocationByIp()
        if (ipLoc != null && ipLoc.isValid()) {
            prefs.setLastLocation(app, ipLoc.lat, ipLoc.lon)
            ipLoc.city?.let { city ->
                ipLoc.country?.let { country ->
                    prefs.setLastCity(app, city, country)
                }
            }
            return Pair(ipLoc.lat, ipLoc.lon)
        }
        return prefs.getLastLocation(app)
    }

    private fun getManualLocation(): Pair<Double, Double>? {
        val city = prefs.getManualCity(app)
        if (city.isNullOrBlank()) {
            return prefs.getLastLocation(app)
        }
        return getIpLocation() ?: prefs.getLastLocation(app)
    }

    fun schedulePeriodicSync() {
        scope.launch {
            val interval = prefs.getRefreshInterval(app)
            WeatherSyncWorker.schedulePeriodicSync(app, interval.minutes)
        }
    }

    fun testApiKey(apiKey: String): Boolean = runBlocking {
        val ipLoc = WeatherApi.fetchLocationByIp()
        ipLoc?.isValid() == true && WeatherApi.fetchWeather(ipLoc.lat, ipLoc.lon, apiKey, "metric") != null
    }
}

import kotlinx.coroutines.runBlocking