package com.reelpilot.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Settings for Auto Pilot: which folder to watch, where and when to post, how to write captions. */
data class AutoConfig(
    val enabled: Boolean = false,
    val folderUri: String = "",
    val folderName: String = "",
    val fb: Boolean = true,
    val ig: Boolean = true,
    val instant: Boolean = false,          // true = post as soon as found, false = at time slots
    val slots: Set<Int> = setOf(13, 18, 21), // hours of the day
    val gapMinutes: Int = 60,
    val perDay: Int = 3,
    val game: String = "",
    val language: String = "English",       // English, Hinglish, Hindi
    val tags: String = "#gaming #gameplay",
    val useAi: Boolean = false,
    val confirmedOriginal: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("enabled", enabled).put("folderUri", folderUri).put("folderName", folderName)
        .put("fb", fb).put("ig", ig).put("instant", instant).put("slots", JSONArray(slots.sorted()))
        .put("gapMinutes", gapMinutes).put("perDay", perDay).put("game", game).put("language", language)
        .put("tags", tags).put("useAi", useAi).put("confirmedOriginal", confirmedOriginal)

    companion object {
        fun fromJson(o: JSONObject): AutoConfig {
            val arr = o.optJSONArray("slots")
            val slots = if (arr == null) setOf(13, 18, 21) else (0 until arr.length()).map { arr.getInt(it) }.toSet()
            return AutoConfig(
                enabled = o.optBoolean("enabled"), folderUri = o.optString("folderUri"), folderName = o.optString("folderName"),
                fb = o.optBoolean("fb", true), ig = o.optBoolean("ig", true), instant = o.optBoolean("instant"),
                slots = slots, gapMinutes = o.optInt("gapMinutes", 60), perDay = o.optInt("perDay", 3),
                game = o.optString("game"), language = o.optString("language", "English"),
                tags = o.optString("tags", "#gaming #gameplay"), useAi = o.optBoolean("useAi"),
                confirmedOriginal = o.optBoolean("confirmedOriginal"),
            )
        }
    }
}

/** A video Auto Pilot found in the watched folder. */
data class AutoItem(
    val docId: String,
    val name: String,
    val modified: Long,
    val status: String = WAITING,
    val message: String = "",
    val jobId: String = "",
    val queuedAt: Long = 0L,
) {
    fun toJson(): JSONObject = JSONObject().put("docId", docId).put("name", name).put("modified", modified)
        .put("status", status).put("message", message).put("jobId", jobId).put("queuedAt", queuedAt)

    companion object {
        const val WAITING = "waiting"
        const val QUEUED = "queued"
        const val SKIPPED = "skipped"
        fun fromJson(o: JSONObject) = AutoItem(
            o.getString("docId"), o.optString("name"), o.optLong("modified"), o.optString("status", WAITING),
            o.optString("message"), o.optString("jobId"), o.optLong("queuedAt"),
        )
    }
}

object AutoStore {
    private lateinit var sp: SharedPreferences
    private val lock = Any()
    private val _config = MutableStateFlow(AutoConfig())
    val config: StateFlow<AutoConfig> = _config.asStateFlow()
    private val _items = MutableStateFlow<List<AutoItem>>(emptyList())
    val items: StateFlow<List<AutoItem>> = _items.asStateFlow()

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.getSharedPreferences("reelpilot_auto", Context.MODE_PRIVATE)
        _config.value = try { AutoConfig.fromJson(JSONObject(sp.getString("config", "{}") ?: "{}")) } catch (e: Exception) { AutoConfig() }
        _items.value = try {
            val a = JSONArray(sp.getString("items", "[]"))
            (0 until a.length()).map { AutoItem.fromJson(a.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    fun save(c: AutoConfig) {
        sp.edit().putString("config", c.toJson().toString()).apply()
        _config.value = c
    }

    private fun writeItems(list: List<AutoItem>) {
        val trimmed = list.sortedByDescending { it.modified }.take(500)
        val a = JSONArray(); trimmed.forEach { a.put(it.toJson()) }
        sp.edit().putString("items", a.toString()).commit()
        _items.value = trimmed
    }

    fun upsert(item: AutoItem) = synchronized(lock) {
        val l = _items.value.toMutableList()
        val i = l.indexOfFirst { it.docId == item.docId }
        if (i >= 0) l[i] = item else l += item
        writeItems(l)
    }

    fun addNew(found: List<AutoItem>) = synchronized(lock) {
        val known = _items.value.map { it.docId }.toSet()
        val fresh = found.filter { it.docId !in known }
        if (fresh.isNotEmpty()) writeItems(_items.value + fresh)
        fresh.size
    }

    /** Forget the folder's history so every video in it is treated as new. */
    fun clearItems() = synchronized(lock) { writeItems(emptyList()) }

    fun waiting(): List<AutoItem> = _items.value.filter { it.status == AutoItem.WAITING }.sortedBy { it.modified }

    fun queuedToday(): Int {
        val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
        return _items.value.count { it.status == AutoItem.QUEUED && it.queuedAt >= start }
    }

    fun lastQueuedAt(): Long = _items.value.filter { it.status == AutoItem.QUEUED }.maxOfOrNull { it.queuedAt } ?: 0L

    /** When Auto Pilot will next post, or null if nothing is waiting. A time <= now means a post is due. */
    fun nextPostTime(now: Long = System.currentTimeMillis()): Long? {
        val c = _config.value
        if (!c.enabled || waiting().isEmpty()) return null
        val last = lastQueuedAt()
        val full = queuedToday() >= c.perDay
        val cal = Calendar.getInstance()
        fun at(dayOffset: Int, hour: Int): Long {
            cal.timeInMillis = now
            cal.add(Calendar.DAY_OF_MONTH, dayOffset)
            cal.set(Calendar.HOUR_OF_DAY, hour); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
        if (c.instant) return if (full) at(1, 0) else maxOf(now, last + c.gapMinutes * 60_000L)
        val hours = c.slots.sorted()
        if (hours.isEmpty()) return null
        if (!full) hours.map { at(0, it) }.firstOrNull { it <= now && last < it }?.let { return now }
        if (!full) hours.map { at(0, it) }.firstOrNull { it > now }?.let { return it }
        return at(1, hours.first())
    }
}
