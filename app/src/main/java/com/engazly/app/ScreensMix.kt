package com.engazly.app

import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private class MixItem(val habit: Habit?, val todo: Todo?, val done: Boolean)

fun MainActivity.weekStrip(h: Habit): View {
    val r = hbox()
    for (i in 6 downTo 0) {
        val d = Store.dayOff(-i)
        val v = View(this)
        v.background = PixBg(if (h.done.contains(d)) T.red else T.panel2, T.border, 0, dp(1).toFloat())
        r.add(v, w = dp(16), h = dp(16), me = 4)
    }
    return r
}

fun MainActivity.itemRow(
    parent: LinearLayout,
    title: String,
    done: Boolean,
    tag: String?,
    tagColor: Int,
    subText: String?,
    onToggle: () -> Unit,
    onDel: (() -> Unit)?,
    extra: View? = null
) {
    val r = hbox()
    r.background = PixBg(T.panel, T.border, 0, dp(2).toFloat())
    r.setPadding(dp(10), dp(10), dp(10), dp(10))
    r.add(checkBox(done, onToggle), w = dp(30), h = dp(30), me = 10)
    val mid = vbox()
    val t = tv(title, 15f, if (done) T.sub else T.text, true)
    if (done) t.paintFlags = t.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
    mid.add(t)
    if (tag != null || subText != null) {
        val l = hbox()
        if (tag != null) {
            val chip = tv(tag, 10f, if (tagColor == T.red) Color.WHITE else T.onAccent, true)
            chip.background = PixBg(tagColor, tagColor, 0, dp(1).toFloat())
            chip.setPadding(dp(6), dp(2), dp(6), dp(2))
            l.add(chip, w = -2, h = -2, me = 8)
        }
        if (subText != null) l.add(tv(subText, 12f, T.sub), w = -2, h = -2)
        mid.add(l, mt = 4)
    }
    if (extra != null) mid.add(extra, mt = 6)
    r.add(mid, w = 0, weight = 1f)
    if (onDel != null) {
        r.add(iconBtn(Icons.TRASH, T.red) { confirm(tr("confirm_del")) { onDel() } }, w = -2, h = -2, ms = 8)
    }
    parent.add(r, mb = 8)
}

fun MainActivity.toggleHabit(id: Long) {
    val l = Store.habits()
    val h = l.firstOrNull { it.id == id } ?: return
    val d = Store.today()
    if (!h.done.add(d)) h.done.remove(d)
    Store.saveHabits(l)
    render()
}

fun MainActivity.toggleTodo(id: Long) {
    val l = Store.todos()
    val t = l.firstOrNull { it.id == id } ?: return
    t.done = !t.done
    Store.saveTodos(l)
    render()
}

fun MainActivity.deleteHabit(id: Long) {
    Store.saveHabits(Store.habits().filter { it.id != id })
    render()
}

fun MainActivity.deleteTodo(id: Long) {
    Store.saveTodos(Store.todos().filter { it.id != id })
    render()
}

fun MainActivity.addHabit(name: String) {
    val l = Store.habits()
    l.add(Habit(System.currentTimeMillis(), name, mutableSetOf()))
    Store.saveHabits(l)
    render()
}

fun MainActivity.addTodo(title: String) {
    val l = Store.todos()
    l.add(Todo(System.currentTimeMillis(), title, false))
    Store.saveTodos(l)
    render()
}

fun MainActivity.addMixDialog() {
    var kind = "habit"
    dialog(tr("add_new")) { d ->
        add(chipRow(listOf(Pair("habit", tr("tag_habit")), Pair("todo", tr("tag_task"))), kind) { kind = it }, mb = 10)
        val e = edit(tr("name_hint"))
        add(e, mb = 12)
        add(btn(tr("save")) {
            val t = e.text.toString().trim()
            if (t.isNotEmpty()) {
                d.dismiss()
                if (kind == "habit") addHabit(t) else addTodo(t)
            }
        })
    }
}

