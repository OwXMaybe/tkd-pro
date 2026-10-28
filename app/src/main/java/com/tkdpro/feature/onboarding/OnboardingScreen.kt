package com.tkdpro.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkdpro.R
import com.tkdpro.core.datastore.SettingsRepository
import com.tkdpro.core.datastore.ThemeMode
import com.tkdpro.core.designsystem.component.ChoiceChips
import com.tkdpro.core.designsystem.component.IntField
import com.tkdpro.core.designsystem.component.TkdTextField
import com.tkdpro.core.designsystem.theme.Spacing
import com.tkdpro.core.ui.AppLanguage
import com.tkdpro.core.ui.label
import com.tkdpro.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profile: ProfileRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    fun finish(name: String, birthYear: Int?, theme: ThemeMode, onDone: () -> Unit) {
        viewModelScope.launch {
            profile.saveProfile { it.copy(displayName = name.trim(), birthYear = birthYear) }
            settings.setTheme(theme)
            onDone()
        }
    }
}

@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var birthYear by remember { mutableStateOf<Int?>(null) }
    var theme by remember { mutableStateOf(ThemeMode.SYSTEM) }
    var language by remember { mutableStateOf(AppLanguage.current()) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f), userScrollEnabled = false) { page ->
            when (page) {
                0 -> IntroPage(Icons.Filled.SportsMartialArts, stringResource(R.string.onboarding_welcome_title), stringResource(R.string.onboarding_welcome_body), showBrand = true)
                1 -> IntroPage(Icons.Filled.Lock, stringResource(R.string.onboarding_privacy_title), stringResource(R.string.onboarding_privacy_body))
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    Icon(Icons.Filled.Insights, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                    Text(stringResource(R.string.onboarding_setup_title), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.onboarding_setup_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TkdTextField(name, { name = it.take(40) }, stringResource(R.string.profile_display_name))
                    IntField(birthYear, { birthYear = it }, stringResource(R.string.profile_birth_year), Modifier.fillMaxWidth(), range = 1900..2100)
                    Text(stringResource(R.string.onboarding_birth_year_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(AppLanguage.entries, language, { language = it; AppLanguage.apply(it) }, { stringResource(it.label) })
                    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(ThemeMode.entries, theme, { theme = it }, { stringResource(it.label()) })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(Spacing.screen), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                repeat(3) { i ->
                    Box(
                        Modifier.size(if (i == pager.currentPage) 22.dp else 8.dp, 8.dp)
                            .background(
                                if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                CircleShape,
                            ),
                    )
                }
            }
            if (pager.currentPage > 0) {
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                    Text(stringResource(R.string.action_back))
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            if (pager.currentPage < 2) {
                Button(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                    Text(stringResource(R.string.action_next))
                }
            } else {
                Button(enabled = name.isNotBlank(), onClick = { viewModel.finish(name, birthYear, theme, onFinished) }) {
                    Text(stringResource(R.string.onboarding_start))
                }
            }
        }
    }
}

@Composable
private fun IntroPage(icon: ImageVector, title: String, body: String, showBrand: Boolean = false) {
    Column(
        Modifier.fillMaxSize().padding(Spacing.screen),
        verticalArrangement = Arrangement.Center,
    ) {
        if (showBrand) {
            Text(stringResource(R.string.brand_name), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
            Text(stringResource(R.string.brand_edition), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(Spacing.xxl))
        }
        Box(
            Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(Spacing.lg))
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Spacing.sm))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
