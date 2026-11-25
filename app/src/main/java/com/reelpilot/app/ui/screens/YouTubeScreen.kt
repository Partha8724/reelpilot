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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.reelpilot.app.ui.MainViewModel
import com.reelpilot.app.ui.Overlay
import com.reelpilot.app.ui.components.Cell
import com.reelpilot.app.ui.components.Field
import com.reelpilot.app.ui.components.PrimaryButton
import com.reelpilot.app.ui.components.Section
import com.reelpilot.app.ui.components.compact
import com.reelpilot.app.ui.theme.Pal

private val YtRed = Color(0xFFFF0000)

@Composable
fun YouTubeScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val yt by vm.yt.collectAsState()
    val channel by vm.ytChannel.collectAsState()
    val videos by vm.ytVideos.collectAsState()
    val busy by vm.ytBusy.collectAsState()
    val error by vm.ytError.collectAsState()
    var clientId by rememberSaveable { mutableStateOf("") }
    var secret by rememberSaveable { mutableStateOf("") }
    var refresh by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().imePadding()) {
        NavBar("YouTube", left = "Back", onLeft = { vm.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (yt.ready) {
                val ch = channel.data
                Column(
                    Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Pal.c.card).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Avatar(ch?.thumb ?: yt.channelThumb, 84, YtRed)
                    Text(ch?.title ?: yt.channelTitle, color = Pal.c.label, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (ch != null) Text("${compact(ch.subscribers)} subscribers · ${compact(ch.videos)} videos · ${compact(ch.views)} views", color = Pal.c.secondary, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton("New YouTube Short", color = YtRed) { vm.back(); vm.open(Overlay.Composer(fb = false, ig = false, yt = true)) }
                }
                if (channel.error != null) Text(channel.error!!, color = Pal.c.red, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 32.dp))

                Section("Public or private?", footer = "YouTube keeps videos uploaded through the API private until your Google Cloud project passes YouTube's API compliance audit. Until then, use \"Post with the YouTube app instead\" on the New Reel screen for public Shorts.") {
                    Cell("Apply for the YouTube API audit", "Free form from Google. Approval can take a few weeks.", chevron = true, divider = false,
                        onClick = { openUrl(ctx, "https://support.google.com/youtube/contact/yt_api_form") })
                }

                val list = videos.data.orEmpty()
                if (list.isNotEmpty()) {
                    Text("RECENT UPLOADS", color = Pal.c.secondary, fontSize = 13.sp, modifier = Modifier.padding(start = 32.dp, top = 8.dp, bottom = 6.dp))
                    list.forEach { v ->
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pal.c.card)
                                .clickable { openUrl(ctx, "https://youtube.com/shorts/${v.id}") }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
                                if (v.thumb.isNotBlank()) AsyncImage(v.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(v.title, color = Pal.c.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                Text(isoAgo(v.published.replace("Z", "+0000")), color = Pal.c.secondary, fontSize = 13.sp)
                            }
                        }
                    }
                }
                Section {
                    Cell(
                        if (confirm) "Tap again to disconnect YouTube" else "Disconnect YouTube", titleColor = Pal.c.red, divider = false,
                        onClick = { if (confirm) { vm.disconnectYouTube(); confirm = false } else confirm = true },
                    )
                }
            } else {
                Text(
                    "Post every Reel as a YouTube Short too. You need a free Google Cloud project once (about 10 minutes), then paste 3 values here.",
                    color = Pal.c.secondary, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                )
                val steps = listOf(
                    "Create a project" to "Open console.cloud.google.com, make a new project (e.g. ReelPilot), then in APIs & Services → Library enable \"YouTube Data API v3\".",
                    "Set up the consent screen" to "Google Auth Platform → Branding: app name ReelPilot, your email. Audience: External. Then tap Publish app so it's \"In production\". This stops your login from expiring every 7 days. You can ignore the \"needs verification\" note; only you will use it.",
                    "Create an OAuth client" to "Google Auth Platform → Clients → Create client → Web application. Under Authorized redirect URIs add: https://developers.google.com/oauthplayground. Copy the Client ID and Client secret.",
                    "Get a refresh token" to "Open developers.google.com/oauthplayground. Tap the gear icon, tick \"Use your own OAuth credentials\" and paste the Client ID and secret. In Step 1 type: https://www.googleapis.com/auth/youtube.upload https://www.googleapis.com/auth/youtube.readonly and tap Authorize APIs. Sign in with your channel's Google account. On the warning tap Advanced → Go to ReelPilot, then Allow.",
                    "Copy the token" to "In Step 2 tap \"Exchange authorization code for tokens\" and copy the Refresh token (starts with 1//). Paste all three below.",
                )
                Section("One-time setup") {
                    steps.forEachIndexed { i, (t, d) -> Cell("${i + 1}. $t", d, divider = i < steps.lastIndex) }
                }
                Section("Links") {
                    Cell("Google Cloud Console", chevron = true, onClick = { openUrl(ctx, "https://console.cloud.google.com/") })
                    Cell("OAuth 2.0 Playground", chevron = true, divider = false, onClick = { openUrl(ctx, "https://developers.google.com/oauthplayground") })
                }
                Section("Your keys", footer = "Saved encrypted on this phone only. ReelPilot sends them only to Google.") {
                    Field("Client ID", clientId, { clientId = it }, "1234…apps.googleusercontent.com")
                    Field("Client secret", secret, { secret = it }, "GOCSPX-…", secret = true)
                    Field("Refresh token", refresh, { refresh = it }, "1//0g…", secret = true, divider = false)
                }
                if (error != null) Text(error!!, color = Pal.c.red, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp))
                PrimaryButton("Connect YouTube", color = YtRed, loading = busy, modifier = Modifier.padding(16.dp)) { vm.connectYouTube(clientId, secret, refresh) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
