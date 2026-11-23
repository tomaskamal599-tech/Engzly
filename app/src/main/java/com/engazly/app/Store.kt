package com.engazly.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Habit(val id: Long, var name: String, val done: MutableSet<String>)
data class Todo(val id: Long, var title: String, var done: Boolean)
data class WorkoutLog(val id: Long, val date: String, val name: String, val minutes: Int, val note: String)
data class Reminder(
    val id: Int,
    var title: String,
    var msg: String,
    var hour: Int,
    var minute: Int,
    var daily: Boolean,
    var enabled: Boolean
)

data class AlarmItem(val id: Int, val label: String, val at: Long)
data class Meal(val id: Long, val ts: Long, val name: String, val json: String, val img: String)

object Store {
    private lateinit var sp: SharedPreferences

    fun init(c: Context) {
        if (!::sp.isInitialized) {
            sp = c.applicationContext.getSharedPreferences("engazly", Context.MODE_PRIVATE)
        }
    }

    // ---------- settings ----------
    var lang: String
        get() = sp.getString("lang", "ar") ?: "ar"
        set(v) {
            sp.edit().putString("lang", v).apply()
        }

    var dark: Boolean
        get() = sp.getBoolean("dark", true)
        set(v) {
            sp.edit().putBoolean("dark", v).apply()
        }

    var goalType: String
        get() = sp.getString("goal_type", "health") ?: "health"
        set(v) {
            sp.edit().putString("goal_type", v).apply()
        }

    var goalText: String
        get() = sp.getString("goal_text", "") ?: ""
        set(v) {
            sp.edit().putString("goal_text", v).apply()
        }

    var calTarget: Int
        get() = sp.getInt("cal_target", 2000)
        set(v) {
            sp.edit().putInt("cal_target", v).apply()
        }

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) {
            sp.edit().putString("api_key", v).apply()
        }

    var model: String
        get() = sp.getString("model", "claude-sonnet-5-5") ?: "claude-sonnet-5-5"
        set(v) {
            sp.edit().putString("model", v).apply()
        }

    var timerEnd: Long
        get() = sp.getLong("t_end", 0L)
        set(v) {
            sp.edit().putLong("t_end", v).apply()
        }

    // ---------- dates ----------
    private fun fmt(d: Date): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d)

    fun today(): String = fmt(Date())

    fun dayOff(n: Int): String {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, n)
        return fmt(c.time)
    }

    // ---------- json helpers ----------
    private fun arr(key: String): JSONArray = try {
        JSONArray(sp.getString(key, "[]"))
    } catch (e: Exception) {
        JSONArray()
    }

    private fun put(key: String, a: JSONArray) {
        sp.edit().putString(key, a.toString()).apply()
    }

    // ---------- habits ----------
    fun habits(): MutableList<Habit> {
        val a = arr("habits")
        val out = mutableListOf<Habit>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val d = mutableSetOf<String>()
            val da = o.optJSONArray("done")
            if (da != null) for (j in 0 until da.length()) d.add(da.getString(j))
            out.add(Habit(o.getLong("id"), o.getString("name"), d))
        }
        return out
    }

    fun saveHabits(l: List<Habit>) {
        val a = JSONArray()
        for (h in l) {
            a.put(JSONObject().put("id", h.id).put("name", h.name).put("done", JSONArray(h.done.toList())))
        }
        put("habits", a)
    }

    fun streak(h: Habit): Int {
        var n = 0
        var off = if (h.done.contains(dayOff(0))) 0 else -1
        while (h.done.contains(dayOff(off))) {
            n++
            off--
        }
        return n
    }

    // ---------- todos ----------
    fun todos(): MutableList<Todo> {
        val a = arr("todos")
        val out = mutableListOf<Todo>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            out.add(Todo(o.getLong("id"), o.getString("title"), o.optBoolean("done", false)))
        }
        return out
    }

    fun saveTodos(l: List<Todo>) {
        val a = JSONArray()
        for (t in l) a.put(JSONObject().put("id", t.id).put("title", t.title).put("done", t.done))
        put("todos", a)
    }

    // ---------- workouts ----------
    fun workouts(): MutableList<WorkoutLog> {
        val a = arr("workouts")
        val out = mutableListOf<WorkoutLog>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            out.add(
                WorkoutLog(
                    o.getLong("id"), o.getString("date"), o.getString("name"),
                    o.optInt("minutes", 0), o.optString("note", "")
                )
            )
        }
        return out
    }

    fun saveWorkouts(l: List<WorkoutLog>) {
        val a = JSONArray()
        for (w in l) {
            a.put(
                JSONObject().put("id", w.id).put("date", w.date).put("name", w.name)
                    .put("minutes", w.minutes).put("note", w.note)
            )
        }
        put("workouts", a)
    }

    // ---------- reminders ----------
    fun reminders(): MutableList<Reminder> {
        val a = arr("reminders")
        val out = mutableListOf<Reminder>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            out.add(
                Reminder(
                    o.getInt("id"), o.getString("title"), o.optString("msg", ""),
                    o.getInt("hour"), o.getInt("minute"),
                    o.optBoolean("daily", true), o.optBoolean("enabled", true)
                )
            )
        }
        return out
    }

    fun saveReminders(l: List<Reminder>) {
        val a = JSONArray()
        for (r in l) {
            a.put(
                JSONObject().put("id", r.id).put("title", r.title).put("msg", r.msg)
                    .put("hour", r.hour).put("minute", r.minute)
                    .put("daily", r.daily).put("enabled", r.enabled)
            )
        }
        put("reminders", a)
    }

    // ---------- alarms (after a duration) ----------
    fun alarms(): MutableList<AlarmItem> {
        val a = arr("alarms")
        val out = mutableListOf<AlarmItem>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            out.add(AlarmItem(o.getInt("id"), o.optString("label", ""), o.getLong("at")))
        }
        return out
    }

    fun saveAlarms(l: List<AlarmItem>) {
        val a = JSONArray()
        for (x in l) a.put(JSONObject().put("id", x.id).put("label", x.label).put("at", x.at))
        put("alarms", a)
    }

    // ---------- meals ----------
    fun meals(): MutableList<Meal> {
        val a = arr("meals")
        val out = mutableListOf<Meal>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            out.add(Meal(o.getLong("id"), o.getLong("ts"), o.getString("name"), o.getString("json"), o.optString("img", "")))
        }
        return out
    }

    fun saveMeals(l: List<Meal>) {
        val a = JSONArray()
        for (m in l) {
            a.put(
                JSONObject().put("id", m.id).put("ts", m.ts).put("name", m.name)
                    .put("json", m.json).put("img", m.img)
            )
        }
        put("meals", a)
    }

    fun caloriesToday(): Int {
        val start = Calendar.getInstance()
        start.set(Calendar.HOUR_OF_DAY, 0)
        start.set(Calendar.MINUTE, 0)
        start.set(Calendar.SECOND, 0)
        start.set(Calendar.MILLISECOND, 0)
        var sum = 0.0
        for (m in meals()) {
            if (m.ts >= start.timeInMillis) {
                try {
                    sum += JSONObject(m.json).optDouble("calories", 0.0)
                } catch (e: Exception) {
                }
            }
        }
        return Math.round(sum).toInt()
    }
}
