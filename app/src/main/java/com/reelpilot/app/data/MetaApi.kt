package com.reelpilot.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Error returned by Meta's Graph API. [transient] errors are worth retrying. */
class MetaError(message: String, val code: Int, val transient: Boolean) : Exception(message)

data class PageOption(
    val id: String, val name: String, val token: String, val picture: String,
    val igId: String, val igUsername: String, val igPicture: String,
)

data class FbPage(val name: String, val picture: String, val followers: Long, val link: String)
data class FbVideo(val id: String, val description: String, val created: String, val picture: String, val link: String, val length: Double)
data class IgProfile(val username: String, val name: String, val picture: String, val followers: Long, val follows: Long, val media: Long, val bio: String)
data class IgMedia(val id: String, val caption: String, val thumb: String, val link: String, val time: String, val likes: Long, val comments: Long, val isReel: Boolean)
data class IgQuota(val used: Int, val total: Int)

class MetaApi(private val version: String = Account.DEFAULT_API_VERSION) {

    companion object {
        val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(600, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        private val TRANSIENT_CODES = setOf(1, 2, 4, 17, 32, 341, 613, 80001, 80002)
    }

    private fun graph(path: String) = "https://graph.facebook.com/$version/$path"

    suspend fun get(path: String, params: Map<String, String>): JSONObject = withContext(Dispatchers.IO) {
        val b = graph(path).toHttpUrl().newBuilder()
        params.forEach { (k, v) -> b.addQueryParameter(k, v) }
        exec(Request.Builder().url(b.build()).get().build())
    }

    suspend fun post(path: String, params: Map<String, String>): JSONObject = withContext(Dispatchers.IO) {
        val f = FormBody.Builder()
        params.forEach { (k, v) -> f.add(k, v) }
        exec(Request.Builder().url(graph(path)).post(f.build()).build())
    }

    private fun exec(req: Request): JSONObject {
        client.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            val json = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
            json.optJSONObject("error")?.let { throw toError(it) }
            json.optJSONObject("debug_info")?.let { if (!r.isSuccessful) throw MetaError(it.optString("message", "Upload failed"), r.code, r.code >= 500) }
            if (!r.isSuccessful) throw MetaError("Meta returned HTTP ${r.code}", r.code, r.code >= 500 || r.code == 429)
            return json
        }
    }

    private fun toError(e: JSONObject): MetaError {
        val code = e.optInt("code")
        val friendly = when (code) {
            190 -> "Your Meta login has expired. Open Settings → Reconnect."
            10, 200 -> "Missing permission. Reconnect and allow every permission listed in the guide."
            368 -> "Meta temporarily blocked posting from this account. Try again later."
            else -> e.optString("error_user_msg").ifBlank { e.optString("message", "Meta API error") }
        }
        val transient = e.optBoolean("is_transient") || code in TRANSIENT_CODES
        return MetaError(friendly, code, transient)
    }

