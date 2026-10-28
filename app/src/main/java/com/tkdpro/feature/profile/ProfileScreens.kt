package com.tkdpro.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.tkdpro.R
import com.tkdpro.core.database.entity.AthleteProfile
import com.tkdpro.core.database.entity.BeltRecord
import com.tkdpro.core.database.entity.BodySide
import com.tkdpro.core.designsystem.component.ChoiceChips
import com.tkdpro.core.designsystem.component.DateField
import com.tkdpro.core.designsystem.component.InlineEmpty
import com.tkdpro.core.designsystem.component.IntField
import com.tkdpro.core.designsystem.component.LoadingState
import com.tkdpro.core.designsystem.component.ScreenMessage
import com.tkdpro.core.designsystem.component.SectionHeader
import com.tkdpro.core.designsystem.component.StatTile
import com.tkdpro.core.designsystem.component.TkdCard
import com.tkdpro.core.designsystem.component.TkdTextField
import com.tkdpro.core.designsystem.component.TkdTopBar
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.ui.AppLanguage
import com.tkdpro.core.ui.LocalFormatter
import com.tkdpro.core.ui.label
import com.tkdpro.data.repository.ProfileRepository
import com.tkdpro.navigation.CompetitionsRoute
import com.tkdpro.navigation.GoalsRoute
import com.tkdpro.navigation.ProfileEditRoute
import com.tkdpro.navigation.SettingsRoute
import com.tkdpro.navigation.TimelineRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(private val repo: ProfileRepository) : ViewModel() {
    val profile = repo.profile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val belts = repo.belts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val goals = repo.activeGoals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun addBelt(b: BeltRecord) = viewModelScope.launch { repo.saveBelt(b) }
    fun deleteBelt(b: BeltRecord) = viewModelScope.launch { repo.deleteBelt(b) }
}

