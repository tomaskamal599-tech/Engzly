package com.engazly.app

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

object Nutrition {

    fun scale(b: Bitmap, maxSide: Int): Bitmap {
        val m = if (b.width > b.height) b.width else b.height
        if (m <= maxSide) return b
        val f = maxSide.toFloat() / m
        return Bitmap.createScaledBitmap(b, (b.width * f).toInt(), (b.height * f).toInt(), true)
    }

    private fun goalDescription(): String {
        val base = when (Store.goalType) {
            "lose" -> "lose weight / body fat (calorie deficit, high protein, low sugar)"
            "muscle" -> "build muscle (calorie surplus, high protein, enough carbs)"
            "maintain" -> "maintain current weight with balanced macros"
            "health" -> "eat healthy in general (whole foods, low sugar, low sodium, enough fiber)"
            else -> ""
        }
        val extra = Store.goalText.trim()
        return when {
            base.isNotEmpty() && extra.isNotEmpty() -> "$base. User's own words: $extra"
            base.isNotEmpty() -> base
            extra.isNotEmpty() -> extra
            else -> "eat healthy in general"
        }
    }

    private fun prompt(note: String): String {
        val language = if (Store.lang == "ar") "Arabic" else "English"
        return """
You are a careful nutrition analyst. Analyze the meal shown in the image${if (note.isNotBlank()) " and described by the user" else ""}.
${if (note.isNotBlank()) "User description: $note" else ""}
Estimate portion sizes from the image as best you can. All numbers are for the whole meal shown.
User goal: ${goalDescription()}.
Daily calorie target: ${Store.calTarget} kcal. Calories already eaten today: ${Store.caloriesToday()} kcal.

Reply with ONLY one JSON object, no markdown, no text before or after it, using exactly these keys:
{
 "name": "short meal name",
 "items": [{"name": "food item", "grams": 0, "calories": 0}],
 "calories": 0, "protein_g": 0, "carbs_g": 0, "sugar_g": 0, "added_sugar_g": 0, "fiber_g": 0,
 "fat_g": 0, "saturated_fat_g": 0, "trans_fat_g": 0,
 "cholesterol_mg": 0, "sodium_mg": 0, "potassium_mg": 0, "calcium_mg": 0, "iron_mg": 0, "magnesium_mg": 0,
 "vitamin_a_ug": 0, "vitamin_c_mg": 0, "vitamin_d_ug": 0,
 "glycemic_load": "low|medium|high",
 "health_score": 0,
 "goal_fit": "yes|partial|no",
 "goal_reason": "2-3 sentences: does this meal serve the user's goal and why, mentioning the numbers that matter",
 "tips": ["1-3 short practical tips to improve this meal for the goal"]
}
health_score is an integer from 1 to 10. If the image does not contain food, set calories to 0, goal_fit to "no" and explain in goal_reason.
Write every text value (name, items names, goal_reason, tips) in $language.
""".trimIndent()
    }

    private fun shortErr(t: String): String {
        return try {
            JSONObject(t).getJSONObject("error").getString("message")
        } catch (e: Exception) {
            if (t.length > 160) t.substring(0, 160) else t
        }
    }

    /** Calls the Claude API on a background thread. cb runs on the main thread: (result, errorMessage). */
    fun analyze(bmp: Bitmap?, note: String, cb: (JSONObject?, String?) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        val key = Store.apiKey.trim()
        val model = Store.model.trim()
        val text = prompt(note)
        Thread {
            var result: JSONObject? = null
            var err: String? = null
            try {
                val content = JSONArray()
                if (bmp != null) {
                    val sc = scale(bmp, 1280)
                    val bo = ByteArrayOutputStream()
                    sc.compress(Bitmap.CompressFormat.JPEG, 82, bo)
                    val b64 = Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP)
                    content.put(
                        JSONObject().put("type", "image").put(
                            "source",
                            JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", b64)
                        )
                    )
                }
                content.put(JSONObject().put("type", "text").put("text", text))
                val body = JSONObject()
                    .put("model", model)
                    .put("max_tokens", 2000)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))

                val conn = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 20000
                conn.readTimeout = 90000
                conn.doOutput = true
                conn.setRequestProperty("content-type", "application/json")
                conn.setRequestProperty("x-api-key", key)
                conn.setRequestProperty("anthropic-version", "2023-06-01")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
                val txt = stream.bufferedReader().use { it.readText() }
                if (code !in 200..299) {
                    err = "HTTP $code: " + shortErr(txt)
                } else {
                    val resp = JSONObject(txt)
                    val arr = resp.getJSONArray("content")
                    var out = ""
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        if (o.optString("type") == "text") out += o.getString("text")
                    }
                    val a = out.indexOf('{')
                    val z = out.lastIndexOf('}')
                    if (a < 0 || z <= a) {
                        err = "bad response"
                    } else {
                        result = JSONObject(out.substring(a, z + 1))
                    }
                }
            } catch (e: Exception) {
                err = e.message ?: e.toString()
            }
            val r = result
            val e = err
            main.post { cb(r, e) }
        }.start()
    }
}
