package com.zylinn.petdiary.ui.reminders

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Notification-permission and battery-saver helpers shown on the reminders page (P4-07 / P4-08). */
object ReminderTips {

    private const val PREFS = "petdiary_prefs"
    private const val KEY_ASKED = "notification_permission_asked"
    private const val KEY_BATTERY_DISMISSED = "battery_tip_dismissed"

    /** Makers whose battery managers are known to kill background apps and delay alarms. */
    private val aggressiveMakers = setOf("oppo", "realme", "oneplus", "samsung", "xiaomi", "redmi", "huawei", "honor", "vivo")

    enum class Brand(val steps: String) {
        COLOROS("設定 → 電池 → 更多電池設定（或「應用耗電管理」）→ PetDiary → 開啟「允許背景活動」與「允許自動啟動」。"),
        ONE_UI("設定 → 電池 → 背景使用限制 → 「永不自動休眠的應用程式」→ 加入 PetDiary。"),
        OTHER("設定 → 電池 → 找到 PetDiary，改成「不限制」或加入省電白名單。"),
    }

    fun brand(maker: String = Build.MANUFACTURER): Brand = when (maker.lowercase()) {
        "oppo", "realme", "oneplus" -> Brand.COLOROS
        "samsung" -> Brand.ONE_UI
        else -> Brand.OTHER
    }

    fun hasNotificationPermission(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun needsBatteryTip(context: Context, maker: String = Build.MANUFACTURER) =
        maker.lowercase() in aggressiveMakers && !prefs(context).getBoolean(KEY_BATTERY_DISMISSED, false)

    fun dismissBatteryTip(context: Context) = prefs(context).edit().putBoolean(KEY_BATTERY_DISMISSED, true).apply()

    /** The system dialog is only requested once; afterwards the page's banner explains how to enable it. */
    fun alreadyAsked(context: Context) = prefs(context).getBoolean(KEY_ASKED, false)

    fun markAsked(context: Context) = prefs(context).edit().putBoolean(KEY_ASKED, true).apply()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun openBatterySettings(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
        )
        intents.firstNotNullOfOrNull { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.getOrNull() }
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