fun MainActivity.buildMix() {
    val habits = Store.habits()
    val todos = Store.todos()
    val today = Store.today()
    val total = habits.size + todos.size
    val done = habits.count { it.done.contains(today) } + todos.count { it.done }
    val frac = if (total == 0) 0f else done.toFloat() / total

    val hero = card()
    hero.add(tv(SimpleDateFormat("EEEE d MMMM", Locale(Store.lang)).format(Date()), 12f, T.sub))
    hero.add(tv(tr("mix_hero"), 20f, T.accent, true), mt = 4, mb = 10)
    val hearts = hbox()
    val filled = Math.round(frac * 5)
    for (i in 0 until 5) {
        hearts.add(icon(Icons.HEART, if (i < filled) T.red else T.panel2, 4), w = -2, h = -2, me = 6)
    }
    hero.add(hearts, mb = 10)
    hero.add(bar(frac), mb = 8)
    hero.add(tv("$done / $total  " + tr("completed"), 13f, T.text, true))
    content.add(hero, mb = 10)

    val wk = Store.workouts().filter { it.date == today }.sumOf { it.minutes }
    val kcal = Store.caloriesToday()
    val stats = hbox()
    val c1 = card(T.panel2)
    c1.add(tv(tr("workout_today"), 11f, T.sub))
    c1.add(tv("$wk " + tr("min"), 20f, T.text, true), mt = 4)
    val c2 = card(T.panel2)
    c2.add(tv(tr("calories"), 11f, T.sub))
    c2.add(tv("$kcal / ${Store.calTarget}", 20f, T.text, true), mt = 4)
    stats.add(c1, w = 0, weight = 1f, me = 5)
    stats.add(c2, w = 0, weight = 1f, ms = 5)
    content.add(stats, mb = 14)

    val head = hbox()
    head.add(tv(tr("today_list"), 16f, T.accent, true), w = 0, weight = 1f)
    head.add(btn("+ " + tr("add"), T.red, Color.WHITE, T.redDark) { addMixDialog() }, w = -2, h = -2)
    content.add(head, mb = 8)

    if (total == 0) {
        content.add(emptyCard(tr("mix_empty")))
        return
    }

    val a = habits.map { MixItem(it, null, it.done.contains(today)) }
    val b = todos.map { MixItem(null, it, it.done) }
    val mixed = mutableListOf<MixItem>()
    var i = 0
    while (i < a.size || i < b.size) {
        if (i < a.size) mixed.add(a[i])
        if (i < b.size) mixed.add(b[i])
        i++
    }
    for (m in mixed.sortedBy { it.done }) {
        val h = m.habit
        val t = m.todo
        if (h != null) {
            itemRow(
                content, h.name, m.done, tr("tag_habit"), T.red,
                tr("streak") + ": " + Store.streak(h),
                { toggleHabit(h.id) }, { deleteHabit(h.id) }
            )
        } else if (t != null) {
            itemRow(
                content, t.title, m.done, tr("tag_task"), T.accent, null,
                { toggleTodo(t.id) }, { deleteTodo(t.id) }
            )
        }
    }
}

fun MainActivity.buildHabits() {
    val habits = Store.habits()
    val today = Store.today()
    val head = hbox()
    head.add(tv(tr("habits_title"), 18f, T.accent, true), w = 0, weight = 1f)
    head.add(btn("+ " + tr("add"), T.red, Color.WHITE, T.redDark) {
        askText(tr("add_habit"), tr("habit_hint")) { addHabit(it) }
    }, w = -2, h = -2)
    content.add(head, mb = 6)
    content.add(tv(tr("habits_sub"), 12f, T.sub), mb = 12)

    if (habits.isEmpty()) {
        content.add(emptyCard(tr("habits_empty")))
        return
    }
    var best = 0
    for (h in habits) {
        val s = Store.streak(h)
        if (s > best) best = s
    }
    val top = card(T.panel2)
    top.add(tv(tr("best_streak") + ": " + best + " " + tr("days"), 14f, T.text, true))
    content.add(top, mb = 10)
    for (h in habits) {
        itemRow(
            content, h.name, h.done.contains(today), null, 0,
            tr("streak") + ": " + Store.streak(h) + " " + tr("days"),
            { toggleHabit(h.id) }, { deleteHabit(h.id) }, weekStrip(h)
        )
    }
}

fun MainActivity.buildTodos() {
    val todos = Store.todos()
    content.add(tv(tr("todo_title"), 18f, T.accent, true), mb = 10)
    val r = hbox()
    val e = edit(tr("todo_hint"))
    r.add(e, w = 0, weight = 1f, me = 8)
    r.add(btn("+", T.red, Color.WHITE, T.redDark) {
        val t = e.text.toString().trim()
        if (t.isNotEmpty()) addTodo(t)
    }, w = -2, h = -2)
    content.add(r, mb = 12)

    if (todos.isEmpty()) {
        content.add(emptyCard(tr("todo_empty")))
        return
    }
    for (t in todos.filter { !it.done }) {
        itemRow(content, t.title, false, null, 0, null, { toggleTodo(t.id) }, { deleteTodo(t.id) })
    }
    val finished = todos.filter { it.done }
    if (finished.isNotEmpty()) {
        val h = hbox()
        h.add(tv(tr("finished") + " (" + finished.size + ")", 14f, T.sub, true), w = 0, weight = 1f)
        h.add(btn(tr("clear_done"), T.panel2, T.text, T.border) {
            Store.saveTodos(Store.todos().filter { !it.done })
            render()
        }, w = -2, h = -2)
        content.add(h, mt = 8, mb = 8)
        for (t in finished) {
            itemRow(content, t.title, true, null, 0, null, { toggleTodo(t.id) }, { deleteTodo(t.id) })
        }
    }
}
