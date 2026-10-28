package com.tkdpro.feature.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.tkdpro.R
import com.tkdpro.core.database.entity.SessionStatus
import com.tkdpro.core.database.entity.TrainingCategory
import com.tkdpro.core.database.entity.TrainingSession
import com.tkdpro.core.designsystem.component.ChoiceChips
import com.tkdpro.core.designsystem.component.DateField
import com.tkdpro.core.designsystem.component.IntField
import com.tkdpro.core.designsystem.component.RatingSelector
import com.tkdpro.core.designsystem.component.TimeField
import com.tkdpro.core.designsystem.component.TkdTextField
import com.tkdpro.core.designsystem.component.TkdTopBar
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.ui.label
import com.tkdpro.data.repository.DuplicateRecordException
import com.tkdpro.data.repository.TrainingRepository
import com.tkdpro.navigation.SessionEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionForm(
    val id: Long = 0,
    val name: String = "",
    val category: TrainingCategory = TrainingCategory.TECHNICAL,
    val status: SessionStatus = SessionStatus.COMPLETED,
    val startTime: Long = System.currentTimeMillis(),
    val durationMin: Int? = 60,
    val location: String = "",
    val difficulty: Int? = null,
    val rpe: Int? = null,
    val notes: String = "",
    val createdAt: Long = 0,
    val saving: Boolean = false,
    val error: Int? = null,
    val savedId: Long? = null,
) {
    val valid: Boolean get() = name.isNotBlank() && (durationMin ?: 0) > 0
}

@HiltViewModel
class SessionEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: TrainingRepository,
) : ViewModel() {
    private val route = savedState.toRoute<SessionEditRoute>()
    val form = MutableStateFlow(SessionForm(status = if (route.planned) SessionStatus.PLANNED else SessionStatus.COMPLETED))

    init {
        if (route.id != 0L) viewModelScope.launch {
            repo.session(route.id).first()?.session?.let { s ->
                form.value = SessionForm(s.id, s.name, s.category, s.status, s.startTime, s.durationMin, s.location.orEmpty(), s.difficulty, s.rpe, s.notes, s.createdAt)
            }
        }
    }

    fun update(block: (SessionForm) -> SessionForm) = form.update { block(it).copy(error = null) }

    fun save() {
        val f = form.value
        if (!f.valid || f.saving) return
        form.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val id = repo.saveSession(
                    TrainingSession(
                        id = f.id,
                        name = f.name.trim(),
                        category = f.category,
                        status = f.status,
                        startTime = f.startTime,
                        durationMin = f.durationMin ?: 0,
                        location = f.location.trim().ifBlank { null },
                        difficulty = f.difficulty,
                        rpe = if (f.status == SessionStatus.COMPLETED) f.rpe else null,
                        notes = f.notes.trim(),
                        createdAt = f.createdAt,
                        updatedAt = 0,
                    ),
                )
                form.update { it.copy(saving = false, savedId = id) }
            } catch (_: DuplicateRecordException) {
                form.update { it.copy(saving = false, error = R.string.error_duplicate_session) }
            }
        }
    }
}

@Composable
fun SessionEditScreen(onBack: () -> Unit, onSaved: (Long, Boolean) -> Unit, viewModel: SessionEditViewModel = hiltViewModel()) {
    val f by viewModel.form.collectAsStateWithLifecycle()
    LaunchedEffect(f.savedId) { f.savedId?.let { onSaved(it, f.id == 0L) } }
    Scaffold(topBar = { TkdTopBar(stringResource(if (f.id == 0L) R.string.session_new else R.string.session_edit), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SessionStatus.entries.forEachIndexed { i, st ->
                    SegmentedButton(
                        selected = f.status == st,
                        onClick = { viewModel.update { it.copy(status = st) } },
                        shape = SegmentedButtonDefaults.itemShape(i, SessionStatus.entries.size),
                    ) { Text(stringResource(if (st == SessionStatus.PLANNED) R.string.status_planned else R.string.status_completed)) }
                }
            }
            TkdTextField(f.name, { v -> viewModel.update { it.copy(name = v.take(80)) } }, stringResource(R.string.session_name), isError = f.name.isBlank())
            Text(stringResource(R.string.session_category), style = MaterialTheme.typography.labelLarge)
            ChoiceChips(TrainingCategory.entries, f.category, { c -> viewModel.update { it.copy(category = c) } }, { stringResource(it.label()) })
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                DateField(stringResource(R.string.label_date), f.startTime, { v -> viewModel.update { it.copy(startTime = v) } }, Modifier.weight(1f))
                TimeField(stringResource(R.string.label_time), f.startTime, { v -> viewModel.update { it.copy(startTime = v) } }, Modifier.weight(1f))
            }
            IntField(f.durationMin, { v -> viewModel.update { it.copy(durationMin = v) } }, stringResource(R.string.session_duration_min), Modifier.fillMaxWidth(), 1..600)
            TkdTextField(f.location, { v -> viewModel.update { it.copy(location = v.take(80)) } }, stringResource(R.string.session_location_optional))
            RatingSelector(stringResource(R.string.session_difficulty), f.difficulty, 1..5, { v -> viewModel.update { it.copy(difficulty = v) } })
            if (f.status == SessionStatus.COMPLETED) {
                RatingSelector(
                    stringResource(R.string.session_rpe),
                    f.rpe,
                    1..10,
                    { v -> viewModel.update { it.copy(rpe = v) } },
                    hint = stringResource(R.string.session_rpe_hint),
                )
            }
            TkdTextField(f.notes, { v -> viewModel.update { it.copy(notes = v.take(2000)) } }, stringResource(R.string.label_notes), singleLine = false, minLines = 3)
            f.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            Button(onClick = viewModel::save, enabled = f.valid && !f.saving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}
