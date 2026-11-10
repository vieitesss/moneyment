package eu.vieitesss.moneyment.ui

import android.content.Context
import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.vieitesss.moneyment.R
import eu.vieitesss.moneyment.domain.Account
import eu.vieitesss.moneyment.domain.Category
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

private val Brand = Color(0xFF00695C)
private val BrandDark = Color(0xFF4DB6AC)

private val LocalDark = staticCompositionLocalOf { false }

@Composable
fun MoneymentTheme(choice: ThemeChoice, content: @Composable () -> Unit) {
    val dark = isDark(choice, isSystemInDarkTheme())
    val scheme = if (dark) {
        darkColorScheme(
            primary = BrandDark, onPrimary = Color(0xFF00201C),
            primaryContainer = Color(0xFF00504A), onPrimaryContainer = Color(0xFFB2DFDB),
            secondary = BrandDark, onSecondary = Color(0xFF00201C),
            secondaryContainer = Color(0xFF1F4D48), onSecondaryContainer = Color(0xFFB2DFDB),
            tertiary = BrandDark,
            background = Color(0xFF101414), onBackground = Color(0xFFE0E3E2),
            surface = Color(0xFF101414), onSurface = Color(0xFFE0E3E2),
            surfaceVariant = Color(0xFF3F4947), onSurfaceVariant = Color(0xFFBEC9C6),
            surfaceContainerLowest = Color(0xFF0B0F0F), surfaceContainerLow = Color(0xFF181C1C),
            surfaceContainer = Color(0xFF1C2020), surfaceContainerHigh = Color(0xFF262B2A),
            surfaceContainerHighest = Color(0xFF313635),
            outline = Color(0xFF899391), outlineVariant = Color(0xFF3F4947),
        )
    } else {
        lightColorScheme(
            primary = Brand, onPrimary = Color.White,
            primaryContainer = Color(0xFFB2DFDB), onPrimaryContainer = Color(0xFF00201C),
            secondary = Brand, onSecondary = Color.White,
            secondaryContainer = Color(0xFFCCE8E4), onSecondaryContainer = Color(0xFF00201C),
            tertiary = Brand,
            background = Color(0xFFF7FAF9), onBackground = Color(0xFF181C1C),
            surface = Color(0xFFF7FAF9), onSurface = Color(0xFF181C1C),
            surfaceVariant = Color(0xFFDAE5E2), onSurfaceVariant = Color(0xFF3F4947),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1F4F3),
            surfaceContainer = Color(0xFFEBEFED), surfaceContainerHigh = Color(0xFFE5E9E7),
            surfaceContainerHighest = Color(0xFFE0E3E2),
            outline = Color(0xFF6F7977), outlineVariant = Color(0xFFBEC9C6),
        )
    }
    CompositionLocalProvider(LocalDark provides dark) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

@Composable
fun expenseColor(): Color = if (LocalDark.current) Color(0xFFEF9A9A) else Color(0xFFC62828)

@Composable
fun incomeColor(): Color = if (LocalDark.current) Color(0xFFA5D6A7) else Color(0xFF2E7D32)

private val builtinLabels = mapOf(
    "groceries" to R.string.cat_groceries,
    "eating_out" to R.string.cat_eating_out,
    "transport" to R.string.cat_transport,
    "housing" to R.string.cat_housing,
    "bills" to R.string.cat_bills,
    "health" to R.string.cat_health,
    "shopping" to R.string.cat_shopping,
    "leisure" to R.string.cat_leisure,
    "travel" to R.string.cat_travel,
    "other_expense" to R.string.cat_other,
    "salary" to R.string.cat_salary,
    "gift" to R.string.cat_gift,
    "refund" to R.string.cat_refund,
    "other_income" to R.string.cat_other,
    "cash" to R.string.acc_cash,
)

private fun label(resources: Resources, name: String?, builtin: String?): String =
    name ?: builtinLabels[builtin]?.let(resources::getString) ?: builtin.orEmpty()

/** The person's own name, or the built-in name in the phone's language. */
fun Category.label(context: Context): String = label(context.resources, name, builtin)
fun Account.label(context: Context): String = label(context.resources, name, builtin)

/** The same, for code that holds only the resources of the chosen language (the ViewModel). */
fun Category.label(resources: Resources): String = label(resources, name, builtin)
fun Account.label(resources: Resources): String = label(resources, name, builtin)

fun formatMoney(cents: Long): String =
    NumberFormat.getCurrencyInstance(Locale.getDefault())
        .apply { currency = Currency.getInstance("EUR") }
        .format(cents / 100.0)

fun formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault()))

fun formatMonth(month: YearMonth): String =
    month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }

@Composable
fun MonthHeader(vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { vm.month = vm.month.minusMonths(1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_month))
        }
        Text(formatMonth(vm.month), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { vm.month = vm.month.plusMonths(1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_month))
        }
    }
}

@Composable
fun CategoryBadge(category: Category?, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).background(Color(category?.color ?: 0xFF888888.toInt()).copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(category?.emoji.orEmpty(), fontSize = (size.value * 0.45f).sp)
    }
}
