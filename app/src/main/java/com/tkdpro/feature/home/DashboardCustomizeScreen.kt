package com.tkdpro.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.tkdpro.core.datastore.DashboardCardConfig
import com.tkdpro.core.datastore.SettingsRepository
import com.tkdpro.core.designsystem.component.ScreenMessage
import com.tkdpro.core.designsystem.component.TkdCard
import com.tkdpro.core.designsystem.component.TkdTopBar
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.ui.label
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardCustomizeViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val cards = settings.settings.map { it.dashboard }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun save(list: List<DashboardCardConfig>) = viewModelScope.launch { settings.setDashboard(list) }

    fun toggle(index: Int) = save(cards.value.toMutableList().also { it[index] = it[index].copy(visible = !it[index].visible) })

    fun move(index: Int, delta: Int) {
        val target = index + delta
        val list = cards.value.toMutableList()
        if (target !in list.indices) return
        list.add(target, list.removeAt(index))
        save(list)
    }
}

@Composable
fun DashboardCustomizeScreen(onBack: () -> Unit, viewModel: DashboardCustomizeViewModel = hiltViewModel()) {
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    Scaffold(topBar = { TkdTopBar(stringResource(R.string.home_customize), onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            item { ScreenMessage(R.string.customize_hint) }
            itemsIndexed(cards, key = { _, c -> c.card.name }) { i, c ->
                TkdCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(c.card.label()), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.move(i, -1) }, enabled = i > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.action_move_up))
                        }
                        IconButton(onClick = { viewModel.move(i, 1) }, enabled = i < cards.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.action_move_down))
                        }
                        Switch(checked = c.visible, onCheckedChange = { viewModel.toggle(i) })
                    }
                }
            }
        }
    }
}
