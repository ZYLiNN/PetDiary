package com.zylinn.petdiary.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.zylinn.petdiary.MainActivity
import com.zylinn.petdiary.R

/**
 * Anyone holding an invitation can join without the owner's approval (D79), so the owner is told
 * "XXX 已加入家庭群組" and can remove a stranger right away. No server involved: the owner's phone compares
 * the member list with the one it saw last time.
 */
object JoinWatch {
    /** [known] null = this group seen for the first time on this phone: remember everyone, announce nobody. */
    fun newcomers(known: Set<String>?, state: FamilyState): List<Member> =
        if (known == null || !state.iAmOwner) emptyList() else state.members.filter { !it.isMe && it.uid !in known }
}

class JoinNotifier(private val context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun announce(member: Member) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "家庭群組", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "有人加入你的家庭群組時通知你" },
            )
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${member.name.ifBlank { "家人" }} 已加入家庭群組")
            .setContentText("如果不認識這個人，到 設定 → 家庭群組 移除。")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(member.uid.hashCode(), n)
    }

    companion object {
        const val CHANNEL = "family"
    }
}
