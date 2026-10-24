package com.gathin.weather

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import com.gathin.weather.prefs.WeatherPrefs

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            val apiKeyPref = findPreference<EditTextPreference>("api_key")
            val locationModePref = findPreference<ListPreference>("location_mode")
            val manualCityPref = findPreference<EditTextPreference>("manual_city")
            val unitsPref = findPreference<ListPreference>("units")
            val refreshPref = findPreference<ListPreference>("refresh_interval")

            apiKeyPref?.setOnPreferenceChangeListener { _, newValue ->
                val key = newValue as String
                if (key.isNotBlank()) {
                    WeatherPrefs.setApiKey(requireContext(), key)
                    testApiKey(key)
                }
                true
            }
            apiKeyPref?.summary = WeatherPrefs.getApiKey(requireContext())?.let { "Set: ${it.take(8)}..." } ?: "Not set"

            locationModePref?.setOnPreferenceChangeListener { _, newValue ->
                val mode = WeatherPrefs.LocationMode.values().firstOrNull { it.key == newValue }
                mode?.let { WeatherPrefs.setLocationMode(requireContext(), it) }
                updateManualCityVisibility()
                true
            }
            locationModePref?.value = WeatherPrefs.getLocationMode(requireContext()).key

            manualCityPref?.setOnPreferenceChangeListener { _, newValue ->
                WeatherPrefs.setManualCity(requireContext(), newValue as String)
                true
            }
            manualCityPref?.summary = WeatherPrefs.getManualCity(requireContext()) ?: "Not set"

            unitsPref?.setOnPreferenceChangeListener { _, newValue ->
                val units = WeatherPrefs.Units.values().firstOrNull { it.key == newValue }
                units?.let { WeatherPrefs.setUnits(requireContext(), it) }
                true
            }
            unitsPref?.value = WeatherPrefs.getUnits(requireContext()).key

            refreshPref?.setOnPreferenceChangeListener { _, newValue ->
                val interval = WeatherPrefs.RefreshInterval.values().firstOrNull { it.key == newValue }
                interval?.let {
                    WeatherPrefs.setRefreshInterval(requireContext(), it)
                    WeatherSyncWorker.cancelPeriodicSync(requireContext())
                    WeatherSyncWorker.schedulePeriodicSync(requireContext(), it.minutes)
                }
                true
            }
            refreshPref?.value = WeatherPrefs.getRefreshInterval(requireContext()).key

            updateManualCityVisibility()
        }

        private fun updateManualCityVisibility() {
            val mode = WeatherPrefs.getLocationMode(requireContext())
            val manualCityPref = findPreference<EditTextPreference>("manual_city")
            manualCityPref?.isVisible = (mode == WeatherPrefs.LocationMode.MANUAL)
        }

        private fun testApiKey(key: String) {
            val repo = WeatherRepository(requireContext().applicationContext as WeatherApp)
            lifecycleScope.launch {
                val result = repo.testApiKey(key)
                if (result) {
                    Toast.makeText(requireContext(), R.string.toast_api_ok, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), R.string.toast_api_fail, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}