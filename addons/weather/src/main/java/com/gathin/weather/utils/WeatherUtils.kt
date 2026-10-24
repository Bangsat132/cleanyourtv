package com.gathin.weather.utils

import android.content.Context
import com.gathin.weather.model.WeatherDisplayData
import com.gathin.weather.model.WeatherResponse
import com.gathin.weather.prefs.WeatherPrefs
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object WeatherUtils {
    private val gson = Gson()

    fun parseDisplayData(json: String, units: WeatherPrefs.Units): WeatherDisplayData? {
        return try {
            val response = gson.fromJson(json, WeatherResponse::class.java)
            parseResponse(response, units)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseResponse(response: WeatherResponse, units: WeatherPrefs.Units): WeatherDisplayData? {
        if (response.cod != 200 || response.weather.isEmpty()) return null

        val w = response.weather[0]
        val tempUnit = if (units == WeatherPrefs.Units.METRIC) "°C" else "°F"
        val windUnit = if (units == WeatherPrefs.Units.METRIC) "m/s" else "mph"

        val isDay = isDaytime(response.dt, response.timezone, response.sys.sunrise, response.sys.sunset)

        return WeatherDisplayData(
            cityName = "${response.name}, ${response.sys.country}",
            temperature = "${response.main.temp.roundToInt()}$tempUnit",
            condition = w.main,
            description = w.description.replaceFirstChar { it.uppercase() },
            humidity = "${response.main.humidity}%",
            windSpeed = "${response.wind.speed.roundToInt()} $windUnit",
            iconCode = w.icon,
            lastUpdated = response.dt * 1000L,
            isDay = isDay
        )
    }

    private fun isDaytime(dt: Long, timezone: Int, sunrise: Long, sunset: Long): Boolean {
        val now = dt + timezone
        return now >= sunrise && now < sunset
    }

    fun getWeatherIconResource(iconCode: String, isDay: Boolean): Int {
        val prefix = iconCode.take(2)
        val suffix = if (isDay) "d" else "n"
        return when ("$prefix$suffix") {
            "01d" -> R.drawable.ic_weather_01d
            "01n" -> R.drawable.ic_weather_01n
            "02d" -> R.drawable.ic_weather_02d
            "02n" -> R.drawable.ic_weather_02n
            "03d", "03n" -> R.drawable.ic_weather_03
            "04d", "04n" -> R.drawable.ic_weather_04
            "09d", "09n" -> R.drawable.ic_weather_09
            "10d" -> R.drawable.ic_weather_10d
            "10n" -> R.drawable.ic_weather_10n
            "11d", "11n" -> R.drawable.ic_weather_11
            "13d", "13n" -> R.drawable.ic_weather_13
            "50d", "50n" -> R.drawable.ic_weather_50
            else -> R.drawable.ic_weather_01d
        }
    }
}