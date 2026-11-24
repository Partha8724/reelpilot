package com.reelpilot.app.work

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.reelpilot.app.App
import com.reelpilot.app.MainActivity
import com.reelpilot.app.data.Account
import com.reelpilot.app.data.Job
import com.reelpilot.app.data.MetaApi
import com.reelpilot.app.data.MetaError
import com.reelpilot.app.data.Store
import com.reelpilot.app.data.isTransient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Uploads one Reel to Facebook and/or Instagram in the background.
 * Runs as a foreground job so it keeps going when the app is closed,
 * and resumes from the last finished step after network drops.
 */
class UploadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        const val TAG = "upload"
        const val KEY_ID = "id"
        const val KEY_PCT = "pct"
        const val KEY_PHASE = "phase"
        private const val MAX_ATTEMPTS = 6

        fun enqueue(ctx: Context, jobId: String) {
            val req = OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(workDataOf(KEY_ID to jobId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("job-$jobId", ExistingWorkPolicy.REPLACE, req)
        }

        fun cancel(ctx: Context, jobId: String) {
            WorkManager.getInstance(ctx).cancelUniqueWork("job-$jobId")
        }
    }

    private val jobId get() = inputData.getString(KEY_ID).orEmpty()
    private val notifId get() = 1000 + (jobId.hashCode() and 0xffff)
    private var lastPct = -1

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(0f, "Preparing upload…")

    override suspend fun doWork(): Result {
        val id = jobId
        var job = Store.job(id) ?: return Result.failure()
        val acct = Store.account.value
        val file = File(job.filePath)

        if (!acct.facebookReady) return failAll(job, "Connect your Facebook Page in Settings first.")
        if (!file.exists()) return failAll(job, "The video file is gone. Please pick it again.")

        try { setForeground(foreground(0f, "Starting upload…")) } catch (_: Exception) { }

        val api = MetaApi(acct.apiVersion)
        val todo = buildList {
            if (job.fbState !in Job.END) add("fb")
            if (job.igState !in Job.END) add("ig")
        }
        var retry = false
        todo.forEachIndexed { i, p ->
            val base = i.toFloat() / todo.size
            val share = 1f / todo.size
            try {
                if (p == "fb") {
                    Store.updateJob(id) { it.copy(fbState = Job.RUNNING, fbMsg = "Uploading…") }
                    runFacebook(api, acct, file, base, share)
                } else {
                    Store.updateJob(id) { it.copy(igState = Job.RUNNING, igMsg = "Uploading…") }
                    runInstagram(api, acct, file, base, share)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: "Upload failed"
                if (e.isTransient() && runAttemptCount < MAX_ATTEMPTS) {
                    retry = true
                    Store.updateJob(id) {
                        if (p == "fb") it.copy(fbState = Job.RETRYING, fbMsg = "Connection problem, retrying automatically…")
                        else it.copy(igState = Job.RETRYING, igMsg = "Connection problem, retrying automatically…")
                    }
                } else {
                    Store.updateJob(id) {
                        if (p == "fb") it.copy(fbState = Job.FAILED, fbMsg = msg) else it.copy(igState = Job.FAILED, igMsg = msg)
                    }
                }
            }
        }
        if (retry) return Result.retry()

        job = Store.job(id) ?: return Result.success()
        if (!job.failed) file.delete()
        notifyDone(job)
        return if (job.failed) Result.failure() else Result.success()
    }

    // ---------------- Facebook Reels ----------------

    private suspend fun runFacebook(api: MetaApi, a: Account, file: File, base: Float, share: Float) {
        var job = Store.job(jobId)!!
        val token = a.pageToken

        if (job.fbStep == 0) {
            var vid = job.fbVideoId
            if (vid.isBlank()) {
                progress(base, "Facebook · starting")
                vid = api.post("${a.pageId}/video_reels", mapOf("upload_phase" to "start", "access_token" to token)).getString("video_id")
                job = Store.updateJob(jobId) { it.copy(fbVideoId = vid) }!!
            }
            // Resume where Meta says we stopped.
            var offset = 0L
            try {
                val st = api.get(vid, mapOf("fields" to "status", "access_token" to token)).optJSONObject("status")
                offset = st?.optJSONObject("uploading_phase")?.optLong("bytes_transfered", 0L) ?: 0L
            } catch (_: Exception) { }
            if (offset < file.length()) {
                uploadWithProgress(file, offset, base, share * 0.85f, "Facebook · uploading") { cb ->
                    api.upload(api.ruploadFacebook(vid), token, file, offset, cb)
                }
            }
            job = Store.updateJob(jobId) { it.copy(fbStep = 1) }!!
        }

        if (job.fbStep == 1) {
            progress(base + share * 0.88f, "Facebook · publishing")
            val params = mutableMapOf(
                "upload_phase" to "finish", "video_id" to job.fbVideoId, "access_token" to token,
                "video_state" to if (job.schedule > 0) "SCHEDULED" else "PUBLISHED",
                "description" to job.caption,
            )
            if (job.schedule > 0) params["scheduled_publish_time"] = job.schedule.toString()
            if (job.aiLabel) params["is_ai_generated"] = "true"
            try {
                api.post("${a.pageId}/video_reels", params)
            } catch (e: MetaError) {
                // Older API versions may not know the AI flag; publish without it rather than fail.
                if (job.aiLabel && e.code == 100 && params.remove("is_ai_generated") != null) api.post("${a.pageId}/video_reels", params)
                else throw e
            }
            job = Store.updateJob(jobId) { it.copy(fbStep = 2) }!!
        }

        // Wait for processing so the user sees a real result.
        var link = ""
        var note = if (job.schedule > 0) "Scheduled" else "Published"
        for (i in 0 until 60) {
            val st = api.get(job.fbVideoId, mapOf("fields" to "status,permalink_url", "access_token" to token))
            link = st.optString("permalink_url").let { if (it.startsWith("/")) "https://www.facebook.com$it" else it }
            val s = st.optJSONObject("status")
            val vs = s?.optString("video_status").orEmpty()
            if (vs == "error" || vs == "upload_failed") {
                val err = s?.optJSONObject("processing_phase")?.optJSONObject("error")?.optString("message")
                throw MetaError(err ?: "Facebook couldn't process this video. Re-export as MP4 H.264 and try again.", 0, false)
            }
            if (vs == "ready" || job.schedule > 0 && vs != "processing" && vs != "uploading") break
            if (i == 59) note = "Uploaded. Facebook is still processing it"
            progress(base + share * (0.9f + 0.1f * i / 60f), "Facebook · processing")
            delay(5000)
        }
        Store.updateJob(jobId) { it.copy(fbState = Job.DONE, fbMsg = note, fbLink = link) }
        progress(base + share, "Facebook · done")
    }

    // ---------------- Instagram Reels ----------------

    private suspend fun runInstagram(api: MetaApi, a: Account, file: File, base: Float, share: Float) {
        var job = Store.job(jobId)!!
        val token = a.igToken
        if (!a.instagramReady) throw MetaError("No Instagram account is linked to your Page.", 0, false)

        if (job.igStep == 0) {
            progress(base, "Instagram · starting")
            val params = mutableMapOf(
                "media_type" to "REELS", "upload_type" to "resumable", "caption" to job.caption,
                "share_to_feed" to "true", "access_token" to token,
            )
            val container = api.post("${a.igId}/media", params).getString("id")
            job = Store.updateJob(jobId) { it.copy(igContainer = container) }!!
            uploadWithProgress(file, 0L, base, share * 0.7f, "Instagram · uploading") { cb ->
                api.upload(api.ruploadInstagram(container), token, file, 0L, cb)
            }
            job = Store.updateJob(jobId) { it.copy(igStep = 1) }!!
        }

        if (job.igStep == 1) {
            var i = 0
            while (true) {
                val st = api.get(job.igContainer, mapOf("fields" to "status_code,status", "access_token" to token))
                when (st.optString("status_code")) {
                    "FINISHED" -> break
                    "ERROR", "EXPIRED" -> {
                        Store.updateJob(jobId) { it.copy(igStep = 0, igContainer = "") }
                        throw MetaError("Instagram couldn't process this video: ${st.optString("status", "unknown error")}", 0, false)
                    }
                }
                if (++i > 180) throw MetaError("Instagram is taking too long to process. It will retry.", 0, true)
                progress(base + share * (0.7f + 0.2f * minOf(i / 40f, 1f)), "Instagram · processing")
                delay(5000)
            }
            progress(base + share * 0.95f, "Instagram · publishing")
            val mediaId = api.post("${a.igId}/media_publish", mapOf("creation_id" to job.igContainer, "access_token" to token)).getString("id")
            val link = try { api.get(mediaId, mapOf("fields" to "permalink", "access_token" to token)).optString("permalink") } catch (_: Exception) { "" }
            Store.updateJob(jobId) { it.copy(igStep = 2, igState = Job.DONE, igMsg = "Published", igLink = link) }
        }
        progress(base + share, "Instagram · done")
    }

    // ---------------- helpers ----------------

    private suspend fun uploadWithProgress(
        file: File, offset: Long, base: Float, share: Float, phase: String,
        block: suspend ((Long) -> Unit) -> Unit,
    ) = coroutineScope {
        val sent = AtomicLong(offset)
        val total = file.length().coerceAtLeast(1L)
        val ticker = launch {
            while (isActive) {
                progress(base + share * sent.get() / total, phase)
                delay(700)
            }
        }
        try { block { sent.set(it) } } finally { ticker.cancel() }
        progress(base + share, phase)
    }

    private suspend fun progress(pct: Float, phase: String) {
        setProgress(workDataOf(KEY_ID to jobId, KEY_PCT to pct, KEY_PHASE to phase))
        val p = (pct * 100).toInt()
        if (p != lastPct) {
            lastPct = p
            val nm = applicationContext.getSystemService(NotificationManager::class.java)
            try { nm.notify(notifId, buildNotification(pct, phase)) } catch (_: SecurityException) { }
        }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        applicationContext, 0,
        Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(pct: Float, phase: String) =
        NotificationCompat.Builder(applicationContext, App.CHANNEL_UPLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Posting your Reel")
            .setContentText(phase)
            .setProgress(100, (pct * 100).toInt(), pct <= 0f)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .build()

    private fun foreground(pct: Float, phase: String): ForegroundInfo {
        val n = buildNotification(pct, phase)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ForegroundInfo(notifId, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(notifId, n)
    }

    private fun failAll(job: Job, msg: String): Result {
        val j = Store.updateJob(job.id) {
            it.copy(
                fbState = if (it.fbState in Job.END) it.fbState else Job.FAILED, fbMsg = if (it.fbState in Job.END) it.fbMsg else msg,
                igState = if (it.igState in Job.END) it.igState else Job.FAILED, igMsg = if (it.igState in Job.END) it.igMsg else msg,
            )
        }
        if (j != null) notifyDone(j)
        return Result.failure()
    }

    private fun notifyDone(job: Job) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.cancel(notifId)
        val ok = buildList {
            if (job.fbState == Job.DONE) add("Facebook")
            if (job.igState == Job.DONE) add("Instagram")
        }
        val title = when {
            job.failed && ok.isEmpty() -> "Reel not posted"
            job.failed -> "Posted to ${ok.joinToString(" & ")}, one failed"
            job.schedule > 0 -> "Reel scheduled on Facebook"
            else -> "Reel posted to ${ok.joinToString(" & ")}"
        }
        val text = if (job.failed) listOf(job.fbMsg, job.igMsg).firstOrNull { it.isNotBlank() && it != "Published" } ?: "Open ReelPilot to retry."
        else job.headline
        val n = NotificationCompat.Builder(applicationContext, App.CHANNEL_RESULTS)
            .setSmallIcon(if (job.failed) android.R.drawable.stat_notify_error else android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        try { nm.notify(notifId + 1, n) } catch (_: SecurityException) { }
    }
}
