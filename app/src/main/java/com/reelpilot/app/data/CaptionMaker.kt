package com.reelpilot.app.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.abs

data class Caption(val hook: String, val body: String, val question: String, val tags: String, val byAi: Boolean) {
    val text get() = CaptionRules.build(hook, body, question, tags)
}

/**
 * Writes the hook, caption, question and hashtags for a clip.
 * With a Claude API key it looks at 3 frames of the video; otherwise it uses built-in templates.
 */
object CaptionMaker {

    suspend fun make(ctx: Context, uri: Uri, seed: String, durationSec: Double, cfg: AutoConfig): Caption {
        val key = Store.aiKey
        if (cfg.useAi && key.isNotBlank()) {
            try {
                val c = withAi(ctx, uri, durationSec, cfg, key)
                if (CaptionRules.check(c.hook, c.text, cfg.ig).none { it.level == Level.BAD }) return c
            } catch (_: Exception) { /* fall back to templates */ }
        }
        return template(seed, cfg)
    }

    // ---------------- built-in templates ----------------

    private val hooksEn = listOf(
        "This {g} clutch was not supposed to happen 😳",
        "POV: you finally hit that shot in {g}",
        "I did NOT expect this in {g}",
        "Cleanest {g} moment I've had this week",
        "One more match, they said… {g}",
        "How did this even work in {g}? 😂",
        "Watch the last 3 seconds of this {g} round",
        "My best {g} play so far",
    )
    private val hooksHi = listOf(
        "{g} mein aisa clutch kabhi dekha hai? 😳",
        "Yeh shot dekh ke squad bhi shock 😂",
        "Bas ek aur match… aur phir yeh hua",
        "{g} ka sabse clean moment 🔥",
        "Last 3 second dhyan se dekhna",
        "Aaj {g} mein kismat full on thi 😂",
        "Is round ke baad sab chup ho gaye",
        "Yeh play mere best {g} moments mein se hai",
    )
    private val hooksHindi = listOf(
        "{g} में ऐसा क्लच कभी देखा है? 😳",
        "ये शॉट देखकर पूरी स्क्वाड हैरान 😂",
        "बस एक और मैच… और फिर ये हुआ",
        "{g} का सबसे क्लीन मोमेंट 🔥",
        "आख़िरी 3 सेकंड ध्यान से देखना",
    )
    private val bodyEn = listOf("Recorded live on my channel.", "No edits on the play itself, just my reaction.", "Full squad was watching this one.", "Took me a lot of tries to pull this off.")
    private val bodyHi = listOf("Live game ka asli moment.", "Play bilkul real hai, sirf meri reaction add ki hai.", "Poori squad dekh rahi thi.", "Bahut try ke baad yeh hua.")
    private val bodyHindi = listOf("लाइव गेम का असली मोमेंट।", "प्ले बिल्कुल असली है, बस मेरा रिएक्शन जोड़ा है।", "पूरी स्क्वाड देख रही थी।")
    private val qEn = listOf("Would you have pushed or played safe?", "What would you rate this play out of 10?", "Which loadout should I try next?", "What's the best clutch you've ever hit?")
    private val qHi = listOf("Tum hote toh push karte ya wait?", "Is play ko 10 mein se kitna doge?", "Next match mein kaunsa gun try karun?", "Tumhara best clutch kaunsa tha?")
    private val qHindi = listOf("आप होते तो पुश करते या रुकते?", "इस प्ले को 10 में से कितने दोगे?", "अगले मैच में कौन सी गन ट्राय करूँ?")

    fun template(seed: String, cfg: AutoConfig): Caption {
        val h = abs(seed.hashCode())
        val (hooks, bodies, qs) = when (cfg.language) {
            "Hinglish" -> Triple(hooksHi, bodyHi, qHi)
            "Hindi" -> Triple(hooksHindi, bodyHindi, qHindi)
            else -> Triple(hooksEn, bodyEn, qEn)
        }
        val game = cfg.game.ifBlank { "this game" }
        val hook = hooks[h % hooks.size].replace("{g}", game).replace("this game clutch", "clutch")
        return Caption(
            hook = hook, body = bodies[(h / 7) % bodies.size], question = qs[(h / 13) % qs.size],
            tags = CaptionRules.suggestTags(cfg.game, cfg.tags), byAi = false,
        )
    }

    // ---------------- Claude AI ----------------

    private const val MODEL = "claude-haiku-5-5"

    private suspend fun withAi(ctx: Context, uri: Uri, durationSec: Double, cfg: AutoConfig, key: String): Caption =
        withContext(Dispatchers.IO) {
            val frames = frames(ctx, uri, durationSec)
            val content = JSONArray()
            frames.forEach { b64 ->
                content.put(JSONObject().put("type", "image").put("source",
                    JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", b64)))
            }
            content.put(JSONObject().put("type", "text").put("text", prompt(cfg)))
            val body = JSONObject()
                .put("model", MODEL).put("max_tokens", 400)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            val req = Request.Builder().url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", key).header("anthropic-version", "2023-06-01")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            MetaApi.client.newCall(req).execute().use { r ->
                val txt = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw IllegalStateException("AI caption failed (${r.code})")
                val out = JSONObject(txt).getJSONArray("content").getJSONObject(0).getString("text")
                val json = JSONObject(out.substring(out.indexOf('{'), out.lastIndexOf('}') + 1))
                val tagsArr = json.optJSONArray("hashtags") ?: JSONArray()
                val tags = (0 until tagsArr.length()).map { t -> tagsArr.getString(t).trim().let { if (it.startsWith("#")) it else "#$it" } }
                    .filter { it.length > 1 }.take(5).joinToString(" ")
                Caption(
                    hook = json.optString("hook").take(90), body = json.optString("body").take(200),
                    question = json.optString("question").take(120),
                    tags = tags.ifBlank { CaptionRules.suggestTags(cfg.game, cfg.tags) }, byAi = true,
                )
            }
        }

    private fun prompt(cfg: AutoConfig) = """
        These are 3 frames from a short vertical gaming clip. The creator recorded this gameplay themselves and is posting it as a Reel on Facebook and Instagram.
        Game: ${cfg.game.ifBlank { "unknown, work it out from the frames if you can" }}.
        Write in: ${cfg.language}${if (cfg.language == "Hinglish") " (Hindi words in Latin letters mixed with English, casual)" else ""}.
        Creator's usual hashtags: ${cfg.tags}

        Return ONLY a JSON object, no other text:
        {"hook": "first line, under 70 characters, about the actual moment in the frames, at most 1 emoji",
         "body": "one short sentence of context in the creator's voice",
         "question": "one genuine question that invites real opinions about the play",
         "hashtags": ["3 to 5 relevant hashtags including the game"]}

        Rules: never ask people to comment a word, like, share, follow or tag friends; don't claim things you can't see; no fake hype or false promises.
    """.trimIndent()

    private fun frames(ctx: Context, uri: Uri, durationSec: Double): List<String> {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            val d = if (durationSec > 0) durationSec else 10.0
            listOf(0.15, 0.5, 0.85).mapNotNull { f ->
                val bmp = r.getFrameAtTime((d * f * 1_000_000).toLong(), MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return@mapNotNull null
                val scale = 512f / maxOf(bmp.width, bmp.height)
                val small = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
                val out = ByteArrayOutputStream()
                small.compress(Bitmap.CompressFormat.JPEG, 72, out)
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            }
        } finally { try { r.release() } catch (_: Exception) { } }
    }
}
