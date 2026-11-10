package eu.vieitesss.moneyment.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import eu.vieitesss.moneyment.R
import eu.vieitesss.moneyment.domain.Kind
import eu.vieitesss.moneyment.domain.isAmountInput
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The form for a new Transaction (a Draft) or for editing a saved one.
 * [onSecondary] clears a Draft or deletes the saved Transaction.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FormScreen(
    vm: AppViewModel,
    form: FormState,
    isEdit: Boolean,
    onChange: (FormState) -> Unit,
    onSave: () -> Unit,
    onSecondary: () -> Unit,
) {
    val context = LocalContext.current
    val expense = form.kind == Kind.EXPENSE
    val title = when {
        isEdit && expense -> R.string.edit_expense
        isEdit -> R.string.edit_income
        expense -> R.string.new_expense
        else -> R.string.new_income
    }
    val amountFocus = remember { FocusRequester() }
    if (!isEdit) LaunchedEffect(Unit) { amountFocus.requestFocus() }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = vm::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (isEdit) {
                        IconButton(onClick = onSecondary) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete))
                        }
                    } else {
                        TextButton(onClick = onSecondary) { Text(stringResource(R.string.clear)) }
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = onSave,
                enabled = form.canSave,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(52.dp),
            ) {
                Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium)
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = form.amountText,
                onValueChange = { if (isAmountInput(it)) onChange(form.copy(amountText = it)) },
                label = { Text(stringResource(R.string.amount)) },
                suffix = { Text("€") },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().focusRequester(amountFocus),
            )

            Label(R.string.category)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.data.categories
                    .filter { it.kind == form.kind && (!it.archived || it.id == form.categoryId) }
                    .forEach { category ->
                        FilterChip(
                            selected = category.id == form.categoryId,
                            onClick = { onChange(form.copy(categoryId = category.id)) },
                            label = { Text("${category.emoji} ${category.label(context)}") },
                        )
                    }
            }

            Label(R.string.account)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.data.accounts
                    .filter { !it.archived || it.id == form.accountId }
                    .forEach { account ->
                        FilterChip(
                            selected = account.id == form.accountId,
                            onClick = { onChange(form.copy(accountId = account.id)) },
                            label = { Text(account.label(context)) },
                        )
                    }
            }

            Label(R.string.date)
            DateField(form.date) { onChange(form.copy(date = it)) }

            OutlinedTextField(
                value = form.note,
                onValueChange = { onChange(form.copy(note = it)) },
                label = { Text(stringResource(R.string.note_optional)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun Label(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(date: LocalDate, onPick: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val today = if (date == LocalDate.now()) " · " + stringResource(R.string.today) else ""
    OutlinedCard(Modifier.fillMaxWidth().clickable { open = true }) {
        Text("📅  " + formatDate(date) + today, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DatePicker(state)
        }
    }
}
