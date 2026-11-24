package com.reelpilot.app.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlin.math.abs

enum class Level { OK, WARN, BAD }
data class Check(val level: Level, val title: String, val detail: String)

data class VideoInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val durationSec: Double,
    val width: Int,
    val height: Int,
    val fps: Float,
    val videoCodec: String,
    val audioCodec: String,
    val thumbnail: Bitmap?,
)

/** Reads a picked video and checks it against Meta's Reels specs. */
object VideoRules {

    fun inspect(ctx: Context, uri: Uri): VideoInfo {
        var name = "video.mp4"
        var size = 0L
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0) ?: name
                size = c.getLong(1)
            }
        }
        val r = MediaMetadataRetriever()
        var w = 0; var h = 0; var dur = 0.0; var thumb: Bitmap? = null
        try {
            r.setDataSource(ctx, uri)
            dur = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000.0
            w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            thumb = r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Exception) {
        } finally { try { r.release() } catch (_: Exception) {} }

        var fps = 0f; var vCodec = ""; var aCodec = ""
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, uri, null)
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && vCodec.isEmpty()) {
                    vCodec = mime
                    if (f.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        fps = try { f.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }
                        catch (e: Exception) { try { f.getFloat(MediaFormat.KEY_FRAME_RATE) } catch (e2: Exception) { 0f } }
                    }
                } else if (mime.startsWith("audio/") && aCodec.isEmpty()) aCodec = mime
            }
        } catch (_: Exception) {
        } finally { ex.release() }

        return VideoInfo(uri, name, size, dur, w, h, fps, vCodec, aCodec, thumb)
    }

    fun check(v: VideoInfo, fb: Boolean, ig: Boolean): List<Check> {
        val out = mutableListOf<Check>()
        val ratio = if (v.height > 0) v.width.toDouble() / v.height else 0.0
        val vertical = abs(ratio - 9.0 / 16.0) < 0.02

        out += if (v.width == 0) Check(Level.BAD, "Can't read this video", "Export it as MP4 (H.264) from your editor and pick it again.")
        else if (vertical) Check(Level.OK, "Vertical 9:16", "${v.width} × ${v.height}")
        else Check(Level.BAD, "Not vertical 9:16", "${v.width} × ${v.height}. Crop to 1080 × 1920 so it fills the phone screen.")

        out += when {
            v.width >= 1080 && v.height >= 1920 -> Check(Level.OK, "Full HD", "Looks sharp in the Reels feed.")
            v.width >= 540 && v.height >= 960 -> Check(Level.WARN, "Below 1080p", "Allowed, but 1080 × 1920 looks sharper.")
            v.width > 0 -> Check(Level.BAD, "Resolution too low", "Meta needs at least 540 × 960.")
            else -> null
        }?.let { out += it }

        val d = v.durationSec
        val minLen = if (ig) 5.0 else 3.0
        out += when {
            d <= 0 -> Check(Level.BAD, "Unknown length", "This file has no readable duration.")
            d > 90 -> Check(Level.BAD, "Too long (${fmt(d)})", "Reels must be 90 seconds or less. Trim it.")
            d < minLen -> Check(Level.BAD, "Too short (${fmt(d)})", if (ig) "Instagram Reels need 5 seconds or more." else "Facebook Reels need 3 seconds or more.")
            d <= 30 -> Check(Level.OK, "Length ${fmt(d)}", "Short clips get more replays and finish-watches.")
            else -> Check(Level.OK, "Length ${fmt(d)}", "Fits both apps. Keep the action in the first 3 seconds.")
        }

        out += when {
            v.fps <= 0f -> Check(Level.WARN, "Frame rate unknown", "Meta wants 24–60 fps. Most recorders already do this.")
            v.fps < 23.5f -> Check(Level.BAD, "Frame rate ${v.fps.toInt()} fps", "Meta needs 24–60 fps.")
            v.fps > 60.5f -> Check(Level.WARN, "Frame rate ${v.fps.toInt()} fps", "Above 60 fps. Export at 60 fps to be safe.")
            else -> Check(Level.OK, "Frame rate ${v.fps.toInt()} fps", "Inside Meta's 24–60 fps range.")
        }

        out += when (v.videoCodec) {
            "video/avc", "video/hevc" -> Check(Level.OK, if (v.videoCodec == "video/avc") "H.264 video" else "H.265 video", "Supported by Facebook and Instagram.")
            "" -> Check(Level.WARN, "Codec unknown", "Export as MP4 H.264 if the upload fails.")
            else -> Check(if (ig) Level.BAD else Level.WARN, "Codec ${v.videoCodec.removePrefix("video/")}", "Instagram needs H.264 or H.265. Re-export as MP4 H.264.")
        }

        out += when {
            v.audioCodec.isEmpty() -> Check(Level.WARN, "No audio", "Allowed, but sound and voice usually keep people watching.")
            v.audioCodec == "audio/mp4a-latm" -> Check(Level.OK, "AAC audio", "Supported.")
            else -> Check(Level.WARN, "Audio ${v.audioCodec.removePrefix("audio/")}", "Meta prefers AAC audio.")
        }

        val mb = v.sizeBytes / 1_048_576.0
        if (v.sizeBytes > 0) out += when {
            ig && mb > 300 -> Check(Level.BAD, "File too large (${mb.toInt()} MB)", "Instagram accepts up to 300 MB. Export at a lower bitrate.")
            ig && mb > 100 -> Check(Level.WARN, "Large file (${mb.toInt()} MB)", "Uploads will be slower. Under 100 MB is safest for Instagram.")
            else -> Check(Level.OK, "Size ${"%.1f".format(mb)} MB", "Good upload size.")
        }
        return out
    }

    private fun fmt(s: Double) = if (s >= 60) "${(s / 60).toInt()}:${"%02d".format((s % 60).toInt())}" else "${"%.1f".format(s)}s"
}

