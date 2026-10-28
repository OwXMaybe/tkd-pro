package com.tkdpro.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tkdpro.R
import com.tkdpro.core.database.entity.ScoringPreset
import com.tkdpro.core.designsystem.component.InlineEmpty
import com.tkdpro.core.designsystem.component.IntField
import com.tkdpro.core.designsystem.component.ScreenMessage
import com.tkdpro.core.designsystem.component.TkdCard
import com.tkdpro.core.designsystem.component.TkdTextField
import com.tkdpro.core.designsystem.component.TkdTopBar
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.data.repository.MatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScoringPresetsViewModel @Inject constructor(private val repo: MatchRepository) : ViewModel() {
    val presets = repo.scoringPresets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun save(p: ScoringPreset) = viewModelScope.launch { repo.savePreset(p) }
    fun delete(p: ScoringPreset) = viewModelScope.launch { repo.deletePreset(p) }
}

/** User-maintained point values, so the app never hardcodes competition rules that may change. */
@Composable
fun ScoringPresetsScreen(onBack: () -> Unit, viewModel: ScoringPresetsViewModel = hiltViewModel()) {
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ScoringPreset?>(null) }
    Scaffold(
        topBar = { TkdTopBar(stringResource(R.string.scoring_title), onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = ScoringPreset(label = "", points = 1, position = presets.size) }) { Icon(Icons.Filled.Add, stringResource(R.string.action_add)) }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            item { ScreenMessage(R.string.scoring_desc) }
            if (presets.isEmpty()) item { InlineEmpty(stringResource(R.string.scoring_empty)) }
            items(presets, key = { it.id }) { p ->
                TkdCard(Modifier.fillMaxWidth(), onClick = { editing = p }, contentPadding = PaddingValues(Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.points_value, p.points), style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = { viewModel.delete(p) }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
                    }
                }
            }
        }
    }
    editing?.let { initial ->
        var label by remember(initial) { mutableStateOf(initial.label) }
        var points by remember(initial) { mutableStateOf<Int?>(initial.points) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.scoring_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TkdTextField(label, { label = it.take(40) }, stringResource(R.string.scoring_label))
                    IntField(points, { points = it }, stringResource(R.string.label_points), Modifier.fillMaxWidth(), 0..20)
                }
            },
            confirmButton = {
                TextButton(enabled = label.isNotBlank() && points != null, onClick = {
                    viewModel.save(initial.copy(label = label.trim(), points = points ?: 0))
                    editing = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
