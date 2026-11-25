package com.reelpilot.app.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Connected YouTube channel. The refresh token comes from Google's OAuth Playground with the user's own client. */
data class YtAccount(
    val clientId: String = "",
    val clientSecret: String = "",
    val refreshToken: String = "",
    val channelId: String = "",
    val channelTitle: String = "",
    val channelThumb: String = "",
) {
    val ready get() = clientId.isNotBlank() && clientSecret.isNotBlank() && refreshToken.isNotBlank()
}

data class YtChannel(val id: String, val title: String, val thumb: String, val subscribers: Long, val videos: Long, val views: Long, val uploads: String)
data class YtVideo(val id: String, val title: String, val thumb: String, val published: String)

class YouTubeApi {

    companion object {
        private var cachedToken = ""
        private var cachedFor = ""
        private var expiresAt = 0L
        private val client get() = MetaApi.client

        /** Title for a Short: the caption's first line without hashtags, max 100 characters. */
        fun titleFor(job: Job): String {
            val raw = job.ytTitle.ifBlank { job.caption.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() }
            val clean = raw.replace(Regex("#[\\p{L}\\p{N}_]+"), "").replace(Regex("\\s+"), " ").replace("<", "").replace(">", "").trim()
            return clean.ifBlank { "Gameplay clip" }.take(100)
        }

        fun tagsFor(caption: String): List<String> {
            val out = mutableListOf<String>()
            var len = 0
            Regex("#([\\p{L}\\p{N}_]+)").findAll(caption).map { it.groupValues[1] }.distinct().forEach {
                if (len + it.length + 1 <= 450) { out += it; len += it.length + 1 }
            }
            return out
        }

        fun metadata(job: Job): JSONObject {
            val desc = (job.caption.replace("<", "").replace(">", "") + if (!job.caption.contains("#shorts", true)) "\n\n#Shorts" else "").take(4900)
            val status = JSONObject()
                .put("privacyStatus", if (job.schedule > 0) "private" else job.ytPrivacy)
                .put("selfDeclaredMadeForKids", false)
                .put("containsSyntheticMedia", job.aiLabel)
            if (job.schedule > 0) {
                val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                status.put("publishAt", f.format(Date(job.schedule * 1000)))
            }
            return JSONObject()
                .put("snippet", JSONObject().put("title", titleFor(job)).put("description", desc)
                    .put("tags", JSONArray(tagsFor(job.caption))).put("categoryId", "20"))
                .put("status", status)
        }
    }

    private fun parseError(code: Int, body: String): MetaError {
        val e = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
        val err = e.optJSONObject("error")
        val reason = err?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
        val oauth = e.optString("error")
        val msg = when {
            oauth == "invalid_grant" -> "YouTube login expired or was removed. Reconnect YouTube in Settings."
            oauth == "invalid_client" -> "Wrong YouTube Client ID or Secret. Check them in Settings → YouTube."
            reason == "quotaExceeded" || reason == "rateLimitExceeded" -> "YouTube's daily upload limit for your Google project is used up. It resets at 1:30 PM India time."
            reason == "uploadLimitExceeded" -> "Your YouTube channel hit its daily upload limit. Try again tomorrow."
            reason == "youtubeSignupRequired" -> "This Google account has no YouTube channel yet. Create one in the YouTube app first."
            code == 401 -> "YouTube login is no longer valid. Reconnect YouTube in Settings."
            code == 403 -> err?.optString("message")?.ifBlank { null } ?: "YouTube refused the upload (no permission)."
            else -> err?.optString("message")?.ifBlank { null } ?: "YouTube error $code"
        }
        val transient = code >= 500 || code == 429 || reason == "backendError"
        if (code == 401) cachedToken = ""
        return MetaError(msg, code, transient)
    }

