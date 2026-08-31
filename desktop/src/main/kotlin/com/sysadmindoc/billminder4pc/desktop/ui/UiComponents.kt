package com.sysadmindoc.billminder4pc.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sysadmindoc.billminder4pc.desktop.theme.CatGreen
import com.sysadmindoc.billminder4pc.desktop.theme.CatRed
import com.sysadmindoc.billminder4pc.desktop.theme.CatYellow
import com.sysadmindoc.billminder4pc.desktop.theme.LedgerLightGreen
import com.sysadmindoc.billminder4pc.desktop.theme.LedgerLightRed

private val LedgerLightWarning = Color(0xFF8A5A00)

val LedgerCardShape = RoundedCornerShape(10.dp)
val LedgerControlShape = RoundedCornerShape(8.dp)

@Composable
internal fun ledgerSuccessColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() > 0.5f) LedgerLightGreen else CatGreen

@Composable
internal fun ledgerWarningColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() > 0.5f) LedgerLightWarning else CatYellow

@Composable
internal fun ledgerDangerColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() > 0.5f) LedgerLightRed else CatRed

@Composable
fun PageHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth().height(58.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
    }
}

@Composable
fun LedgerCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = LedgerCardShape,
        color = color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        content = content
    )
}

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
    enabled: Boolean = true
) {
    val container = if (primary) MaterialTheme.colorScheme.primary else Color.Transparent
    val content = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        enabled = enabled,
        shape = LedgerControlShape,
        color = when {
            enabled -> container
            primary -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> Color.Transparent
        },
        contentColor = if (enabled) content else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (primary) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.fillMaxHeight().padding(horizontal = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(17.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.size(7.dp))
            }
            Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SquareIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(38.dp),
        shape = LedgerControlShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun SquareCheck(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 26.dp
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier.size(size),
        enabled = enabled,
        shape = RoundedCornerShape(6.dp),
        color = when {
            checked -> MaterialTheme.colorScheme.primary
            else -> Color.Transparent
        },
        border = BorderStroke(
            1.dp,
            if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (checked) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(size * 0.65f)
                )
            }
        }
    }
}

@Composable
fun MessageCard(
    message: String,
    error: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (error) ledgerDangerColor() else ledgerSuccessColor()
    Surface(
        modifier = modifier,
        shape = LedgerControlShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = accent,
                modifier = Modifier.weight(1f)
            )
            Surface(
                onClick = onDismiss,
                shape = RoundedCornerShape(6.dp),
                color = Color.Transparent,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
