package com.tkdpro.feature.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tkdpro.R
import com.tkdpro.core.database.entity.AiReport
import com.tkdpro.core.designsystem.component.EmptyState
import com.tkdpro.core.designsystem.component.Provenance
import com.tkdpro.core.designsystem.component.ProvenanceBadge
import com.tkdpro.core.designsystem.component.ScreenMessage
import com.tkdpro.core.designsystem.component.TkdCard
import com.tkdpro.core.designsystem.component.TkdTopBar
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.ui.LocalFormatter
import com.tkdpro.data.ai.DataSource
import com.tkdpro.data.repository.AiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiReportsViewModel @Inject constructor(private val repo: AiRepository) : ViewModel() {
    val reports = repo.reports.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun sources(r: AiReport): List<DataSource> = repo.decodeSources(r.dataSourcesJson)
    fun delete(id: Long) = viewModelScope.launch { repo.deleteReport(id) }
}

@Composable
fun AiReportsScreen(onBack: () -> Unit, viewModel: AiReportsViewModel = hiltViewModel()) {
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val f = LocalFormatter.current
    Scaffold(topBar = { TkdTopBar(stringResource(R.string.ai_reports), onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            val list = reports
            if (list != null && list.isEmpty()) item { EmptyState(stringResource(R.string.ai_reports_empty_title), stringResource(R.string.ai_reports_empty_body), icon = Icons.AutoMirrored.Filled.Article) }
            items(list.orEmpty(), key = { it.id }) { r ->
                TkdCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.ai_weekly_report_title, r.periodStart?.let { f.shortDate(it) } ?: "", r.periodEnd?.let { f.shortDate(it - 1) } ?: ""),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        ProvenanceBadge(Provenance.AI)
                        IconButton(onClick = { viewModel.delete(r.id) }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
                    }
                    Text(r.content, modifier = Modifier.padding(top = Spacing.sm))
                    Text(stringResource(R.string.coach_sources), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = Spacing.sm))
                    DataSourceChips(viewModel.sources(r))
                    Text(
                        stringResource(R.string.ai_generated_on, f.dateTime(r.createdAt)) + (r.model.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ScreenMessage(R.string.ai_disclaimer)
                }
            }
        }
    }
}
