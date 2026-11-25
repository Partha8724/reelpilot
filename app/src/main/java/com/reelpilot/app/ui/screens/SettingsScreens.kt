package com.reelpilot.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Facebook
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelpilot.app.BuildConfigInfo
import com.reelpilot.app.data.Account
import com.reelpilot.app.data.Job
import com.reelpilot.app.data.Store
import com.reelpilot.app.ui.MainViewModel
import com.reelpilot.app.ui.Overlay
import com.reelpilot.app.ui.components.Cell
import com.reelpilot.app.ui.components.Field
import com.reelpilot.app.ui.components.IconTile
import com.reelpilot.app.ui.components.LargeTitle
import com.reelpilot.app.ui.components.PrimaryButton
import com.reelpilot.app.ui.components.Section
import com.reelpilot.app.ui.components.ToggleCell
import com.reelpilot.app.ui.theme.Pal

// =============================== Settings ===============================

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val account by vm.account.collectAsState()
    var confirmDisconnect by remember { mutableStateOf(false) }
    var tags by rememberSaveable { mutableStateOf(Store.defaultTags) }
    var defFb by remember { mutableStateOf(Store.defaultFb) }
    var defIg by remember { mutableStateOf(Store.defaultIg) }
    var defYt by remember { mutableStateOf(Store.defaultYt) }
    val ytAcct by vm.yt.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        LargeTitle("Settings")

        Section("Accounts", footer = if (account.facebookReady) (if (account.permanent) "Your login is long-lived, so you won't need to reconnect often." else "Tip: add your App ID and App Secret when connecting so your login doesn't expire after an hour.") else null) {
            if (account.facebookReady) {
                Cell("Facebook Page", account.pageName, icon = { IconTile(Icons.Rounded.Facebook, Pal.c.facebook) })
                Cell("Instagram", if (account.instagramReady) "@${account.igUsername}" else "Not linked", icon = { IconTile(Icons.Rounded.CameraAlt, brush = Pal.c.instagram) })
                Cell("Reconnect or switch Page", icon = { IconTile(Icons.Rounded.Refresh, Pal.c.blue) }, chevron = true, onClick = { vm.open(Overlay.Connect) })
                Cell(
                    if (confirmDisconnect) "Tap again to disconnect" else "Disconnect", titleColor = Pal.c.red,
                    icon = { IconTile(Icons.Rounded.Delete, Pal.c.red) }, divider = false,
                    onClick = { if (confirmDisconnect) { vm.disconnect(); confirmDisconnect = false } else confirmDisconnect = true },
                )
            } else {
                Cell("Connect Facebook & Instagram", icon = { IconTile(Icons.Rounded.Link, Pal.c.blue) }, chevron = true, divider = false, onClick = { vm.open(Overlay.Connect) })
            }
        }

        Section("YouTube") {
            Cell(
                if (ytAcct.ready) ytAcct.channelTitle.ifBlank { "YouTube connected" } else "Connect YouTube",
                if (ytAcct.ready) "Shorts upload directly from ReelPilot" else "Post the same Reel as a YouTube Short",
                icon = { IconTile(Icons.Rounded.SmartDisplay, androidx.compose.ui.graphics.Color(0xFFFF0000)) },
                chevron = true, divider = false, onClick = { vm.open(Overlay.YouTube) },
            )
        }

        Section("New Reel defaults") {
            ToggleCell("Post to Facebook", checked = defFb) { defFb = it; Store.defaultFb = it }
            ToggleCell("Post to Instagram", checked = defIg) { defIg = it; Store.defaultIg = it }
            ToggleCell("Post to YouTube", checked = defYt) { defYt = it; Store.defaultYt = it }
            Field("Default hashtags", tags, { tags = it; Store.defaultTags = it }, "#gaming #gameplay", divider = false)
        }

        Section("Help") {
            Cell("Setup guide", "Get your token in about 15 minutes", icon = { IconTile(Icons.Rounded.MenuBook, Pal.c.green) }, chevron = true, onClick = { vm.open(Overlay.Guide) })
            Cell("Meta's original content rules", icon = { IconTile(Icons.Rounded.Policy, Pal.c.orange) }, chevron = true, onClick = { openUrl(ctx, "https://creators.facebook.com/blog/supporting-original-content-on-facebook") })
            Cell("How Facebook Reels are ranked", icon = { IconTile(Icons.Rounded.Tag, Pal.c.purple) }, chevron = true, divider = false, onClick = { openUrl(ctx, "https://transparency.meta.com/features/explaining-ranking/fb-reels/") })
        }

        Section("About", footer = "ReelPilot uses Meta's official Graph API. Your tokens are encrypted and stay on this phone. Nothing is sent anywhere except Meta.") {
            Cell("Version", value = BuildConfigInfo.version(ctx), icon = { IconTile(Icons.Rounded.Info, Pal.c.secondary) })
            Cell("API version", value = account.apiVersion, icon = { IconTile(Icons.Rounded.Key, Pal.c.secondary) }, divider = false)
        }
        Spacer(Modifier.height(32.dp))
    }
}

