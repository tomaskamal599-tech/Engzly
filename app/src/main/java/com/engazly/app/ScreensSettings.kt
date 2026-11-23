package com.engazly.app

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import kotlin.random.Random

fun MainActivity.notificationsAllowed(): Boolean =
    Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

fun MainActivity.reminderDialog(existing: Reminder?, preTitle: String, preMsg: String, preHour: Int) {
    var hh = existing?.hour ?: preHour
    var mm = existing?.minute ?: 0
    var daily = existing?.daily ?: true
    dialog(if (existing == null) tr("new_reminder") else tr("edit_reminder")) { d ->
        val t = edit(tr("rem_title"))
        t.setText(existing?.title ?: preTitle)
        val m = edit(tr("rem_msg"), multi = true)
        m.setText(existing?.msg ?: preMsg)
        add(t, mb = 8)
        add(m, mb = 10)

        val timeBtn = btn(tr("time") + ":  " + fmtTime(hh, mm), T.panel2, T.text, T.border) {}
        timeBtn.setOnClickListener {
            TimePickerDialog(this@reminderDialog, { _, h, mi ->
                hh = h
                mm = mi
                timeBtn.text = tr("time") + ":  " + fmtTime(h, mi)
            }, hh, mm, true).show()
        }
        add(timeBtn, mb = 10)
        add(toggleRow(tr("repeat_daily"), daily) { daily = it }, mb = 14)

        add(btn(tr("save")) {
            var title = t.text.toString().trim()
            if (title.isEmpty()) title = tr("reminder_default")
            val msg = m.text.toString().trim()
            val list = Store.reminders()
            if (existing == null) {
                val r = Reminder(Random.nextInt(2000, 1_900_000), title, msg, hh, mm, daily, true)
                list.add(r)
                Notify.scheduleReminder(this@reminderDialog, r)
            } else {
                val r = list.firstOrNull { it.id == existing.id }
                if (r != null) {
                    r.title = title
                    r.msg = msg
                    r.hour = hh
                    r.minute = mm
                    r.daily = daily
                    r.enabled = true
                    Notify.scheduleReminder(this@reminderDialog, r)
                }
            }
            Store.saveReminders(list)
            d.dismiss()
            render()
        })
    }
}

fun MainActivity.buildReminders() {
    val head = hbox()
    head.add(tv(tr("reminders_title"), 18f, T.accent, true), w = 0, weight = 1f)
    head.add(btn("+ " + tr("add"), T.red, Color.WHITE, T.redDark) { reminderDialog(null, "", "", 18) }, w = -2, h = -2)
    content.add(head, mb = 6)
    content.add(tv(tr("reminders_sub"), 12f, T.sub), mb = 12)

    if (!notificationsAllowed()) {
        val w = card(T.panel2, T.red)
        w.add(tv(tr("notif_off"), 13f, T.text, true), mb = 8)
        w.add(btn(tr("allow_notif"), T.red, Color.WHITE, T.redDark) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        })
        content.add(w, mb = 12)
    }
    if (Build.VERSION.SDK_INT >= 31 && !getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
        val w = card(T.panel2, T.amber)
        w.add(tv(tr("exact_off"), 13f, T.text, true), mb = 8)
        w.add(btn(tr("allow_exact"), T.amber, T.onAccent, T.border) {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + packageName))
                )
            } catch (e: Exception) {
            }
        })
        content.add(w, mb = 12)
    }

    content.add(tv(tr("quick_add"), 13f, T.sub, true), mb = 6)
    val tpl = listOf(
        Pair("w", tr("tpl_workout")),
        Pair("h", tr("tpl_water")),
        Pair("m", tr("tpl_meal")),
        Pair("s", tr("tpl_sleep"))
    )
    content.add(chipRow(tpl, "") {
        when (it) {
            "w" -> reminderDialog(null, tr("tpl_workout_t"), tr("tpl_workout_m"), 17)
            "h" -> reminderDialog(null, tr("tpl_water_t"), tr("tpl_water_m"), 11)
            "m" -> reminderDialog(null, tr("tpl_meal_t"), tr("tpl_meal_m"), 14)
            else -> reminderDialog(null, tr("tpl_sleep_t"), tr("tpl_sleep_m"), 23)
        }
    }, mb = 12)

    val list = Store.reminders()
    if (list.isEmpty()) {
        content.add(emptyCard(tr("reminders_empty")), mb = 12)
    }
    for (r in list) {
        val row = hbox()
        row.background = PixBg(T.panel, T.border, 0, dp(2).toFloat())
        row.setPadding(dp(10), dp(10), dp(10), dp(10))
        row.add(checkBox(r.enabled) {
            val all = Store.reminders()
            val x = all.firstOrNull { it.id == r.id }
            if (x != null) {
                x.enabled = !x.enabled
                Store.saveReminders(all)
                Notify.scheduleReminder(this, x)
            }
            render()
        }, w = dp(30), h = dp(30), me = 10)
        val mid = vbox()
        mid.add(tv(fmtTime(r.hour, r.minute), 20f, if (r.enabled) T.accent else T.sub, true))
        mid.add(tv(r.title, 14f, T.text, true), mt = 2)
        if (r.msg.isNotEmpty()) mid.add(tv(r.msg, 12f, T.sub), mt = 2)
        mid.add(tv(if (r.daily) tr("daily") else tr("once"), 11f, T.accent2, true), mt = 4)
        mid.setOnClickListener { reminderDialog(r, "", "", 18) }
        row.add(mid, w = 0, weight = 1f)
        row.add(iconBtn(Icons.TRASH, T.red) {
            confirm(tr("confirm_del")) {
                Notify.cancel(this, r.id)
                Store.saveReminders(Store.reminders().filter { it.id != r.id })
                render()
            }
        }, w = -2, h = -2, ms = 8)
        content.add(row, mb = 8)
    }

    content.add(btn(tr("test_notif"), T.panel2, T.text, T.border) {
        if (notificationsAllowed()) {
            Notify.show(this, 7, "rem", tr("test_title"), tr("test_text"))
        } else {
            toast(tr("notif_off"))
        }
    }, mt = 8)
}

