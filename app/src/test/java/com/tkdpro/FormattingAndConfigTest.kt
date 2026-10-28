package com.tkdpro

import com.tkdpro.core.database.entity.MatchRound
import com.tkdpro.core.datastore.DashboardCard
import com.tkdpro.core.datastore.DashboardCardConfig
import com.tkdpro.core.datastore.SettingsRepository
import com.tkdpro.core.datastore.UnitSystem
import com.tkdpro.core.ui.Formatter
import com.tkdpro.data.repository.MatchTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class FormattingAndConfigTest {

    @Test
    fun dashboardConfigRoundTripsAndAppendsNewCards() {
        val cards = listOf(DashboardCardConfig(DashboardCard.RUNNING, false), DashboardCardConfig(DashboardCard.TODAY, true))
        val decoded = SettingsRepository.decodeDashboard(SettingsRepository.encodeDashboard(cards))
        assertEquals(cards, decoded.take(2))
        assertEquals(DashboardCard.entries.size, decoded.size)
        // Unknown entries from older/newer versions are ignored.
        assertEquals(DashboardCard.entries.size, SettingsRepository.decodeDashboard("BOGUS:1,TODAY:0").size)
    }

    @Test
    fun turkishUsesCommaDecimalsAndDayFirstDates() {
        val tr = Formatter(Locale.forLanguageTag("tr-TR"), UnitSystem.METRIC, true, ZoneOffset.UTC)
        assertEquals("5,23 km", tr.distance(5234.0))
        assertEquals("25 Eki", tr.shortDate(LocalDate.of(2026, 10, 25)))
        val en = Formatter(Locale.US, UnitSystem.METRIC, false, ZoneOffset.UTC)
        assertEquals("5.23 km", en.distance(5234.0))
        assertEquals("Oct 25", en.shortDate(LocalDate.of(2026, 10, 25)))
    }

    @Test
    fun imperialConvertsDistanceAndPace() {
        val f = Formatter(Locale.US, UnitSystem.IMPERIAL, false, ZoneOffset.UTC)
        assertEquals("1.00 mi", f.distance(1609.344))
        // 5:00 /km = 8:03 /mi
        assertEquals("8:03 /mi", f.pace(300.0))
        assertEquals("-- /mi", f.pace(null))
    }

    @Test
    fun matchTotalsOnlyCountEnteredValues() {
        val rounds = listOf(
            MatchRound(matchId = 1, number = 1, attackAttempts = 10, successfulAttacks = 4),
            MatchRound(matchId = 1, number = 2, attackAttempts = 6, successfulAttacks = 2),
            MatchRound(matchId = 1, number = 3),
        )
        val t = MatchTotals.of(rounds)
        assertEquals(16, t.attackAttempts)
        assertEquals(6, t.successfulAttacks)
        assertEquals(0.375, t.attackSuccessRate!!, 1e-9)
        assertNull(t.defensiveActions)
        assertNull(MatchTotals.of(emptyList()).attackSuccessRate)
    }
}
