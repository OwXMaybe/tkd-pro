package com.tkdpro.feature.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Scoreboard
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tkdpro.BuildConfig
import com.tkdpro.R
import com.tkdpro.core.database.dao.SyncDao
import com.tkdpro.core.database.entity.AnalysisEngine
import com.tkdpro.core.datastore.AiPermissions
import com.tkdpro.core.datastore.AppSettings
import com.tkdpro.core.datastore.SettingsRepository
import com.tkdpro.core.datastore.ThemeMode
import com.tkdpro.core.datastore.UnitSystem
import com.tkdpro.core.designsystem.component.ChoiceChips
import com.tkdpro.core.designsystem.component.ConfirmDialog
import com.tkdpro.core.designsystem.component.LoadingState
import com.tkdpro.core.designsystem.component.ScreenMessage
import com.tkdpro.core.designsystem.component.SectionHeader
import com.tkdpro.core.designsystem.component.TimeField
import com.tkdpro.core.designsystem.component.TkdCard
import com.tkdpro.core.designsystem.component.TkdTopBar
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.designsystem.theme.TkdTheme
import com.tkdpro.core.network.BackendClient
import com.tkdpro.core.notifications.ReminderScheduler
import com.tkdpro.core.security.SecretStore
import com.tkdpro.core.ui.AppLanguage
import com.tkdpro.core.ui.label
import com.tkdpro.data.repository.DataManagementRepository
import com.tkdpro.data.repository.ProfileRepository
import com.tkdpro.data.sync.SyncEngine
import com.tkdpro.data.sync.SyncScheduler
import com.tkdpro.feature.auth.canUseOwnerLock
import com.tkdpro.feature.auth.promptOwner
import com.tkdpro.feature.profile.NavRow
import com.tkdpro.navigation.DashboardCustomizeRoute
import com.tkdpro.navigation.ScoringPresetsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Testing : ConnectionState
    data class Ok(val aiConfigured: Boolean, val motionAvailable: Boolean) : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

