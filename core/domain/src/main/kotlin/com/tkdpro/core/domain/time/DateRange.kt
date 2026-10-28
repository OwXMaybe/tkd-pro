package com.tkdpro.core.domain.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    init {
        require(!endInclusive.isBefore(start)) { "endInclusive must not be before start" }
    }

    fun startMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
        start.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endMillisExclusive(zone: ZoneId = ZoneId.systemDefault()): Long =
        endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun days(): Long = endInclusive.toEpochDay() - start.toEpochDay() + 1

    /** The range of equal length immediately before this one, used for historical comparison. */
    fun previous(): DateRange {
        val len = days()
        return DateRange(start.minusDays(len), start.minusDays(1))
    }

    companion object {
        fun week(today: LocalDate): DateRange {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return DateRange(monday, monday.plusDays(6))
        }

        fun month(today: LocalDate): DateRange {
            val first = today.withDayOfMonth(1)
            return DateRange(first, first.plusMonths(1).minusDays(1))
        }

        fun lastDays(today: LocalDate, n: Long): DateRange = DateRange(today.minusDays(n - 1), today)
    }
}
