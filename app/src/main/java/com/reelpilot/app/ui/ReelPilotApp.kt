package com.reelpilot.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Facebook
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reelpilot.app.ui.screens.ComposerScreen
import com.reelpilot.app.ui.screens.ConnectScreen
import com.reelpilot.app.ui.screens.FacebookScreen
import com.reelpilot.app.ui.screens.GuideScreen
import com.reelpilot.app.ui.screens.HomeScreen
import com.reelpilot.app.ui.screens.InstagramScreen
import com.reelpilot.app.ui.screens.JobDetailScreen
import com.reelpilot.app.ui.screens.SettingsScreen
import com.reelpilot.app.ui.theme.Pal

@Composable
fun ReelPilotApp(vm: MainViewModel = viewModel()) {
    val tab by vm.tab.collectAsState()
    val overlays by vm.overlays.collectAsState()
    val account by vm.account.collectAsState()

    LaunchedEffect(account.pageId) { vm.loadFacebook(); vm.loadInstagram() }
    BackHandler(enabled = overlays.isNotEmpty()) { vm.back() }

    Box(Modifier.fillMaxSize().background(Pal.c.bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).statusBarsPadding()) {
                AnimatedContent(tab, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "tabs") { t ->
                    when (t) {
                        Tab.Home -> HomeScreen(vm)
                        Tab.Facebook -> FacebookScreen(vm)
                        Tab.Instagram -> InstagramScreen(vm)
                        Tab.Settings -> SettingsScreen(vm)
                    }
                }
            }
            TabBar(tab) { vm.tab.value = it }
        }

        // Full-screen pages slide over the tabs, like iOS modal sheets.
        AnimatedContent(
            targetState = overlays.lastOrNull(),
            transitionSpec = {
                if (targetState is Overlay.Composer || initialState is Overlay.Composer && targetState == null)
                    slideInVertically { it } togetherWith slideOutVertically { it }
                else slideInHorizontally { it } togetherWith slideOutHorizontally { it }
            },
            label = "overlay",
        ) { o ->
            if (o != null) Box(Modifier.fillMaxSize().background(Pal.c.bg).statusBarsPadding()) {
                when (o) {
                    is Overlay.Composer -> ComposerScreen(vm, o.fb, o.ig)
                    Overlay.Connect -> ConnectScreen(vm)
                    Overlay.Guide -> GuideScreen(vm)
                    is Overlay.JobDetail -> JobDetailScreen(vm, o.id)
                }
            }
        }
    }
}

@Composable
private fun TabBar(current: Tab, onSelect: (Tab) -> Unit) {
    val items = listOf(
        Triple(Tab.Home, Icons.Rounded.Home, "Studio"),
        Triple(Tab.Facebook, Icons.Rounded.Facebook, "Facebook"),
        Triple(Tab.Instagram, Icons.Rounded.CameraAlt, "Instagram"),
        Triple(Tab.Settings, Icons.Rounded.Settings, "Settings"),
    )
    Column(Modifier.fillMaxWidth().background(Pal.c.card.copy(alpha = 0.96f)).windowInsetsPadding(WindowInsets.navigationBars)) {
        HorizontalDivider(thickness = 0.5.dp, color = Pal.c.separator)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceAround) {
            items.forEach { (t, icon, label) -> TabItem(icon, label, t == current) { onSelect(t) } }
        }
    }
}

@Composable
private fun TabItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) Pal.c.blue else Pal.c.secondary
    Column(
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = color, modifier = Modifier.size(26.dp))
        Text(label, color = color, fontSize = 10.sp)
    }
}
