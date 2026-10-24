package com.gathin.weather.network

import com.gathin.weather.model.WeatherResponse
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object WeatherApi {
    private const val BASE_URL = "https://api.openweathermap.org/data/2.5/"
    private const val GEO_URL = "http://ip-api.com/json/"

    val gson = GsonBuilder().create()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.NONE))
        .build()

    fun fetchWeather(lat: Double, lon: Double, apiKey: String, units: String): WeatherResponse? {
        val url = "${BASE_URL}weather?lat=$lat&lon=$lon&appid=$apiKey&units=$units&lang=en"
        val request = Request.Builder().url(url).build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()?.let { gson.fromJson(it, WeatherResponse::class.java) }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun fetchLocationByIp(): IpLocation? {
        val request = Request.Builder().url(GEO_URL).build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()?.let { gson.fromJson(it, IpLocation::class.java) }
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    data class IpLocation(
        val lat: Double,
        val lon: Double,
        val city: String?,
        val country: String?,
        val status: String
    ) {
        fun isValid(): Boolean = status == "success" && lat != 0.0 && lon != 0.0
    }
}