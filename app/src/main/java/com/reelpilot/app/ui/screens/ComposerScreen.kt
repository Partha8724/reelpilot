package com.reelpilot.app.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Facebook
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelpilot.app.data.CaptionRules
import com.reelpilot.app.data.Level
import com.reelpilot.app.data.Store
import com.reelpilot.app.data.VideoRules
import com.reelpilot.app.ui.MainViewModel
import com.reelpilot.app.ui.components.Cell
import com.reelpilot.app.ui.components.CheckRow
import com.reelpilot.app.ui.components.Field
import com.reelpilot.app.ui.components.IconTile
import com.reelpilot.app.ui.components.PrimaryButton
import com.reelpilot.app.ui.components.Section
import com.reelpilot.app.ui.components.TextButtonIos
import com.reelpilot.app.ui.components.ToggleCell
import com.reelpilot.app.ui.theme.Pal
import java.util.Calendar

@Composable
fun ComposerScreen(vm: MainViewModel, presetFb: Boolean, presetIg: Boolean) {
    val ctx = LocalContext.current
    val account by vm.account.collectAsState()
    val video by vm.video.collectAsState()
    val inspecting by vm.inspecting.collectAsState()
    val posting by vm.posting.collectAsState()
    val postError by vm.postError.collectAsState()

    var fb by rememberSaveable { mutableStateOf(presetFb && account.facebookReady) }
    var ig by rememberSaveable { mutableStateOf(presetIg && account.instagramReady) }
    var own by rememberSaveable { mutableStateOf(false) }
    var added by rememberSaveable { mutableStateOf(false) }
    var notCopied by rememberSaveable { mutableStateOf(false) }
    var music by rememberSaveable { mutableStateOf(false) }
    var ai by rememberSaveable { mutableStateOf(false) }
    var game by rememberSaveable { mutableStateOf("") }
    var hook by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var question by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf(Store.defaultTags) }
    var scheduleOn by rememberSaveable { mutableStateOf(false) }
    var scheduleAt by remember { mutableLongStateOf(0L) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.pickVideo(uri) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))

    val caption = CaptionRules.build(hook, body, question, tags)
    val videoChecks = video?.let { VideoRules.check(it, fb, ig) }.orEmpty()
    val captionChecks = CaptionRules.check(hook, caption, ig)
    val scheduleSec = if (scheduleOn) scheduleAt / 1000 else 0L

    val blockers = buildList {
        if (video == null) add("Choose a video")
        if (videoChecks.any { it.level == Level.BAD }) add("Fix the red video checks")
        if (!(own && added && notCopied && music)) add("Confirm all originality checks")
        if (captionChecks.any { it.level == Level.BAD }) add("Fix the caption")
        if (!fb && !ig) add("Pick Facebook or Instagram")
        if (scheduleOn && (scheduleAt < System.currentTimeMillis() + 11 * 60_000 || scheduleAt > System.currentTimeMillis() + 29L * 86_400_000)) add("Pick a schedule time 11 min to 29 days ahead")
        if (scheduleOn && !fb) add("Scheduling works for Facebook only")
    }

    fun close() { vm.clearComposer(); vm.back() }
    fun submit() {
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        vm.post(caption, fb, ig && !scheduleOn, scheduleSec, ai)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        NavBar("New Reel", left = "Cancel", onLeft = ::close, right = "Post", rightEnabled = blockers.isEmpty() && !posting, onRight = ::submit)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // ---- Video ----
            Section("Video") {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.width(96.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp)).background(Color.Black)
                            .clickable { pick() },
                        contentAlignment = Alignment.Center,
                    ) {
                        val thumb = video?.thumbnail
                        when {
                            inspecting -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                            thumb != null -> Image(thumb.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else -> Icon(Icons.Rounded.AddCircle, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(video?.name ?: "No video yet", color = Pal.c.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        Text(
                            video?.let { "${it.width}×${it.height} · ${"%.1f".format(it.durationSec)}s" } ?: "Pick a vertical gameplay clip, 5–90 seconds.",
                            color = Pal.c.secondary, fontSize = 14.sp,
                        )
                        TextButtonIos(if (video == null) "Choose from gallery" else "Change video") { pick() }
                    }
                }
            }
            if (videoChecks.isNotEmpty()) Section("Meta video rules") {
                videoChecks.forEachIndexed { i, c -> CheckRow(c, i < videoChecks.lastIndex) }
            }

            // ---- Originality ----
            Section(
                "Originality",
                footer = "Since March 2026 Meta gives more reach to original content and can demonetise accounts that mostly re-upload. Your own gameplay plus your own voice or edits counts as original.",
            ) {
                ToggleCell("I recorded this gameplay", checked = own) { own = it }
                ToggleCell("I added my voice, face-cam or edits", checked = added) { added = it }
                ToggleCell("Not someone else's clip or stream", "Borders, captions or speed changes don't make a copy original.", checked = notCopied) { notCopied = it }
                ToggleCell("Music is game audio, mine or licensed", checked = music) { music = it }
                ToggleCell("Contains realistic AI footage or voice", "Adds Meta's AI label to the Facebook Reel.", checked = ai, divider = false) { ai = it }
            }

            // ---- Caption ----
            Section("Caption") {
                Field("Game", game, { game = it }, "e.g. BGMI, Free Fire, Valorant")
                Field("Hook (first line)", hook, { hook = it }, "1v4 clutch with 5 HP left")
                Field("Details", body, { body = it }, "What happened, your tip or reaction", singleLine = false)
                Field("Question for comments", question, { question = it }, "Would you have pushed or waited?")
                Field("Hashtags", tags, { tags = it }, "#gaming #gameplay", divider = true)
                Cell("Suggest hashtags", icon = { IconTile(Icons.Rounded.Tag, Pal.c.purple) }, divider = false, onClick = { tags = CaptionRules.suggestTags(game, Store.defaultTags) })
            }
            Section("Caption check") {
                captionChecks.forEachIndexed { i, c -> CheckRow(c, i < captionChecks.lastIndex) }
            }
            if (caption.isNotBlank()) Section("Preview") {
                Text(caption, color = Pal.c.label, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(16.dp))
            }

            // ---- Destinations ----
            Section("Post to", footer = if (!account.instagramReady) "Link a professional Instagram account to your Page to post there too." else null) {
                ToggleCell(
                    "Facebook Reels", account.pageName.ifBlank { null }, checked = fb,
                    icon = { IconTile(Icons.Rounded.Facebook, Pal.c.facebook) },
                ) { if (account.facebookReady) fb = it }
                ToggleCell(
                    "Instagram Reels", if (account.instagramReady) "@${account.igUsername}" else "Not linked", checked = ig, divider = false,
                    icon = { IconTile(Icons.Rounded.CameraAlt, brush = Pal.c.instagram) },
                ) { if (account.instagramReady) ig = it }
            }
            Section("When", footer = "Meta's API can schedule Facebook Reels 10 minutes to 29 days ahead. Instagram can only be posted right away.") {
                ToggleCell("Schedule", checked = scheduleOn, icon = { IconTile(Icons.Rounded.Schedule, Pal.c.orange) }) {
                    scheduleOn = it
                    if (it && scheduleAt == 0L) scheduleAt = defaultSlot()
                }
                if (scheduleOn) Cell(
                    "Date and time", value = fmtDateTime(scheduleAt / 1000), divider = false, chevron = true,
                    onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = scheduleAt }
                        DatePickerDialog(ctx, { _, y, m, d ->
                            TimePickerDialog(ctx, { _, h, min ->
                                cal.set(y, m, d, h, min, 0)
                                scheduleAt = cal.timeInMillis
                            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                    },
                ) else Cell("Post now", value = "Default", divider = false)
            }
            if (ai) Section {
                Cell("AI label on", "Honest labels protect your account from penalties.", icon = { IconTile(Icons.Rounded.AutoAwesome, Pal.c.purple) }, divider = false)
            }
            Spacer(Modifier.height(12.dp))
        }

        Column(Modifier.background(Pal.c.bg).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (postError != null) Text(postError!!, color = Pal.c.red, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
            Text(
                if (blockers.isEmpty()) "Ready. Uploads keep running even if you close the app." else blockers.first(),
                color = if (blockers.isEmpty()) Pal.c.green else Pal.c.secondary, fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            PrimaryButton(
                when {
                    posting -> "Preparing…"
                    scheduleOn -> "Schedule Reel"
                    fb && ig -> "Post to Facebook & Instagram"
                    ig -> "Post to Instagram"
                    else -> "Post to Facebook"
                },
                enabled = blockers.isEmpty(), loading = posting, onClick = ::submit,
            )
        }
    }
}

private fun defaultSlot(): Long {
    // Next 8 PM, a common peak time for gaming audiences in India.
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 20); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0)
    if (c.timeInMillis < System.currentTimeMillis() + 15 * 60_000) c.add(Calendar.DAY_OF_MONTH, 1)
    return c.timeInMillis
}
