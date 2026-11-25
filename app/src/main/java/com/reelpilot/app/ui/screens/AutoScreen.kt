package com.reelpilot.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Facebook
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelpilot.app.data.AutoItem
import com.reelpilot.app.data.AutoStore
import com.reelpilot.app.ui.MainViewModel
import com.reelpilot.app.ui.Overlay
import com.reelpilot.app.ui.components.Cell
import com.reelpilot.app.ui.components.Field
import com.reelpilot.app.ui.components.IconTile
import com.reelpilot.app.ui.components.IosSwitch
import com.reelpilot.app.ui.components.LargeTitle
import com.reelpilot.app.ui.components.Pill
import com.reelpilot.app.ui.components.Section
import com.reelpilot.app.ui.components.ToggleCell
import com.reelpilot.app.ui.theme.Pal
import kotlinx.coroutines.delay

@Composable
fun AutoScreen(vm: MainViewModel) {
    val cfg by vm.autoConfig.collectAsState()
    val items by vm.autoItems.collectAsState()
    val account by vm.account.collectAsState()
    val error by vm.autoError.collectAsState()
    val preview by vm.captionPreview.collectAsState()
    val previewBusy by vm.previewBusy.collectAsState()
    val aiKey by vm.aiKey.collectAsState()
    val ytAcct by vm.yt.collectAsState()
    var keyDraft by rememberSaveable { mutableStateOf(aiKey) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }

    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) vm.chooseFolder(uri) }

    val waiting = items.count { it.status == AutoItem.WAITING }
    val next = AutoStore.nextPostTime(now)
    val blockers = buildList {
        if (!account.facebookReady && !ytAcct.ready) add("Connect Facebook or YouTube in Settings")
        if (cfg.folderUri.isBlank()) add("Choose a folder for your clips")
        if (!cfg.confirmedOriginal) add("Confirm the clips are your own gameplay")
        if (!(cfg.fb && account.facebookReady) && !(cfg.ig && account.instagramReady) && !(cfg.yt && ytAcct.ready) && !cfg.ytShare) add("Turn on at least one place to post")
        if (!cfg.instant && cfg.slots.isEmpty()) add("Pick at least one posting time")
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        LargeTitle("Auto Pilot", "Drop clips in a folder. ReelPilot does the rest.")

        // ---- hero status ----
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(if (cfg.enabled) Pal.c.hero else androidx.compose.ui.graphics.SolidColor(Pal.c.card))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val ink = if (cfg.enabled) Color.White else Pal.c.label
            val sub = if (cfg.enabled) Color.White.copy(alpha = 0.88f) else Pal.c.secondary
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.RocketLaunch, null, tint = ink, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Text(if (cfg.enabled) "Auto Pilot is on" else "Auto Pilot is off", color = ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IosSwitch(cfg.enabled) { on -> if (!on || blockers.isEmpty()) vm.updateAuto { it.copy(enabled = on) } }
            }
            Text(
                when {
                    blockers.isNotEmpty() -> "To turn on: ${blockers.first().lowercase()}."
                    !cfg.enabled -> "Ready. Switch it on and ReelPilot will post from \"${cfg.folderName}\"."
                    waiting == 0 -> "Watching \"${cfg.folderName}\". Add a clip and it will be posted automatically."
                    next == null -> "$waiting clip${if (waiting == 1) "" else "s"} waiting."
                    next <= now + 60_000 -> "$waiting waiting · posting the next one now"
                    else -> "$waiting waiting · next post ${fmtDateTime(next / 1000)}"
                },
                color = sub, fontSize = 15.sp, lineHeight = 21.sp,
            )
            Text("Posted today: ${AutoStore.queuedToday()} of ${cfg.perDay}", color = sub, fontSize = 13.sp)
        }

        if (error != null) Text(error!!, color = Pal.c.red, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp))

        // ---- folder ----
        Section("Clips folder", footer = "ReelPilot can only read this one folder (and its sub-folders). It never deletes your videos. Each clip is copied for upload, then the copy is removed.") {
            Cell(
                if (cfg.folderUri.isBlank()) "Choose folder" else cfg.folderName,
                if (cfg.folderUri.isBlank()) "e.g. Movies/ReelPilot or your screen-recordings folder" else "${items.size} clips found · $waiting waiting",
                icon = { IconTile(Icons.Rounded.Folder, Pal.c.blue) }, chevron = true,
                onClick = { folder.launch(null) },
            )
            Cell("Check folder now", icon = { IconTile(Icons.Rounded.Refresh, Pal.c.green) }, onClick = { vm.scanNow() })
            Cell(
                "Post next clip now", "Skips the schedule. Daily limit still applies.", divider = false,
                icon = { IconTile(Icons.Rounded.PlayArrow, Pal.c.orange) },
                onClick = { if (blockers.isEmpty()) vm.postNextNow() },
            )
        }

        Section("Post to") {
            ToggleCell("Facebook Reels", if (account.facebookReady) account.pageName else "Not connected", checked = cfg.fb && account.facebookReady,
                icon = { IconTile(Icons.Rounded.Facebook, Pal.c.facebook) }) { v -> vm.updateAuto { it.copy(fb = v) } }
            ToggleCell("Instagram Reels", if (account.instagramReady) "@${account.igUsername}" else "Not linked", checked = cfg.ig && account.instagramReady,
                icon = { IconTile(Icons.Rounded.CameraAlt, brush = Pal.c.instagram) }) { v -> vm.updateAuto { it.copy(ig = v) } }
            ToggleCell("YouTube Shorts (direct upload)", if (ytAcct.ready) ytAcct.channelTitle.ifBlank { "Connected" } else "Not connected · tap to set up", checked = cfg.yt && ytAcct.ready,
                icon = { IconTile(Icons.Rounded.SmartDisplay, Color(0xFFFF0000)) }) { v -> if (ytAcct.ready) vm.updateAuto { it.copy(yt = v) } else vm.open(Overlay.YouTube) }
            ToggleCell("YouTube Shorts (tap notification)", "Get a notification. One tap posts it publicly with the YouTube app.", checked = cfg.ytShare, divider = false,
                icon = { IconTile(Icons.Rounded.SmartDisplay, Pal.c.orange) }) { v -> vm.updateAuto { it.copy(ytShare = v) } }
        }

        // ---- schedule ----
        Section("Schedule", footer = if (cfg.instant) "Posts each new clip once the gap has passed since the last post." else "Android may run Auto Pilot up to about 15 minutes after the chosen time.") {
            Segmented(listOf("At set times", "As soon as added"), if (cfg.instant) 1 else 0) { i -> vm.updateAuto { it.copy(instant = i == 1) } }
            if (!cfg.instant) {
                Text("Post times", color = Pal.c.secondary, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
                listOf(listOf(8, 10, 12, 13), listOf(15, 17, 18, 19), listOf(20, 21, 22, 23)).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { h ->
                            val on = h in cfg.slots
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) Pal.c.blue else Pal.c.fill)
                                    .clickable { vm.updateAuto { c -> c.copy(slots = if (on) c.slots - h else c.slots + h) } }.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(hourLabel(h), color = if (on) Color.White else Pal.c.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            } else {
                Stepper("Gap between posts", "${cfg.gapMinutes} min", { vm.updateAuto { it.copy(gapMinutes = (it.gapMinutes - 30).coerceAtLeast(30)) } }, { vm.updateAuto { it.copy(gapMinutes = (it.gapMinutes + 30).coerceAtMost(480)) } })
            }
            Stepper("Max posts per day", "${cfg.perDay}", { vm.updateAuto { it.copy(perDay = (it.perDay - 1).coerceAtLeast(1)) } }, { vm.updateAuto { it.copy(perDay = (it.perDay + 1).coerceAtMost(10)) } })
        }

        // ---- captions ----
        Section("Auto captions", footer = "Every clip gets a hook line, one sentence of context, a question for real comments and 3–5 hashtags. With a Claude API key, AI looks at 3 frames of the clip and writes about what actually happens in it.") {
            Field("Game", cfg.game, { v -> vm.updateAuto { it.copy(game = v) } }, "e.g. BGMI, Free Fire, Valorant")
            Field("Your hashtags", cfg.tags, { v -> vm.updateAuto { it.copy(tags = v) } }, "#gaming #gameplay")
            Text("Language", color = Pal.c.secondary, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
            Segmented(listOf("English", "Hinglish", "Hindi"), listOf("English", "Hinglish", "Hindi").indexOf(cfg.language).coerceAtLeast(0)) { i ->
                vm.updateAuto { it.copy(language = listOf("English", "Hinglish", "Hindi")[i]) }
            }
            ToggleCell("AI captions (Claude)", if (aiKey.isBlank()) "Add an API key below" else "Key saved on this phone", checked = cfg.useAi,
                icon = { IconTile(Icons.Rounded.AutoAwesome, Pal.c.purple) }) { v -> vm.updateAuto { it.copy(useAi = v) } }
            if (cfg.useAi) {
                Field("Claude API key", keyDraft, { keyDraft = it; vm.setAiKey(it) }, "sk-ant-…", secret = true)
            }
            Cell(
                "Preview a caption", "Writes one for the next clip so you can see the style", divider = false,
                icon = { IconTile(Icons.Rounded.Translate, Pal.c.green) },
                trailing = { if (previewBusy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp)) },
                onClick = { vm.previewCaption() },
            )
        }
        preview?.let { p ->
            Section(if (p.byAi) "Preview · written by AI" else "Preview · template") {
                Text(p.text, color = Pal.c.label, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(16.dp))
            }
        }

        Section("Originality", footer = "Meta pushes down re-uploads and can stop paying accounts that mostly post other people's clips. Only put your own recordings in this folder.") {
            ToggleCell("Every clip in this folder is my own gameplay with my own voice, face-cam or edits", checked = cfg.confirmedOriginal, divider = false,
                icon = { IconTile(Icons.Rounded.VerifiedUser, Pal.c.green) }) { v -> vm.updateAuto { it.copy(confirmedOriginal = v, enabled = if (!v) false else it.enabled) } }
        }

        // ---- queue ----
        if (items.isNotEmpty()) Section("Clips") {
            val sorted = items.sortedWith(compareBy<AutoItem>({ it.status != AutoItem.WAITING }, { if (it.status == AutoItem.WAITING) it.modified else -maxOf(it.queuedAt, it.modified) }))
            sorted.take(40).forEachIndexed { i, item ->
                val (label, color) = when (item.status) {
                    AutoItem.QUEUED -> "Posted" to Pal.c.green
                    AutoItem.SKIPPED -> "Skipped" to Pal.c.orange
                    else -> "Waiting" to Pal.c.blue
                }
                Cell(
                    item.name, item.message.ifBlank { null }, divider = i < minOf(sorted.size, 40) - 1,
                    trailing = { Pill(label, color) },
                    onClick = {
                        when (item.status) {
                            AutoItem.QUEUED -> if (item.jobId.isNotBlank()) vm.open(Overlay.JobDetail(item.jobId))
                            AutoItem.SKIPPED -> vm.requeueItem(item)
                            else -> vm.skipItem(item)
                        }
                    },
                )
            }
        }
        if (items.isNotEmpty()) Text(
            "Tap a waiting clip to skip it, or a skipped clip to put it back in line.",
            color = Pal.c.secondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.padding(12.dp).fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Pal.c.fill).padding(2.dp)) {
        options.forEachIndexed { i, o ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(7.dp))
                    .background(if (i == selected) (if (Pal.c.dark) Pal.c.elevated else Color.White) else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(o, color = Pal.c.label, fontSize = 14.sp, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

@Composable
private fun Stepper(title: String, value: String, minus: () -> Unit, plus: () -> Unit) {
    Cell(title, value = value, divider = false) {
        Row(Modifier.clip(RoundedCornerShape(8.dp)).background(Pal.c.fill)) {
            Text("−", color = Pal.c.label, fontSize = 20.sp, modifier = Modifier.clickable(onClick = minus).padding(horizontal = 14.dp, vertical = 2.dp))
            Box(Modifier.width(0.5.dp).height(28.dp).background(Pal.c.separator))
            Text("+", color = Pal.c.label, fontSize = 20.sp, modifier = Modifier.clickable(onClick = plus).padding(horizontal = 14.dp, vertical = 2.dp))
        }
    }
}

private fun hourLabel(h: Int) = when {
    h == 0 -> "12 AM"
    h < 12 -> "$h AM"
    h == 12 -> "12 PM"
    else -> "${h - 12} PM"
}

@Suppress("unused")
private val CircleShapeRef = CircleShape
