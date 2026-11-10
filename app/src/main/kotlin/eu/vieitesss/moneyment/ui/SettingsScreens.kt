package eu.vieitesss.moneyment.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import android.net.Uri
import eu.vieitesss.moneyment.R
import eu.vieitesss.moneyment.domain.Account
import eu.vieitesss.moneyment.domain.Category
import eu.vieitesss.moneyment.domain.Kind
import eu.vieitesss.moneyment.domain.PALETTE
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreen(
    vm: AppViewModel,
    title: String,
    onAdd: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = vm::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            if (onAdd != null) {
                FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, stringResource(R.string.add)) }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) { content() }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var themeDialog by remember { mutableStateOf(false) }
    var languageDialog by remember { mutableStateOf(false) }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(vm::exportBackup)
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        it?.let(vm::exportCsv)
    }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { importUri = it }

    SubScreen(vm, stringResource(R.string.settings)) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Section(stringResource(R.string.appearance))
            Entry(stringResource(R.string.theme), themeName(vm.theme)) { themeDialog = true }
            Entry(stringResource(R.string.language), languageName(vm.language)) { languageDialog = true }
            HorizontalDivider()
            Section(stringResource(R.string.updates))
            Entry(
                stringResource(R.string.check_for_updates),
                stringResource(R.string.version_name, vm.installedVersion),
            ) { vm.checkForUpdates(manual = true) }
            SwitchEntry(stringResource(R.string.check_automatically), vm.autoUpdates, vm::chooseAutoUpdates)
            HorizontalDivider()
            Section(stringResource(R.string.categories_and_accounts))
            Entry(stringResource(R.string.expense_categories)) { vm.open(Screen.Categories(Kind.EXPENSE)) }
            Entry(stringResource(R.string.income_categories)) { vm.open(Screen.Categories(Kind.INCOME)) }
            Entry(stringResource(R.string.accounts)) { vm.open(Screen.Accounts) }
            HorizontalDivider()
            Section(stringResource(R.string.backup))
            Entry(stringResource(R.string.export_backup), stringResource(R.string.export_backup_hint)) {
                exportBackup.launch("moneyment-${LocalDate.now()}.json")
            }
            Entry(stringResource(R.string.import_backup), stringResource(R.string.import_backup_hint)) {
                pickBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
            Entry(stringResource(R.string.export_csv), stringResource(R.string.export_csv_hint)) {
                exportCsv.launch("moneyment-${LocalDate.now()}.csv")
            }
        }
    }

    importUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { importUri = null },
            title = { Text(stringResource(R.string.import_confirm_title)) },
            text = { Text(stringResource(R.string.import_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { vm.importBackup(uri); importUri = null }) {
                    Text(stringResource(R.string.replace))
                }
            },
            dismissButton = {
                TextButton(onClick = { importUri = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (themeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.theme),
            options = ThemeChoice.entries.map { it to themeName(it) },
            selected = vm.theme,
            onPick = vm::chooseTheme,
            onDismiss = { themeDialog = false },
        )
    }

    if (languageDialog) {
        ChoiceDialog(
            title = stringResource(R.string.language),
            options = LanguageChoice.entries.map { it to languageName(it) },
            selected = vm.language,
            onPick = vm::chooseLanguage,
            onDismiss = { languageDialog = false },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        Modifier.padding(start = 16.dp, top = 16.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun themeName(choice: ThemeChoice): String = stringResource(
    when (choice) {
        ThemeChoice.SYSTEM -> R.string.system_default
        ThemeChoice.LIGHT -> R.string.light
        ThemeChoice.DARK -> R.string.dark
    },
)

/** Both language names stay in their own language, whatever the app shows now. */
@Composable
private fun languageName(choice: LanguageChoice): String = stringResource(
    when (choice) {
        LanguageChoice.SYSTEM -> R.string.system_default
        LanguageChoice.ENGLISH -> R.string.language_english
        LanguageChoice.SPANISH -> R.string.language_spanish
    },
)

/** One radio row per choice; picking one applies it and closes the dialog. */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { (choice, label) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = choice == selected, role = Role.RadioButton) {
                                onPick(choice)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = choice == selected,
                            onClick = null,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun Entry(title: String, hint: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = hint?.let { { Text(it) } },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** A setting that is on or off; the whole row toggles it, not only the switch. */
@Composable
private fun SwitchEntry(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.minimumInteractiveComponentSize(),
            )
        },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
fun CategoriesScreen(vm: AppViewModel, kind: Kind) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Category?>(null) }
    val categories = vm.data.categories.filter { it.kind == kind }
    val title = if (kind == Kind.EXPENSE) R.string.expense_categories else R.string.income_categories
    val activeCount = categories.count { !it.archived }

    SubScreen(
        vm, stringResource(title),
        onAdd = { editing = Category(0, kind, null, "", "🏷️", PALETTE[categories.size % PALETTE.size], false, 0) },
    ) {
        ArchivableList(
            items = categories,
            archived = { it.archived },
            key = { it.id },
        ) { category ->
            ListItem(
                leadingContent = { CategoryBadge(category) },
                headlineContent = { Text(category.label(context)) },
                modifier = Modifier.clickable { editing = category }.alpha(if (category.archived) 0.5f else 1f),
            )
        }
    }

    editing?.let { category ->
        NameDialog(
            title = stringResource(if (category.id == 0L) R.string.new_category else R.string.edit_category),
            initialName = category.label(context),
            initialEmoji = category.emoji,
            initialColor = category.color,
            archived = category.archived,
            // The last Category of a kind stays, or the form could never be saved.
            canArchive = category.id != 0L && (category.archived || activeCount > 1),
            onDismiss = { editing = null },
            onArchive = { vm.saveCategory(category.copy(archived = !category.archived)); editing = null },
            onSave = { name, emoji, color ->
                val renamed = if (name == category.label(context)) category.name else name
                vm.saveCategory(category.copy(name = renamed, emoji = emoji, color = color))
                editing = null
            },
        )
    }
}

@Composable
fun AccountsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Account?>(null) }
    val activeCount = vm.data.accounts.count { !it.archived }

    SubScreen(vm, stringResource(R.string.accounts), onAdd = { editing = Account(0, null, "", false) }) {
        ArchivableList(
            items = vm.data.accounts,
            archived = { it.archived },
            key = { it.id },
        ) { account ->
            ListItem(
                headlineContent = { Text(account.label(context)) },
                modifier = Modifier.clickable { editing = account }.alpha(if (account.archived) 0.5f else 1f),
            )
        }
    }

    editing?.let { account ->
        NameDialog(
            title = stringResource(if (account.id == 0L) R.string.new_account else R.string.edit_account),
            initialName = account.label(context),
            initialEmoji = null,
            initialColor = null,
            archived = account.archived,
            // Every Transaction needs an Account, so the last one stays.
            canArchive = account.id != 0L && (account.archived || activeCount > 1),
            onDismiss = { editing = null },
            onArchive = { vm.saveAccount(account.copy(archived = !account.archived)); editing = null },
            onSave = { name, _, _ ->
                val renamed = if (name == account.label(context)) account.name else name
                vm.saveAccount(account.copy(name = renamed))
                editing = null
            },
        )
    }
}

@Composable
private fun <T> ArchivableList(
    items: List<T>,
    archived: (T) -> Boolean,
    key: (T) -> Any,
    row: @Composable (T) -> Unit,
) {
    val (old, active) = items.partition(archived)
    LazyColumn(Modifier.fillMaxSize()) {
        items(active, key = key) { row(it) }
        if (old.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.archived),
                    Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(old, key = key) { row(it) }
        }
    }
}

private val paletteColorLabels = mapOf(
    0xFF2E7D32.toInt() to R.string.color_green,
    0xFFEF6C00.toInt() to R.string.color_orange,
    0xFF1565C0.toInt() to R.string.color_blue,
    0xFF6D4C41.toInt() to R.string.color_brown,
    0xFF546E7A.toInt() to R.string.color_blue_grey,
    0xFFC62828.toInt() to R.string.color_red,
    0xFFAD1457.toInt() to R.string.color_pink,
    0xFF6A1B9A.toInt() to R.string.color_purple,
    0xFF00838F.toInt() to R.string.color_cyan,
    0xFF9E9D24.toInt() to R.string.color_olive,
    0xFF4527A0.toInt() to R.string.color_deep_purple,
    0xFF00695C.toInt() to R.string.color_teal,
)

/** Dialog for a Category (with emoji and colour) or an Account (name only). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameDialog(
    title: String,
    initialName: String,
    initialEmoji: String?,
    initialColor: Int?,
    archived: Boolean,
    canArchive: Boolean,
    onDismiss: () -> Unit,
    onArchive: () -> Unit,
    onSave: (name: String, emoji: String, color: Int) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var emoji by remember { mutableStateOf(initialEmoji.orEmpty()) }
    var color by remember { mutableIntStateOf(initialColor ?: 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                if (initialEmoji != null) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { if (it.length <= 8) emoji = it },
                        label = { Text(stringResource(R.string.icon)) },
                        singleLine = true,
                    )
                }
                if (initialColor != null) {
                    FlowRow(
                        modifier = Modifier.selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PALETTE.forEach { option ->
                            val colorLabel = stringResource(paletteColorLabels.getValue(option))
                            Box(
                                Modifier.size(32.dp)
                                    .background(Color(option), CircleShape)
                                    .border(
                                        if (option == color) 3.dp else 0.dp,
                                        if (option == color) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        CircleShape,
                                    )
                                    .selectable(selected = option == color, role = Role.RadioButton) { color = option }
                                    .semantics { contentDescription = colorLabel },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), emoji.trim(), color) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canArchive) {
                    TextButton(onClick = onArchive) {
                        Text(stringResource(if (archived) R.string.restore else R.string.archive))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
