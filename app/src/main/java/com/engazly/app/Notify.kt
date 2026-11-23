package com.engazly.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import java.util.Calendar

object Notify {
    const val CH_REM = "reminders2"
    const val CH_ALARM = "alarms2"
    const val TIMER_ID = 1001

    fun channels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        val rem = NotificationChannel(CH_REM, tr("ch_rem"), NotificationManager.IMPORTANCE_HIGH)
        rem.enableVibration(true)
        val al = NotificationChannel(CH_ALARM, tr("ch_alarm"), NotificationManager.IMPORTANCE_HIGH)
        al.setSound(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        al.enableVibration(true)
        al.vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
        nm.createNotificationChannel(rem)
        nm.createNotificationChannel(al)
    }

    private fun pi(c: Context, req: Int, kind: String, title: String, text: String, rid: Int): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java)
        i.putExtra("kind", kind)
        i.putExtra("title", title)
        i.putExtra("text", text)
        i.putExtra("rid", rid)
        return PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** kind: "rem" (reminder), "alarm" (alarm after a duration), "timer" (countdown finished). */
    fun schedule(c: Context, req: Int, at: Long, kind: String, title: String, text: String, rid: Int) {
        val am = c.getSystemService(AlarmManager::class.java)
        val p = pi(c, req, kind, title, text, rid)
        if (kind == "rem") {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            }
        } else {
            val show = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), p)
        }
    }

    fun cancel(c: Context, req: Int) {
        val am = c.getSystemService(AlarmManager::class.java)
        val p = pi(c, req, "x", "", "", -1)
        am.cancel(p)
        p.cancel()
    }

    fun nextTime(h: Int, m: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, h)
        cal.set(Calendar.MINUTE, m)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis() + 1000) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun scheduleReminder(c: Context, r: Reminder) {
        if (r.enabled) schedule(c, r.id, nextTime(r.hour, r.minute), "rem", r.title, r.msg, r.id)
        else cancel(c, r.id)
    }

    fun rescheduleAll(c: Context) {
        Store.init(c)
        for (r in Store.reminders()) scheduleReminder(c, r)
        val now = System.currentTimeMillis()
        for (a in Store.alarms()) {
            if (a.at > now) schedule(c, a.id, a.at, "alarm", tr("alarm_title"), a.label, a.id)
        }
        if (Store.timerEnd > now) {
            schedule(c, TIMER_ID, Store.timerEnd, "timer", tr("timer_done"), tr("timer_done_text"), -1)
        }
    }

    fun show(c: Context, id: Int, kind: String, title: String, text: String) {
        channels(c)
        val nm = c.getSystemService(NotificationManager::class.java)
        val open = PendingIntent.getActivity(
            c, 0,
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val isRem = kind == "rem"
        val b = Notification.Builder(c, if (isRem) CH_REM else CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(if (isRem) Notification.CATEGORY_REMINDER else Notification.CATEGORY_ALARM)
        val n = b.build()
        if (!isRem) n.flags = n.flags or Notification.FLAG_INSISTENT
        nm.notify(id, n)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Store.init(c)
        val kind = i.getStringExtra("kind") ?: "rem"
        val title = i.getStringExtra("title") ?: ""
        val text = i.getStringExtra("text") ?: ""
        val rid = i.getIntExtra("rid", -1)
        Notify.show(c, (System.currentTimeMillis() % 100000000L).toInt(), kind, title, text)
        if (kind == "rem" && rid >= 0) {
            val all = Store.reminders()
            val r = all.firstOrNull { it.id == rid }
            if (r != null) {
                if (r.daily) {
                    Notify.scheduleReminder(c, r)
                } else {
                    r.enabled = false
                    Store.saveReminders(all)
                }
            }
        } else if (kind == "alarm" && rid >= 0) {
            val left = Store.alarms().filter { it.id != rid }
            Store.saveAlarms(left)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Notify.rescheduleAll(c)
    }
}
