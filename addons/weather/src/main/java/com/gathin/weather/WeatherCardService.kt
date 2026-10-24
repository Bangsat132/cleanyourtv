package com.gathin.weather

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import com.gathin.weather.model.WeatherDisplayData
import com.gathin.weather.repository.WeatherRepository
import com.gathin.tvhome.card.CardService
import com.gathin.tvhome.card.CardUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WeatherCardService : Service() {

    private lateinit var repository: WeatherRepository
    private val scope = CoroutineScope(Dispatchers.IO)
    private var currentCallback: CardService.Callback? = null

    override fun onCreate() {
        super.onCreate()
        repository = WeatherRepository(application)
    }

    override fun onBind(intent: Intent): IBinder {
        return object : CardService.Stub() {
            override fun registerCallback(callback: CardService.Callback) {
                currentCallback = callback
                sendUpdate()
            }

            override fun unregisterCallback(callback: CardService.Callback) {
                if (currentCallback == callback) currentCallback = null
            }

            override fun refresh() {
                sendUpdate()
            }

            override fun onSettingsChanged() {
                sendUpdate()
            }
        }
    }

    private fun sendUpdate() {
        scope.launch {
            val result = repository.getCurrentWeather().await()
            val update = when (result) {
                is Result.Success -> buildCardUpdate(result.value)
                is Result.Failure -> buildErrorUpdate(result.exception.message ?: "Unknown error")
            }
            try {
                currentCallback?.onCardUpdate(update)
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
        }
    }

    private fun buildCardUpdate(data: WeatherDisplayData): CardUpdate {
        return CardUpdate.Builder()
            .setTitle(data.cityName)
            .setSubtitle("${data.temperature} • ${data.condition}")
            .setBody(data.description)
            .setIconResId(getWeatherIconRes(data.iconCode, data.isDay))
            .setTimestamp(data.lastUpdated)
            .setAction("weather_details")
            .build()
    }

    private fun buildErrorUpdate(message: String): CardUpdate {
        return CardUpdate.Builder()
            .setTitle("Weather")
            .setSubtitle("Unavailable")
            .setBody(message)
            .setIconResId(R.drawable.ic_weather_error)
            .setTimestamp(System.currentTimeMillis())
            .build()
    }

    private fun getWeatherIconRes(iconCode: String, isDay: Boolean): Int {
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