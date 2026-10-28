package com.tkdpro.feature.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.tkdpro.R
import com.tkdpro.core.database.entity.BodySide
import com.tkdpro.core.database.entity.TechniqueRecord
import com.tkdpro.core.database.entity.TrainingTechnique
import com.tkdpro.core.designsystem.component.ChoiceChips
import com.tkdpro.core.designsystem.component.IntField
import com.tkdpro.core.designsystem.component.RatingSelector
import com.tkdpro.core.designsystem.component.TkdCard
import com.tkdpro.core.designsystem.component.TkdTextField
import com.tkdpro.core.designsystem.component.TkdTopBar
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.ui.LocalFormatter
import com.tkdpro.core.ui.label
import com.tkdpro.data.repository.TrainingRepository
import com.tkdpro.navigation.TechnicalRecordingRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TechnicalRecordingViewModel @Inject constructor(savedState: SavedStateHandle, private val repo: TrainingRepository) : ViewModel() {
    val sessionId = savedState.toRoute<TechnicalRecordingRoute>().sessionId
    val techniques = repo.techniques.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(record: TechniqueRecord, onDone: () -> Unit) = viewModelScope.launch {
        repo.saveRecord(record)
        onDone()
    }
}

/**
 * Live recording of a technique block: tap counters for right/left repetitions and a block timer.
 * Counts are what the athlete taps; nothing is detected automatically here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicalRecordingScreen(onBack: () -> Unit, viewModel: TechnicalRecordingViewModel = hiltViewModel()) {
    val techniques by viewModel.techniques.collectAsStateWithLifecycle()
    val f = LocalFormatter.current
    var technique by remember { mutableStateOf<TrainingTechnique?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var right by remember { mutableStateOf(0) }
    var left by remember { mutableStateOf(0) }
    var sets by remember { mutableStateOf<Int?>(null) }
    var side by remember { mutableStateOf(BodySide.BOTH) }
    var difficulty by remember { mutableStateOf<Int?>(null) }
    var notes by remember { mutableStateOf("") }
    var coach by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableStateOf(0L) }

    LaunchedEffect(running) {
        var last = System.nanoTime()
        while (running) {
            delay(200)
            val now = System.nanoTime()
            elapsedMs += (now - last) / 1_000_000
            last = now
        }
    }

    Scaffold(topBar = { TkdTopBar(stringResource(R.string.recording_title), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = technique?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.label_technique)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    techniques.forEach { t ->
                        DropdownMenuItem(text = { Text(t.name) }, onClick = { technique = t; expanded = false })
                    }
                }
            }
            ChoiceChips(BodySide.entries, side, { side = it }, { stringResource(it.label()) })

            TkdCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recording_timer), style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.duration(elapsedMs), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = { running = !running }) {
                        Icon(if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, null)
                        Text(stringResource(if (running) R.string.action_pause else R.string.action_start))
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                if (side != BodySide.LEFT) Counter(stringResource(R.string.side_right), right, { right = it }, Modifier.weight(1f))
                if (side != BodySide.RIGHT) Counter(stringResource(R.string.side_left), left, { left = it }, Modifier.weight(1f))
            }
            IntField(sets, { sets = it }, stringResource(R.string.label_sets), Modifier.fillMaxWidth(), 1..100)
            RatingSelector(stringResource(R.string.session_difficulty), difficulty, 1..5, { difficulty = it })
            TkdTextField(notes, { notes = it.take(1000) }, stringResource(R.string.label_notes), singleLine = false, minLines = 2)
            TkdTextField(coach, { coach = it.take(1000) }, stringResource(R.string.label_coach_feedback_optional), singleLine = false, minLines = 2)

            val total = (if (side != BodySide.LEFT) right else 0) + (if (side != BodySide.RIGHT) left else 0)
            Button(
                onClick = {
                    val t = technique ?: return@Button
                    viewModel.save(
                        TechniqueRecord(
                            sessionId = viewModel.sessionId,
                            techniqueId = t.id,
                            sets = sets,
                            reps = total,
                            rightReps = if (side != BodySide.LEFT) right else null,
                            leftReps = if (side != BodySide.RIGHT) left else null,
                            side = side,
                            durationSec = (elapsedMs / 1000).toInt().takeIf { it > 0 },
                            difficulty = difficulty,
                            notes = notes.trim(),
                            coachFeedback = coach.trim(),
                            createdAt = 0,
                        ),
                        onBack,
                    )
                },
                enabled = technique != null && total > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.recording_save, total)) }
        }
    }
}

@Composable
private fun Counter(label: String, value: Int, onChange: (Int) -> Unit, modifier: Modifier) {
    TkdCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value.toString(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange((value - 1).coerceAtLeast(0)) }) { Icon(Icons.Filled.Remove, stringResource(R.string.action_decrease)) }
            Button(
                onClick = { onChange(value + 1) },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(),
            ) { Text("+1", style = MaterialTheme.typography.titleLarge) }
        }
    }
}