    /** Streams [file] from [offset] to Meta's resumable upload host, reporting bytes sent. */
    suspend fun upload(url: String, token: String, file: File, offset: Long, onProgress: (Long) -> Unit): JSONObject =
        withContext(Dispatchers.IO) {
            val body = object : RequestBody() {
                override fun contentType(): MediaType = "application/octet-stream".toMediaType()
                override fun contentLength(): Long = file.length() - offset
                override fun writeTo(sink: BufferedSink) {
                    FileInputStream(file).use { input ->
                        input.channel.position(offset)
                        val buf = ByteArray(256 * 1024)
                        var sent = offset
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            sink.write(buf, 0, n)
                            sent += n
                            onProgress(sent)
                        }
                    }
                }
            }
            val req = Request.Builder().url(url)
                .header("Authorization", "OAuth $token")
                .header("offset", offset.toString())
                .header("file_size", file.length().toString())
                .post(body).build()
            exec(req)
        }

    fun ruploadFacebook(videoId: String) = "https://rupload.facebook.com/video-upload/$version/$videoId"
    fun ruploadInstagram(containerId: String) = "https://rupload.facebook.com/ig-api-upload/$version/$containerId"

    // ---------------- account set-up ----------------

    suspend fun exchangeForLongLived(appId: String, appSecret: String, shortToken: String): String =
        get("oauth/access_token", mapOf(
            "grant_type" to "fb_exchange_token", "client_id" to appId,
            "client_secret" to appSecret, "fb_exchange_token" to shortToken,
        )).getString("access_token")

    suspend fun pages(userToken: String): List<PageOption> {
        val j = get("me/accounts", mapOf(
            "fields" to "id,name,access_token,picture.type(large){url},instagram_business_account{id,username,profile_picture_url}",
            "limit" to "100", "access_token" to userToken,
        ))
        val arr = j.optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val p = arr.getJSONObject(i)
            val ig = p.optJSONObject("instagram_business_account")
            PageOption(
                id = p.getString("id"), name = p.optString("name"), token = p.optString("access_token"),
                picture = p.optJSONObject("picture")?.optJSONObject("data")?.optString("url").orEmpty(),
                igId = ig?.optString("id").orEmpty(), igUsername = ig?.optString("username").orEmpty(),
                igPicture = ig?.optString("profile_picture_url").orEmpty(),
            )
        }
    }

    suspend fun pageWithToken(pageId: String, pageToken: String): PageOption {
        val p = get(pageId, mapOf(
            "fields" to "id,name,picture.type(large){url},instagram_business_account{id,username,profile_picture_url}",
            "access_token" to pageToken,
        ))
        val ig = p.optJSONObject("instagram_business_account")
        return PageOption(
            id = p.getString("id"), name = p.optString("name"), token = pageToken,
            picture = p.optJSONObject("picture")?.optJSONObject("data")?.optString("url").orEmpty(),
            igId = ig?.optString("id").orEmpty(), igUsername = ig?.optString("username").orEmpty(),
            igPicture = ig?.optString("profile_picture_url").orEmpty(),
        )
    }

    // ---------------- Facebook ----------------

    suspend fun facebookPage(a: Account): FbPage {
        val p = get(a.pageId, mapOf("fields" to "name,picture.type(large){url},followers_count,fan_count,link", "access_token" to a.pageToken))
        return FbPage(
            name = p.optString("name"),
            picture = p.optJSONObject("picture")?.optJSONObject("data")?.optString("url").orEmpty(),
            followers = p.optLong("followers_count", p.optLong("fan_count")),
            link = p.optString("link"),
        )
    }

    suspend fun facebookVideos(a: Account): List<FbVideo> {
        val j = get("${a.pageId}/videos", mapOf(
            "fields" to "id,description,created_time,permalink_url,picture,length",
            "limit" to "25", "access_token" to a.pageToken,
        ))
        val arr = j.optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val v = arr.getJSONObject(i)
            val link = v.optString("permalink_url")
            FbVideo(
                id = v.optString("id"), description = v.optString("description"),
                created = v.optString("created_time"), picture = v.optString("picture"),
                link = if (link.startsWith("/")) "https://www.facebook.com$link" else link,
                length = v.optDouble("length", 0.0),
            )
        }
    }

    // ---------------- Instagram ----------------

    suspend fun instagramProfile(a: Account): IgProfile {
        val p = get(a.igId, mapOf(
            "fields" to "username,name,profile_picture_url,followers_count,follows_count,media_count,biography",
            "access_token" to a.igToken,
        ))
        return IgProfile(
            username = p.optString("username"), name = p.optString("name"),
            picture = p.optString("profile_picture_url"), followers = p.optLong("followers_count"),
            follows = p.optLong("follows_count"), media = p.optLong("media_count"), bio = p.optString("biography"),
        )
    }

    suspend fun instagramMedia(a: Account): List<IgMedia> {
        val j = get("${a.igId}/media", mapOf(
            "fields" to "id,caption,media_type,media_product_type,thumbnail_url,media_url,permalink,timestamp,like_count,comments_count",
            "limit" to "30", "access_token" to a.igToken,
        ))
        val arr = j.optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val m = arr.getJSONObject(i)
            val isVideo = m.optString("media_type") == "VIDEO"
            IgMedia(
                id = m.optString("id"), caption = m.optString("caption"),
                thumb = if (isVideo) m.optString("thumbnail_url") else m.optString("media_url"),
                link = m.optString("permalink"), time = m.optString("timestamp"),
                likes = m.optLong("like_count"), comments = m.optLong("comments_count"),
                isReel = m.optString("media_product_type") == "REELS",
            )
        }
    }

    suspend fun instagramQuota(a: Account): IgQuota {
        val j = get("${a.igId}/content_publishing_limit", mapOf("fields" to "quota_usage,config", "access_token" to a.igToken))
        val d = j.optJSONArray("data")?.optJSONObject(0) ?: return IgQuota(0, 100)
        return IgQuota(d.optInt("quota_usage"), d.optJSONObject("config")?.optInt("quota_total", 100) ?: 100)
    }
}

fun Throwable.isTransient(): Boolean = this is IOException || (this is MetaError && transient)
