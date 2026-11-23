package com.engazly.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.widget.ImageView
import android.widget.LinearLayout
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun fmtNum(d: Double): String =
    if (d == Math.floor(d)) d.toLong().toString() else String.format(Locale.US, "%.1f", d)

fun goalLabel(): String = when (Store.goalType) {
    "lose" -> tr("goal_lose")
    "muscle" -> tr("goal_muscle")
    "maintain" -> tr("goal_maintain")
    "health" -> tr("goal_health")
    else -> if (Store.goalText.isNotBlank()) Store.goalText else tr("goal_custom")
}

fun MainActivity.saveThumb(bmp: Bitmap?): String {
    if (bmp == null) return ""
    return try {
        val f = File(filesDir, "meal_" + System.currentTimeMillis() + ".jpg")
        val sc = Nutrition.scale(bmp, 360)
        f.outputStream().use { sc.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        f.absolutePath
    } catch (e: Exception) {
        ""
    }
}

fun MainActivity.startAnalyze(bmp: Bitmap?, note: String) {
    if (Store.apiKey.isBlank()) {
        toast(tr("need_key"))
        tab = 6
        render()
        return
    }
    if (bmp == null && note.isBlank()) {
        toast(tr("need_input"))
        return
    }
    analyzing = true
    analyzeErr = null
    lastMeal = null
    render()
    Nutrition.analyze(bmp, note) { json, err ->
        analyzing = false
        if (json != null) {
            val fallback = if (note.isNotBlank()) note else tr("meal")
            val name = json.optString("name", fallback)
            val now = System.currentTimeMillis()
            val m = Meal(now, now, name, json.toString(), saveThumb(bmp))
            val l = Store.meals()
            l.add(m)
            Store.saveMeals(l)
            lastMeal = m
            mealNote = ""
        } else {
            analyzeErr = err ?: "error"
        }
        render()
    }
}

fun MainActivity.buildMeals() {
    // goal + today
    val gc = card()
    gc.add(tv(tr("my_goal"), 12f, T.sub))
    gc.add(tv(goalLabel(), 17f, T.accent, true), mt = 2, mb = 10)
    val kcal = Store.caloriesToday()
    val target = if (Store.calTarget > 0) Store.calTarget else 2000
    gc.add(tv(tr("calories_today") + ": " + kcal + " / " + target + " kcal", 13f, T.text, true), mb = 6)
    gc.add(bar(kcal.toFloat() / target, if (kcal > target) T.red else T.accent2), mb = 10)
    gc.add(btn(tr("change_goal"), T.panel2, T.text, T.border) {
        tab = 6
        scroll.scrollTo(0, 0)
        render()
    })
    content.add(gc, mb = 12)

    if (Store.apiKey.isBlank()) {
        val w = card(T.panel2, T.red)
        w.add(tv(tr("need_key"), 13f, T.text, true), mb = 8)
        w.add(btn(tr("open_settings"), T.red, Color.WHITE, T.redDark) {
            tab = 6
            scroll.scrollTo(0, 0)
            render()
        })
        content.add(w, mb = 12)
    }

    // input
    val ic = card()
    ic.add(tv(tr("scan_meal"), 16f, T.accent, true), mb = 4)
    ic.add(tv(tr("scan_sub"), 12f, T.sub), mb = 10)
    val note = edit(tr("meal_note_hint"), multi = true)
    note.setText(mealNote)
    note.onText { mealNote = it }
    ic.add(note, mb = 10)
    val r = hbox()
    r.add(btn(tr("camera"), T.red, Color.WHITE, T.redDark) {
        takePhoto { bmp -> startAnalyze(bmp, mealNote) }
    }, w = 0, weight = 1f, me = 6)
    r.add(btn(tr("gallery")) {
        pickPhoto { bmp -> startAnalyze(bmp, mealNote) }
    }, w = 0, weight = 1f, ms = 6)
    ic.add(r, mb = 8)
    ic.add(btn(tr("analyze_text"), T.panel2, T.text, T.border) { startAnalyze(null, mealNote) })
    content.add(ic, mb = 12)

    if (analyzing) {
        val a = card(T.panel2)
        a.add(tv(tr("analyzing"), 15f, T.accent, true))
        a.add(tv(tr("analyzing_sub"), 12f, T.sub), mt = 4)
        content.add(a, mb = 12)
    }
    val err = analyzeErr
    if (err != null) {
        val e = card(T.panel2, T.red)
        e.add(tv(tr("err_analyze"), 14f, T.red, true))
        e.add(tv(err, 12f, T.text), mt = 4)
        content.add(e, mb = 12)
    }
    val lm = lastMeal
    if (lm != null) {
        val d = card(T.panel, T.accent)
        mealDetail(d, lm)
        content.add(d, mb = 12)
    }

    // history
    content.add(tv(tr("meals_history"), 16f, T.accent, true), mb = 8)
    val meals = Store.meals().reversed().take(30)
    if (meals.isEmpty()) {
        content.add(emptyCard(tr("meals_empty")))
        return
    }
    for (m in meals) {
        val j = try {
            JSONObject(m.json)
        } catch (e: Exception) {
            JSONObject()
        }
        val row = hbox()
        row.background = PixBg(T.panel, T.border, 0, dp(2).toFloat())
        row.setPadding(dp(8), dp(8), dp(8), dp(8))
        val iv = ImageView(this)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        iv.background = PixBg(T.panel2, T.border, 0, dp(2).toFloat())
        if (m.img.isNotEmpty()) {
            val bm = BitmapFactory.decodeFile(m.img)
            if (bm != null) iv.setImageBitmap(bm)
        }
        row.add(iv, w = dp(56), h = dp(56), me = 10)
        val mid = vbox()
        mid.add(tv(m.name, 15f, T.text, true))
        val whenText = SimpleDateFormat("d MMM  HH:mm", Locale(Store.lang)).format(Date(m.ts))
        mid.add(tv(fmtNum(j.optDouble("calories", 0.0)) + " kcal  ·  " + whenText, 12f, T.sub), mt = 2)
        val fit = j.optString("goal_fit", "")
        if (fit.isNotEmpty()) {
            val fc = when (fit) {
                "yes" -> T.good
                "partial" -> T.amber
                else -> T.red
            }
            val fl = when (fit) {
                "yes" -> tr("fit_yes")
                "partial" -> tr("fit_partial")
                else -> tr("fit_no")
            }
            mid.add(tv(fl, 12f, fc, true), mt = 2)
        }
        row.add(mid, w = 0, weight = 1f)
        row.setOnClickListener {
            dialog(m.name) { d ->
                mealDetail(this, m, false)
                add(btn(tr("delete"), T.red, Color.WHITE, T.redDark) {
                    d.dismiss()
                    Store.saveMeals(Store.meals().filter { it.id != m.id })
                    if (lastMeal?.id == m.id) lastMeal = null
                    render()
                }, mt = 12)
            }
        }
        content.add(row, mb = 8)
    }
}

fun MainActivity.mealDetail(into: LinearLayout, m: Meal, withTitle: Boolean = true) {
    val j = try {
        JSONObject(m.json)
    } catch (e: Exception) {
        JSONObject()
    }
    if (withTitle) into.add(tv(m.name, 18f, T.accent, true))
    into.add(tv(fmtNum(j.optDouble("calories", 0.0)) + " kcal", 30f, T.text, true), mt = 2, mb = 10)

    val fit = j.optString("goal_fit", "")
    if (fit.isNotEmpty()) {
        val colr = when (fit) {
            "yes" -> T.good
            "partial" -> T.amber
            else -> T.red
        }
        val lbl = when (fit) {
            "yes" -> tr("fit_yes")
            "partial" -> tr("fit_partial")
            else -> tr("fit_no")
        }
        val b = vbox()
        b.background = PixBg(T.panel2, colr, 0, dp(3).toFloat())
        b.setPadding(dp(12), dp(10), dp(12), dp(10))
        b.add(tv(tr("serves_goal") + ": " + lbl, 15f, colr, true))
        b.add(tv(j.optString("goal_reason", ""), 13f, T.text), mt = 4)
        into.add(b, mb = 12)
    }

    // macro split bar
    val p = j.optDouble("protein_g", 0.0)
    val cb = j.optDouble("carbs_g", 0.0)
    val ft = j.optDouble("fat_g", 0.0)
    val tot = p * 4 + cb * 4 + ft * 9
    if (tot > 0) {
        val parts = listOf(
            Pair(((p * 4) / tot).toFloat(), T.accent),
            Pair(((cb * 4) / tot).toFloat(), T.accent2),
            Pair(((ft * 9) / tot).toFloat(), T.red)
        )
        into.add(PixBar(this, parts, T.panel2, T.border), mb = 6)
        val lg = hbox()
        lg.add(tv(tr("n_protein_g") + " " + Math.round(p * 400 / tot) + "%", 11f, T.accent, true), w = 0, weight = 1f)
        lg.add(tv(tr("n_carbs_g") + " " + Math.round(cb * 400 / tot) + "%", 11f, T.accent2, true), w = 0, weight = 1f)
        lg.add(tv(tr("n_fat_g") + " " + Math.round(ft * 900 / tot) + "%", 11f, T.red, true), w = 0, weight = 1f)
        into.add(lg, mb = 12)
    }

    // facts table: key, unit, indent
    val rows = listOf(
        Triple("calories", "kcal", 0),
        Triple("protein_g", "g", 0),
        Triple("carbs_g", "g", 0),
        Triple("sugar_g", "g", 1),
        Triple("added_sugar_g", "g", 1),
        Triple("fiber_g", "g", 1),
        Triple("fat_g", "g", 0),
        Triple("saturated_fat_g", "g", 1),
        Triple("trans_fat_g", "g", 1),
        Triple("cholesterol_mg", "mg", 0),
        Triple("sodium_mg", "mg", 0),
        Triple("potassium_mg", "mg", 0),
        Triple("calcium_mg", "mg", 0),
        Triple("iron_mg", "mg", 0),
        Triple("magnesium_mg", "mg", 0),
        Triple("vitamin_a_ug", "µg", 0),
        Triple("vitamin_c_mg", "mg", 0),
        Triple("vitamin_d_ug", "µg", 0)
    )
    val table = vbox()
    table.background = PixBg(T.panel2, T.border, 0, dp(2).toFloat())
    table.setPadding(dp(12), dp(8), dp(12), dp(8))
    table.add(tv(tr("facts"), 13f, T.accent, true), mb = 6)
    for (rw in rows) {
        if (!j.has(rw.first) || j.isNull(rw.first)) continue
        val line = hbox()
        val label = tv(tr("n_" + rw.first), 13f, if (rw.third == 1) T.sub else T.text, rw.third == 0)
        line.add(label, w = 0, weight = 1f, ms = rw.third * 16)
        line.add(tv(fmtNum(j.optDouble(rw.first, 0.0)) + " " + rw.second, 13f, T.text, true), w = -2, h = -2)
        table.add(line, mt = 4, mb = 4)
    }
    into.add(table, mb = 12)

    val gl = j.optString("glycemic_load", "")
    val hs = j.optInt("health_score", 0)
    if (gl.isNotEmpty() || hs > 0) {
        val l = hbox()
        if (hs > 0) l.add(tv(tr("health_score") + ": " + hs + "/10", 13f, T.text, true), w = 0, weight = 1f)
        if (gl.isNotEmpty()) l.add(tv(tr("glycemic") + ": " + gl, 13f, T.text, true), w = 0, weight = 1f)
        into.add(l, mb = 12)
    }

    val items = j.optJSONArray("items")
    if (items != null && items.length() > 0) {
        into.add(tv(tr("items"), 13f, T.accent, true), mb = 6)
        for (i in 0 until items.length()) {
            val o = items.optJSONObject(i) ?: continue
            val line = hbox()
            line.add(tv(o.optString("name", ""), 13f, T.text), w = 0, weight = 1f)
            line.add(
                tv(fmtNum(o.optDouble("grams", 0.0)) + " g · " + fmtNum(o.optDouble("calories", 0.0)) + " kcal", 12f, T.sub),
                w = -2, h = -2
            )
            into.add(line, mb = 4)
        }
    }

    val tips = j.optJSONArray("tips")
    if (tips != null && tips.length() > 0) {
        into.add(tv(tr("tips"), 13f, T.accent, true), mt = 10, mb = 6)
        for (i in 0 until tips.length()) {
            into.add(tv("• " + tips.optString(i), 13f, T.text), mb = 4)
        }
    }
    into.add(tv(tr("estimate_note"), 11f, T.sub), mt = 10)
}
