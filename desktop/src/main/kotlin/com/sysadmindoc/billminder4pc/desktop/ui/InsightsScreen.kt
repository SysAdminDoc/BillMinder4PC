package com.sysadmindoc.billminder4pc.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sysadmindoc.billminder4pc.core.cycle.CycleEngine
import com.sysadmindoc.billminder4pc.core.model.BillCategory
import com.sysadmindoc.billminder4pc.desktop.AppState
import com.sysadmindoc.billminder4pc.desktop.Dashboard
import com.sysadmindoc.billminder4pc.desktop.theme.privateAmount
import com.sysadmindoc.billminder4pc.desktop.theme.storedBillColor
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val insightsMonthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

private data class CategoryTotal(
    val category: BillCategory,
    val amount: Double,
    val color: Color
)

@Composable
fun InsightsScreen(state: AppState) {
    val dashboard by state.dashboard.collectAsState()
    if (!dashboard.loaded) return

    var displayedMonth by remember(dashboard.asOfDate) { mutableStateOf(YearMonth.from(dashboard.asOfDate)) }
    val scheduled = dashboard.rows.sumOf { it.bill.amount }
    val paid = dashboard.paid.sumOf { it.bill.amount }
    val remaining = dashboard.totalDue
    val categories = remember(dashboard.rows) {
        dashboard.rows.groupBy { it.bill.category }
            .map { (category, rows) ->
                CategoryTotal(
                    category = category,
                    amount = rows.sumOf { it.bill.amount },
                    color = storedBillColor(rows.first().bill.color)
                )
            }
            .sortedByDescending { it.amount }
    }
    val outlook = remember(displayedMonth, dashboard.rows) {
        (1L..6L).map { offset ->
            val month = displayedMonth.plusMonths(offset)
            month to dashboard.rows.sumOf { row ->
                CycleEngine.occurrencesInRange(row.bill, month.atDay(1), month.atEndOfMonth()).size * row.bill.amount
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PageHeader(title = "Insights", subtitle = "See where the month is going") {
            SquareIconButton(
                Icons.AutoMirrored.Filled.ArrowBack,
                "Previous month",
                onClick = { displayedMonth = displayedMonth.minusMonths(1) }
            )
            Box(Modifier.width(132.dp).height(38.dp), contentAlignment = Alignment.Center) {
                Text(displayedMonth.format(insightsMonthFormatter), style = MaterialTheme.typography.titleMedium)
            }
            SquareIconButton(
                Icons.AutoMirrored.Filled.ArrowForward,
                "Next month",
                onClick = { displayedMonth = displayedMonth.plusMonths(1) }
            )
        }

        InsightSummary(scheduled, paid, remaining)

        Row(
            modifier = Modifier.fillMaxWidth().height(248.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CategoryCard(categories, scheduled, Modifier.weight(1.32f).fillMaxHeight())
            MonthStatusCard(dashboard, scheduled, paid, Modifier.weight(1f).fillMaxHeight())
        }

        OutlookCard(outlook, dashboard, Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
private fun InsightSummary(scheduled: Double, paid: Double, remaining: Double) {
    val success = ledgerSuccessColor()
    val danger = ledgerDangerColor()
    LedgerCard(Modifier.fillMaxWidth().height(88.dp)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            InsightMetric("scheduled", scheduled, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            VerticalRule()
            InsightMetric("paid", paid, success, Modifier.weight(1f))
            VerticalRule()
            InsightMetric("remaining", remaining, if (remaining > 0) danger else success, Modifier.weight(1f))
        }
    }
}

@Composable
private fun InsightMetric(label: String, amount: Double, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            privateAmount(amount),
            style = MaterialTheme.typography.headlineMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VerticalRule() {
    Box(Modifier.width(1.dp).height(54.dp).background(MaterialTheme.colorScheme.outline))
}

@Composable
private fun CategoryCard(categories: List<CategoryTotal>, total: Double, modifier: Modifier) {
    LedgerCard(modifier) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Spending by category", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(13.dp))
            categories.take(5).forEach { category ->
                val fraction = if (total <= 0.0) 0f else (category.amount / total).toFloat().coerceIn(0f, 1f)
                Row(
                    modifier = Modifier.fillMaxWidth().height(35.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        category.category.insightsLabel(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(116.dp)
                    )
                    Box(
                        Modifier.weight(1f).height(15.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp))
                    ) {
                        Box(
                            Modifier.fillMaxWidth(fraction.coerceAtLeast(0.025f)).fillMaxHeight()
                                .background(category.color, RoundedCornerShape(4.dp))
                        )
                    }
                    Text(
                        privateAmount(category.amount),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.width(80.dp).padding(start = 10.dp)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
            Row(Modifier.fillMaxWidth().padding(top = 9.dp)) {
                Text("Total", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(privateAmount(total), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MonthStatusCard(
    dashboard: Dashboard,
    scheduled: Double,
    paid: Double,
    modifier: Modifier
) {
    val progress = if (scheduled == 0.0) 0f else (paid / scheduled).toFloat().coerceIn(0f, 1f)
    val success = ledgerSuccessColor()
    val danger = ledgerDangerColor()
    LedgerCard(modifier) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Month status", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(136.dp).drawBehind {
                        val stroke = Stroke(width = 15f, cap = StrokeCap.Butt)
                        drawArc(
                            color = Color(0xFF2A3C55),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = stroke
                        )
                        drawArc(
                            color = success,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            style = stroke
                        )
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${dashboard.paid.size} of ${dashboard.rows.size}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text("paid", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(22.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${(progress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = success
                    )
                    Text("paid", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(18.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        dashboard.overdue.size.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (dashboard.overdue.isEmpty()) success else danger
                    )
                    Text(
                        if (dashboard.overdue.size == 1) "overdue" else "overdue bills",
                        color = if (dashboard.overdue.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else danger
                    )
                }
            }
        }
    }
}

@Composable
private fun OutlookCard(
    outlook: List<Pair<YearMonth, Double>>,
    dashboard: Dashboard,
    modifier: Modifier
) {
    val largest = dashboard.rows.maxByOrNull { it.bill.amount }
    val maximum = outlook.maxOfOrNull { it.second }?.takeIf { it > 0.0 } ?: 1.0
    val chartCeiling = maximum * 1.28
    LedgerCard(modifier) {
        Row(Modifier.fillMaxSize().padding(16.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text("Six-month outlook", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                val guideColor = MaterialTheme.colorScheme.outline
                Row(Modifier.fillMaxWidth().height(170.dp)) {
                    Column(
                        modifier = Modifier.width(44.dp).fillMaxHeight().padding(bottom = 20.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("$2.4k", "$1.8k", "$1.2k", "$0.6k", "$0").forEach { label ->
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight().drawBehind {
                            drawLine(
                                color = guideColor,
                                start = Offset(0f, size.height * 0.31f),
                                end = Offset(size.width, size.height * 0.31f),
                                strokeWidth = 1f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 6f))
                            )
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            outlook.forEach { (month, amount) ->
                                Column(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "$${"%.2f".format(Locale.US, amount / 1000.0)}k",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        Box(
                                            Modifier.fillMaxWidth()
                                                .fillMaxHeight((amount / chartCeiling).toFloat().coerceAtLeast(0.08f))
                                                .background(
                                                    MaterialTheme.colorScheme.primary,
                                                    RoundedCornerShape(5.dp, 5.dp, 1.dp, 1.dp)
                                                )
                                        )
                                    }
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        month.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(18.dp))
            Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
            Column(
                modifier = Modifier.width(210.dp).fillMaxHeight().padding(start = 24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("Largest bill", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(7.dp))
                Text(
                    largest?.let { "${it.bill.name} · ${privateAmount(it.bill.amount, it.bill.currency)}" } ?: "None",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun BillCategory.insightsLabel(): String = when (this) {
    BillCategory.RENT -> "Rent & mortgage"
    BillCategory.PHONE -> "Phone & internet"
    BillCategory.SUBSCRIPTION -> "Subscriptions"
    else -> label
}
