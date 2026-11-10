package eu.vieitesss.moneyment.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.vieitesss.moneyment.BuildConfig
import eu.vieitesss.moneyment.R
import eu.vieitesss.moneyment.data.Store
import eu.vieitesss.moneyment.domain.Account
import eu.vieitesss.moneyment.domain.Category
import eu.vieitesss.moneyment.domain.Kind
import eu.vieitesss.moneyment.domain.Snapshot
import eu.vieitesss.moneyment.domain.Transaction
import eu.vieitesss.moneyment.domain.centsToText
import eu.vieitesss.moneyment.domain.decodeBackup
import eu.vieitesss.moneyment.domain.defaultAccountId
import eu.vieitesss.moneyment.domain.encodeBackup
import eu.vieitesss.moneyment.domain.encodeCsv
import eu.vieitesss.moneyment.domain.parseAmount
import eu.vieitesss.moneyment.update.INSTALL_STATUS
import eu.vieitesss.moneyment.update.Release
import eu.vieitesss.moneyment.update.SNOOZE_MS
import eu.vieitesss.moneyment.update.UpdateManager
import eu.vieitesss.moneyment.update.checkDue
import eu.vieitesss.moneyment.update.isNewer
import eu.vieitesss.moneyment.update.isSnoozed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.YearMonth

enum class Tab { TRANSACTIONS, CHARTS }

sealed interface Screen {
    data class New(val kind: Kind) : Screen
    data object Edit : Screen
    data object Settings : Screen
    data class Categories(val kind: Kind) : Screen
    data object Accounts : Screen
}

/** The fields of the Transaction form as the person typed them. */
data class FormState(
    val kind: Kind,
    val amountText: String,
    val categoryId: Long?,
    val accountId: Long,
    val date: LocalDate,
    val note: String,
) {
    val canSave: Boolean get() = parseAmount(amountText) != null && categoryId != null
}

data class Editing(val original: Transaction, val initial: FormState, val form: FormState) {
    val dirty: Boolean get() = form != initial
}

/**
 * [form] after an Archive: a Draft cannot keep a choice that can no longer be made, so an
 * archived Category becomes no choice at all and an archived Account the default one.
 */