    suspend fun token(y: YtAccount): String = withContext(Dispatchers.IO) {
        if (cachedToken.isNotBlank() && cachedFor == y.refreshToken && System.currentTimeMillis() < expiresAt - 60_000) return@withContext cachedToken
        val form = FormBody.Builder().add("client_id", y.clientId).add("client_secret", y.clientSecret)
            .add("refresh_token", y.refreshToken).add("grant_type", "refresh_token").build()
        client.newCall(Request.Builder().url("https://oauth2.googleapis.com/token").post(form).build()).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw parseError(r.code, body)
            val j = JSONObject(body)
            cachedToken = j.getString("access_token"); cachedFor = y.refreshToken
            expiresAt = System.currentTimeMillis() + j.optLong("expires_in", 3600) * 1000
            cachedToken
        }
    }

    private suspend fun getJson(y: YtAccount, url: String): JSONObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("Authorization", "Bearer ${token(y)}").get().build()
        client.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw parseError(r.code, body)
            JSONObject(body)
        }
    }

    suspend fun channel(y: YtAccount): YtChannel {
        val j = getJson(y, "https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics,contentDetails&mine=true")
        val c = j.optJSONArray("items")?.optJSONObject(0) ?: throw MetaError("No YouTube channel found on this Google account.", 0, false)
        val sn = c.optJSONObject("snippet"); val st = c.optJSONObject("statistics")
        return YtChannel(
            id = c.optString("id"), title = sn?.optString("title").orEmpty(),
            thumb = sn?.optJSONObject("thumbnails")?.optJSONObject("medium")?.optString("url").orEmpty(),
            subscribers = st?.optString("subscriberCount")?.toLongOrNull() ?: 0,
            videos = st?.optString("videoCount")?.toLongOrNull() ?: 0,
            views = st?.optString("viewCount")?.toLongOrNull() ?: 0,
            uploads = c.optJSONObject("contentDetails")?.optJSONObject("relatedPlaylists")?.optString("uploads").orEmpty(),
        )
    }

    suspend fun recent(y: YtAccount, uploadsPlaylist: String): List<YtVideo> {
        if (uploadsPlaylist.isBlank()) return emptyList()
        val j = getJson(y, "https://www.googleapis.com/youtube/v3/playlistItems?part=snippet&maxResults=20&playlistId=$uploadsPlaylist")
        val arr = j.optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val s = arr.getJSONObject(i).optJSONObject("snippet") ?: return@mapNotNull null
            YtVideo(
                id = s.optJSONObject("resourceId")?.optString("videoId").orEmpty(), title = s.optString("title"),
                thumb = s.optJSONObject("thumbnails")?.optJSONObject("medium")?.optString("url").orEmpty(),
                published = s.optString("publishedAt"),
            )
        }
    }

    /** Opens a resumable upload session and returns its URL. */
    suspend fun startUpload(y: YtAccount, file: File, meta: JSONObject): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status")
            .header("Authorization", "Bearer ${token(y)}")
            .header("X-Upload-Content-Length", file.length().toString())
            .header("X-Upload-Content-Type", "video/mp4")
            .post(meta.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()
        client.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw parseError(r.code, body)
            r.header("Location") ?: throw MetaError("YouTube didn't return an upload link.", 0, true)
        }
    }

    /** Asks YouTube how much of an interrupted upload it has. Returns bytes received, or the finished video. */
    suspend fun progress(y: YtAccount, url: String, total: Long): Pair<Long, JSONObject?> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("Authorization", "Bearer ${token(y)}")
            .header("Content-Range", "bytes */$total").put(ByteArray(0).toRequestBody(null)).build()
        client.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            when {
                r.code == 200 || r.code == 201 -> total to JSONObject(body)
                r.code == 308 -> (r.header("Range")?.substringAfterLast('-')?.toLongOrNull()?.plus(1) ?: 0L) to null
                r.code == 404 || r.code == 410 -> -1L to null   // session expired: start again
                else -> throw parseError(r.code, body)
            }
        }
    }

    suspend fun upload(y: YtAccount, url: String, file: File, offset: Long, onProgress: (Long) -> Unit): JSONObject =
        withContext(Dispatchers.IO) {
            val total = file.length()
            val body = object : RequestBody() {
                override fun contentType(): MediaType = "video/mp4".toMediaType()
                override fun contentLength(): Long = total - offset
                override fun writeTo(sink: BufferedSink) {
                    FileInputStream(file).use { input ->
                        input.channel.position(offset)
                        val buf = ByteArray(256 * 1024)
                        var sent = offset
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            sink.write(buf, 0, n); sent += n; onProgress(sent)
                        }
                    }
                }
            }
            val b = Request.Builder().url(url).header("Authorization", "Bearer ${token(y)}").put(body)
            if (offset > 0) b.header("Content-Range", "bytes $offset-${total - 1}/$total")
            client.newCall(b.build()).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (r.code == 200 || r.code == 201) JSONObject(text) else throw parseError(r.code, text)
            }
        }
}

/** Fallback that always posts publicly: hands the clip to the official YouTube app with title and description filled in. */
object YouTubeShare {
    fun intent(ctx: Context, source: Uri, title: String, description: String): Intent {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 86_400_000) it.delete() } }
        val out = File(dir, "reel_${System.currentTimeMillis()}.mp4")
        ctx.contentResolver.openInputStream(source)!!.use { input -> out.outputStream().use { input.copyTo(it, 1 shl 20) } }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", out)
        return Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, title)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, description)
            clipData = ClipData.newRawUri(title, uri)
            setPackage("com.google.android.youtube")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun start(ctx: Context, intent: Intent) {
        try { ctx.startActivity(intent) }
        catch (e: Exception) {
            // YouTube app not installed: let the user pick another app.
            ctx.startActivity(Intent.createChooser(Intent(intent).setPackage(null), "Post to YouTube").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