fun MainActivity.buildSettings() {
    content.add(tv(tr("settings"), 18f, T.accent, true), mb = 12)

    // language
    val lc = card()
    lc.add(tv(tr("language"), 15f, T.accent, true), mb = 8)
    lc.add(chipRow(listOf(Pair("ar", "العربية"), Pair("en", "English")), Store.lang) {
        if (it != Store.lang) {
            Store.lang = it
            recreate()
        }
    })
    content.add(lc, mb = 12)

    // theme
    val tc = card()
    tc.add(tv(tr("theme"), 15f, T.accent, true), mb = 8)
    tc.add(
        chipRow(
            listOf(Pair("dark", tr("theme_dark")), Pair("light", tr("theme_light"))),
            if (Store.dark) "dark" else "light"
        ) {
            val wantDark = it == "dark"
            if (wantDark != Store.dark) {
                Store.dark = wantDark
                recreate()
            }
        })
    content.add(tc, mb = 12)

    // goal
    val gc = card()
    gc.add(tv(tr("my_goal"), 15f, T.accent, true), mb = 4)
    gc.add(tv(tr("goal_sub"), 12f, T.sub), mb = 10)
    gc.add(
        chipRow(
            listOf(
                Pair("lose", tr("goal_lose")),
                Pair("muscle", tr("goal_muscle")),
                Pair("maintain", tr("goal_maintain")),
                Pair("health", tr("goal_health")),
                Pair("custom", tr("goal_custom"))
            ), Store.goalType
        ) { Store.goalType = it }, mb = 10
    )
    val eg = edit(tr("goal_text_hint"), multi = true)
    eg.setText(Store.goalText)
    eg.onText { Store.goalText = it }
    gc.add(eg, mb = 8)
    val ek = edit(tr("cal_target"), number = true)
    ek.setText(Store.calTarget.toString())
    ek.onText { Store.calTarget = it.toIntOrNull() ?: 2000 }
    gc.add(ek)
    content.add(gc, mb = 12)

    // AI key
    val ac = card()
    ac.add(tv(tr("ai_title"), 15f, T.accent, true), mb = 4)
    ac.add(tv(tr("ai_sub"), 12f, T.sub), mb = 10)
    val ekey = edit(tr("api_key"), password = true)
    ekey.setText(Store.apiKey)
    ekey.onText { Store.apiKey = it.trim() }
    ac.add(ekey, mb = 8)
    val emod = edit(tr("model_name"))
    emod.setText(Store.model)
    emod.onText { Store.model = if (it.isBlank()) "claude-sonnet-5-5" else it.trim() }
    ac.add(emod)
    content.add(ac, mb = 12)

    // permissions
    val pc = card()
    pc.add(tv(tr("permissions"), 15f, T.accent, true), mb = 8)
    pc.add(btn(tr("allow_notif"), T.panel2, T.text, T.border) {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        else toast(tr("already_ok"))
    }, mb = 8)
    pc.add(btn(tr("allow_exact"), T.panel2, T.text, T.border) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + packageName))
                )
            } catch (e: Exception) {
            }
        } else {
            toast(tr("already_ok"))
        }
    })
    content.add(pc, mb = 12)

    val about = card(T.panel2)
    about.add(icon(Icons.HEART, T.red, 5).apply { }, w = -2, h = -2)
    about.add(tv(tr("app") + "  v1.0", 15f, T.text, true), mt = 8)
    about.add(tv(tr("about"), 12f, T.sub).apply { gravity = Gravity.START }, mt = 4)
    content.add(about)
}
