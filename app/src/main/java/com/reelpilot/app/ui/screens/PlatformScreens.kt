package com.reelpilot.app.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Facebook
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.reelpilot.app.data.IgMedia
import com.reelpilot.app.ui.MainViewModel
import com.reelpilot.app.ui.Overlay
import com.reelpilot.app.ui.components.EmptyState
import com.reelpilot.app.ui.components.LargeTitle
import com.reelpilot.app.ui.components.Pill
import com.reelpilot.app.ui.components.PrimaryButton
import com.reelpilot.app.ui.components.ProgressBar
import com.reelpilot.app.ui.components.compact
import com.reelpilot.app.ui.theme.Pal

@Composable
private fun RefreshButton(loading: Boolean, onClick: () -> Unit) {
    if (loading) CircularProgressIndicator(strokeWidth = 2.dp, color = Pal.c.blue, modifier = Modifier.padding(12.dp).size(22.dp))
    else IconButton(onClick = onClick) { Icon(Icons.Rounded.Refresh, "Refresh", tint = Pal.c.blue) }
}

@Composable
private fun ErrorBanner(msg: String?) {
    if (msg == null) return
    Text(
        msg, color = Pal.c.red, fontSize = 14.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)).background(Pal.c.red.copy(alpha = 0.1f)).padding(12.dp),
    )
}

// =============================== Facebook ===============================

@Composable
fun FacebookScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val account by vm.account.collectAsState()
    val page by vm.fbPage.collectAsState()
    val videos by vm.fbVideos.collectAsState()

    if (!account.facebookReady) {
        Column {
            LargeTitle("Facebook")
            EmptyState(Icons.Rounded.Facebook, "No Page connected", "Connect your Facebook Page to post Reels and see how they're doing.", "Connect Page") { vm.open(Overlay.Connect) }
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item { LargeTitle("Facebook", "Page") { RefreshButton(page.loading || videos.loading) { vm.loadFacebook() } } }
        item {
            val p = page.data
            Column(
                Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Pal.c.card).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Avatar(p?.picture ?: account.pagePicture, 84, Pal.c.facebook)
                Text(p?.name ?: account.pageName, color = Pal.c.label, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (p != null) Text("${compact(p.followers)} followers", color = Pal.c.secondary, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                PrimaryButton("New Facebook Reel", color = Pal.c.facebook) { vm.open(Overlay.Composer(fb = true, ig = false)) }
                if (p?.link?.isNotBlank() == true) Text(
                    "Open Page on Facebook", color = Pal.c.blue, fontSize = 15.sp,
                    modifier = Modifier.clickable { openUrl(ctx, p.link) }.padding(8.dp),
                )
            }
        }
        item { ErrorBanner(page.error ?: videos.error) }
        item { Text("RECENT VIDEOS", color = Pal.c.secondary, fontSize = 13.sp, modifier = Modifier.padding(start = 32.dp, top = 8.dp, bottom = 6.dp)) }
        val list = videos.data.orEmpty()
        if (list.isEmpty() && !videos.loading) item {
            Text("No videos on this Page yet. Your first Reel will show up here.", color = Pal.c.secondary, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp))
        }
        items(list, key = { it.id }) { v ->
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Pal.c.card).clickable { openUrl(ctx, v.link) }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(64.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
                    if (v.picture.isNotBlank()) AsyncImage(v.picture, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(v.description.lineSequence().firstOrNull().orEmpty().ifBlank { "Untitled video" }, color = Pal.c.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Text(listOf(isoAgo(v.created), if (v.length > 0) "${v.length.toInt()}s" else "").filter { it.isNotBlank() }.joinToString(" · "), color = Pal.c.secondary, fontSize = 13.sp)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// =============================== Instagram ===============================

@Composable
fun InstagramScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val account by vm.account.collectAsState()
    val profile by vm.igProfile.collectAsState()
    val media by vm.igMedia.collectAsState()
    val quota by vm.igQuota.collectAsState()

    if (!account.instagramReady) {
        Column {
            LargeTitle("Instagram")
            EmptyState(
                Icons.Rounded.CameraAlt, "Instagram not linked",
                if (account.facebookReady) "Switch your Instagram to a Professional account, link it to your Facebook Page in Instagram's settings, then reconnect here."
                else "Connect your Facebook Page first. ReelPilot finds the Instagram account linked to it.",
                if (account.facebookReady) "Reconnect" else "Connect",
            ) { vm.open(Overlay.Connect) }
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item { LargeTitle("Instagram", "@${account.igUsername}") { RefreshButton(profile.loading || media.loading) { vm.loadInstagram() } } }
        item {
            val p = profile.data
            Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Pal.c.card).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(90.dp).clip(CircleShape).background(Pal.c.instagram).padding(3.dp)) {
                        Box(Modifier.fillMaxSize().clip(CircleShape).background(Pal.c.card).padding(3.dp)) {
                            Avatar(p?.picture ?: account.igPicture, 78)
                        }
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Stat(compact(p?.media ?: 0), "Posts")
                        Stat(compact(p?.followers ?: 0), "Followers")
                        Stat(compact(p?.follows ?: 0), "Following")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(p?.name?.ifBlank { null } ?: account.igUsername, color = Pal.c.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (!p?.bio.isNullOrBlank()) Text(p!!.bio, color = Pal.c.label, fontSize = 14.sp, lineHeight = 19.sp, maxLines = 3)
                quota?.let { q ->
                    Spacer(Modifier.height(14.dp))
                    Row { Text("Posted with the API today", color = Pal.c.secondary, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text("${q.used} / ${q.total}", color = Pal.c.secondary, fontSize = 13.sp) }
                    Spacer(Modifier.height(6.dp))
                    ProgressBar(q.used / q.total.coerceAtLeast(1).toFloat(), Color(0xFFD62976))
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(Pal.c.instagram).clickable { vm.open(Overlay.Composer(fb = false, ig = true)) }, contentAlignment = Alignment.Center) {
                    Text("New Instagram Reel", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item { ErrorBanner(profile.error ?: media.error) }
        val rows = media.data.orEmpty().chunked(3)
        if (rows.isEmpty() && !media.loading) item {
            Text("No posts yet. Your first Reel will appear here.", color = Pal.c.secondary, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp))
        }
        items(rows.size) { i ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 1.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                rows[i].forEach { m -> MediaTile(m, Modifier.weight(1f)) { openUrl(ctx, m.link) } }
                repeat(3 - rows[i].size) { Spacer(Modifier.weight(1f)) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Pal.c.label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Pal.c.secondary, fontSize = 12.sp)
    }
}

@Composable
private fun MediaTile(m: IgMedia, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.aspectRatio(3f / 4f).clip(RoundedCornerShape(4.dp)).background(Pal.c.fill).clickable(onClick = onClick)) {
        if (m.thumb.isNotBlank()) AsyncImage(m.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (m.isReel) Icon(Icons.Rounded.Movie, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp))
        Row(
            Modifier.align(Alignment.BottomStart).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Favorite, null, tint = Color.White, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(3.dp))
            Text(compact(m.likes), color = Color.White, fontSize = 11.sp)
        }
    }
}

@Suppress("unused")
@Composable
private fun ReelPill() = Pill("Reel", Pal.c.purple)
