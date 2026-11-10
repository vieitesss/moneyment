package eu.vieitesss.moneyment.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.vieitesss.moneyment.R
import eu.vieitesss.moneyment.domain.Kind

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    /** The chosen language must reach the Activity's own resources, not only the phone's. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localizedContext(newBase, Prefs(newBase).language()))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShortcut(intent)
        setContent { MoneymentTheme(vm.theme) { App(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcut(intent)
    }

    /**
     * Every return to the front, and the first one, asks for an automatic update check. It is
     * guarded: at most once a day, only while the switch is on, silent on errors.
     */
    override fun onStart() {
        super.onStart()
        vm.checkForUpdates(manual = false)
    }

    /** Back from the phone setting that allows installs: an update that waited for it carries on. */
    override fun onResume() {
        super.onResume()
        vm.installWhenAllowed()
    }

    private fun handleShortcut(intent: Intent?) {
        when (intent?.action) {
            "eu.vieitesss.moneyment.NEW_EXPENSE" -> vm.openNew(Kind.EXPENSE)
            "eu.vieitesss.moneyment.NEW_INCOME" -> vm.openNew(Kind.INCOME)
        }
    }
}

@Composable
private fun App(vm: AppViewModel) {
    val activity = LocalActivity.current
    // The language the screens were built with; a new choice rebuilds them all.
    val builtWith = remember { vm.language }
    LaunchedEffect(vm.language) {
        if (vm.language != builtWith) activity?.recreate()
    }

    // A Theme chosen against the phone's own still needs system bar icons that can be read.
    val dark = isDark(vm.theme, isSystemInDarkTheme())
    SideEffect {
        val bars = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        if (activity is ComponentActivity) {
            activity.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        }
    }

    Surface(Modifier.fillMaxSize()) {
        BackHandler(enabled = vm.stack.isNotEmpty()) { vm.back() }
        Box(Modifier.fillMaxSize()) {
            if (vm.loaded) {
                when (val screen = vm.stack.lastOrNull()) {
                    null -> MainScreen(vm)
                    is Screen.New -> vm.drafts[screen.kind]?.let { draft ->
                        FormScreen(
                            vm = vm, form = draft, isEdit = false,
                            onChange = vm::updateDraft,
                            onSave = { vm.saveDraft(screen.kind) },
                            onSecondary = { vm.clearDraft(screen.kind) },
                        )
                    }
                    Screen.Edit -> vm.editing?.let { editing ->
                        FormScreen(
                            vm = vm, form = editing.form, isEdit = true,
                            onChange = vm::updateEdit,
                            onSave = vm::saveEdit,
                            onSecondary = vm::askDelete,
                        )
                    }
                    Screen.Settings -> SettingsScreen(vm)
                    is Screen.Categories -> CategoriesScreen(vm, screen.kind)
                    Screen.Accounts -> AccountsScreen(vm)
                }
            }
            SnackbarHost(
                vm.snackbar,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding(),
            )
        }
        if (vm.confirmDelete) {
            AlertDialog(
                onDismissRequest = { vm.confirmDelete = false },
                title = { Text(stringResource(R.string.delete_title)) },
                confirmButton = {
                    TextButton(onClick = vm::deleteEditing) { Text(stringResource(R.string.delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { vm.confirmDelete = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
        if (vm.confirmDiscard) {
            AlertDialog(
                onDismissRequest = { vm.confirmDiscard = false },
                title = { Text(stringResource(R.string.discard_title)) },
                text = { Text(stringResource(R.string.discard_text)) },
                confirmButton = {
                    TextButton(onClick = vm::discardEdit) { Text(stringResource(R.string.discard)) }
                },
                dismissButton = {
                    TextButton(onClick = { vm.confirmDiscard = false }) {
                        Text(stringResource(R.string.keep_editing))
                    }
                },
            )
        }
        vm.offeredRelease?.let { release ->
            AlertDialog(
                onDismissRequest = vm::later,
                title = { Text(stringResource(R.string.update_available_title, release.version)) },
                confirmButton = {
                    TextButton(onClick = vm::update) { Text(stringResource(R.string.update)) }
                },
                dismissButton = {
                    TextButton(onClick = vm::later) { Text(stringResource(R.string.later)) }
                },
            )
        }
        if (vm.installing) {
            val progress = vm.downloadProgress
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.update_downloading)) },
                text = {
                    if (progress < 0f) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {},
            )
        }
    }
}
