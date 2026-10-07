package com.youmak.ps4led

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.text.InputFilter
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit

class MainActivity : ComponentActivity() {
    companion object {
        const val PREFS = "ps4_led"
        const val KEY_R = "r"
        const val KEY_G = "g"
        const val KEY_B = "b"
        const val KEY_AUTO = "auto"
        private const val REQ_NOTIFICATIONS = 50
        private const val REQ_BLUETOOTH = 51
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var picker: ColorPickerView
    private lateinit var preview: View
    private lateinit var rgbText: TextView
    private lateinit var hexText: TextView
    private lateinit var statusText: TextView
    private lateinit var detailsText: TextView
    private lateinit var batteryText: TextView
    private lateinit var autoSwitch: Switch
    private lateinit var redInput: EditText
    private lateinit var greenInput: EditText
    private lateinit var blueInput: EditText
    private lateinit var brightnessSlider: BrightnessSliderView
    private lateinit var brightnessValueText: TextView
    private lateinit var brightnessLeftButton: Button
    private lateinit var brightnessRightButton: Button
    private var updatingInputs = false
    private var lastBrightnessValue = 0
    private var brightnessBase = ColorPickerView.Rgb(1, 1, 80)
    private var brightnessStartValue = 0
    private var brightnessSessionActive = false
    private var applyingBrightness = false
    private var current = ColorPickerView.Rgb(1, 1, 80)
    private val batteryHandler = Handler(Looper.getMainLooper())
    private val batteryUpdater = object : Runnable {
        override fun run() {
            Thread {
                val capacity = RootShell.findSonyControllerBattery()
                runOnUiThread {
                    batteryText.text = if (capacity != null) {
                        "نسبة البطارية : ${capacity}%"
                    } else {
                        "نسبة البطارية : --%"
                    }
                }
            }.start()
            batteryHandler.postDelayed(this, 2000L)
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LedMonitorService.ACTION_STATUS) {
                statusText.text = "الحالة: ${intent.getStringExtra(LedMonitorService.EXTRA_STATUS) ?: "غير معروف"}"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

        picker = findViewById(R.id.colorPicker)
        preview = findViewById(R.id.colorPreview)
        rgbText = findViewById(R.id.rgbText)
        hexText = findViewById(R.id.hexText)
        statusText = findViewById(R.id.statusText)
        detailsText = findViewById(R.id.detailsText)
        batteryText = findViewById(R.id.batteryText)
        autoSwitch = findViewById(R.id.autoSwitch)
        redInput = findViewById(R.id.redInput)
        greenInput = findViewById(R.id.greenInput)
        blueInput = findViewById(R.id.blueInput)
        brightnessSlider = findViewById(R.id.brightnessSlider)
        brightnessValueText = findViewById(R.id.brightnessValueText)
        brightnessLeftButton = findViewById(R.id.brightnessLeftButton)
        brightnessRightButton = findViewById(R.id.brightnessRightButton)
        val savedRaw = ColorPickerView.Rgb(
            prefs.getInt(KEY_R, 1),
            prefs.getInt(KEY_G, 1),
            prefs.getInt(KEY_B, 80)
        )
        val applyButton: Button = findViewById(R.id.applyButton)
        val rootButton: Button = findViewById(R.id.testRootButton)

        listInputSetup()

        picker.setRgb(savedRaw.r, savedRaw.g, savedRaw.b)
        autoSwitch.isChecked = prefs.getBoolean(KEY_AUTO, true)

        picker.setOnColorChangedListener { rgb ->
            if (!applyingBrightness) {
                current = rgb
                brightnessBase = rgb
                brightnessSessionActive = false
                lastBrightnessValue = 0
                brightnessSlider.setValue(0)
                brightnessValueText.text = "0"
                prefs.edit {
                    putInt(KEY_R, rgb.r)
                    putInt(KEY_G, rgb.g)
                    putInt(KEY_B, rgb.b)
                }
                updateColorUi(rgb)
                updateRgbInputs(rgb)
            }
        }

        // Restore the raw brightness-adjusted values after the picker has initialized.
        // The picker itself is still limited to RGB 1..255 visually.
        current = savedRaw
        brightnessBase = savedRaw
        prefs.edit {
            putInt(KEY_R, savedRaw.r)
            putInt(KEY_G, savedRaw.g)
            putInt(KEY_B, savedRaw.b)
        }
        updateColorUi(savedRaw)
        updateRgbInputs(savedRaw)

        brightnessSlider.setOnDragStateChangedListener { dragging ->
            if (dragging) {
                brightnessBase = current
                brightnessStartValue = brightnessSlider.getValue()
                brightnessSessionActive = true
            } else {
                brightnessSessionActive = false
            }
        }

        brightnessSlider.setOnValueChangedListener { sliderValue ->
            if (!brightnessSessionActive) {
                brightnessBase = current
                brightnessStartValue = lastBrightnessValue
                brightnessSessionActive = true
            }

            val delta = sliderValue - brightnessStartValue
            if (sliderValue == lastBrightnessValue && delta == 0) {
                brightnessValueText.text = if (sliderValue >= 0) "+$sliderValue" else "$sliderValue"
                return@setOnValueChangedListener
            }
            lastBrightnessValue = sliderValue

            // Keep the real adjusted values, including values below 0.
            // Only the picker/preview representation is limited to RGB 1..255.
            val next = ColorPickerView.Rgb(
                brightnessBase.r + delta,
                brightnessBase.g + delta,
                brightnessBase.b + delta
            )
            current = next
            prefs.edit {
                putInt(KEY_R, next.r)
                putInt(KEY_G, next.g)
                putInt(KEY_B, next.b)
            }

            applyingBrightness = true
            picker.setRgb(next.r, next.g, next.b)
            applyingBrightness = false

            updateColorUi(next)
            updateRgbInputs(next)
            brightnessValueText.text = if (sliderValue >= 0) "+$sliderValue" else "$sliderValue"
        }

        brightnessLeftButton.setOnClickListener {
            brightnessSlider.changeBy(-1)
        }
        brightnessRightButton.setOnClickListener {
            brightnessSlider.changeBy(1)
        }

        autoSwitch.setOnCheckedChangeListener { _, enabled ->
            prefs.edit { putBoolean(KEY_AUTO, enabled) }
            if (enabled) startMonitor() else stopMonitor()
        }

        applyButton.setOnClickListener { applyNow() }
        rootButton.setOnClickListener { scanRootAndLed() }

        if (autoSwitch.isChecked) startMonitor()
        scanRootAndLed()
        batteryHandler.post(batteryUpdater)
        requestNotificationPermission()
        requestBluetoothPermission()
    }

    override fun onStart() {
        super.onStart()
        batteryHandler.removeCallbacks(batteryUpdater)
        batteryHandler.post(batteryUpdater)
        ContextCompat.registerReceiver(
            this,
            statusReceiver,
            IntentFilter(LedMonitorService.ACTION_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        batteryHandler.removeCallbacks(batteryUpdater)
        try { unregisterReceiver(statusReceiver) } catch (_: Exception) { }
        super.onStop()
    }

    private fun listInputSetup() {
        val inputs = arrayOf(redInput, greenInput, blueInput)
        inputs.forEach { input ->
            input.filters = arrayOf(InputFilter.LengthFilter(3))
            input.setSelectAllOnFocus(true)
            input.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    applyManualRgb()
                    true
                } else {
                    false
                }
            }
        }

        findViewById<Button>(R.id.setRgbButton).setOnClickListener { applyManualRgb() }
    }

    private fun updateRgbInputs(rgb: ColorPickerView.Rgb) {
        if (updatingInputs) return
        updatingInputs = true
        redInput.setText(rgb.r.toString())
        greenInput.setText(rgb.g.toString())
        blueInput.setText(rgb.b.toString())
        if (!brightnessSlider.isDragging() && !brightnessSessionActive) {
            brightnessSlider.setValue(0)
            lastBrightnessValue = 0
            brightnessValueText.text = "0"
        }
        updatingInputs = false
    }

    private fun applyManualRgb() {
        fun read(input: EditText, fallback: Int): Int {
            return input.text.toString().trim().toIntOrNull()?.coerceIn(0, 255) ?: fallback
        }

        val rgb = ColorPickerView.Rgb(
            read(redInput, current.r).coerceAtLeast(1),
            read(greenInput, current.g).coerceAtLeast(1),
            read(blueInput, current.b).coerceAtLeast(1)
        )
        picker.setRgb(rgb.r, rgb.g, rgb.b)
    }

    private fun updateColorUi(rgb: ColorPickerView.Rgb) {
        val shown = ColorPickerView.Rgb(
            rgb.r.coerceIn(1, 255),
            rgb.g.coerceIn(1, 255),
            rgb.b.coerceIn(1, 255)
        )
        preview.setBackgroundColor(android.graphics.Color.rgb(shown.r, shown.g, shown.b))
        rgbText.text = "RGB: ${rgb.r}, ${rgb.g}, ${rgb.b}"
        hexText.text = "#%02X%02X%02X".format(shown.r, shown.g, shown.b)
    }

    private fun startMonitor() {
        val intent = Intent(this, LedMonitorService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(this, intent)
            else startService(intent)
            statusText.text = "الحالة: المراقبة التلقائية مفعّلة"
        } catch (e: Exception) {
            statusText.text = "الحالة: تعذر تشغيل المراقبة: ${e.message}"
        }
    }

    private fun stopMonitor() {
        stopService(Intent(this, LedMonitorService::class.java))
        statusText.text = "الحالة: المراقبة التلقائية متوقفة"
    }

    private fun convertChannelForApply(value: Int): Int {
        // 1.9921875 is exactly 255 / 128, so this performs
        // value / 1.9921875 as integer division without floating-point rounding.
        return (value * 128) / 255
    }

    private fun applyNow() {
        statusText.text = "الحالة: جارٍ تطبيق اللون…"
        Thread {
            val root = RootShell.hasRoot()
            val base = if (root) RootShell.findPs4Led() else null
            runOnUiThread {
                if (!root) {
                    statusText.text = "الحالة: لم يتم الحصول على Root"
                    detailsText.text = "تأكد أن التطبيق مسموح له بـ su من KernelSU."
                    return@runOnUiThread
                }
                if (base == null) {
                    statusText.text = "الحالة: لم يتم العثور على يد PS4"
                    detailsText.text = "المسار المتوقع: /sys/class/leds/0005:054C:*"
                    return@runOnUiThread
                }
                Thread {
                    val result = RootShell.setRgb(
                        base,
                        convertChannelForApply(current.r),
                        convertChannelForApply(current.g),
                        convertChannelForApply(current.b)
                    )
                    runOnUiThread {
                        if (result.exitCode == 0) {
                            statusText.text = "الحالة: تم تطبيق اللون"
                            detailsText.text = "تم اكتشاف: $base"
                        } else {
                            statusText.text = "الحالة: فشل التطبيق"
                            detailsText.text = result.output
                        }
                    }
                }.start()
            }
        }.start()
    }

    private fun scanRootAndLed() {
        statusText.text = "الحالة: جارٍ الفحص…"
        Thread {
            val rootResult = RootShell.exec("id")
            val base = if (rootResult.exitCode == 0) RootShell.findPs4Led() else null
            runOnUiThread {
                when {
                    rootResult.exitCode != 0 -> {
                        statusText.text = "الحالة: Root غير متاح"
                        detailsText.text = "su: ${rootResult.output.ifBlank { "رفض أو غير موجود" }}"
                    }
                    base == null -> {
                        statusText.text = "الحالة: Root متاح — اليد غير مكتشفة"
                        detailsText.text = "في انتظار /sys/class/leds/0005:054C:*"
                    }
                    else -> {
                        statusText.text = "الحالة: اليد متصلة"
                        detailsText.text = "LED base: $base"
                    }
                }
            }
        }.start()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
        }
    }

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            val missing = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) {
                requestPermissions(missing.toTypedArray(), REQ_BLUETOOTH)
            }
        }
    }
}
