package com.reelpilot.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Connected Meta account. Tokens are kept in encrypted storage on the device only. */
data class Account(
    val userToken: String = "",
    val pageId: String = "",
    val pageName: String = "",
    val pagePicture: String = "",
    val pageToken: String = "",
    val igId: String = "",
    val igUsername: String = "",
    val igPicture: String = "",
    val apiVersion: String = DEFAULT_API_VERSION,
    val permanent: Boolean = false,
) {
    val facebookReady get() = pageId.isNotBlank() && pageToken.isNotBlank()
    val instagramReady get() = facebookReady && igId.isNotBlank()
    val igToken get() = userToken.ifBlank { pageToken }

    companion object {
        const val DEFAULT_API_VERSION = "v26.0"
    }
}

/** One Reel the user asked to post. Each platform keeps its own progress so retries resume. */
data class Job(
    val id: String,
    val created: Long,
    val fileName: String,
    val filePath: String,
    val caption: String,
    val fb: Boolean,
    val ig: Boolean,
    val schedule: Long = 0L,     // unix seconds, 0 = now
    val aiLabel: Boolean = false,
    val fbState: String = if (fb) PENDING else SKIPPED,
    val igState: String = if (ig && schedule == 0L) PENDING else SKIPPED,
    val fbMsg: String = "",
    val igMsg: String = "",
    val fbVideoId: String = "",
    val fbStep: Int = 0,         // 0 start, 1 uploaded, 2 finished
    val igContainer: String = "",
    val igStep: Int = 0,         // 0 new, 1 uploaded, 2 published
    val fbLink: String = "",
    val igLink: String = "",
) {
    val finished get() = fbState in END && igState in END
    val failed get() = fbState == FAILED || igState == FAILED
    val headline get() = caption.lineSequence().firstOrNull { it.isNotBlank() }?.take(80) ?: fileName

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("created", created).put("fileName", fileName).put("filePath", filePath)
        .put("caption", caption).put("fb", fb).put("ig", ig).put("schedule", schedule)
        .put("aiLabel", aiLabel).put("fbState", fbState).put("igState", igState)
        .put("fbMsg", fbMsg).put("igMsg", igMsg).put("fbVideoId", fbVideoId).put("fbStep", fbStep)
        .put("igContainer", igContainer).put("igStep", igStep).put("fbLink", fbLink).put("igLink", igLink)

    companion object {
        const val PENDING = "pending"
        const val RUNNING = "running"
        const val RETRYING = "retrying"
        const val DONE = "done"
        const val FAILED = "failed"
        const val SKIPPED = "skipped"
        val END = setOf(DONE, FAILED, SKIPPED)

        fun fromJson(o: JSONObject) = Job(
            id = o.getString("id"), created = o.optLong("created"), fileName = o.optString("fileName"),
            filePath = o.optString("filePath"), caption = o.optString("caption"),
            fb = o.optBoolean("fb"), ig = o.optBoolean("ig"), schedule = o.optLong("schedule"),
            aiLabel = o.optBoolean("aiLabel"), fbState = o.optString("fbState", SKIPPED),
            igState = o.optString("igState", SKIPPED), fbMsg = o.optString("fbMsg"), igMsg = o.optString("igMsg"),
            fbVideoId = o.optString("fbVideoId"), fbStep = o.optInt("fbStep"),
            igContainer = o.optString("igContainer"), igStep = o.optInt("igStep"),
            fbLink = o.optString("fbLink"), igLink = o.optString("igLink"),
        )
    }
}

object Store {
    private lateinit var secure: SharedPreferences
    private lateinit var plain: SharedPreferences
    private val lock = Any()

    private val _account = MutableStateFlow(Account())
    val account: StateFlow<Account> = _account.asStateFlow()

    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()

    fun init(ctx: Context) {
        if (::secure.isInitialized) return
        plain = ctx.getSharedPreferences("reelpilot", Context.MODE_PRIVATE)
        secure = try {
            val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                ctx, "reelpilot_secure", key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            ctx.getSharedPreferences("reelpilot_fallback", Context.MODE_PRIVATE)
        }
        _account.value = readAccount()
        _jobs.value = readJobs()
    }

    // ---------- account ----------
    private fun readAccount() = Account(
        userToken = secure.getString("userToken", "") ?: "",
        pageId = secure.getString("pageId", "") ?: "",
        pageName = secure.getString("pageName", "") ?: "",
        pagePicture = secure.getString("pagePicture", "") ?: "",
        pageToken = secure.getString("pageToken", "") ?: "",
        igId = secure.getString("igId", "") ?: "",
        igUsername = secure.getString("igUsername", "") ?: "",
        igPicture = secure.getString("igPicture", "") ?: "",
        apiVersion = secure.getString("apiVersion", Account.DEFAULT_API_VERSION) ?: Account.DEFAULT_API_VERSION,
        permanent = secure.getBoolean("permanent", false),
    )

    fun saveAccount(a: Account) {
        secure.edit()
            .putString("userToken", a.userToken).putString("pageId", a.pageId)
            .putString("pageName", a.pageName).putString("pagePicture", a.pagePicture)
            .putString("pageToken", a.pageToken).putString("igId", a.igId)
            .putString("igUsername", a.igUsername).putString("igPicture", a.igPicture)
            .putString("apiVersion", a.apiVersion).putBoolean("permanent", a.permanent)
            .apply()
        _account.value = a
    }

    fun disconnect() = saveAccount(Account(apiVersion = _account.value.apiVersion))

    // ---------- preferences ----------
    var defaultTags: String
        get() = plain.getString("defaultTags", "#gaming #gameplay #reels") ?: ""
        set(v) = plain.edit().putString("defaultTags", v).apply()

    var defaultFb: Boolean
        get() = plain.getBoolean("defaultFb", true)
        set(v) = plain.edit().putBoolean("defaultFb", v).apply()

    var defaultIg: Boolean
        get() = plain.getBoolean("defaultIg", true)
        set(v) = plain.edit().putBoolean("defaultIg", v).apply()

    // ---------- jobs ----------
    private fun readJobs(): List<Job> = try {
        val arr = JSONArray(plain.getString("jobs", "[]"))
        (0 until arr.length()).map { Job.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) { emptyList() }

    private fun writeJobs(list: List<Job>) {
        val arr = JSONArray()
        list.take(300).forEach { arr.put(it.toJson()) }
        plain.edit().putString("jobs", arr.toString()).commit()
        _jobs.value = list.take(300)
    }

    fun job(id: String): Job? = synchronized(lock) { _jobs.value.firstOrNull { it.id == id } }

    fun saveJob(job: Job) = synchronized(lock) {
        val list = _jobs.value.toMutableList()
        val i = list.indexOfFirst { it.id == job.id }
        if (i >= 0) list[i] = job else list.add(0, job)
        writeJobs(list)
    }

    fun updateJob(id: String, change: (Job) -> Job): Job? = synchronized(lock) {
        val cur = _jobs.value.firstOrNull { it.id == id } ?: return null
        val next = change(cur)
        saveJob(next)
        next
    }

    fun deleteJob(id: String) = synchronized(lock) { writeJobs(_jobs.value.filterNot { it.id == id }) }

    /** Successful API posts in the last 24 h, used to stay inside Meta's publishing limits. */
    fun postsLast24h(facebook: Boolean): Int {
        val since = System.currentTimeMillis() - 24 * 3600_000L
        return _jobs.value.count { it.created > since && if (facebook) it.fbState == Job.DONE else it.igState == Job.DONE }
    }
}