sealed interface PairingState {
    data object Idle : PairingState
    data object Working : PairingState
    data object Done : PairingState
    data class Failed(val message: String) : PairingState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
    private val client: BackendClient,
    private val reminders: ReminderScheduler,
    private val data: DataManagementRepository,
    private val syncScheduler: SyncScheduler,
    private val syncEngine: SyncEngine,
    profile: ProfileRepository,
    syncDao: SyncDao,
) : ViewModel() {
    val state = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val pendingSync = syncDao.observeCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val hasToken = MutableStateFlow(!secrets.get(SecretStore.BACKEND_TOKEN).isNullOrBlank())
    val connection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val message = MutableStateFlow<Int?>(null)
    val isMinor = MutableStateFlow(false)
    val pairing = MutableStateFlow<PairingState>(PairingState.Idle)

    init {
        viewModelScope.launch { isMinor.value = profile.isMinor() }
    }

    fun setTheme(m: ThemeMode) = viewModelScope.launch { settings.setTheme(m) }
    fun setUnits(u: UnitSystem) = viewModelScope.launch { settings.setUnits(u) }
    fun setAppLock(enabled: Boolean) = viewModelScope.launch { settings.setAppLock(enabled) }
    fun setAi(ai: AiPermissions) = viewModelScope.launch { settings.setAiPermissions(ai) }
    fun setAutoPause(v: Boolean) = viewModelScope.launch { settings.setGpsAutoPause(v) }
    fun setKeepScreenOn(v: Boolean) = viewModelScope.launch { settings.setGpsKeepScreenOn(v) }
    fun setAccuracy(v: Int) = viewModelScope.launch { settings.setGpsMaxAccuracy(v) }
    fun setEngine(e: AnalysisEngine) = viewModelScope.launch { settings.setAnalysisEngine(e) }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) = viewModelScope.launch {
        settings.setRecoveryReminder(enabled, hour, minute)
        reminders.apply(enabled, hour, minute)
    }

    fun setSync(enabled: Boolean) = viewModelScope.launch {
        val wasEnabled = settings.current().syncEnabled
        settings.setSyncEnabled(enabled)
        if (enabled && !wasEnabled) syncEngine.enqueueEverything()
        syncScheduler.schedulePeriodic(enabled)
        if (enabled) syncScheduler.schedule(delaySeconds = 0)
    }

    fun syncNow() = syncScheduler.schedule(delaySeconds = 0)

    /** Pairs with TKD PRO on the computer, then uploads everything and starts two-way sync. */
    fun pair(url: String, code: String) = viewModelScope.launch {
        pairing.value = PairingState.Working
        val clean = url.trim().trimEnd('/')
        client.pair(clean, code, "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            .onSuccess { token ->
                if (settings.current().backendUrl != clean) settings.setSyncCursor(0)
                settings.setBackendUrl(clean)
                secrets.put(SecretStore.BACKEND_TOKEN, token)
                hasToken.value = true
                settings.setSyncEnabled(true)
                syncEngine.enqueueEverything()
                syncScheduler.schedulePeriodic(true)
                syncScheduler.schedule(delaySeconds = 0)
                pairing.value = PairingState.Done
            }
            .onFailure { pairing.value = PairingState.Failed(it.message ?: it.javaClass.simpleName) }
    }

    fun saveBackend(url: String, token: String?) = viewModelScope.launch {
        if (settings.current().backendUrl != url.trim().trimEnd('/')) settings.setSyncCursor(0)
        settings.setBackendUrl(url)
        if (token != null) secrets.put(SecretStore.BACKEND_TOKEN, token.trim())
        hasToken.value = !secrets.get(SecretStore.BACKEND_TOKEN).isNullOrBlank()
        connection.value = ConnectionState.Idle
    }

    fun testConnection() = viewModelScope.launch {
        connection.value = ConnectionState.Testing
        client.call { it.health() }
            .onSuccess { connection.value = ConnectionState.Ok(it.aiConfigured, it.motionAvailable) }
            .onFailure { connection.value = ConnectionState.Failed(it.message ?: it.javaClass.simpleName) }
    }

    fun export(uri: android.net.Uri) = viewModelScope.launch {
        runCatching { data.exportTo(uri) }
            .onSuccess { message.value = R.string.settings_export_done }
            .onFailure { message.value = R.string.settings_export_failed }
    }

    fun deleteAll() = viewModelScope.launch {
        data.deleteEverything()
        hasToken.value = false
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onNavigate: (Any) -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val settings = s ?: return LoadingState()
    Scaffold(topBar = { TkdTopBar(stringResource(R.string.settings_title), onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { AppearanceSection(settings, viewModel, onNavigate) }
            item { NotificationSection(settings, viewModel) }
            item { GpsSection(settings, viewModel) }
            item { PrivacySection(settings, viewModel) }
            item { AiSection(settings, viewModel) }
            item { PairSection(settings, viewModel) }
            item { BackendSection(settings, viewModel) }
            item { DataSection(viewModel) }
            item {
                Text(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, description: String? = null, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun AppearanceSection(s: AppSettings, vm: SettingsViewModel, onNavigate: (Any) -> Unit) {
    var language by remember { mutableStateOf(AppLanguage.current()) }
    SectionHeader(stringResource(R.string.settings_appearance))
    TkdCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.labelLarge)
        ChoiceChips(AppLanguage.entries, language, { language = it; AppLanguage.apply(it) }, { stringResource(it.label) })
        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.sm))
        ChoiceChips(ThemeMode.entries, s.themeMode, vm::setTheme, { stringResource(it.label()) })
        Text(stringResource(R.string.settings_units), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.sm))
        ChoiceChips(UnitSystem.entries, s.units, vm::setUnits, { stringResource(it.label()) })
    }
    TkdCard(Modifier.fillMaxWidth().padding(top = Spacing.sm), contentPadding = PaddingValues(vertical = Spacing.xs)) {
        NavRow(Icons.Filled.Dashboard, stringResource(R.string.home_customize)) { onNavigate(DashboardCustomizeRoute) }
        NavRow(Icons.Filled.Scoreboard, stringResource(R.string.scoring_title)) { onNavigate(ScoringPresetsRoute) }
    }
}

@Composable
private fun NotificationSection(s: AppSettings, vm: SettingsViewModel) {
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    SectionHeader(stringResource(R.string.settings_notifications))
    TkdCard(Modifier.fillMaxWidth()) {
        SwitchRow(stringResource(R.string.settings_recovery_reminder), s.recoveryReminderEnabled, { enabled ->
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            vm.setReminder(enabled, s.reminderHour, s.reminderMinute)
        }, stringResource(R.string.settings_recovery_reminder_desc))
        if (s.recoveryReminderEnabled) {
            val zone = ZoneId.systemDefault()
            val ms = LocalDate.now().atTime(s.reminderHour, s.reminderMinute).atZone(zone).toInstant().toEpochMilli()
            TimeField(stringResource(R.string.settings_reminder_time), ms, { v ->
                val t = java.time.Instant.ofEpochMilli(v).atZone(zone)
                vm.setReminder(true, t.hour, t.minute)
            }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun GpsSection(s: AppSettings, vm: SettingsViewModel) {
    SectionHeader(stringResource(R.string.settings_gps))
    TkdCard(Modifier.fillMaxWidth()) {
        SwitchRow(stringResource(R.string.settings_auto_pause), s.gpsAutoPause, vm::setAutoPause, stringResource(R.string.settings_auto_pause_desc))
        SwitchRow(stringResource(R.string.settings_keep_screen_on), s.gpsKeepScreenOn, vm::setKeepScreenOn)
        Text(stringResource(R.string.settings_gps_accuracy, s.gpsMaxAccuracyM), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Spacing.sm))
        var slider by remember(s.gpsMaxAccuracyM) { mutableStateOf(s.gpsMaxAccuracyM.toFloat()) }
        Slider(value = slider, onValueChange = { slider = it }, onValueChangeFinished = { vm.setAccuracy(slider.toInt()) }, valueRange = 10f..50f, steps = 7)
        ScreenMessage(R.string.settings_gps_accuracy_desc)
        ScreenMessage(R.string.settings_gps_privacy)
    }
}

@Composable
private fun PrivacySection(s: AppSettings, vm: SettingsViewModel) {
    val activity = LocalContext.current as FragmentActivity
    val minor by vm.isMinor.collectAsStateWithLifecycle()
    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.settings_lock_confirm)
    var error by remember { mutableStateOf<String?>(null) }
    SectionHeader(stringResource(R.string.settings_privacy))
    TkdCard(Modifier.fillMaxWidth()) {
        val available = remember { canUseOwnerLock(activity) }
        SwitchRow(
            stringResource(R.string.settings_app_lock),
            s.appLockEnabled,
            { enabled -> promptOwner(activity, title, subtitle, onSuccess = { vm.setAppLock(enabled) }, onError = { error = it }) },
            stringResource(if (available) R.string.settings_app_lock_desc else R.string.lock_unavailable),
            enabled = available || s.appLockEnabled,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        ScreenMessage(R.string.settings_privacy_summary)
        if (minor) {
            Text(stringResource(R.string.settings_minor_notice), color = TkdTheme.colors.warning, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.sm))
        }
    }
}

@Composable
private fun AiSection(s: AppSettings, vm: SettingsViewModel) {
    val ai = s.ai
    SectionHeader(stringResource(R.string.settings_ai))
    TkdCard(Modifier.fillMaxWidth()) {
        SwitchRow(stringResource(R.string.settings_ai_enabled), ai.enabled, { vm.setAi(ai.copy(enabled = it)) }, stringResource(R.string.settings_ai_desc))
        if (ai.enabled) {
            Text(stringResource(R.string.settings_ai_categories), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.sm))
            SwitchRow(stringResource(R.string.ai_perm_training), ai.training, { vm.setAi(ai.copy(training = it)) })
            SwitchRow(stringResource(R.string.ai_perm_running), ai.running, { vm.setAi(ai.copy(running = it)) })
            SwitchRow(stringResource(R.string.ai_perm_matches), ai.matches, { vm.setAi(ai.copy(matches = it)) })
            SwitchRow(stringResource(R.string.ai_perm_motion), ai.motion, { vm.setAi(ai.copy(motion = it)) })
            SwitchRow(stringResource(R.string.ai_perm_recovery), ai.recovery, { vm.setAi(ai.copy(recovery = it)) })
            SwitchRow(stringResource(R.string.ai_perm_reaction), ai.reaction, { vm.setAi(ai.copy(reaction = it)) })
            SwitchRow(stringResource(R.string.ai_perm_profile), ai.profile, { vm.setAi(ai.copy(profile = it)) })
            ScreenMessage(R.string.settings_ai_privacy)
        }
    }
}

@Composable
private fun PairSection(s: AppSettings, vm: SettingsViewModel) {
    val state by vm.pairing.collectAsStateWithLifecycle()
    val hasToken by vm.hasToken.collectAsStateWithLifecycle()
    val pending by vm.pendingSync.collectAsStateWithLifecycle()
    var url by remember(s.backendUrl) { mutableStateOf(s.backendUrl.ifBlank { "http://" }) }
    var code by remember { mutableStateOf("") }
    SectionHeader(stringResource(R.string.pair_title))
    TkdCard(Modifier.fillMaxWidth()) {
        ScreenMessage(R.string.pair_desc)
        OutlinedTextField(
            value = url, onValueChange = { url = it.trim() }, label = { Text(stringResource(R.string.pair_address)) },
            placeholder = { Text("http://192.168.1.20:8000") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            value = code, onValueChange = { v -> code = v.filter(Char::isDigit).take(6) },
            label = { Text(stringResource(R.string.pair_code)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        val urlOk = BackendClient.isAllowedUrl(url.trimEnd('/'))
        if (url.length > 8 && !urlOk) {
            Text(stringResource(R.string.pair_insecure), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
            Button(onClick = { vm.pair(url, code); code = "" }, enabled = urlOk && code.length == 6 && state != PairingState.Working) {
                Text(stringResource(R.string.pair_action))
            }
            if (s.syncEnabled && hasToken) {
                OutlinedButton(onClick = vm::syncNow) { Text(stringResource(R.string.pair_sync_now)) }
            }
        }
        when (val p = state) {
            PairingState.Working -> Text(stringResource(R.string.settings_testing), style = MaterialTheme.typography.bodySmall)
            PairingState.Done -> Text(stringResource(R.string.pair_done), color = TkdTheme.colors.success, style = MaterialTheme.typography.bodySmall)
            is PairingState.Failed -> Text(stringResource(R.string.pair_failed, p.message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            PairingState.Idle -> Unit
        }
        if (s.syncEnabled && hasToken) {
            val last = s.lastSyncAt?.let {
                java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(it))
            } ?: stringResource(R.string.pair_never)
            Text(stringResource(R.string.pair_status, last, pending), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.sm))
            s.lastSyncError?.let {
                Text(stringResource(R.string.pair_last_error, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun BackendSection(s: AppSettings, vm: SettingsViewModel) {
    val hasToken by vm.hasToken.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val pending by vm.pendingSync.collectAsStateWithLifecycle()
    var url by remember(s.backendUrl) { mutableStateOf(s.backendUrl) }
    var token by remember { mutableStateOf("") }
    SectionHeader(stringResource(R.string.settings_backend))
    TkdCard(Modifier.fillMaxWidth()) {
        ScreenMessage(R.string.settings_backend_desc)
        OutlinedTextField(
            value = url, onValueChange = { url = it.trim() }, label = { Text(stringResource(R.string.settings_backend_url)) },
            placeholder = { Text("https://tkd.example.com") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            value = token, onValueChange = { token = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(if (hasToken) R.string.settings_backend_token_set else R.string.settings_backend_token)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
            Button(onClick = { vm.saveBackend(url, token.ifBlank { null }); token = "" }) { Text(stringResource(R.string.action_save)) }
            OutlinedButton(onClick = vm::testConnection, enabled = s.backendUrl.isNotBlank() && hasToken) { Text(stringResource(R.string.settings_test_connection)) }
        }
        when (val c = connection) {
            ConnectionState.Testing -> Text(stringResource(R.string.settings_testing), style = MaterialTheme.typography.bodySmall)
            is ConnectionState.Ok -> Text(
                stringResource(
                    R.string.settings_connection_ok,
                    stringResource(if (c.aiConfigured) R.string.label_yes else R.string.label_no),
                    stringResource(if (c.motionAvailable) R.string.label_yes else R.string.label_no),
                ),
                color = TkdTheme.colors.success, style = MaterialTheme.typography.bodySmall,
            )
            is ConnectionState.Failed -> Text(stringResource(R.string.settings_connection_failed, c.message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            ConnectionState.Idle -> Unit
        }
        SwitchRow(stringResource(R.string.settings_sync), s.syncEnabled, vm::setSync, stringResource(R.string.settings_sync_desc, pending), enabled = hasToken && s.backendUrl.isNotBlank())
        Text(stringResource(R.string.settings_engine), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.sm))
        ChoiceChips(AnalysisEngine.entries, s.analysisEngine, vm::setEngine, {
            stringResource(if (it == AnalysisEngine.ON_DEVICE) R.string.engine_device else R.string.engine_server)
        })
    }
}

@Composable
private fun DataSection(vm: SettingsViewModel) {
    val message by vm.message.collectAsStateWithLifecycle()
    val activity = LocalContext.current as FragmentActivity
    var confirm by remember { mutableStateOf(false) }
    var confirmFinal by remember { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::export) }
    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.settings_delete_auth)
    SectionHeader(stringResource(R.string.settings_data))
    TkdCard(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { exporter.launch("tkdpro-export-${LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_export))
        }
        ScreenMessage(R.string.settings_export_desc)
        message?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_delete_all), color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirm) {
        ConfirmDialog(
            stringResource(R.string.settings_delete_all), stringResource(R.string.settings_delete_all_body), stringResource(R.string.action_continue),
            onConfirm = { confirmFinal = true }, onDismiss = { confirm = false }, destructive = true,
        )
    }
    if (confirmFinal) {
        ConfirmDialog(
            stringResource(R.string.settings_delete_final_title), stringResource(R.string.settings_delete_final_body), stringResource(R.string.action_delete),
            onConfirm = {
                if (canUseOwnerLock(activity)) promptOwner(activity, title, subtitle, onSuccess = { vm.deleteAll() }, onError = {}) else vm.deleteAll()
            },
            onDismiss = { confirmFinal = false },
            destructive = true,
        )
    }
}