@Composable
fun ProfileScreen(onNavigate: (Any) -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val belts by viewModel.belts.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val f = LocalFormatter.current
    var addingBelt by remember { mutableStateOf(false) }
    val p = profile
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.md, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfilePhoto(p, 72)
                Column(Modifier.weight(1f).padding(start = Spacing.lg)) {
                    Text(p?.displayName?.ifBlank { null } ?: stringResource(R.string.profile_unnamed), style = MaterialTheme.typography.headlineSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.profile_private), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                    }
                }
                OutlinedButton(onClick = { onNavigate(ProfileEditRoute) }) { Text(stringResource(R.string.action_edit)) }
            }
        }
        item {
            TkdCard(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    val years = p?.startedTrainingYear?.let { LocalDate.now().year - it }
                    StatTile(stringResource(R.string.profile_experience), years?.toString() ?: "--", Modifier.weight(1f), unit = stringResource(R.string.unit_years))
                    StatTile(stringResource(R.string.profile_belt), p?.currentBelt?.ifBlank { null } ?: "--", Modifier.weight(1f))
                }
                if (!p?.club.isNullOrBlank()) Text(stringResource(R.string.profile_club_value, p!!.club), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.sm))
                if (!p?.coach.isNullOrBlank()) Text(stringResource(R.string.profile_coach_value, p!!.coach), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.profile_language_value, stringResource(AppLanguage.current().label)), style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            SectionHeader(stringResource(R.string.profile_belt_history), actionLabel = stringResource(R.string.action_add), onAction = { addingBelt = true })
            if (belts.isEmpty()) InlineEmpty(stringResource(R.string.profile_no_belts))
        }
        items(belts, key = { it.id }) { b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.belt, style = MaterialTheme.typography.bodyLarge)
                    Text(f.date(b.achievedOn), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { viewModel.deleteBelt(b) }) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
            }
        }
        item {
            SectionHeader(stringResource(R.string.card_goals))
            if (goals.isEmpty()) InlineEmpty(stringResource(R.string.home_goals_empty))
            goals.take(3).forEach { Text("• ${it.title}", style = MaterialTheme.typography.bodyMedium) }
        }
        item {
            TkdCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                NavRow(Icons.Filled.Flag, stringResource(R.string.goals_title)) { onNavigate(GoalsRoute) }
                NavRow(Icons.Filled.EmojiEvents, stringResource(R.string.competitions_title)) { onNavigate(CompetitionsRoute) }
                NavRow(Icons.Filled.Timeline, stringResource(R.string.timeline_title)) { onNavigate(TimelineRoute) }
                NavRow(Icons.Filled.Settings, stringResource(R.string.settings_title)) { onNavigate(SettingsRoute) }
            }
        }
    }
    if (addingBelt) {
        var belt by remember { mutableStateOf("") }
        var date by remember { mutableStateOf(System.currentTimeMillis()) }
        AlertDialog(
            onDismissRequest = { addingBelt = false },
            title = { Text(stringResource(R.string.profile_add_belt)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TkdTextField(belt, { belt = it.take(40) }, stringResource(R.string.profile_belt))
                    DateField(stringResource(R.string.label_date), date, { date = it }, Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = belt.isNotBlank(), onClick = {
                    viewModel.addBelt(BeltRecord(belt = belt.trim(), achievedOn = date))
                    addingBelt = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { addingBelt = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
fun NavRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Spacing.md))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ProfilePhoto(p: AthleteProfile?, sizeDp: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.size(sizeDp.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val photo = p?.photoPath
        if (photo != null) {
            AsyncImage(model = File(photo), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(sizeDp.dp))
        } else {
            Text(p?.displayName?.firstOrNull()?.uppercase() ?: "T", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ---------- Edit ----------

@HiltViewModel
class ProfileEditViewModel @Inject constructor(private val repo: ProfileRepository) : ViewModel() {
    val profile = MutableStateFlow<AthleteProfile?>(null)
    val saved = MutableStateFlow(false)
    val photoError = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            profile.value = repo.profile.first() ?: AthleteProfile(displayName = "", createdAt = 0, updatedAt = 0)
        }
    }

    fun update(p: AthleteProfile) { profile.value = p }

    fun setPhoto(uri: android.net.Uri) = viewModelScope.launch {
        runCatching { repo.setPhoto(uri) }
            .onSuccess { repo.profile.first()?.let { fresh -> profile.value = profile.value?.copy(photoPath = fresh.photoPath) } }
            .onFailure { photoError.value = true }
    }

    fun save() = viewModelScope.launch {
        val p = profile.value ?: return@launch
        repo.saveProfile { current ->
            current.copy(
                displayName = p.displayName.trim(),
                startedTrainingYear = p.startedTrainingYear,
                birthYear = p.birthYear,
                club = p.club.trim(),
                coach = p.coach.trim(),
                currentBelt = p.currentBelt.trim(),
                weightCategory = p.weightCategory.trim(),
                dominantSide = p.dominantSide,
            )
        }
        saved.value = true
    }
}

@Composable
fun ProfileEditScreen(onBack: () -> Unit, viewModel: ProfileEditViewModel = hiltViewModel()) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val photoError by viewModel.photoError.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::setPhoto) }
    LaunchedEffect(saved) { if (saved) onBack() }
    Scaffold(topBar = { TkdTopBar(stringResource(R.string.profile_edit), onBack) }) { padding ->
        val p = profile
        if (p == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfilePhoto(p, 64)
                TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text(stringResource(R.string.profile_change_photo))
                }
            }
            if (photoError) Text(stringResource(R.string.error_photo), color = MaterialTheme.colorScheme.error)
            TkdTextField(p.displayName, { viewModel.update(p.copy(displayName = it.take(40))) }, stringResource(R.string.profile_display_name))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                IntField(p.startedTrainingYear, { viewModel.update(p.copy(startedTrainingYear = it)) }, stringResource(R.string.profile_started_year), Modifier.weight(1f), 1950..2100)
                IntField(p.birthYear, { viewModel.update(p.copy(birthYear = it)) }, stringResource(R.string.profile_birth_year), Modifier.weight(1f), 1900..2100)
            }
            TkdTextField(p.currentBelt, { viewModel.update(p.copy(currentBelt = it.take(40))) }, stringResource(R.string.profile_belt))
            TkdTextField(p.weightCategory, { viewModel.update(p.copy(weightCategory = it.take(40))) }, stringResource(R.string.profile_weight_category))
            TkdTextField(p.club, { viewModel.update(p.copy(club = it.take(60))) }, stringResource(R.string.profile_club_optional))
            TkdTextField(p.coach, { viewModel.update(p.copy(coach = it.take(60))) }, stringResource(R.string.profile_coach_optional))
            Text(stringResource(R.string.profile_dominant_side), style = MaterialTheme.typography.labelLarge)
            ChoiceChips(listOf(BodySide.RIGHT, BodySide.LEFT), p.dominantSide, { viewModel.update(p.copy(dominantSide = it)) }, { stringResource(it.label()) })
            ScreenMessage(R.string.profile_minimal_data)
            Button(onClick = { viewModel.save() }, enabled = p.displayName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }
        }
    }
}