// =============================== Connect ===============================

@Composable
fun ConnectScreen(vm: MainViewModel) {
    val busy by vm.connectBusy.collectAsState()
    val error by vm.connectError.collectAsState()
    val pages by vm.pageOptions.collectAsState()
    val current by vm.account.collectAsState()
    var token by rememberSaveable { mutableStateOf("") }
    var appId by rememberSaveable { mutableStateOf("") }
    var secret by rememberSaveable { mutableStateOf("") }
    var version by rememberSaveable { mutableStateOf(current.apiVersion) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var pageId by rememberSaveable { mutableStateOf("") }
    var pageToken by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize().imePadding()) {
        NavBar("Connect", left = "Back", onLeft = { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                "Paste the access token from Meta's Graph API Explorer. Need help? Open the setup guide.",
                color = Pal.c.secondary, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
            )
            Section {
                Cell("Open setup guide", icon = { IconTile(Icons.Rounded.MenuBook, Pal.c.green) }, chevron = true, divider = false, onClick = { vm.open(Overlay.Guide) })
            }

            if (pages.isNotEmpty()) {
                Section("Choose your Page") {
                    pages.forEachIndexed { i, p ->
                        Cell(
                            p.name, if (p.igUsername.isNotBlank()) "Instagram: @${p.igUsername}" else "No Instagram linked",
                            icon = { Avatar(p.picture, 40, Pal.c.facebook) }, chevron = true, divider = i < pages.lastIndex,
                            onClick = { vm.choosePage(p, version) },
                        )
                    }
                }
            }

            Section("Access token", footer = "Needs: pages_show_list, pages_read_engagement, pages_manage_posts, instagram_basic, instagram_content_publish, business_management.") {
                Field("User access token", token, { token = it }, "EAAG…", secret = true, divider = false)
            }
            Section("Keep me logged in (recommended)", footer = "From your app's Settings → Basic page on developers.facebook.com. Used once to turn your 1-hour token into a long-lived one. It's stored only on this phone.") {
                Field("App ID", appId, { appId = it }, "1234567890", number = true)
                Field("App Secret", secret, { secret = it }, "••••••••", secret = true, divider = false)
            }
            if (error != null) Text(error!!, color = Pal.c.red, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp))
            PrimaryButton("Find my Pages", loading = busy, modifier = Modifier.padding(16.dp)) { vm.findPages(token, appId, secret, version) }

            Section {
                ToggleCell("Advanced", "Paste a Page ID and Page token instead, or change the API version", checked = advanced, divider = false) { advanced = it }
            }
            if (advanced) {
                Section("Page token") {
                    Field("Page ID", pageId, { pageId = it }, "1029384756", number = true)
                    Field("Page access token", pageToken, { pageToken = it }, "EAAG…", secret = true)
                    Field("Graph API version", version, { version = it }, Account.DEFAULT_API_VERSION, divider = false)
                }
                PrimaryButton("Connect with Page token", enabled = pageId.isNotBlank() && pageToken.isNotBlank(), loading = busy, modifier = Modifier.padding(16.dp)) {
                    vm.connectManual(pageId, pageToken, version)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// =============================== Guide ===============================

@Composable
fun GuideScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val steps = listOf(
        "Make Instagram professional" to "In Instagram: Settings → Account type and tools → Switch to professional account. Then link it to your Facebook Page (Page settings → Linked accounts).",
        "Create a Meta app" to "Open developers.facebook.com/apps → Create app → choose Business (or 'Other' then Business). You don't need App Review to post to Pages you manage while you're an admin of the app.",
        "Add products" to "In the app dashboard add 'Facebook Login for Business' and 'Instagram' (Instagram API with Facebook Login).",
        "Generate a token" to "Open Graph API Explorer, pick your app, choose 'Get User Access Token' and tick: pages_show_list, pages_read_engagement, pages_manage_posts, instagram_basic, instagram_content_publish, business_management. Click Generate and allow everything.",
        "Copy into ReelPilot" to "Copy the token. In ReelPilot → Connect, paste it. Add your App ID and App Secret (App settings → Basic) so the login lasts. Tap Find my Pages and choose your Page.",
        "Post!" to "Tap New Reel, pick your gameplay, tick the checks and post. Uploads continue in the background with a notification.",
    )
    Column(Modifier.fillMaxSize()) {
        NavBar("Setup guide", left = "Back", onLeft = { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Section("One-time setup, about 15 minutes") {
                steps.forEachIndexed { i, (t, d) ->
                    Cell("${i + 1}. $t", d, divider = i < steps.lastIndex)
                }
            }
            Section("Links") {
                Cell("Meta for Developers: My apps", chevron = true, onClick = { openUrl(ctx, "https://developers.facebook.com/apps") })
                Cell("Graph API Explorer", chevron = true, onClick = { openUrl(ctx, "https://developers.facebook.com/tools/explorer") })
                Cell("Access Token Debugger", chevron = true, divider = false, onClick = { openUrl(ctx, "https://developers.facebook.com/tools/debug/accesstoken") })
            }
            Section("Limits to know", footer = "These come from Meta's API, not ReelPilot.") {
                Cell("Facebook Reels", "30 API posts per Page every 24 hours · 3–90 seconds · 9:16")
                Cell("Instagram Reels", "100 API posts every 24 hours · 5–90 seconds for the Reels tab")
                Cell("Scheduling", "Facebook only, 10 minutes to 29 days ahead", divider = false)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// =============================== Job detail ===============================

@Composable
fun JobDetailScreen(vm: MainViewModel, id: String) {
    val ctx = LocalContext.current
    val jobs by vm.jobs.collectAsState()
    val live by vm.live.collectAsState()
    val job = jobs.firstOrNull { it.id == id }
    Column(Modifier.fillMaxSize()) {
        NavBar("Upload", left = "Back", onLeft = { vm.back() })
        if (job == null) Text("This upload was removed.", color = Pal.c.secondary, modifier = Modifier.padding(32.dp))
        else Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            JobCard(job, live[job.id]) {}
            Section("Status") {
                if (job.fb) Cell("Facebook", job.fbMsg.ifBlank { job.fbState }, icon = { StateIcon(job.fbState) }, chevron = job.fbLink.isNotBlank(), onClick = if (job.fbLink.isNotBlank()) ({ openUrl(ctx, job.fbLink) }) else null)
                if (job.ig) Cell("Instagram", if (job.igState == Job.SKIPPED) "Skipped: Instagram can't be scheduled" else job.igMsg.ifBlank { job.igState }, icon = { StateIcon(job.igState) }, chevron = job.igLink.isNotBlank(), onClick = if (job.igLink.isNotBlank()) ({ openUrl(ctx, job.igLink) }) else null)
                if (job.yt) Cell("YouTube", job.ytMsg.ifBlank { job.ytState }, icon = { StateIcon(job.ytState) }, chevron = job.ytLink.isNotBlank(), onClick = if (job.ytLink.isNotBlank()) ({ openUrl(ctx, job.ytLink) }) else null)
                Cell("Created", value = ago(job.created), divider = false)
            }
            Section("Caption") { Text(job.caption.ifBlank { "No caption" }, color = Pal.c.label, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(16.dp)) }
            if (job.failed) PrimaryButton("Retry failed uploads", modifier = Modifier.padding(16.dp)) { vm.retry(job) }
            if (!job.finished) PrimaryButton("Cancel upload", color = Pal.c.orange, modifier = Modifier.padding(horizontal = 16.dp)) { vm.cancelJob(job) }
            PrimaryButton("Delete from list", color = Pal.c.red, modifier = Modifier.padding(16.dp)) { vm.deleteJob(job) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
