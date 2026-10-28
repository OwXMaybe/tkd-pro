package com.tkdpro.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.tkdpro.core.datastore.UnitSystem
import com.tkdpro.core.domain.run.Pace
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Locale- and unit-aware formatting. Dates, decimal separators, distances and paces all follow the
 * selected app language (TR uses "," decimals and day-first dates) and the chosen unit system.
 */
class Formatter(val locale: Locale, val units: UnitSystem, val is24Hour: Boolean, private val zone: ZoneId = ZoneId.systemDefault()) {
    private val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    private val longDateFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)
    private val shortDateFmt = DateTimeFormatter.ofPattern(if (locale.language == "tr") "d MMM" else "MMM d", locale)
    private val timeFmt = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale)

    fun date(ms: Long): String = dateFmt.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun date(d: LocalDate): String = dateFmt.format(d)
    fun longDate(d: LocalDate): String = longDateFmt.format(d).replaceFirstChar { it.titlecase(locale) }
    fun shortDate(d: LocalDate): String = shortDateFmt.format(d)
    fun shortDate(ms: Long): String = shortDate(Instant.ofEpochMilli(ms).atZone(zone).toLocalDate())
    fun time(ms: Long): String = timeFmt.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun dateTime(ms: Long): String = "${date(ms)} · ${time(ms)}"

    fun number(value: Double, decimals: Int = 1): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }.format(value)

    fun integer(value: Number): String = NumberFormat.getIntegerInstance(locale).format(value)

    fun percent(fraction: Double): String = NumberFormat.getPercentInstance(locale).format(fraction)

    val distanceUnit: String get() = if (units == UnitSystem.METRIC) "km" else "mi"
    val paceUnit: String get() = if (units == UnitSystem.METRIC) "/km" else "/mi"
    val speedUnit: String get() = if (units == UnitSystem.METRIC) "km/h" else "mph"
    val elevationUnit: String get() = if (units == UnitSystem.METRIC) "m" else "ft"

    fun distanceValue(meters: Double): String =
        number(if (units == UnitSystem.METRIC) meters / 1000.0 else meters / METERS_PER_MILE, 2)

    fun distance(meters: Double): String = "${distanceValue(meters)} $distanceUnit"

    /** Pace is formatted from seconds per km; null (insufficient distance) yields "--". */
    fun paceValue(secPerKm: Double?): String {
        val v = secPerKm?.let { if (units == UnitSystem.METRIC) it else it * METERS_PER_MILE / 1000.0 }
        return Pace.format(v) ?: "--"
    }

    fun pace(secPerKm: Double?): String = "${paceValue(secPerKm)} $paceUnit"

    fun speedValue(kmh: Double?): String =
        kmh?.let { number(if (units == UnitSystem.METRIC) it else it * 1000.0 / METERS_PER_MILE, 1) } ?: "--"

    fun elevation(meters: Double?): String =
        meters?.let { "${integer((if (units == UnitSystem.METRIC) it else it * 3.28084).roundToLong())} $elevationUnit" } ?: "--"

    fun duration(ms: Long): String = Pace.formatDuration(ms)

    /** Compact duration such as "1 h 15 min" / "1 sa 15 dk". */
    fun minutes(min: Int): String {
        val tr = locale.language == "tr"
        val hourUnit = if (tr) "sa" else "h"
        val minUnit = if (tr) "dk" else "min"
        val h = min / 60
        val m = min % 60
        return if (h > 0) "$h $hourUnit $m $minUnit" else "$m $minUnit"
    }

    companion object {
        const val METERS_PER_MILE = 1609.344
    }
}

val LocalFormatter = staticCompositionLocalOf { Formatter(Locale.getDefault(), UnitSystem.METRIC, true) }
