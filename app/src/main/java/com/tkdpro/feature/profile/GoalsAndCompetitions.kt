package com.tkdpro.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.tkdpro.core.database.entity.Competition
import com.tkdpro.core.database.entity.GoalCategory
import com.tkdpro.core.database.entity.GoalStatus
import com.tkdpro.core.database.entity.PersonalGoal
import com.tkdpro.core.designsystem.component.ChoiceChips
import com.tkdpro.core.designsystem.component.DateField
import com.tkdpro.core.designsystem.component.DecimalField
import com.tkdpro.core.designsystem.component.EmptyState
import com.tkdpro.core.designsystem.component.TkdCard
import com.tkdpro.core.designsystem.component.TkdTextField
import com.tkdpro.core.designsystem.component.TkdTopBar
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.designsystem.theme.TkdTheme
import com.tkdpro.core.ui.LocalFormatter
import com.tkdpro.core.ui.label
import com.tkdpro.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GoalsViewModel @Inject constructor(private val repo: ProfileRepository) : ViewModel() {
    val goals = repo.goals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun save(g: PersonalGoal) = viewModelScope.launch { repo.saveGoal(g) }
    fun delete(id: Long) = viewModelScope.launch { repo.deleteGoal(id) }
}

@Composable
fun GoalsScreen(onBack: () -> Unit, viewModel: GoalsViewModel = hiltViewModel()) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val f = LocalFormatter.current
    var editing by remember { mutableStateOf<PersonalGoal?>(null) }
    Scaffold(
        topBar = { TkdTopBar(stringResource(R.string.goals_title), onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = PersonalGoal(title = "", category = GoalCategory.TECHNICAL, createdAt = 0, updatedAt = 0) }) {
                Icon(Icons.Filled.Add, stringResource(R.string.goal_add))
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val list = goals
            if (list != null && list.isEmpty()) item { EmptyState(stringResource(R.string.goals_empty_title), stringResource(R.string.goals_empty_body), icon = Icons.Filled.Flag) }
            items(list.orEmpty(), key = { it.id }) { g ->
                TkdCard(Modifier.fillMaxWidth(), onClick = { editing = g }, contentPadding = PaddingValues(Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(g.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull(stringResource(g.category.label()), g.targetDate?.let { f.date(it) }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            stringResource(g.status.label()),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (g.status == GoalStatus.ACHIEVED) TkdTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val target = g.targetValue
                    val current = g.currentValue
                    if (target != null && target > 0 && current != null) {
                        LinearProgressIndicator(progress = { (current / target).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm))
                        Text("${f.number(current, 1)} / ${f.number(target, 1)} ${g.unit}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    editing?.let { g ->
        GoalDialog(g, onDismiss = { editing = null }, onSave = { viewModel.save(it); editing = null }, onDelete = { viewModel.delete(g.id); editing = null })
    }
}

@Composable
private fun GoalDialog(initial: PersonalGoal, onDismiss: () -> Unit, onSave: (PersonalGoal) -> Unit, onDelete: () -> Unit) {
    var g by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.id == 0L) R.string.goal_add else R.string.goal_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TkdTextField(g.title, { g = g.copy(title = it.take(80)) }, stringResource(R.string.goal_title))
                TkdTextField(g.description, { g = g.copy(description = it.take(500)) }, stringResource(R.string.label_description), singleLine = false)
                ChoiceChips(GoalCategory.entries, g.category, { g = g.copy(category = it) }, { stringResource(it.label()) })
                DateField(stringResource(R.string.goal_target_date), g.targetDate, { g = g.copy(targetDate = it) }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    DecimalField(g.currentValue, { g = g.copy(currentValue = it) }, stringResource(R.string.goal_current), Modifier.weight(1f))
                    DecimalField(g.targetValue, { g = g.copy(targetValue = it) }, stringResource(R.string.goal_target), Modifier.weight(1f))
                }
                TkdTextField(g.unit, { g = g.copy(unit = it.take(12)) }, stringResource(R.string.goal_unit))
                ChoiceChips(GoalStatus.entries, g.status, { g = g.copy(status = it) }, { stringResource(it.label()) })
                if (initial.id != 0L) TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = g.title.isNotBlank(), onClick = { onSave(g.copy(title = g.title.trim())) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@HiltViewModel
class CompetitionsViewModel @Inject constructor(private val repo: ProfileRepository) : ViewModel() {
    val competitions = repo.competitions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun save(c: Competition) = viewModelScope.launch { repo.saveCompetition(c) }
    fun delete(id: Long) = viewModelScope.launch { repo.deleteCompetition(id) }
}

@Composable
fun CompetitionsScreen(onBack: () -> Unit, viewModel: CompetitionsViewModel = hiltViewModel()) {
    val list by viewModel.competitions.collectAsStateWithLifecycle()
    val f = LocalFormatter.current
    var editing by remember { mutableStateOf<Competition?>(null) }
    val now = System.currentTimeMillis()
    Scaffold(
        topBar = { TkdTopBar(stringResource(R.string.competitions_title), onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Competition(name = "", date = now, createdAt = 0) }) { Icon(Icons.Filled.Add, stringResource(R.string.competition_add)) }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val l = list
            if (l != null && l.isEmpty()) item { EmptyState(stringResource(R.string.competitions_empty_title), stringResource(R.string.competitions_empty_body), icon = Icons.Filled.EmojiEvents) }
            items(l.orEmpty(), key = { it.id }) { c ->
                TkdCard(Modifier.fillMaxWidth(), onClick = { editing = c }, contentPadding = PaddingValues(Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleSmall)
                            Text(listOf(f.date(c.date), c.location, c.category).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (c.date >= now) Text(stringResource(R.string.competition_upcoming), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        IconButton(onClick = { viewModel.delete(c.id) }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
                    }
                }
            }
        }
    }
    editing?.let { initial ->
        var c by remember(initial) { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.competition_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TkdTextField(c.name, { c = c.copy(name = it.take(80)) }, stringResource(R.string.match_competition))
                    DateField(stringResource(R.string.label_date), c.date, { c = c.copy(date = it) }, Modifier.fillMaxWidth())
                    TkdTextField(c.location, { c = c.copy(location = it.take(80)) }, stringResource(R.string.label_location))
                    TkdTextField(c.category, { c = c.copy(category = it.take(40)) }, stringResource(R.string.match_category))
                    TkdTextField(c.notes, { c = c.copy(notes = it.take(500)) }, stringResource(R.string.label_notes))
                }
            },
            confirmButton = { TextButton(enabled = c.name.isNotBlank(), onClick = { viewModel.save(c.copy(name = c.name.trim())); editing = null }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