/** Caption checks based on Meta's ranking guidance (no engagement bait, real conversation). */
object CaptionRules {
    private val bait = Regex(
        "\\b(comment\\s+(yes|1|ok|done|below if)|like if|share if|tag (a|3|your|two|2) friends?|type amen|follow for follow|f4f|like for like|l4l|sub for sub)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val hashtag = Regex("#[\\p{L}\\p{N}_]+")

    fun build(hook: String, body: String, question: String, tags: String): String =
        listOf(hook, body, question, tags).map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")

    fun check(hook: String, caption: String, ig: Boolean): List<Check> {
        val out = mutableListOf<Check>()
        val tags = hashtag.findAll(caption).count()
        out += if (hook.isBlank()) Check(Level.BAD, "Add a hook", "The first line decides if people stop scrolling.")
        else Check(Level.OK, "Hook ready", "\"${hook.take(60)}\"")
        bait.find(caption)?.let {
            out += Check(Level.BAD, "Engagement bait: \"${it.value}\"", "Meta lowers the reach of posts that beg for likes, comments or tags. Ask a real question instead.")
        }
        out += when {
            ig && tags > 30 -> Check(Level.BAD, "$tags hashtags", "Instagram allows 30 at most.")
            tags > 8 -> Check(Level.WARN, "$tags hashtags", "3–5 relevant hashtags work better than many.")
            tags == 0 -> Check(Level.WARN, "No hashtags", "Add 3–5 so people searching for your game find it.")
            else -> Check(Level.OK, "$tags hashtags", "A good amount.")
        }
        if (ig && caption.length > 2200) out += Check(Level.BAD, "Caption too long", "Instagram allows 2,200 characters (now ${caption.length}).")
        return out
    }

    fun suggestTags(game: String, defaults: String): String {
        val g = game.filter { it.isLetterOrDigit() }.lowercase()
        val list = mutableListOf<String>()
        if (g.isNotEmpty()) { list += "#$g"; list += "#${g}clips" }
        defaults.split(" ", ",").map { it.trim() }.filter { it.startsWith("#") }.forEach { if (it !in list) list += it }
        return list.take(5).joinToString(" ")
    }
}