fun repairedDraft(form: FormState, data: Snapshot, lastUsedAccountId: Long?): FormState = form.copy(
    categoryId = if (data.category(form.categoryId ?: 0L)?.archived == true) null else form.categoryId,
    accountId = if (data.account(form.accountId)?.archived == true) {
        defaultAccountId(data.accounts, data.transactions, lastUsedAccountId)
    } else {
        form.accountId
    },
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val prefs = Prefs(app)
    private val updater = UpdateManager(app)
    private val appContext = getApplication<Application>()

    var theme by mutableStateOf(prefs.theme())
        private set
    var language by mutableStateOf(prefs.language())
        private set

    /**
     * The resources of the chosen language, for the messages and CSV labels this ViewModel
     * builds itself: the Application keeps the phone's language otherwise.
     */
    private var strings = localizedContext(app, language).resources

    var data by mutableStateOf(Snapshot.EMPTY)
        private set
    var loaded by mutableStateOf(false)
        private set

    var tab by mutableStateOf(Tab.TRANSACTIONS)
    val stack = mutableStateListOf<Screen>()
    var month: YearMonth by mutableStateOf(YearMonth.now())
    var filterCategory by mutableStateOf<Long?>(null)
    var filterAccount by mutableStateOf<Long?>(null)

    /** Drafts live here only: they are gone when Android ends the app's process. */
    val drafts = mutableStateMapOf<Kind, FormState>()
    var editing by mutableStateOf<Editing?>(null)
    var confirmDiscard by mutableStateOf(false)
    var confirmDelete by mutableStateOf(false)

    /** The Transaction the list scrolls to after a save; the list clears it once shown. */
    var revealId by mutableStateOf<Long?>(null)
        private set

    /** The Account of the most recent successful save; gone when Android ends the process. */
    private var lastUsedAccountId: Long? = null

    val snackbar = SnackbarHostState()
    private var pendingNew: Kind? = null

    /** True while a Transaction is being written, so a second tap on Save does nothing. */
    private var saving = false

    /** The version of the installed APK, as built. */
    val installedVersion: String = BuildConfig.VERSION_NAME

    /** The newer release the dialog offers, or null when no dialog is showing. */
    var offeredRelease by mutableStateOf<Release?>(null)
        private set

    /** True from tapping Update until Android takes the install over. */
    var installing by mutableStateOf(false)
        private set

    /** How much of the APK has arrived, 0..1, or -1 while its size is unknown. */
    var downloadProgress by mutableFloatStateOf(-1f)
        private set

    var autoUpdates by mutableStateOf(prefs.autoUpdates())
        private set

    /** Set while the person is away in the phone setting that allows installs. */
    private var waitingForInstallPermission = false

    /** How the install ended, sent back by the phone to [INSTALL_STATUS]. */
    private val installStatus = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    // The download is over: from here the phone's own screen takes the decision.
                    installing = false
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    if (confirm == null) updateFailed() else appContext.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                PackageInstaller.STATUS_SUCCESS -> {
                    installing = false
                    updater.apkFile().delete()
                }
                else -> updateFailed()
            }
        }
    }

    // Kotlin sets up properties in source order, and init starts coroutines that run at once on
    // the main thread: every property they read must be declared above this block.
    init {
        registerInstallStatus()
        viewModelScope.launch {
            data = withContext(Dispatchers.IO) {
                // An APK left over from an install Android never finished is worthless.
                updater.apkFile().delete()
                store.snapshot()
            }
            loaded = true
            pendingNew?.let { openNew(it) }
        }
    }

    // Navigation

    fun open(screen: Screen) {
        stack.add(screen)
    }

    /** Back from the top screen. Leaving an edit with changes needs a confirmation. */
    fun back() {
        if (stack.lastOrNull() == Screen.Edit && editing?.dirty == true) {
            confirmDiscard = true
        } else {
            pop()
        }
    }

    fun discardEdit() {
        confirmDiscard = false
        pop()
    }

    private fun pop() {
        if (stack.removeLastOrNull() == Screen.Edit) editing = null
    }

    // Settings

    fun chooseTheme(choice: ThemeChoice) {
        theme = choice
        prefs.theme(choice)
    }

    /** The Activity recreates itself so every screen picks up the new language. */
    fun chooseLanguage(choice: LanguageChoice) {
        language = choice
        prefs.language(choice)
        strings = localizedContext(getApplication(), choice).resources
    }

    // Updates

    fun chooseAutoUpdates(on: Boolean) {
        autoUpdates = on
        prefs.autoUpdates(on)
    }

    /**
     * Asks GitHub for the newest release. An automatic check happens at most once a day, only
     * while the switch is on, and says nothing at all when offline or on any error. A check the
     * person asked for skips the day, the snooze, and the silence.
     */
    fun checkForUpdates(manual: Boolean) {
        viewModelScope.launch {
            if (!manual) {
                if (!autoUpdates) return@launch
                if (!checkDue(prefs.lastCheck(), System.currentTimeMillis())) return@launch
                // The attempt counts before it is made: leaving mid-request must not earn a new
                // day, just as a check that failed already counts.
                prefs.checked(System.currentTimeMillis())
            }
            val release = withContext(Dispatchers.IO) { updater.latestRelease() }
            if (manual) prefs.checked(System.currentTimeMillis())
            if (release == null) {
                if (manual) message(R.string.update_check_failed)
                return@launch
            }
            if (!isNewer(release.version, installedVersion)) {
                if (manual) message(R.string.update_none)
                return@launch
            }
            val snoozed = !manual && isSnoozed(
                release.version, prefs.snoozedVersion(), prefs.snoozedUntil(), System.currentTimeMillis(),
            )
            if (!snoozed) offeredRelease = release
        }
    }

    /** "Later": this version hides for a day, though asking by hand still finds it. */
    fun later() {
        offeredRelease?.let { prefs.snooze(it.version, System.currentTimeMillis() + SNOOZE_MS) }
        offeredRelease = null
    }

    /** "Update": download the APK, then hand it to the phone's own installer. */
    fun update() {
        val release = offeredRelease ?: return
        if (installing) return
        offeredRelease = null
        installing = true
        downloadProgress = -1f
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching { updater.download(release) { fraction -> downloadProgress = fraction ?: -1f } }
            }.getOrNull()
            if (file == null) return@launch updateFailed()
            if (updater.canInstall()) startInstall(file) else askToInstall()
        }
    }

    /** Called when the app comes back to the front: after the phone setting, carry on installing. */
    fun installWhenAllowed() {
        if (!waitingForInstallPermission) return
        waitingForInstallPermission = false
        val file = updater.apkFile()
        if (updater.canInstall() && file.exists()) {
            viewModelScope.launch { startInstall(file) }
        } else {
            updateFailed()
        }
    }

    private suspend fun startInstall(file: File) {
        val started = withContext(Dispatchers.IO) { runCatching { updater.install(file) }.isSuccess }
        // Otherwise the phone now owns the copy and shows its own confirmation; the answer follows.
        if (!started) updateFailed()
    }

    private fun askToInstall() {
        waitingForInstallPermission = true
        updater.askToInstall()
    }

    private fun updateFailed() {
        installing = false
        waitingForInstallPermission = false
        updater.apkFile().delete()
        message(R.string.update_failed)
    }

    private fun registerInstallStatus() {
        ContextCompat.registerReceiver(
            appContext, installStatus, IntentFilter(INSTALL_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onCleared() {
        appContext.unregisterReceiver(installStatus)
    }

    // New Transactions and their Drafts

    fun openNew(kind: Kind) {
        if (!loaded) {
            pendingNew = kind
            return
        }
        pendingNew = null
        if (kind !in drafts) drafts[kind] = freshForm(kind)
        if (stack.lastOrNull() != Screen.New(kind)) stack.add(Screen.New(kind))
    }

    fun updateDraft(form: FormState) {
        drafts[form.kind] = form
    }

    fun clearDraft(kind: Kind) {
        val old = drafts[kind] ?: return
        drafts[kind] = freshForm(kind)
        // A Category or Account archived meanwhile cannot come back with the Undo.
        withUndo(R.string.form_cleared) { drafts[kind] = repairedDraft(old, data, lastUsedAccountId) }
    }

    fun saveDraft(kind: Kind) {
        val form = drafts[kind] ?: return
        val transaction = form.toTransaction(0) ?: return
        saveTransaction(transaction) { drafts.remove(kind) }
    }

    private fun freshForm(kind: Kind) = FormState(kind, "", null, preselectedAccountId(), LocalDate.now(), "")

    /** The Account a new Draft preselects: the last used one when it can still be chosen. */
    private fun preselectedAccountId() = defaultAccountId(data.accounts, data.transactions, lastUsedAccountId)

    // Editing saved Transactions

    fun openEdit(transaction: Transaction) {
        val separator = DecimalFormatSymbols.getInstance().decimalSeparator
        val form = FormState(
            transaction.kind, centsToText(transaction.amountCents, separator),
            transaction.categoryId, transaction.accountId, transaction.date, transaction.note,
        )
        editing = Editing(transaction, form, form)
        stack.add(Screen.Edit)
    }

    fun updateEdit(form: FormState) {
        editing = editing?.copy(form = form)
    }

    fun saveEdit() {
        val current = editing ?: return
        val transaction = current.form.toTransaction(current.original.id) ?: return
        saveTransaction(transaction) { editing = null }
    }

    /**
     * Writes [transaction], then leaves the form and shows it in the list. A failed write
     * says so and leaves the form as it is, so nothing the person typed is lost.
     */
    private fun saveTransaction(transaction: Transaction, onSaved: () -> Unit) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { store.save(transaction) to store.snapshot() }
            }.getOrNull()
            saving = false
            if (result == null) {
                message(R.string.save_failed)
                return@launch
            }
            val (savedId, fresh) = result
            data = fresh
            lastUsedAccountId = transaction.accountId
            stack.clear()
            onSaved()
            showInList(transaction.copy(id = savedId))
        }
    }

    /** Asks before anything is deleted; [deleteEditing] does the work. */
    fun askDelete() {
        if (editing != null) confirmDelete = true
    }

    fun deleteEditing() {
        confirmDelete = false
        val transaction = editing?.original ?: return
        pop()
        mutate { deleteTransaction(transaction.id) }
        withUndo(R.string.transaction_deleted) { mutate { save(transaction) } }
    }

    private fun FormState.toTransaction(id: Long): Transaction? {
        val cents = parseAmount(amountText) ?: return null
        return Transaction(id, kind, cents, categoryId ?: return null, accountId, date, note.trim())
    }

    /** Moves the list to where [transaction] is visible. */
    private fun showInList(transaction: Transaction) {
        tab = Tab.TRANSACTIONS
        month = YearMonth.from(transaction.date)
        if (filterCategory != null && filterCategory != transaction.categoryId) filterCategory = null
        if (filterAccount != null && filterAccount != transaction.accountId) filterAccount = null
        revealId = transaction.id
    }

    /** The list scrolled to [revealId]. */
    fun revealed() {
        revealId = null
    }

    // Categories and Accounts

    fun saveCategory(category: Category) {
        val position = if (category.id == 0L) {
            (data.categories.filter { it.kind == category.kind }.maxOfOrNull { it.position } ?: -1) + 1
        } else {
            category.position
        }
        val saved = category.copy(position = position)
        mutate(after = { if (saved.archived) repairDrafts() }) { save(saved) }
    }

    fun saveAccount(account: Account) {
        mutate(after = { if (account.archived) repairDrafts() }) { save(account) }
    }

    /**
     * An archived Category or Account can no longer be chosen, so a Draft that held one drops
     * it, as [repairedDraft] decides. Edits of saved Transactions keep their archived choices.
     */
    private fun repairDrafts() {
        drafts.keys.toList().forEach { kind ->
            val form = drafts[kind] ?: return@forEach
            drafts[kind] = repairedDraft(form, data, lastUsedAccountId)
        }
    }

    // Backup

    fun exportBackup(uri: Uri) = export(uri) { encodeBackup(it) }

    fun exportCsv(uri: Uri) {
        val resources = strings
        export(uri) { snapshot -> encodeCsv(snapshot, { it.label(resources) }, { it.label(resources) }) }
    }

    private fun export(uri: Uri, encode: (Snapshot) -> String) {
        val snapshot = data
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!
                        .use { it.write(encode(snapshot).toByteArray()) }
                }.isSuccess
            }
            message(if (ok) R.string.export_done else R.string.export_failed)
        }
    }

    /** Replaces all data with the backup file at [uri]. */
    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) {
                runCatching {
                    val text = getApplication<Application>().contentResolver.openInputStream(uri)!!
                        .use { it.readBytes().decodeToString() }
                    store.replaceAll(decodeBackup(text))
                    store.snapshot()
                }.getOrNull()
            }
            if (restored == null) {
                message(R.string.import_failed)
            } else {
                drafts.clear()
                filterCategory = null
                filterAccount = null
                data = restored
                message(R.string.import_done)
            }
        }
    }

    // Helpers

    /** Applies [change] and refreshes the data. A failed write says so and changes nothing. */
    private fun mutate(after: () -> Unit = {}, change: Store.() -> Unit) {
        viewModelScope.launch {
            val fresh = withContext(Dispatchers.IO) {
                runCatching {
                    store.change()
                    store.snapshot()
                }
            }.getOrNull()
            if (fresh == null) {
                message(R.string.save_failed)
            } else {
                data = fresh
                after()
            }
        }
    }

    private fun message(text: Int) {
        viewModelScope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(strings.getString(text))
        }
    }

    private fun withUndo(text: Int, undo: () -> Unit) {
        viewModelScope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = strings.getString(text),
                actionLabel = strings.getString(R.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }
}
