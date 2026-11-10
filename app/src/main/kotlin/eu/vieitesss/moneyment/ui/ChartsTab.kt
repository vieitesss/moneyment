package eu.vieitesss.moneyment.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.vieitesss.moneyment.R
import eu.vieitesss.moneyment.domain.CategoryShare
import eu.vieitesss.moneyment.domain.MonthBar
import eu.vieitesss.moneyment.domain.expensesByCategory
import eu.vieitesss.moneyment.domain.lastTwelveMonths
import java.time.YearMonth
import java.time.format.TextStyle
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun ChartsTab(vm: AppViewModel) {
    val shares = remember(vm.data, vm.month) { expensesByCategory(vm.data.transactions, vm.month) }
    val bars = remember(vm.data) { lastTwelveMonths(vm.data.transactions, YearMonth.now()) }
    val showCategory: (Long) -> Unit = {
        vm.filterCategory = it
        // Every Account is counted in the chart, so the list must not stay filtered by one.
        vm.filterAccount = null
        vm.tab = Tab.TRANSACTIONS
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MonthHeader(vm)
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.expenses_by_category), style = MaterialTheme.typography.titleMedium)
                if (shares.isEmpty()) {
                    Text(
                        stringResource(R.string.no_expenses),
                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Donut(vm, shares, showCategory)
                    shares.forEach { ShareRow(vm, it, showCategory) }
                }
            }
        }
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.income_vs_expenses), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Legend(incomeColor(), stringResource(R.string.income))
                    Legend(expenseColor(), stringResource(R.string.expenses))
                }
                Spacer(Modifier.height(12.dp))
                Bars(bars)
            }
        }
    }
}

@Composable
private fun Donut(vm: AppViewModel, shares: List<CategoryShare>, onTap: (Long) -> Unit) {
    val colors = shares.map { Color(vm.data.category(it.categoryId)?.color ?: 0xFF888888.toInt()) }
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.size(200.dp).pointerInput(shares) {
                detectTapGestures { tap ->
                    val dx = tap.x - size.width / 2f
                    val dy = tap.y - size.height / 2f
                    val radius = hypot(dx, dy)
                    if (radius > size.width / 2f || radius < size.width / 2f - 44.dp.toPx()) return@detectTapGestures
                    // Slices start at 12 o'clock and go clockwise.
                    val turn = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 90 + 360) % 360 / 360).toFloat()
                    var end = 0f
                    for (share in shares) {
                        end += share.fraction
                        if (turn <= end) {
                            onTap(share.categoryId)
                            break
                        }
                    }
                }
            },
        ) {
            val stroke = 36.dp.toPx()
            val inset = stroke / 2
            var start = -90f
            shares.forEachIndexed { i, share ->
                val sweep = share.fraction * 360f
                drawArc(
                    color = colors[i],
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke),
                )
                start += sweep
            }
        }
        Text(
            formatMoney(shares.sumOf { it.cents }),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ShareRow(vm: AppViewModel, share: CategoryShare, onTap: (Long) -> Unit) {
    val category = vm.data.category(share.categoryId)
    Row(
        Modifier.fillMaxWidth().clickable { onTap(share.categoryId) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).background(Color(category?.color ?: 0xFF888888.toInt()), CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            "${category?.emoji.orEmpty()} ${category?.label(LocalContext.current).orEmpty()}",
            Modifier.weight(1f),
        )
        Text(
            "${(share.fraction * 100).roundToInt()} %",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.width(12.dp))
        Text(formatMoney(share.cents), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Bars(bars: List<MonthBar>) {
    val income = incomeColor()
    val expenses = expenseColor()
    val axis = MaterialTheme.colorScheme.outlineVariant
    val max = bars.maxOf { maxOf(it.income, it.expenses) }
    val description = bars.map { month ->
        stringResource(
            R.string.month_income_expenses,
            formatMonth(month.month),
            formatMoney(month.income),
            formatMoney(month.expenses),
        )
    }.joinToString("\n")
    Text(
        formatMoney(max),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Canvas(Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = description }) {
        val slot = size.width / bars.size
        val bar = slot * 0.34f
        val corner = CornerRadius(3.dp.toPx())
        drawLine(axis, Offset(0f, 0f), Offset(size.width, 0f))
        drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height))
        if (max == 0L) return@Canvas
        bars.forEachIndexed { i, month ->
            val left = i * slot + (slot - 2 * bar) / 2
            listOf(month.income to income, month.expenses to expenses).forEachIndexed { j, (cents, color) ->
                val height = size.height * cents / max
                drawRoundRect(color, Offset(left + j * bar, size.height - height), Size(bar * 0.9f, height), corner)
            }
        }
    }
    Row(Modifier.fillMaxWidth()) {
        val locale = LocalConfiguration.current.locales[0]
        bars.forEach {
            Text(
                it.month.month.getDisplayName(TextStyle.NARROW_STANDALONE, locale).uppercase(),
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
