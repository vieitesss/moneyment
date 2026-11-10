package eu.vieitesss.moneyment.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.vieitesss.moneyment.R
import eu.vieitesss.moneyment.domain.Kind
import eu.vieitesss.moneyment.domain.Totals
import eu.vieitesss.moneyment.domain.Transaction
import eu.vieitesss.moneyment.domain.groupByDay
import eu.vieitesss.moneyment.domain.monthTransactions
import eu.vieitesss.moneyment.domain.totals
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: AppViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { vm.open(Screen.Settings) }) {
                        Icon(Icons.Default.Settings, stringResource(R.string.settings))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = vm.tab == Tab.TRANSACTIONS,
                    onClick = { vm.tab = Tab.TRANSACTIONS },
                    icon = { Text("🧾") },
                    label = { Text(stringResource(R.string.tab_transactions)) },
                )
                NavigationBarItem(
                    selected = vm.tab == Tab.CHARTS,
                    onClick = { vm.tab = Tab.CHARTS },
                    icon = { Text("📊") },
                    label = { Text(stringResource(R.string.tab_charts)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (vm.tab) {
                Tab.TRANSACTIONS -> TransactionsTab(vm)
                Tab.CHARTS -> ChartsTab(vm)
            }
        }
    }
}

@Composable
private fun TransactionsTab(vm: AppViewModel) {
    val list = remember(vm.data, vm.month, vm.filterCategory, vm.filterAccount) {
        monthTransactions(vm.data.transactions, vm.month, vm.filterCategory, vm.filterAccount)
    }
    val days = remember(list) { groupByDay(list) }
    val listState = rememberLazyListState()
    val reveal = vm.revealId
    LaunchedEffect(reveal, days) {
        val index = reveal?.let { revealIndex(days, it) } ?: return@LaunchedEffect
        listState.animateScrollToItem(index)
        vm.revealed()
    }

    Column(Modifier.fillMaxSize()) {
        MonthHeader(vm)
        TotalsCard(totals(list))
        Filters(vm)
        Box(
            Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        if (dragged > 120f) vm.month = vm.month.minusMonths(1)
                        if (dragged < -120f) vm.month = vm.month.plusMonths(1)
                    },
                ) { _, amount -> dragged += amount }
            },
        ) {
            if (list.isEmpty()) {
                Text(
                    stringResource(R.string.no_transactions),
                    Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    days.forEach { (date, transactions) ->
                        item(key = "day-$date") {
                            Text(
                                formatDate(date),
                                Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(transactions, key = { it.id }) { TransactionRow(vm, it) }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LogButton(expenseColor(), Modifier.weight(1f), onClick = { vm.openNew(Kind.EXPENSE) }) {
                Icon(painterResource(R.drawable.ic_minus), stringResource(R.string.new_expense))
            }
            LogButton(incomeColor(), Modifier.weight(1f), onClick = { vm.openNew(Kind.INCOME) }) {
                Icon(Icons.Default.Add, stringResource(R.string.new_income))
            }
        }
    }
}

/** Position of the Transaction in the LazyColumn, counting the day headers above it. */
private fun revealIndex(days: List<Pair<LocalDate, List<Transaction>>>, id: Long): Int? {
    var index = 0
    for ((_, transactions) in days) {
        index++
        val position = transactions.indexOfFirst { it.id == id }
        if (position >= 0) return index + position
        index += transactions.size
    }
    return null
}

/** One of the two big buttons at the bottom; the icon carries the words for TalkBack. */
@Composable
private fun LogButton(
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        icon()
    }
}

@Composable
private fun TotalsCard(totals: Totals) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Total(stringResource(R.string.income), formatMoney(totals.income), incomeColor())
            Total(stringResource(R.string.expenses), formatMoney(totals.expenses), expenseColor())
            Total(
                stringResource(R.string.difference),
                formatMoney(totals.difference),
                MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun Total(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Filters(vm: AppViewModel) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val category = vm.filterCategory?.let(vm.data::category)
        FilterMenu(
            selected = category != null,
            label = category?.let { "${it.emoji} ${it.label(context)}" } ?: stringResource(R.string.category),
            allLabel = stringResource(R.string.all_categories),
            options = vm.data.categories.map { it.id to "${it.emoji} ${it.label(context)}" },
            onSelect = { vm.filterCategory = it },
        )
        val account = vm.filterAccount?.let(vm.data::account)
        FilterMenu(
            selected = account != null,
            label = account?.label(context) ?: stringResource(R.string.account),
            allLabel = stringResource(R.string.all_accounts),
            options = vm.data.accounts.map { it.id to it.label(context) },
            onSelect = { vm.filterAccount = it },
        )
    }
}

@Composable
private fun FilterMenu(
    selected: Boolean,
    label: String,
    allLabel: String,
    options: List<Pair<Long, String>>,
    onSelect: (Long?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = selected, onClick = { open = true }, label = { Text("$label ▾") })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(allLabel) }, onClick = { onSelect(null); open = false })
            options.forEach { (id, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(id); open = false })
            }
        }
    }
}

@Composable
private fun TransactionRow(vm: AppViewModel, transaction: Transaction) {
    val context = LocalContext.current
    val category = vm.data.category(transaction.categoryId)
    val account = vm.data.account(transaction.accountId)
    val expense = transaction.kind == Kind.EXPENSE
    Row(
        Modifier.fillMaxWidth().clickable { vm.openEdit(transaction) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(category)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(category?.label(context).orEmpty(), style = MaterialTheme.typography.bodyLarge)
            Text(
                listOf(account?.label(context).orEmpty(), transaction.note).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            (if (expense) "−" else "+") + formatMoney(transaction.amountCents),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (expense) expenseColor() else incomeColor(),
        )
    }
}
