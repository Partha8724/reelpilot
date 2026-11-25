package com.reelpilot.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.reelpilot.app.data.Account
import com.reelpilot.app.data.FbPage
import com.reelpilot.app.data.FbVideo
import com.reelpilot.app.data.IgMedia
import com.reelpilot.app.data.IgProfile
import com.reelpilot.app.data.IgQuota
import com.reelpilot.app.data.Job
import com.reelpilot.app.data.MetaApi
import com.reelpilot.app.data.PageOption
import com.reelpilot.app.data.Store
import com.reelpilot.app.data.VideoInfo
import com.reelpilot.app.data.VideoRules
import com.reelpilot.app.work.UploadWorker
import com.reelpilot.app.work.AutoWorker
import com.reelpilot.app.data.AutoConfig
import com.reelpilot.app.data.AutoItem
import com.reelpilot.app.data.AutoStore
import com.reelpilot.app.data.Caption
import com.reelpilot.app.data.CaptionMaker
import android.content.Intent
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class Tab { Home, Auto, Facebook, Instagram, Settings }

sealed interface Overlay {
    data class Composer(val fb: Boolean, val ig: Boolean) : Overlay
    data object Connect : Overlay
    data object Guide : Overlay
    data class JobDetail(val id: String) : Overlay
}

data class Live(val pct: Float, val phase: String)

