package eu.vieitesss.moneyment.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
import kotlinx.coroutines.launch

/**
 * The form for a new Transaction (a Draft) or for editing a saved one.
 * [onSecondary] clears a Draft or deletes the saved Transaction.
 *
 * Amount and Category, the only fields that can block Save, carry a required mark that turns
 * into a green check once filled. Save is always tappable: with a field missing it saves
 * nothing, shows what is missing in red and moves to the first missing field.
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
    val focusManager = LocalFocusManager.current
    val categoryView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    // Set by a Save tap with a field missing; lasts until Clear or leaving the form.
    var showErrors by remember { mutableStateOf(false) }
    val amountError = showErrors && !form.amountValid
    val categoryError = showErrors && !form.categoryValid

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
                        TextButton(onClick = {
                            showErrors = false
                            onSecondary()
                        }) { Text(stringResource(R.string.clear)) }
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    when {
                        form.canSave -> onSave()
                        !form.amountValid -> {
                            showErrors = true
                            amountFocus.requestFocus()
                        }
                        else -> {
                            showErrors = true
                            focusManager.clearFocus()
                            scope.launch { categoryView.bringIntoView() }
                        }
                    }
                },
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
                label = { RequiredLabel(R.string.amount, form.amountValid, amountError) },
                suffix = { Text("€") },
                isError = amountError,
                supportingText = if (amountError) {
                    { Text(stringResource(R.string.amount_missing)) }
                } else {
                    null
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().focusRequester(amountFocus),
            )

            Row(Modifier.bringIntoViewRequester(categoryView), verticalAlignment = Alignment.CenterVertically) {
                Label(R.string.category, if (categoryError) MaterialTheme.colorScheme.error else null)
                RequiredMark(form.categoryValid, categoryError)
            }
            if (categoryError) {
                Text(
                    stringResource(R.string.category_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
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
private fun Label(text: Int, color: Color? = null) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A text field label followed by its [RequiredMark]; the field colors the text itself. */
@Composable
private fun RequiredLabel(text: Int, filled: Boolean, error: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(text))
        RequiredMark(filled, error)
    }
}

/** An asterisk while a required field is missing, red once Save was tapped; a green check when filled. */
@Composable
private fun RequiredMark(filled: Boolean, error: Boolean) {
    val modifier = Modifier.padding(start = 4.dp).size(14.dp)
    if (filled) {
        Icon(Icons.Default.Check, stringResource(R.string.filled), modifier, tint = filledColor())
    } else {
        val tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(Asterisk, stringResource(R.string.required), modifier, tint = tint)
    }
}

/** Three crossing bars, each 3 wide and 18 long: material-icons-core has no asterisk. */
private val Asterisk: ImageVector by lazy {
    // One path per bar, so overlaps never cancel out under the nonzero fill rule.
    val bars = listOf(
        listOf(10.5f to 3f, 13.5f to 3f, 13.5f to 21f, 10.5f to 21f),
        listOf(3.46f to 8.8f, 4.96f to 6.2f, 20.54f to 15.2f, 19.04f to 17.8f),
        listOf(3.46f to 15.2f, 4.96f to 17.8f, 20.54f to 8.8f, 19.04f to 6.2f),
    )
    ImageVector.Builder("Asterisk", 24.dp, 24.dp, 24f, 24f).apply {
        bars.forEach { corners ->
            path(fill = SolidColor(Color.Black)) {
                moveTo(corners[0].first, corners[0].second)
                corners.drop(1).forEach { (x, y) -> lineTo(x, y) }
                close()
            }
        }
    }.build()
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
