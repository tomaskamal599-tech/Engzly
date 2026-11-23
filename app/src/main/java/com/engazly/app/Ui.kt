package com.engazly.app

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

fun rgb(h: String): Int = Color.parseColor(h)

/** Theme colours. Light blue + dark blue come from the app icon, mixed with red and black. */
object T {
    var dark = true
    val bg: Int get() = if (dark) rgb("#0A0B10") else rgb("#EAF6FF")
    val panel: Int get() = if (dark) rgb("#141A26") else rgb("#FFFFFF")
    val panel2: Int get() = if (dark) rgb("#1D2736") else rgb("#D3ECFF")
    val border: Int get() = rgb("#29627F")
    val text: Int get() = if (dark) rgb("#EAF6FF") else rgb("#0A0B10")
    val sub: Int get() = if (dark) rgb("#8FB3C9") else rgb("#3D6479")
    val accent: Int get() = rgb("#B3E5FF")
    val accent2: Int get() = rgb("#6AB0DB")
    val onAccent: Int get() = rgb("#0A0B10")
    val red: Int get() = rgb("#E5383B")
    val redDark: Int get() = rgb("#7A1417")
    val good: Int get() = rgb("#5BD98A")
    val amber: Int get() = rgb("#F4B942")
}

fun layoutDir(): Int = if (Store.lang == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR

/** A rectangle with stepped (pixel) corners, a border and an optional bottom shade. */
class PixBg(
    private val fill: Int,
    private val border: Int,
    private val shade: Int,
    private val p: Float
) : Drawable() {
    private val paint = Paint()

    private fun shape(c: Canvas, l: Float, t: Float, r: Float, b: Float, col: Int) {
        paint.color = col
        c.drawRect(l + p, t, r - p, b, paint)
        c.drawRect(l, t + p, r, b - p, paint)
    }

    override fun draw(c: Canvas) {
        val bd = bounds
        val l = bd.left.toFloat()
        val t = bd.top.toFloat()
        val r = bd.right.toFloat()
        val b = bd.bottom.toFloat()
        shape(c, l, t, r, b, border)
        if (shade != 0) {
            shape(c, l + p, t + p, r - p, b - p, shade)
            shape(c, l + p, t + p, r - p, b - 2 * p, fill)
        } else {
            shape(c, l + p, t + p, r - p, b - p, fill)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Draws an icon from a grid of strings where X is a filled pixel. */
class IconView(ctx: Context, val grid: List<String>, var tint: Int, val cellPx: Int) : View(ctx) {
    private val paint = Paint()

    override fun onMeasure(w: Int, h: Int) {
        var cols = 1
        for (r in grid) if (r.length > cols) cols = r.length
        setMeasuredDimension(cols * cellPx, grid.size * cellPx)
    }

    override fun onDraw(cv: Canvas) {
        paint.color = tint
        for (y in grid.indices) {
            val row = grid[y]
            for (x in row.indices) {
                if (row[x] == 'X') {
                    cv.drawRect(
                        (x * cellPx).toFloat(), (y * cellPx).toFloat(),
                        ((x + 1) * cellPx).toFloat(), ((y + 1) * cellPx).toFloat(), paint
                    )
                }
            }
        }
    }
}

/** Segmented pixel bar. Parts are (fraction, colour); the track shows what is left. */
class PixBar(
    ctx: Context,
    private val parts: List<Pair<Float, Int>>,
    private val track: Int,
    private val border: Int
) : View(ctx) {
    private val paint = Paint()

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (resources.displayMetrics.density * 20).toInt())
    }

    override fun onDraw(cv: Canvas) {
        val ww = width.toFloat()
        val hh = height.toFloat()
        val b = resources.displayMetrics.density * 3
        paint.color = border
        cv.drawRect(0f, 0f, ww, hh, paint)
        paint.color = track
        cv.drawRect(b, b, ww - b, hh - b, paint)
        var total = 0f
        for (pt in parts) total += pt.first
        val den = if (total > 1f) total else 1f
        val inner = ww - 2 * b
        var x = b
        for (pt in parts) {
            val w = inner * pt.first / den
            if (w > 0f) {
                paint.color = pt.second
                cv.drawRect(x, b, x + w, hh - b, paint)
            }
            x += w
        }
        paint.color = track
        val seg = inner / 20f
        for (i in 1 until 20) {
            val sx = b + seg * i
            cv.drawRect(sx - 1f, b, sx + 1f, hh - b, paint)
        }
    }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun Context.vbox(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

fun Context.hbox(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

fun LinearLayout.add(
    v: View, w: Int = -1, h: Int = -2, weight: Float = 0f,
    mt: Int = 0, mb: Int = 0, ms: Int = 0, me: Int = 0
): View {
    val d = context.resources.displayMetrics.density
    val lp = LinearLayout.LayoutParams(w, h, weight)
    lp.topMargin = (mt * d).toInt()
    lp.bottomMargin = (mb * d).toInt()
    lp.marginStart = (ms * d).toInt()
    lp.marginEnd = (me * d).toInt()
    addView(v, lp)
    return v
}

fun Context.tv(text: String, size: Float = 15f, color: Int = T.text, bold: Boolean = false): TextView =
    TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        typeface = if (Store.lang == "ar") {
            if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        } else {
            Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG
    }

fun Context.icon(g: List<String>, color: Int, cellDp: Int = 3): IconView = IconView(this, g, color, dp(cellDp))

fun Context.card(fill: Int = T.panel, border: Int = T.border): LinearLayout = vbox().apply {
    background = PixBg(fill, border, 0, dp(3).toFloat())
    val q = dp(14)
    setPadding(q, q, q, q)
}

fun Context.emptyCard(text: String): View = card(T.panel2).apply {
    add(tv(text, 13f, T.sub).apply { gravity = Gravity.CENTER })
}

fun Context.btn(
    label: String,
    fill: Int = T.accent,
    fg: Int = T.onAccent,
    shade: Int = T.accent2,
    onClick: () -> Unit
): TextView = tv(label, 14f, fg, true).apply {
    gravity = Gravity.CENTER
    setPadding(dp(14), dp(11), dp(14), dp(14))
    background = PixBg(fill, T.border, shade, dp(2).toFloat())
    isClickable = true
    setOnClickListener { onClick() }
}

fun Context.iconBtn(g: List<String>, color: Int, onClick: () -> Unit): View {
    val f = FrameLayout(this)
    f.setPadding(dp(9), dp(9), dp(9), dp(9))
    f.background = PixBg(T.panel2, T.border, 0, dp(2).toFloat())
    f.addView(icon(g, color, 3), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
    f.setOnClickListener { onClick() }
    return f
}

fun Context.edit(hint: String, number: Boolean = false, multi: Boolean = false, password: Boolean = false): EditText =
    EditText(this).apply {
        this.hint = hint
        setHintTextColor(T.sub)
        setTextColor(T.text)
        textSize = 15f
        typeface = if (Store.lang == "ar") Typeface.DEFAULT else Typeface.MONOSPACE
        background = PixBg(T.panel2, T.border, 0, dp(2).toFloat())
        setPadding(dp(12), dp(10), dp(12), dp(10))
        inputType = when {
            number -> InputType.TYPE_CLASS_NUMBER
            password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            multi -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG
    }

fun EditText.onText(cb: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(e: Editable?) {
            cb(e?.toString() ?: "")
        }

        override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
        override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
    })
}

fun Context.checkBox(on: Boolean, onToggle: () -> Unit): View {
    val f = FrameLayout(this)
    f.background = PixBg(if (on) T.accent else T.panel2, T.border, 0, dp(2).toFloat())
    if (on) f.addView(icon(Icons.CHECK, T.onAccent, 3), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
    f.setOnClickListener { onToggle() }
    return f
}

fun Context.toggleRow(label: String, initial: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
    val r = hbox()
    var on = initial
    fun draw() {
        r.removeAllViews()
        r.add(checkBox(on) {
            on = !on
            onChange(on)
            draw()
        }, w = dp(30), h = dp(30), me = 10)
        r.add(tv(label, 14f), w = 0, weight = 1f)
    }
    draw()
    return r
}

fun Context.chipRow(opts: List<Pair<String, String>>, sel: String, onSel: (String) -> Unit): View {
    val hs = HorizontalScrollView(this)
    hs.isHorizontalScrollBarEnabled = false
    val r = hbox()
    var cur = sel
    fun draw() {
        r.removeAllViews()
        for (o in opts) {
            val k = o.first
            val on = k == cur
            val t = tv(o.second, 13f, if (on) T.onAccent else T.text, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(8), dp(12), dp(10))
                background = PixBg(if (on) T.accent else T.panel2, T.border, 0, dp(2).toFloat())
                setOnClickListener {
                    cur = k
                    onSel(k)
                    draw()
                }
            }
            r.add(t, w = -2, h = -2, me = 6)
        }
    }
    draw()
    hs.addView(r)
    return hs
}

fun Context.bar(frac: Float, color: Int = T.red): View {
    var f = frac
    if (f < 0f) f = 0f
    if (f > 1f) f = 1f
    return PixBar(this, listOf(Pair(f, color)), T.panel2, T.border)
}

fun Context.toast(m: String) {
    Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}

fun Context.vibrate() {
    try {
        val v = getSystemService(Vibrator::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    } catch (e: Exception) {
    }
}

fun Context.dialog(title: String, build: LinearLayout.(Dialog) -> Unit): Dialog {
    val d = Dialog(this)
    d.requestWindowFeature(Window.FEATURE_NO_TITLE)
    val box = vbox()
    box.background = PixBg(T.panel, T.accent, 0, dp(3).toFloat())
    box.setPadding(dp(16), dp(16), dp(16), dp(16))
    box.add(tv(title, 18f, T.accent, true), mb = 12)
    box.build(d)
    val sv = ScrollView(this)
    sv.layoutDirection = layoutDir()
    sv.addView(box)
    d.setContentView(sv)
    d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    d.window?.setLayout(
        (resources.displayMetrics.widthPixels * 0.92).toInt(),
        WindowManager.LayoutParams.WRAP_CONTENT
    )
    d.show()
    return d
}

fun Context.confirm(msg: String, onYes: () -> Unit) {
    dialog(tr("confirm")) { d ->
        add(tv(msg, 15f), mb = 14)
        val r = hbox()
        r.add(btn(tr("cancel"), T.panel2, T.text, T.border) { d.dismiss() }, w = 0, weight = 1f, me = 6)
        r.add(btn(tr("yes"), T.red, Color.WHITE, T.redDark) {
            d.dismiss()
            onYes()
        }, w = 0, weight = 1f, ms = 6)
        add(r)
    }
}

fun Context.askText(title: String, hint: String, initial: String = "", onOk: (String) -> Unit) {
    dialog(title) { d ->
        val e = edit(hint)
        e.setText(initial)
        add(e, mb = 12)
        add(btn(tr("save")) {
            val t = e.text.toString().trim()
            if (t.isNotEmpty()) {
                d.dismiss()
                onOk(t)
            }
        })
    }
}