data class Loadable<T>(val data: T? = null, val loading: Boolean = false, val error: String? = null)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val account: StateFlow<Account> = Store.account
    val jobs: StateFlow<List<Job>> = Store.jobs

    val tab = MutableStateFlow(Tab.Home)
    val overlays = MutableStateFlow<List<Overlay>>(emptyList())

    /** Live progress of running uploads, by job id. */
    val live: StateFlow<Map<String, Live>> = WorkManager.getInstance(app)
        .getWorkInfosByTagFlow(UploadWorker.TAG)
        .map { list ->
            list.filter { it.state == WorkInfo.State.RUNNING }.mapNotNull { w ->
                val id = w.progress.getString(UploadWorker.KEY_ID) ?: return@mapNotNull null
                id to Live(w.progress.getFloat(UploadWorker.KEY_PCT, 0f), w.progress.getString(UploadWorker.KEY_PHASE).orEmpty())
            }.toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun open(o: Overlay) { overlays.value = overlays.value + o }
    fun back(): Boolean {
        if (overlays.value.isEmpty()) return false
        overlays.value = overlays.value.dropLast(1)
        return true
    }
    fun closeAll() { overlays.value = emptyList() }

    private fun api() = MetaApi(account.value.apiVersion)

    // ---------------- Facebook / Instagram dashboards ----------------

    val fbPage = MutableStateFlow(Loadable<FbPage>())
    val fbVideos = MutableStateFlow(Loadable<List<FbVideo>>())
    val igProfile = MutableStateFlow(Loadable<IgProfile>())
    val igMedia = MutableStateFlow(Loadable<List<IgMedia>>())
    val igQuota = MutableStateFlow<IgQuota?>(null)

    fun loadFacebook() {
        val a = account.value
        if (!a.facebookReady) return
        viewModelScope.launch {
            fbPage.value = fbPage.value.copy(loading = true, error = null)
            fbVideos.value = fbVideos.value.copy(loading = true, error = null)
            fbPage.value = try { Loadable(api().facebookPage(a)) } catch (e: Exception) { Loadable(fbPage.value.data, error = e.message) }
            fbVideos.value = try { Loadable(api().facebookVideos(a)) } catch (e: Exception) { Loadable(fbVideos.value.data, error = e.message) }
        }
    }

    fun loadInstagram() {
        val a = account.value
        if (!a.instagramReady) return
        viewModelScope.launch {
            igProfile.value = igProfile.value.copy(loading = true, error = null)
            igMedia.value = igMedia.value.copy(loading = true, error = null)
            igProfile.value = try { Loadable(api().instagramProfile(a)) } catch (e: Exception) { Loadable(igProfile.value.data, error = e.message) }
            igMedia.value = try { Loadable(api().instagramMedia(a)) } catch (e: Exception) { Loadable(igMedia.value.data, error = e.message) }
            igQuota.value = try { api().instagramQuota(a) } catch (e: Exception) { null }
        }
    }

    // ---------------- Connect ----------------

    val connectBusy = MutableStateFlow(false)
    val connectError = MutableStateFlow<String?>(null)
    val pageOptions = MutableStateFlow<List<PageOption>>(emptyList())
    private var pendingUserToken = ""
    private var pendingPermanent = false

    fun findPages(userToken: String, appId: String, appSecret: String, version: String) {
        val token = userToken.trim()
        if (token.isEmpty()) { connectError.value = "Paste your access token first."; return }
        viewModelScope.launch {
            connectBusy.value = true; connectError.value = null; pageOptions.value = emptyList()
            try {
                val api = MetaApi(version.ifBlank { Account.DEFAULT_API_VERSION })
                var t = token
                pendingPermanent = false
                if (appId.isNotBlank() && appSecret.isNotBlank()) {
                    t = api.exchangeForLongLived(appId.trim(), appSecret.trim(), token)
                    pendingPermanent = true
                }
                pendingUserToken = t
                val pages = api.pages(t)
                if (pages.isEmpty()) connectError.value = "No Pages found. Make sure you're an admin of a Facebook Page and allowed pages_show_list."
                pageOptions.value = pages
            } catch (e: Exception) {
                connectError.value = e.message ?: "Couldn't reach Meta. Check your internet."
            } finally { connectBusy.value = false }
        }
    }

    fun choosePage(p: PageOption, version: String) {
        Store.saveAccount(
            Account(
                userToken = pendingUserToken, pageId = p.id, pageName = p.name, pagePicture = p.picture,
                pageToken = p.token, igId = p.igId, igUsername = p.igUsername, igPicture = p.igPicture,
                apiVersion = version.ifBlank { Account.DEFAULT_API_VERSION }, permanent = pendingPermanent,
            )
        )
        pageOptions.value = emptyList()
        back()
        loadFacebook(); loadInstagram()
    }

    fun connectManual(pageId: String, pageToken: String, version: String) {
        viewModelScope.launch {
            connectBusy.value = true; connectError.value = null
            try {
                val v = version.ifBlank { Account.DEFAULT_API_VERSION }
                val p = MetaApi(v).pageWithToken(pageId.trim(), pageToken.trim())
                pendingUserToken = ""
                pendingPermanent = false
                choosePage(p, v)
            } catch (e: Exception) {
                connectError.value = e.message ?: "Couldn't connect."
            } finally { connectBusy.value = false }
        }
    }

    fun disconnect() {
        Store.disconnect()
        fbPage.value = Loadable(); fbVideos.value = Loadable(); igProfile.value = Loadable(); igMedia.value = Loadable()
    }

    // ---------------- Composer ----------------

    val video = MutableStateFlow<VideoInfo?>(null)
    val inspecting = MutableStateFlow(false)
    val posting = MutableStateFlow(false)
    val postError = MutableStateFlow<String?>(null)

    fun pickVideo(uri: Uri) {
        viewModelScope.launch {
            inspecting.value = true
            video.value = withContext(Dispatchers.IO) { VideoRules.inspect(getApplication<Application>(), uri) }
            inspecting.value = false
        }
    }

    fun clearComposer() { video.value = null; postError.value = null }

    fun post(caption: String, fb: Boolean, ig: Boolean, schedule: Long, ai: Boolean) {
        val v = video.value ?: return
        viewModelScope.launch {
            posting.value = true; postError.value = null
            try {
                val id = UUID.randomUUID().toString().take(12)
                val dir = File(getApplication<Application>().filesDir, "uploads").apply { mkdirs() }
                val out = File(dir, "$id.mp4")
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(v.uri)!!.use { input ->
                        out.outputStream().use { input.copyTo(it, 1 shl 20) }
                    }
                }
                val job = Job(
                    id = id, created = System.currentTimeMillis(), fileName = v.name, filePath = out.absolutePath,
                    caption = caption, fb = fb, ig = ig, schedule = schedule, aiLabel = ai,
                    fbMsg = if (fb) "Waiting…" else "", igMsg = if (ig && schedule == 0L) "Waiting…" else "",
                )
                Store.saveJob(job)
                UploadWorker.enqueue(getApplication<Application>(), id)
                video.value = null
                closeAll()
                tab.value = Tab.Home
            } catch (e: Exception) {
                postError.value = "Couldn't prepare the video: ${e.message}"
            } finally { posting.value = false }
        }
    }

    fun retry(job: Job) {
        if (!File(job.filePath).exists()) {
            Store.updateJob(job.id) { it.copy(fbMsg = if (it.fbState == Job.FAILED) "Video file missing. Post it again." else it.fbMsg, igMsg = if (it.igState == Job.FAILED) "Video file missing. Post it again." else it.igMsg) }
            return
        }
        Store.updateJob(job.id) {
            it.copy(
                fbState = if (it.fbState == Job.FAILED) Job.PENDING else it.fbState,
                igState = if (it.igState == Job.FAILED) Job.PENDING else it.igState,
                fbMsg = if (it.fbState == Job.FAILED) "Waiting…" else it.fbMsg,
                igMsg = if (it.igState == Job.FAILED) "Waiting…" else it.igMsg,
            )
        }
        UploadWorker.enqueue(getApplication<Application>(), job.id)
    }

    fun cancelJob(job: Job) {
        UploadWorker.cancel(getApplication<Application>(), job.id)
        Store.updateJob(job.id) {
            it.copy(
                fbState = if (it.fbState in Job.END) it.fbState else Job.FAILED,
                igState = if (it.igState in Job.END) it.igState else Job.FAILED,
                fbMsg = if (it.fbState in Job.END) it.fbMsg else "Cancelled",
                igMsg = if (it.igState in Job.END) it.igMsg else "Cancelled",
            )
        }
    }

    fun deleteJob(job: Job) {
        UploadWorker.cancel(getApplication<Application>(), job.id)
        File(job.filePath).delete()
        Store.deleteJob(job.id)
        back()
    }

    // ---------------- Auto Pilot ----------------

    val autoConfig: StateFlow<AutoConfig> = AutoStore.config
    val autoItems: StateFlow<List<AutoItem>> = AutoStore.items
    val autoError = MutableStateFlow<String?>(null)
    val captionPreview = MutableStateFlow<Caption?>(null)
    val previewBusy = MutableStateFlow(false)
    val aiKey = MutableStateFlow(Store.aiKey)

    fun updateAuto(change: (AutoConfig) -> AutoConfig) {
        val next = change(AutoStore.config.value)
        AutoStore.save(next)
        val app = getApplication<Application>()
        if (next.enabled) AutoWorker.schedule(app) else AutoWorker.stop(app)
    }

    fun setAiKey(k: String) { Store.aiKey = k; aiKey.value = k.trim() }

    fun chooseFolder(uri: Uri) {
        val app = getApplication<Application>()
        try {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            autoError.value = "Android didn't allow access to that folder. Pick another one."
            return
        }
        val old = AutoStore.config.value.folderUri
        if (old.isNotBlank() && old != uri.toString()) {
            try { app.contentResolver.releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
        }
        val name = DocumentFile.fromTreeUri(app, uri)?.name ?: "Selected folder"
        autoError.value = null
        // Videos already in the folder count as new: they'll be posted one per slot.
        if (old != uri.toString()) AutoStore.clearItems()
        updateAuto { it.copy(folderUri = uri.toString(), folderName = name) }
        AutoWorker.runNow(app, force = false)
    }

    fun scanNow() = AutoWorker.runNow(getApplication<Application>(), force = false)
    fun postNextNow() = AutoWorker.runNow(getApplication<Application>(), force = true)

    fun skipItem(item: AutoItem) = AutoStore.upsert(item.copy(status = AutoItem.SKIPPED, message = "Skipped by you"))
    fun requeueItem(item: AutoItem) = AutoStore.upsert(item.copy(status = AutoItem.WAITING, message = ""))

    fun previewCaption() {
        val item = AutoStore.waiting().firstOrNull() ?: AutoStore.items.value.firstOrNull()
        if (item == null) { autoError.value = "Add a video to your folder first."; return }
        viewModelScope.launch {
            previewBusy.value = true
            try {
                val uri = Uri.parse(item.docId)
                val info = withContext(Dispatchers.IO) { VideoRules.inspect(getApplication<Application>(), uri) }
                captionPreview.value = CaptionMaker.make(getApplication<Application>(), uri, item.docId + System.currentTimeMillis(), info.durationSec, AutoStore.config.value)
            } catch (e: Exception) {
                autoError.value = "Couldn't read ${item.name}."
            } finally { previewBusy.value = false }
        }
    }
}
