package com.seongja.jarvis

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.roundToInt

/** One-time WeatherAPI key and coordinate setup with a live verification test. */
class WeatherSetupActivity : Activity() {
    private lateinit var store: SecureWeatherRegistry
    private lateinit var intelligence: WeatherIntelligence
    private lateinit var apiKeyInput: EditText
    private lateinit var latitudeInput: EditText
    private lateinit var longitudeInput: EditText
    private lateinit var enabledInput: CheckBox
    private lateinit var status: TextView
    private lateinit var saveAndFinish: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureWeatherRegistry(this)
        intelligence = WeatherIntelligence(this)
        setContentView(buildUi())
        populate(store.load())
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(28))
            setBackgroundColor(Color.rgb(6, 13, 20))
        }

        root.addView(TextView(this).apply {
            text = "JARVIS // WEATHER CORE"
            textSize = 24f
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(Color.rgb(88, 231, 211))
            setPadding(0, 0, 0, dp(8))
        })

        root.addView(TextView(this).apply {
            text = "Connect WeatherAPI.com once. Jarvis will use the saved latitude and longitude for verified current conditions, hourly rain probability, wind, temperature, sunrise, sunset, and official weather alerts."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(12))
        })

        root.addView(TextView(this).apply {
            text = "The API key and coordinates are encrypted with Android Keystore. The key never enters HELIX, conversation memory, logs, or GitHub."
            textSize = 12f
            setTextColor(Color.rgb(145, 205, 255))
            setPadding(0, 0, 0, dp(14))
        })

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = panelBackground(Color.rgb(11, 25, 36), Color.rgb(46, 139, 143))
        }

        apiKeyInput = EditText(this).apply {
            hint = "WeatherAPI key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = panelBackground(Color.rgb(17, 34, 47), Color.rgb(40, 82, 94))
        }
        panel.addView(apiKeyInput, matchWidth(bottom = 9))

        latitudeInput = coordinateInput("Latitude, for example 26.7271", signed = true)
        panel.addView(latitudeInput, matchWidth(bottom = 9))

        longitudeInput = coordinateInput("Longitude, for example 93.1479", signed = true)
        panel.addView(longitudeInput, matchWidth(bottom = 8))

        enabledInput = CheckBox(this).apply {
            text = "Weather intelligence enabled"
            setTextColor(Color.LTGRAY)
            isChecked = true
        }
        panel.addView(enabledInput)

        status = TextView(this).apply {
            text = "STATUS // NOT CONFIGURED"
            textSize = 13f
            setTextColor(Color.rgb(145, 205, 255))
            setPadding(0, dp(7), 0, dp(7))
        }
        panel.addView(status)

        panel.addView(Button(this).apply {
            text = "SAVE + TEST WEATHERAPI"
            setOnClickListener { saveAndTest() }
        }, matchWidth(bottom = 7))

        saveAndFinish = Button(this).apply {
            text = "SAVE WEATHER + CONTINUE"
            setOnClickListener { saveAndContinue() }
        }
        panel.addView(saveAndFinish, matchWidth())
        root.addView(panel, matchWidth(bottom = 14))

        root.addView(TextView(this).apply {
            text = "Weather refreshes approximately every 15 minutes while Jarvis is active. Severe alerts, active rain, strong wind, and dangerous heat can trigger a concise spoken advisory once per meaningful change."
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, 0, 0, dp(12))
        })

        root.addView(Button(this).apply {
            text = "RESET WEATHER CORE"
            setOnClickListener {
                store.clear()
                populate(store.load())
                status.text = "STATUS // RESET"
                Toast.makeText(this@WeatherSetupActivity, "Weather core reset.", Toast.LENGTH_SHORT).show()
            }
        }, matchWidth(bottom = 8))

        root.addView(Button(this).apply {
            text = "RETURN"
            setOnClickListener { finish() }
        }, matchWidth())

        return ScrollView(this).apply { addView(root) }
    }

    private fun coordinateInput(hintText: String, signed: Boolean): EditText = EditText(this).apply {
        hint = hintText
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
            if (signed) InputType.TYPE_NUMBER_FLAG_SIGNED else 0
        setTextColor(Color.WHITE)
        setHintTextColor(Color.GRAY)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = panelBackground(Color.rgb(17, 34, 47), Color.rgb(40, 82, 94))
    }

    private fun populate(settings: WeatherSettings) {
        apiKeyInput.setText(settings.apiKey)
        latitudeInput.setText(settings.latitude?.let { String.format(Locale.US, "%.6f", it) }.orEmpty())
        longitudeInput.setText(settings.longitude?.let { String.format(Locale.US, "%.6f", it) }.orEmpty())
        enabledInput.isChecked = settings.enabled
        status.text = "STATUS // ${settings.healthLabel()} // SUCCESS ${settings.successes} // FAIL ${settings.failures}"
    }

    private fun settingsFromInputs(): WeatherSettings? {
        val latitude = latitudeInput.text.toString().trim().toDoubleOrNull()
        val longitude = longitudeInput.text.toString().trim().toDoubleOrNull()
        val settings = store.load().copy(
            apiKey = apiKeyInput.text.toString().trim(),
            latitude = latitude,
            longitude = longitude,
            enabled = enabledInput.isChecked,
            lastError = "",
            lastStatusCode = 0
        )
        val error = when {
            settings.apiKey.isBlank() -> "Enter the WeatherAPI key."
            latitude == null || latitude !in -90.0..90.0 -> "Latitude must be between -90 and 90."
            longitude == null || longitude !in -180.0..180.0 -> "Longitude must be between -180 and 180."
            else -> null
        }
        if (error != null) {
            status.text = "STATUS // $error"
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            return null
        }
        return settings
    }

    private fun saveAndTest() {
        val settings = settingsFromInputs() ?: return
        store.save(settings)
        status.text = "STATUS // CONTACTING WEATHERAPI..."
        setControlsEnabled(false)
        Thread {
            val result = runCatching { intelligence.test(settings) }
            runOnUiThread {
                setControlsEnabled(true)
                result.onSuccess { snapshot ->
                    status.text = "STATUS // ONLINE // ${snapshot.icon()} ${snapshot.tempC.roundToInt()}°C ${snapshot.conditionText.uppercase(Locale.US)} // ${snapshot.locationLabel()}"
                    WeatherRuntime.refresh(force = true)
                }.onFailure { error ->
                    status.text = "STATUS // FAILED // ${(error.message ?: error.javaClass.simpleName).take(180)}"
                }
            }
        }.start()
    }

    private fun saveAndContinue() {
        val settings = settingsFromInputs() ?: return
        store.save(settings)
        markOnboardingComplete(this)
        WeatherRuntime.refresh(force = true)
        Toast.makeText(this, "Weather intelligence saved securely.", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun setControlsEnabled(enabled: Boolean) {
        apiKeyInput.isEnabled = enabled
        latitudeInput.isEnabled = enabled
        longitudeInput.isEnabled = enabled
        enabledInput.isEnabled = enabled
        saveAndFinish.isEnabled = enabled
    }

    private fun panelBackground(fill: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            setStroke(dp(1), stroke)
            cornerRadius = dp(12).toFloat()
        }

    private fun matchWidth(bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(bottom) }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val PREFS_NAME = "jarvis_weather_onboarding"
        private const val KEY_COMPLETED = "completed"

        fun isOnboardingComplete(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_COMPLETED, false)

        fun markOnboardingComplete(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_COMPLETED, true)
                .apply()
        }
    }
}
