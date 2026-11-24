package com.reelpilot.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelpilot.app.data.Job
import com.reelpilot.app.data.Store
import com.reelpilot.app.ui.Live
import com.reelpilot.app.ui.MainViewModel
import com.reelpilot.app.ui.Overlay
import com.reelpilot.app.ui.components.Cell
import com.reelpilot.app.ui.components.IconTile
import com.reelpilot.app.ui.components.LargeTitle
import com.reelpilot.app.ui.components.ProgressBar
import com.reelpilot.app.ui.components.Section
import com.reelpilot.app.ui.theme.Pal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(vm: MainViewModel) {
    val account by vm.account.collectAsState()
    val jobs by vm.jobs.collectAsState()
    val live by vm.live.collectAsState()
    val today = SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date())

    LazyColumn(Modifier.fillMaxWidth()) {
        item { LargeTitle("Studio", today) }

        item {
            Box(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp)).background(Pal.c.hero)
                    .clickable {
                        if (account.facebookReady) vm.open(Overlay.Composer(Store.defaultFb, Store.defaultIg && account.instagramReady))
                        else vm.open(Overlay.Connect)
                    }
                    .padding(22.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.Movie, null, tint = Color.White, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(if (account.facebookReady) "New Reel" else "Connect to start", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (account.facebookReady) "Check your gameplay clip against Meta's rules and post it to ${if (account.instagramReady) "Facebook and Instagram" else "Facebook"} in one tap."
                        else "Link your Facebook Page and Instagram once. ReelPilot does the rest.",
                        color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, lineHeight = 21.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (account.facebookReady) Icons.Rounded.Add else Icons.Rounded.Link, null, tint = Color(0xFF3A3AFF), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(if (account.facebookReady) "Create" else "Connect accounts", color = Color(0xFF3A3AFF), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
            }
        }

        if (account.facebookReady) item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Facebook", "${Store.postsLast24h(true)}", "of 30 today", Pal.c.facebook, Modifier.weight(1f))
                StatTile("Instagram", if (account.instagramReady) "${Store.postsLast24h(false)}" else "–", if (account.instagramReady) "of 100 today" else "not linked", Color(0xFFD62976), Modifier.weight(1f))
            }
        }

        if (jobs.isNotEmpty()) {
            item { Text("UPLOADS", color = Pal.c.secondary, fontSize = 13.sp, modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 6.dp)) }
            items(jobs.take(15), key = { it.id }) { job ->
                JobCard(job, live[job.id]) { vm.open(Overlay.JobDetail(job.id)) }
            }
        }

        item {
            Section("Grow faster", footer = "Based on how Meta ranks Reels in 2026: original content, real interest and shares come first.") {
                Cell("Hook in the first 3 seconds", "Open on the kill, the clutch or the fail, not the menu.", icon = { IconTile(Icons.Rounded.Bolt, Pal.c.orange) })
                Cell("Make it yours", "Your gameplay plus your voice or face-cam counts as original. Re-uploads get pushed down.", icon = { IconTile(Icons.Rounded.Movie, Pal.c.purple) })
                Cell("Ask a real question", "Comments and DM shares weigh more than likes. Skip 'comment YES'.", icon = { IconTile(Icons.Rounded.Forum, Pal.c.green) })
                Cell("Post trends the same day", "Fresh Reels get a boost. New patch, new map, new meta: post today.", icon = { IconTile(Icons.Rounded.Timer, Pal.c.blue) })
                Cell("Stay in one niche", "Viewers rate how well Reels match their interests. Consistency helps Meta find your audience.", icon = { IconTile(Icons.Rounded.Send, Pal.c.red) }, divider = false)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StatTile(title: String, big: String, small: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Pal.c.card).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.size(6.dp))
            Text(title, color = Pal.c.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(big, color = Pal.c.label, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(small, color = Pal.c.secondary, fontSize = 13.sp)
    }
}

@Composable
fun JobCard(job: Job, live: Live?, onClick: () -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(Pal.c.card).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(job.headline, color = Pal.c.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
            Text(ago(job.created), color = Pal.c.secondary, fontSize = 13.sp)
        }
        if (job.fb) PlatformLine("Facebook", job.fbState, job.fbMsg)
        if (job.ig) PlatformLine("Instagram", job.igState, if (job.schedule > 0) "Skipped: Instagram can't be scheduled" else job.igMsg)
        if (live != null && !job.finished) {
            ProgressBar(live.pct)
            Text("${live.phase} · ${(live.pct * 100).toInt()}%", color = Pal.c.secondary, fontSize = 12.sp)
        }
        if (job.schedule > 0) Text("Scheduled for ${fmtDateTime(job.schedule)}", color = Pal.c.secondary, fontSize = 12.sp)
    }
}

@Composable
private fun PlatformLine(name: String, state: String, msg: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StateIcon(state)
        Spacer(Modifier.size(6.dp))
        Text("$name · ", color = Pal.c.label, fontSize = 14.sp)
        Text(msg.ifBlank { state.replaceFirstChar { it.uppercase() } }, color = if (state == Job.FAILED) Pal.c.red else Pal.c.secondary, fontSize = 14.sp, maxLines = 2)
    }
}
