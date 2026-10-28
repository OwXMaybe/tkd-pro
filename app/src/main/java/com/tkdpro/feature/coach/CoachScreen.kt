package com.tkdpro.feature.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tkdpro.R
import com.tkdpro.core.database.entity.ChatMessage
import com.tkdpro.core.database.entity.ChatRole
import com.tkdpro.core.datastore.SettingsRepository
import com.tkdpro.core.designsystem.component.ConfirmDialog
import com.tkdpro.core.designsystem.component.Provenance
import com.tkdpro.core.designsystem.component.ProvenanceBadge
import com.tkdpro.core.designsystem.component.ScreenMessage
import com.tkdpro.core.designsystem.component.TkdCard
import com.tkdpro.core.designsystem.component.UnavailableNotice
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.designsystem.theme.TkdTheme
import com.tkdpro.core.network.BackendClient
import com.tkdpro.core.network.BackendError
import com.tkdpro.core.ui.LocalFormatter
import com.tkdpro.data.ai.DataSource
import com.tkdpro.data.repository.AiNotPermittedException
import com.tkdpro.data.repository.AiRepository
import com.tkdpro.navigation.AiReportsRoute
import com.tkdpro.navigation.SettingsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

fun Throwable.aiMessage(): Int = when (this) {
    is AiNotPermittedException -> R.string.ai_not_permitted
    is BackendError.NotConfigured -> R.string.ai_not_configured
    is BackendError.ServiceUnavailable -> R.string.ai_service_unavailable
    is BackendError.Unauthorized -> R.string.ai_unauthorized
    is BackendError.Network -> R.string.ai_network_error
    else -> R.string.ai_generic_error
}

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val repo: AiRepository,
    private val client: BackendClient,
    settings: SettingsRepository,
) : ViewModel() {
    val messages = repo.messages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val ai = settings.settings.map { it.ai }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val configured = MutableStateFlow<Boolean?>(null)
    val sending = MutableStateFlow(false)
    val error = MutableStateFlow<Int?>(null)
    val generatingReport = MutableStateFlow(false)
    val reportDone = MutableStateFlow(false)

    init {
        viewModelScope.launch { configured.value = client.isConfigured() }
    }

    fun sources(m: ChatMessage): List<DataSource> = repo.decodeSources(m.dataSourcesJson)

    fun send(text: String) {
        if (text.isBlank() || sending.value) return
        sending.value = true
        error.value = null
        viewModelScope.launch {
            repo.ask(text.trim()).onFailure { error.value = it.aiMessage() }
            sending.value = false
        }
    }

    fun weeklyReport() {
        if (generatingReport.value) return
        generatingReport.value = true
        error.value = null
        viewModelScope.launch {
            repo.generateWeeklyReport()
                .onSuccess { reportDone.value = true }
                .onFailure { error.value = it.aiMessage() }
            generatingReport.value = false
        }
    }

    fun clear() = viewModelScope.launch { repo.clearChat() }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CoachScreen(onNavigate: (Any) -> Unit, viewModel: CoachViewModel = hiltViewModel()) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val ai by viewModel.ai.collectAsStateWithLifecycle()
    val configured by viewModel.configured.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val generating by viewModel.generatingReport.collectAsStateWithLifecycle()
    val reportDone by viewModel.reportDone.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size + 1) }
    LaunchedEffect(reportDone) {
        if (reportDone) {
            viewModel.reportDone.value = false
            onNavigate(AiReportsRoute)
        }
    }
    val examples = listOf(
        stringResource(R.string.coach_example_1), stringResource(R.string.coach_example_2), stringResource(R.string.coach_example_3),
        stringResource(R.string.coach_example_4), stringResource(R.string.coach_example_5), stringResource(R.string.coach_example_6),
    )
    val ready = ai?.enabled == true && configured == true

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, null, tint = TkdTheme.colors.ai)
            Text(stringResource(R.string.tab_coach), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(start = Spacing.sm))
            IconButton(onClick = { onNavigate(AiReportsRoute) }) { Icon(Icons.AutoMirrored.Filled.Article, stringResource(R.string.ai_reports)) }
            if (messages.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.DeleteSweep, stringResource(R.string.coach_clear)) }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                if (ai != null && ai?.enabled != true) {
                    UnavailableNotice(
                        stringResource(R.string.coach_disabled_title),
                        stringResource(R.string.coach_disabled_body),
                        actionLabel = stringResource(R.string.action_open_settings),
                        onAction = { onNavigate(SettingsRoute) },
                    )
                } else if (configured == false) {
                    UnavailableNotice(
                        stringResource(R.string.ai_unavailable_title),
                        stringResource(R.string.ai_not_configured),
                        actionLabel = stringResource(R.string.action_open_settings),
                        onAction = { onNavigate(SettingsRoute) },
                    )
                } else if (ai?.anyCategory == false) {
                    UnavailableNotice(stringResource(R.string.coach_no_categories_title), stringResource(R.string.coach_no_categories_body),
                        actionLabel = stringResource(R.string.action_open_settings), onAction = { onNavigate(SettingsRoute) })
                }
            }
            item {
                TkdCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.coach_intro), style = MaterialTheme.typography.bodyMedium)
                    ScreenMessage(R.string.ai_disclaimer)
                    OutlinedButton(onClick = viewModel::weeklyReport, enabled = ready && !generating, modifier = Modifier.padding(top = Spacing.sm)) {
                        if (generating) CircularProgressIndicator(Modifier.padding(end = Spacing.sm).widthIn(max = 18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.coach_weekly_report))
                    }
                }
            }
            if (messages.isEmpty()) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        examples.forEach { q -> SuggestionChip(onClick = { if (ready) viewModel.send(q) else input = q }, label = { Text(q) }) }
                    }
                }
            }
            items(messages, key = { it.id }) { m -> MessageBubble(m, viewModel.sources(m)) }
            item {
                if (sending) CircularProgressIndicator(Modifier.padding(Spacing.sm))
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(1000) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.coach_input_hint)) },
                enabled = ready,
                maxLines = 4,
                shape = MaterialTheme.shapes.large,
            )
            FilledIconButton(
                onClick = { viewModel.send(input); input = "" },
                enabled = ready && input.isNotBlank() && !sending,
                modifier = Modifier.padding(start = Spacing.sm),
            ) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.action_send)) }
        }
    }
    if (confirmClear) {
        ConfirmDialog(
            stringResource(R.string.coach_clear), stringResource(R.string.coach_clear_body), stringResource(R.string.action_delete),
            onConfirm = { viewModel.clear() }, onDismiss = { confirmClear = false }, destructive = true,
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MessageBubble(m: ChatMessage, sources: List<DataSource>) {
    val user = m.role == ChatRole.USER
    Box(Modifier.fillMaxWidth(), contentAlignment = if (user) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier.widthIn(max = 320.dp)
                .background(
                    if (user) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(18.dp, 18.dp, if (user) 4.dp else 18.dp, if (user) 18.dp else 4.dp),
                )
                .padding(Spacing.md),
        ) {
            if (m.failed) {
                Text(stringResource(R.string.coach_failed_message), color = MaterialTheme.colorScheme.error)
            } else {
                if (!user) ProvenanceBadge(Provenance.AI, Modifier.padding(bottom = Spacing.xs))
                Text(m.content, color = if (user) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            }
            if (!user && sources.isNotEmpty()) {
                Text(stringResource(R.string.coach_sources), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = Spacing.sm))
                DataSourceChips(sources)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DataSourceChips(sources: List<DataSource>) {
    val f = LocalFormatter.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        sources.forEach { s ->
            AssistChip(onClick = {}, label = {
                Text(
                    stringResource(R.string.coach_source_item, sourceLabel(s.category), s.records, f.shortDate(s.fromMs), f.shortDate(s.toMs - 1)),
                    style = MaterialTheme.typography.labelSmall,
                )
            })
        }
    }
}

@Composable
private fun sourceLabel(category: String): String = stringResource(
    when (category) {
        "training" -> R.string.ai_perm_training
        "running" -> R.string.ai_perm_running
        "matches" -> R.string.ai_perm_matches
        "motion" -> R.string.ai_perm_motion
        "recovery" -> R.string.ai_perm_recovery
        "reaction_and_performance_tests" -> R.string.ai_perm_reaction
        else -> R.string.ai_perm_profile
    },
)
