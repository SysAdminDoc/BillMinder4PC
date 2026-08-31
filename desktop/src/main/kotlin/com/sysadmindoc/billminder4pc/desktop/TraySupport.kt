package com.sysadmindoc.billminder4pc.desktop

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter

internal data class TrayPresentation(
    val dueCount: Int,
    val tooltip: String,
    val nextBill: BillRow?
)

internal fun Dashboard.toTrayPresentation(): TrayPresentation {
    val billsToday = rows.filter { it.dueDate == asOfDate }
    val dueCount = rows.count { !it.isPaid && (it.isOverdue || it.dueDate == asOfDate) }
    val todaySummary = if (billsToday.isEmpty()) {
        "No bills due today"
    } else {
        "Today: " + billsToday.joinToString(", ") { row ->
            if (row.isPaid) "${row.bill.name} (paid)" else row.bill.name
        }
    }
    return TrayPresentation(
        dueCount = dueCount,
        tooltip = "BillMinder for PC\n$todaySummary",
        nextBill = rows.firstOrNull { !it.isPaid && it.dueDate != null }
    )
}

internal object TrayBadgeIcon {
    private const val SIZE = 64
    private val digitSegments = mapOf(
        '0' to setOf(0, 1, 2, 3, 4, 5),
        '1' to setOf(1, 2),
        '2' to setOf(0, 1, 6, 4, 3),
        '3' to setOf(0, 1, 6, 2, 3),
        '4' to setOf(5, 6, 1, 2),
        '5' to setOf(0, 5, 6, 2, 3),
        '6' to setOf(0, 5, 6, 4, 2, 3),
        '7' to setOf(0, 1, 2),
        '8' to setOf(0, 1, 2, 3, 4, 5, 6),
        '9' to setOf(0, 1, 2, 3, 5, 6)
    )

    fun painter(dueCount: Int): Painter = BitmapPainter(image(dueCount))

    fun image(dueCount: Int): ImageBitmap {
        val bitmap = ImageBitmap(SIZE, SIZE)
        val canvas = Canvas(bitmap)
        val bodyPaint = solidPaint(Color(0xFF62A5FF))
        val detailPaint = solidPaint(Color(0xFFF4F7FF))
        val badgePaint = solidPaint(if (dueCount > 0) Color(0xFFFF7186) else Color(0xFF38506F))

        canvas.drawRoundRect(5f, 8f, 51f, 62f, 9f, 9f, bodyPaint)
        canvas.drawRoundRect(13f, 20f, 43f, 24f, 2f, 2f, detailPaint)
        canvas.drawRoundRect(13f, 33f, 37f, 37f, 2f, 2f, detailPaint)
        canvas.drawRoundRect(13f, 46f, 32f, 50f, 2f, 2f, detailPaint)
        canvas.drawCircle(androidx.compose.ui.geometry.Offset(49f, 15f), 14f, badgePaint)

        val label = dueCount.coerceIn(0, 99).toString()
        val firstX = if (label.length == 1) 45f else 40.5f
        label.forEachIndexed { index, digit ->
            drawDigit(canvas, digit, firstX + index * 9f, 8f, detailPaint)
        }
        return bitmap
    }

    private fun solidPaint(color: Color): Paint = Paint().apply {
        this.color = color
        isAntiAlias = true
    }

    private fun drawDigit(canvas: Canvas, digit: Char, x: Float, y: Float, paint: Paint) {
        val active = digitSegments.getValue(digit)
        val segments = arrayOf(
            floatArrayOf(x + 1f, y, x + 6f, y + 2f),
            floatArrayOf(x + 5f, y + 1f, x + 7f, y + 7f),
            floatArrayOf(x + 5f, y + 7f, x + 7f, y + 13f),
            floatArrayOf(x + 1f, y + 12f, x + 6f, y + 14f),
            floatArrayOf(x, y + 7f, x + 2f, y + 13f),
            floatArrayOf(x, y + 1f, x + 2f, y + 7f),
            floatArrayOf(x + 1f, y + 6f, x + 6f, y + 8f)
        )
        active.forEach { segment ->
            val rect = segments[segment]
            canvas.drawRoundRect(rect[0], rect[1], rect[2], rect[3], 1f, 1f, paint)
        }
    }
}
