package com.reelpilot.app.work

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.reelpilot.app.App
import com.reelpilot.app.MainActivity
import com.reelpilot.app.data.AutoItem
import com.reelpilot.app.data.AutoStore
import com.reelpilot.app.data.CaptionMaker
import com.reelpilot.app.data.Job
import com.reelpilot.app.data.Level
import com.reelpilot.app.data.Store
import com.reelpilot.app.data.VideoRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Auto Pilot. Every 15 minutes it looks in the chosen folder for new videos and,
 * when a time slot is due, checks the next clip, writes its caption and hands it to [UploadWorker].
 */
class AutoWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        private const val PERIODIC = "auto-pilot"
        private const val ONCE = "auto-pilot-now"
        private const val KEY_FORCE = "force"
        private val VIDEO_EXT = setOf("mp4", "mov", "m4v", "3gp", "webm", "mkv")

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<AutoWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun stop(ctx: Context) = WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC)

        /** Scan now. With [force] it also posts the next waiting clip right away (daily limits still apply). */
        fun runNow(ctx: Context, force: Boolean) {
            val req = OneTimeWorkRequestBuilder<AutoWorker>().setInputData(workDataOf(KEY_FORCE to force)).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, req)
        }

        /** Lists videos in the folder (and one level of sub-folders). */
        fun scan(ctx: Context, folderUri: String): List<AutoItem> {
            val root = DocumentFile.fromTreeUri(ctx, Uri.parse(folderUri)) ?: return emptyList()
            val out = mutableListOf<AutoItem>()
            fun add(f: DocumentFile) {
                val name = f.name ?: return
                val isVideo = f.type?.startsWith("video/") == true || name.substringAfterLast('.', "").lowercase() in VIDEO_EXT
                if (f.isFile && isVideo && !name.startsWith(".")) out += AutoItem(f.uri.toString(), name, f.lastModified())
            }
            root.listFiles().forEach { f ->
                if (f.isDirectory) f.listFiles().forEach(::add) else add(f)
            }
            return out
        }
    }

    override suspend fun doWork(): Result {
        val cfg = AutoStore.config.value
        if (cfg.folderUri.isBlank()) return Result.success()
        val force = inputData.getBoolean(KEY_FORCE, false)

        val found = try { withContext(Dispatchers.IO) { scan(applicationContext, cfg.folderUri) } }
        catch (e: Exception) { return Result.success() } // folder removed or permission lost; the Auto tab shows this
        AutoStore.addNew(found)

        if (!cfg.enabled && !force) return Result.success()
        val acct = Store.account.value
        val toFb = cfg.fb && acct.facebookReady
        val toIg = cfg.ig && acct.instagramReady
        if (!toFb && !toIg || !cfg.confirmedOriginal) return Result.success()

        // Is a post due?
        if (AutoStore.queuedToday() >= cfg.perDay) return Result.success()
        if (toFb && Store.postsLast24h(true) >= 30 || toIg && Store.postsLast24h(false) >= 100) return Result.success()
        if (!force) {
            val next = AutoStore.nextPostTime() ?: return Result.success()
            if (next > System.currentTimeMillis() + 60_000) return Result.success()
        }

        // Take the oldest waiting clip that passes Meta's checks.
        for (item in AutoStore.waiting().take(5)) {
            val uri = Uri.parse(item.docId)
            val info = try { withContext(Dispatchers.IO) { VideoRules.inspect(applicationContext, uri) } } catch (e: Exception) { null }
            if (info == null) { AutoStore.upsert(item.copy(status = AutoItem.SKIPPED, message = "Can't read this file")); continue }
            val bad = VideoRules.check(info, toFb, toIg).firstOrNull { it.level == Level.BAD }
            if (bad != null) { AutoStore.upsert(item.copy(status = AutoItem.SKIPPED, message = bad.title)); notify("Skipped ${item.name}", bad.title + ". " + bad.detail); continue }

            val caption = CaptionMaker.make(applicationContext, uri, item.docId, info.durationSec, cfg)
            val id = UUID.randomUUID().toString().take(12)
            val out = File(File(applicationContext.filesDir, "uploads").apply { mkdirs() }, "$id.mp4")
            try {
                withContext(Dispatchers.IO) {
                    applicationContext.contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it, 1 shl 20) } }
                }
            } catch (e: Exception) {
                out.delete()
                AutoStore.upsert(item.copy(status = AutoItem.SKIPPED, message = "Couldn't copy the video"))
                continue
            }
            Store.saveJob(
                Job(
                    id = id, created = System.currentTimeMillis(), fileName = info.name, filePath = out.absolutePath,
                    caption = caption.text, fb = toFb, ig = toIg,
                    fbMsg = if (toFb) "Waiting…" else "", igMsg = if (toIg) "Waiting…" else "",
                )
            )
            UploadWorker.enqueue(applicationContext, id)
            AutoStore.upsert(item.copy(status = AutoItem.QUEUED, jobId = id, queuedAt = System.currentTimeMillis(),
                message = if (caption.byAi) "AI caption: ${caption.hook}" else caption.hook))
            notify("Auto Pilot is posting a Reel", caption.hook)
            break
        }
        return Result.success()
    }

    private fun notify(title: String, text: String) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        val pi = PendingIntent.getActivity(applicationContext, 1, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(applicationContext, App.CHANNEL_RESULTS)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true).setContentIntent(pi).build()
        try { nm.notify(title.hashCode(), n) } catch (_: SecurityException) { }
    }
}
