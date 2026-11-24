package com.reelpilot.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelpilot.app.data.Check
import com.reelpilot.app.data.Level
import com.reelpilot.app.ui.theme.Pal

@Composable
fun LargeTitle(title: String, subtitle: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            if (subtitle != null) Text(subtitle, color = Pal.c.secondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(title, color = Pal.c.label, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp)
        }
        trailing()
    }
}

@Composable
fun Section(
    header: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (header != null) Text(
            header.uppercase(), color = Pal.c.secondary, fontSize = 13.sp,
            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
        )
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.c.card), content = content)
        if (footer != null) Text(
            footer, color = Pal.c.secondary, fontSize = 13.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
        )
    }
}

@Composable
fun IconTile(icon: ImageVector, color: Color? = null, brush: Brush? = null, size: Int = 30) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.24).dp))
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(color ?: Pal.c.blue)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size((size * 0.6).dp)) }
}

@Composable
fun Cell(
    title: String,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    value: String? = null,
    titleColor: Color = Pal.c.label,
    chevron: Boolean = false,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) { icon(); Spacer(Modifier.width(14.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, color = titleColor, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, color = Pal.c.secondary, fontSize = 14.sp, lineHeight = 19.sp)
            }
            if (value != null) Text(value, color = Pal.c.secondary, fontSize = 17.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
            if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
            if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Pal.c.tertiary, modifier = Modifier.padding(start = 4.dp))
        }
        if (divider) HorizontalDivider(Modifier.padding(start = if (icon != null) 60.dp else 16.dp), thickness = 0.5.dp, color = Pal.c.separator)
    }
}

@Composable
fun ToggleCell(title: String, subtitle: String? = null, checked: Boolean, divider: Boolean = true, icon: (@Composable () -> Unit)? = null, onChange: (Boolean) -> Unit) {
    Cell(title = title, subtitle = subtitle, icon = icon, divider = divider, onClick = { onChange(!checked) }) {
        IosSwitch(checked, onChange)
    }
}

@Composable
fun IosSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = Pal.c.green, checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White, uncheckedTrackColor = Pal.c.fill, uncheckedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    secret: Boolean = false,
    singleLine: Boolean = true,
    number: Boolean = false,
    divider: Boolean = true,
) {
    Column(Modifier.padding(start = 16.dp)) {
        Column(Modifier.padding(end = 16.dp, top = 10.dp, bottom = 10.dp)) {
            Text(label, color = Pal.c.secondary, fontSize = 13.sp)
            Box(Modifier.padding(top = 2.dp)) {
                if (value.isEmpty()) Text(placeholder, color = Pal.c.tertiary, fontSize = 17.sp)
                BasicTextField(
                    value = value, onValueChange = onChange, singleLine = singleLine,
                    minLines = if (singleLine) 1 else 3,
                    textStyle = TextStyle(color = Pal.c.label, fontSize = 17.sp),
                    cursorBrush = SolidColor(Pal.c.blue),
                    visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else if (secret) KeyboardType.Password else KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (divider) HorizontalDivider(thickness = 0.5.dp, color = Pal.c.separator)
    }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, loading: Boolean = false, color: Color? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = color ?: Pal.c.blue
    Box(
        modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.35f))
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
        else Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TextButtonIos(text: String, color: Color = Pal.c.blue, onClick: () -> Unit) {
    Text(
        text, color = color, fontSize = 17.sp,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
fun CheckRow(check: Check, divider: Boolean) {
    val (icon, tint) = when (check.level) {
        Level.OK -> Icons.Rounded.CheckCircle to Pal.c.green
        Level.WARN -> Icons.Rounded.Warning to Pal.c.orange
        Level.BAD -> Icons.Rounded.Error to Pal.c.red
    }
    Cell(
        title = check.title, subtitle = check.detail, divider = divider,
        icon = { Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp)) },
    )
}

@Composable
fun ProgressBar(progress: Float, color: Color = Pal.c.blue, modifier: Modifier = Modifier) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), label = "progress")
    Box(modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Pal.c.fill)) {
        Box(Modifier.fillMaxWidth(p).height(6.dp).clip(CircleShape).background(color))
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = Pal.c.tertiary, modifier = Modifier.size(56.dp))
        Text(title, color = Pal.c.label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(text, color = Pal.c.secondary, fontSize = 15.sp, lineHeight = 21.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(6.dp)); PrimaryButton(action, onClick = onAction) }
    }
}

fun compact(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0).replace(".0M", "M")
    n >= 10_000 -> "${n / 1000}K"
    n >= 1_000 -> "%.1fK".format(n / 1000.0).replace(".0K", "K")
    else -> n.toString()
}
