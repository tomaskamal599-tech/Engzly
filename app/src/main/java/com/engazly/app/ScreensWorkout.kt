package com.engazly.app

import android.graphics.Color
import android.view.Gravity
import android.widget.EditText
import android.widget.TextView
import java.util.Locale
import kotlin.random.Random

fun fmtCountdown(ms: Long): String {
    val t = (ms + 999) / 1000
    return String.format(Locale.US, "%02d:%02d", t / 60, t % 60)
}

fun fmtStopwatch(ms: Long): String {
    val t = ms / 1000
    val tenth = (ms % 1000) / 100
    return String.format(Locale.US, "%02d:%02d.%d", t / 60, t % 60, tenth)
}

fun fmtTime(h: Int, m: Int): String = String.format(Locale.US, "%02d:%02d", h, m)

fun MainActivity.buildWorkout() {
    buildTimerCard()
    buildAlarmCard()
    buildLogCard()
    buildHistory()
}

private fun MainActivity.buildTimerCard() {
    val tc = card()
    tc.add(tv(tr("timer"), 16f, T.accent, true), mb = 8)
    tc.add(
        chipRow(
            listOf(Pair("0", tr("countdown")), Pair("1", tr("stopwatch"))),
            TimerState.mode.toString()
        ) {
            TimerState.mode = it.toInt()
            render()
        }, mb = 12
    )

    val big: TextView = tv("00:00", 46f, T.text, true)
    big.gravity = Gravity.CENTER
    tc.add(big, mb = 12)

    if (TimerState.mode == 0) {
        val presets = listOf(30, 60, 90, 120, 300, 600)
        val opts = presets.map { Pair(it.toString(), fmtCountdown(it * 1000L)) }
        tc.add(
            chipRow(opts, (TimerState.totalMs / 1000).toString()) {
                if (!TimerState.running()) {
                    TimerState.totalMs = it.toLong() * 1000L
                    TimerState.leftMs = TimerState.totalMs
                    render()
                }
            }, mb = 10
        )
        val r = hbox()
        val em: EditText = edit(tr("min"), number = true)
        val es: EditText = edit(tr("sec"), number = true)
        r.add(em, w = 0, weight = 1f, me = 6)
        r.add(es, w = 0, weight = 1f, me = 6)
        r.add(btn(tr("set"), T.panel2, T.text, T.border) {
            val m = em.text.toString().toIntOrNull() ?: 0
            val s = es.text.toString().toIntOrNull() ?: 0
            val total = (m * 60 + s).toLong()
            if (total > 0 && !TimerState.running()) {
                TimerState.totalMs = total * 1000L
                TimerState.leftMs = TimerState.totalMs
                render()
            }
        }, w = -2, h = -2)
        tc.add(r, mb = 12)
    }

    val controls = hbox()
    if (TimerState.mode == 0) {
        val running = TimerState.running()
        controls.add(btn(if (running) tr("pause") else tr("start"), T.red, Color.WHITE, T.redDark) {
            if (TimerState.running()) {
                TimerState.leftMs = TimerState.remaining()
                Store.timerEnd = 0L
                Notify.cancel(this, Notify.TIMER_ID)
            } else {
                if (TimerState.leftMs <= 0L) TimerState.leftMs = TimerState.totalMs
                val end = System.currentTimeMillis() + TimerState.leftMs
                Store.timerEnd = end
                Notify.schedule(this, Notify.TIMER_ID, end, "timer", tr("timer_done"), tr("timer_done_text"), -1)
            }
            render()
        }, w = 0, weight = 1f, me = 6)
        controls.add(btn(tr("reset"), T.panel2, T.text, T.border) {
            Store.timerEnd = 0L
            TimerState.leftMs = TimerState.totalMs
            Notify.cancel(this, Notify.TIMER_ID)
            render()
        }, w = 0, weight = 1f, ms = 6)
        big.text = fmtCountdown(TimerState.remaining())
        tickFn = {
            val end = Store.timerEnd
            if (end != 0L && end <= System.currentTimeMillis()) {
                Store.timerEnd = 0L
                TimerState.leftMs = 0L
                vibrate()
                render()
            } else {
                big.text = fmtCountdown(TimerState.remaining())
            }
        }
    } else {
        controls.add(btn(if (TimerState.swRunning()) tr("pause") else tr("start"), T.red, Color.WHITE, T.redDark) {
            if (TimerState.swRunning()) {
                TimerState.swAcc = TimerState.swElapsed()
                TimerState.swStart = 0L
            } else {
                TimerState.swStart = System.currentTimeMillis()
            }
            render()
        }, w = 0, weight = 1f, me = 6)
        controls.add(btn(tr("reset"), T.panel2, T.text, T.border) {
            TimerState.swStart = 0L
            TimerState.swAcc = 0L
            render()
        }, w = 0, weight = 1f, ms = 6)
        big.text = fmtStopwatch(TimerState.swElapsed())
        tickFn = { big.text = fmtStopwatch(TimerState.swElapsed()) }
    }
    tc.add(controls)
    content.add(tc, mb = 12)
}

