package com.youmak.ps4led

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class LedMonitorService : Service() {
    companion object {
        const val ACTION_STATUS = "com.youmak.ps4led.STATUS"
        const val EXTRA_STATUS = "status"
        private const val CHANNEL_ID = "ps4_led_monitor"
        private const val NOTIFICATION_ID = 1001
    }

    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var lastBase: String? = null
    private var firstRun = true

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = buildNotification("مراقبة يد PS4 مفعّلة")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        executor.scheduleWithFixedDelay(::check, 0, 1000, TimeUnit.MILLISECONDS)
    }

    private fun check() {
        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(MainActivity.KEY_AUTO, true)) return
        val base = RootShell.findPs4Led()
        val rgb = intArrayOf(
            prefs.getInt(MainActivity.KEY_R, 0),
            prefs.getInt(MainActivity.KEY_G, 0),
            prefs.getInt(MainActivity.KEY_B, 80)
        )

        if (base == null) {
            if (lastBase != null || firstRun) {
                firstRun = false
                lastBase = null
                sendStatus("لا توجد يد PS4 متصلة")
                updateNotification("في انتظار يد PS4…")
            }
            return
        }

        if (base != lastBase || firstRun) {
            firstRun = false
            val result = RootShell.setRgb(base, rgb[0], rgb[1], rgb[2])
            lastBase = if (result.exitCode == 0) base else null
            if (result.exitCode == 0) {
                sendStatus("تم تطبيق اللون — ${base.substringAfterLast('.')}")
                updateNotification("تم تطبيق اللون على اليد")
            } else {
                sendStatus("فشل تطبيق اللون: ${result.output}")
            }
        }
    }

    private fun sendStatus(status: String) {
        sendBroadcast(Intent(ACTION_STATUS).putExtra(EXTRA_STATUS, status))
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PS4 LED Controller",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "مراقبة اتصال يد PS4 وتطبيق اللون تلقائيًا"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("PS4 LED Controller")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
