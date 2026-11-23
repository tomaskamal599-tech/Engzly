package com.engazly.app

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

object TimerState {
    var mode = 0 // 0 = countdown, 1 = stopwatch
    var totalMs = 60_000L
    var leftMs = 60_000L
    var swStart = 0L
    var swAcc = 0L

    fun running(): Boolean = Store.timerEnd > System.currentTimeMillis()

    fun remaining(): Long {
        val e = Store.timerEnd
        return if (e != 0L) {
            val r = e - System.currentTimeMillis()
            if (r < 0L) 0L else r
        } else leftMs
    }

    fun swRunning(): Boolean = swStart > 0L

    fun swElapsed(): Long = swAcc + (if (swStart > 0L) System.currentTimeMillis() - swStart else 0L)
}

class MainActivity : Activity() {
    lateinit var content: LinearLayout
    lateinit var scroll: ScrollView
    lateinit var nav: LinearLayout
    var tab = 0
    val handler = Handler(Looper.getMainLooper())
    var tickFn: (() -> Unit)? = null
    var imgCb: ((Bitmap?) -> Unit)? = null
    var camUri: Uri? = null

    // meals screen state
    var mealNote = ""
    var analyzing = false
    var analyzeErr: String? = null
    var lastMeal: Meal? = null

    private val ticker = object : Runnable {
        override fun run() {
            tickFn?.invoke()
            handler.postDelayed(this, 150)
        }
    }

    private val navItems = listOf(
        Triple(0, Icons.HEART, "tab_mix"),
        Triple(1, Icons.FLAME, "tab_habits"),
        Triple(2, Icons.LIST, "tab_todo"),
        Triple(3, Icons.DUMB, "tab_workout"),
        Triple(4, Icons.APPLE, "tab_meals")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        T.dark = Store.dark
        Notify.channels(this)
        window.decorView.layoutDirection = layoutDir()
        window.statusBarColor = T.bg
        window.navigationBarColor = T.bg
        window.decorView.systemUiVisibility = if (T.dark) 0 else
            (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
        tab = savedInstanceState?.getInt("tab") ?: 0
        buildFrame()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", tab)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        if (tab == 5) render()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun buildFrame() {
        val root = vbox()
        root.setBackgroundColor(T.bg)

        val top = hbox()
        top.setPadding(dp(14), dp(14), dp(14), dp(8))
        top.add(icon(Icons.HEART, T.red, 4), w = -2, h = -2, me = 10)
        top.add(tv(tr("app"), 24f, T.accent, true), w = 0, weight = 1f)
        top.add(iconBtn(Icons.BELL, if (tab == 5) T.red else T.accent) {
            tab = 5
            scroll.scrollTo(0, 0)
            render()
        }, w = -2, h = -2, me = 8)
        top.add(iconBtn(Icons.GEAR, if (tab == 6) T.red else T.accent) {
            tab = 6
            scroll.scrollTo(0, 0)
            render()
        }, w = -2, h = -2)
        root.add(top)

        scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        content = vbox()
        content.setPadding(dp(14), dp(6), dp(14), dp(20))
        scroll.addView(content)
        root.add(scroll, h = 0, weight = 1f)

        nav = hbox()
        nav.setPadding(dp(6), dp(6), dp(6), dp(6))
        nav.setBackgroundColor(T.panel)
        root.add(nav)

        setContentView(root)
        this.topBar = top
    }

    var topBar: LinearLayout? = null

    fun render() {
        val y = scroll.scrollY
        tickFn = null
        content.removeAllViews()
        when (tab) {
            0 -> buildMix()
            1 -> buildHabits()
            2 -> buildTodos()
            3 -> buildWorkout()
            4 -> buildMeals()
            5 -> buildReminders()
            else -> buildSettings()
        }
        renderNav()
        refreshTop()
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun refreshTop() {
        val t = topBar ?: return
        // rebuild bell + gear tint (children 2 and 3)
        t.removeViewAt(3)
        t.removeViewAt(2)
        t.add(iconBtn(Icons.BELL, if (tab == 5) T.red else T.accent) {
            tab = 5
            scroll.scrollTo(0, 0)
            render()
        }, w = -2, h = -2, me = 8)
        t.add(iconBtn(Icons.GEAR, if (tab == 6) T.red else T.accent) {
            tab = 6
            scroll.scrollTo(0, 0)
            render()
        }, w = -2, h = -2)
    }

    private fun renderNav() {
        nav.removeAllViews()
        for (n in navItems) {
            val sel = tab == n.first
            val item = vbox()
            item.gravity = Gravity.CENTER_HORIZONTAL
            item.setPadding(dp(2), dp(8), dp(2), dp(6))
            if (sel) item.background = PixBg(T.panel2, T.accent, 0, dp(2).toFloat())
            item.setOnClickListener {
                tab = n.first
                scroll.scrollTo(0, 0)
                render()
            }
            item.add(icon(n.second, if (sel) T.red else T.sub, 3), w = -2, h = -2)
            item.add(tv(tr(n.third), 11f, if (sel) T.accent else T.sub, sel).apply { maxLines = 1 }, w = -2, h = -2, mt = 4)
            nav.add(item, w = 0, h = -2, weight = 1f, ms = 2, me = 2)
        }
    }

    // ---------- photos ----------
    fun takePhoto(cb: (Bitmap?) -> Unit) {
        imgCb = cb
        val v = ContentValues()
        v.put(MediaStore.Images.Media.DISPLAY_NAME, "engazly_" + System.currentTimeMillis() + ".jpg")
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Engazly")
        val u = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)
        if (u == null) {
            toast(tr("err_camera"))
            return
        }
        camUri = u
        val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        i.putExtra(MediaStore.EXTRA_OUTPUT, u)
        try {
            startActivityForResult(i, 11)
        } catch (e: Exception) {
            toast(tr("err_camera"))
        }
    }

    fun pickPhoto(cb: (Bitmap?) -> Unit) {
        imgCb = cb
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.type = "image/*"
        startActivityForResult(Intent.createChooser(i, null), 12)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 11) {
            val u = camUri
            if (res == RESULT_OK && u != null) {
                imgCb?.invoke(decode(u))
            } else if (u != null) {
                try {
                    contentResolver.delete(u, null, null)
                } catch (e: Exception) {
                }
            }
        } else if (req == 12 && res == RESULT_OK) {
            val u = data?.data
            if (u != null) imgCb?.invoke(decode(u))
        }
    }

    private fun decode(u: Uri): Bitmap? {
        return try {
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o) }
            var ss = 1
            while (o.outWidth / ss > 2000 || o.outHeight / ss > 2000) ss *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = ss
            val bmp = contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o2) }
            var rot = 0
            contentResolver.openInputStream(u)?.use {
                val ex = ExifInterface(it)
                when (ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    6 -> rot = 90
                    3 -> rot = 180
                    8 -> rot = 270
                }
            }
            if (bmp != null && rot != 0) {
                val m = Matrix()
                m.postRotate(rot.toFloat())
                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            } else bmp
        } catch (e: Exception) {
            null
        }
    }
}