private fun MainActivity.buildAlarmCard() {
    val ac = card()
    ac.add(tv(tr("alarm_after"), 16f, T.accent, true), mb = 4)
    ac.add(tv(tr("alarm_sub"), 12f, T.sub), mb = 10)
    val r = hbox()
    val eh = edit(tr("hours"), number = true)
    val em = edit(tr("minutes"), number = true)
    r.add(eh, w = 0, weight = 1f, me = 6)
    r.add(em, w = 0, weight = 1f, ms = 6)
    ac.add(r, mb = 8)
    val el = edit(tr("alarm_label"))
    ac.add(el, mb = 10)
    ac.add(btn(tr("set_alarm")) {
        val h = eh.text.toString().toIntOrNull() ?: 0
        val m = em.text.toString().toIntOrNull() ?: 0
        val mins = h * 60 + m
        if (mins <= 0) {
            toast(tr("err_time"))
        } else {
            val at = System.currentTimeMillis() + mins * 60_000L
            val id = Random.nextInt(2_000_000, 2_000_000_000)
            var label = el.text.toString().trim()
            if (label.isEmpty()) label = tr("alarm_default")
            val l = Store.alarms()
            l.add(AlarmItem(id, label, at))
            Store.saveAlarms(l)
            Notify.schedule(this, id, at, "alarm", tr("alarm_title"), label, id)
            toast(tr("alarm_set") + " " + fmtTimeOf(at))
            render()
        }
    })

    val now = System.currentTimeMillis()
    val active = Store.alarms().filter { it.at > now }.sortedBy { it.at }
    if (active.size != Store.alarms().size) Store.saveAlarms(active)
    for (a in active) {
        val row = hbox()
        row.background = PixBg(T.panel2, T.border, 0, dp(2).toFloat())
        row.setPadding(dp(10), dp(8), dp(10), dp(8))
        row.add(icon(Icons.CLOCK, T.red, 3), w = -2, h = -2, me = 10)
        val mid = vbox()
        mid.add(tv(fmtTimeOf(a.at), 16f, T.text, true))
        mid.add(tv(a.label, 12f, T.sub))
        row.add(mid, w = 0, weight = 1f)
        row.add(iconBtn(Icons.TRASH, T.red) {
            Notify.cancel(this, a.id)
            Store.saveAlarms(Store.alarms().filter { it.id != a.id })
            render()
        }, w = -2, h = -2)
        ac.add(row, mt = 8)
    }
    content.add(ac, mb = 12)
}

private fun fmtTimeOf(at: Long): String {
    val c = java.util.Calendar.getInstance()
    c.timeInMillis = at
    return fmtTime(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
}

private fun MainActivity.buildLogCard() {
    val lc = card()
    lc.add(tv(tr("log_workout"), 16f, T.accent, true), mb = 4)
    lc.add(tv(tr("log_sub"), 12f, T.sub), mb = 10)
    val en = edit(tr("activity_hint"))
    val sports = listOf("run", "walk", "gym", "swim", "bike", "football", "yoga", "rope", "hiit", "box")
    val opts = sports.map { Pair(it, tr("sp_$it")) }
    lc.add(chipRow(opts, "") { en.setText(tr("sp_$it")) }, mb = 10)
    lc.add(en, mb = 8)
    val emin = edit(tr("minutes"), number = true)
    lc.add(emin, mb = 8)
    val enote = edit(tr("note_hint"))
    lc.add(enote, mb = 10)
    lc.add(btn("+ " + tr("add_workout"), T.red, Color.WHITE, T.redDark) {
        val n = en.text.toString().trim()
        if (n.isEmpty()) {
            toast(tr("err_activity"))
        } else {
            val l = Store.workouts()
            l.add(
                WorkoutLog(
                    System.currentTimeMillis(), Store.today(), n,
                    emin.text.toString().toIntOrNull() ?: 0, enote.text.toString().trim()
                )
            )
            Store.saveWorkouts(l)
            toast(tr("saved"))
            render()
        }
    })
    content.add(lc, mb = 12)
}

private fun MainActivity.buildHistory() {
    val all = Store.workouts()
    val week = HashSet<String>()
    for (i in 0..6) week.add(Store.dayOff(-i))
    val inWeek = all.filter { week.contains(it.date) }
    val mins = inWeek.sumOf { it.minutes }

    val st = card(T.panel2)
    st.add(tv(tr("this_week"), 12f, T.sub))
    st.add(tv("${inWeek.size} " + tr("sessions") + "  ·  " + mins + " " + tr("min"), 17f, T.text, true), mt = 4)
    content.add(st, mb = 12)

    content.add(tv(tr("history"), 16f, T.accent, true), mb = 8)
    if (all.isEmpty()) {
        content.add(emptyCard(tr("history_empty")))
        return
    }
    for (w in all.reversed().take(20)) {
        val r = hbox()
        r.background = PixBg(T.panel, T.border, 0, dp(2).toFloat())
        r.setPadding(dp(10), dp(10), dp(10), dp(10))
        r.add(icon(Icons.DUMB, T.accent, 3), w = -2, h = -2, me = 10)
        val mid = vbox()
        mid.add(tv(w.name, 15f, T.text, true))
        val sub = w.date + (if (w.minutes > 0) "  ·  " + w.minutes + " " + tr("min") else "") +
                (if (w.note.isNotEmpty()) "  ·  " + w.note else "")
        mid.add(tv(sub, 12f, T.sub), mt = 2)
        r.add(mid, w = 0, weight = 1f)
        r.add(iconBtn(Icons.TRASH, T.red) {
            confirm(tr("confirm_del")) {
                Store.saveWorkouts(Store.workouts().filter { it.id != w.id })
                render()
            }
        }, w = -2, h = -2, ms = 8)
        content.add(r, mb = 8)
    }
}
