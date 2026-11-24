package com.reelpilot.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.reelpilot.app.data.Job
import com.reelpilot.app.ui.components.TextButtonIos
import com.reelpilot.app.ui.theme.Pal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** iOS navigation bar: back/cancel on the left, centred title, optional action on the right. */
@Composable
fun NavBar(title: String, left: String = "Back", onLeft: () -> Unit, right: String? = null, rightEnabled: Boolean = true, onRight: () -> Unit = {}) {
    Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp)) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            if (left == "Back") Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, null, tint = Pal.c.blue, modifier = Modifier.padding(start = 6.dp).size(18.dp))
            TextButtonIos(left, onClick = onLeft)
        }
        Text(title, color = Pal.c.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
        if (right != null) Box(Modifier.align(Alignment.CenterEnd)) {
            TextButtonIos(right, color = if (rightEnabled) Pal.c.blue else Pal.c.tertiary) { if (rightEnabled) onRight() }
        }
    }
}

@Composable
fun Avatar(url: String, size: Int, fallback: Color = Pal.c.fill) {
    if (url.isBlank()) Box(Modifier.size(size.dp).clip(CircleShape).background(fallback))
    else AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(size.dp).clip(CircleShape).background(Pal.c.fill))
}

@Composable
fun StateIcon(state: String) {
    val (icon, tint) = when (state) {
        Job.DONE -> Icons.Rounded.CheckCircle to Pal.c.green
        Job.FAILED -> Icons.Rounded.Error to Pal.c.red
        Job.RUNNING, Job.RETRYING -> Icons.Rounded.Sync to Pal.c.blue
        else -> Icons.Rounded.Schedule to Pal.c.secondary
    }
    Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
}

fun openUrl(ctx: Context, url: String) {
    if (url.isBlank()) return
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
}

fun ago(ms: Long): String {
    val d = (System.currentTimeMillis() - ms) / 1000
    return when {
        d < 60 -> "Just now"
        d < 3600 -> "${d / 60} min ago"
        d < 86400 -> "${d / 3600} h ago"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ms))
    }
}

fun isoAgo(iso: String): String = try {
    val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
    ago(f.parse(iso)!!.time)
} catch (e: Exception) { "" }

fun fmtDateTime(sec: Long): String = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(Date(sec * 1000))
