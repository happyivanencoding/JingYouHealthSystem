package com.thegreatnovel.jingyouhealth.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SleepTimingTest {
    @Test fun localClocksCrossMidnightWithoutUsingThePhoneTimezone() {
        val record = SleepClockPoint("2026-06-10", "2026-06-09T23:30:00", "2026-06-10T07:10:00")
        val night = sleepTimingNights(listOf(record), "2026-06-10").single()
        assertEquals(-0.5, night.bedtimeHour, 1e-8)
        assertEquals(7.0 + 10.0 / 60, night.wakeHour, 1e-8)
    }

    @Test fun offsetChangesMissingEndpointsAndMismatchedWakeDatesAreExcluded() {
        val good = clock(3)
        val values = listOf(good, clock(4).copy(offsetChanged = true), clock(5).copy(startLocal = null), clock(6).copy(date = date(7)))
        assertEquals(listOf(date(3)), sleepTimingNights(values, date(10)).map { it.date })
    }

    @Test fun habitualWakeAndDelayUseOnlyThePrior42Days() {
        val records = (0..59).map { clock(it) }.toMutableList()
        records[59] = clock(59, bedMinutes = 120, wakeMinutes = 570)
        assertEquals(100f / 60f, bedtimeDelaySeries(records, date(59)).last().value!!, 1e-5f)
        assertEquals(7f, habitualWakeSeries(records, date(59)).last().value!!, 1e-5f)
        assertNull(bedtimeDelaySeries(records, date(59))[6].value)
        val before = bedtimeDelaySeries(records, date(40))
        records[59] = clock(59, bedMinutes = 360, wakeMinutes = 660)
        assertEquals(before, bedtimeDelaySeries(records, date(40)))
    }

    @Test fun laterBedtimeWithFixedWakeIsComparedUsingActualStageDurations() {
        val clocks = (0..39).map { clock(it, if (it >= 30) 110 else 20) }
        val trends = Trends(sleepClocks = clocks,
            sleepHours = (0..39).map { TrendPoint(date(it), if (it >= 30) 5.5f else 7f) },
            deepHours = (0..39).map { TrendPoint(date(it), if (it >= 30) 1.2f else 1.5f) },
            remHours = (0..39).map { TrendPoint(date(it), if (it >= 30) 0.9f else 1.8f) })
        val result = sleepTimingSummary(trends, date(39))
        assertEquals(10, result.lateCount)
        assertEquals(30, result.otherCount)
        assertEquals(1.5, result.bedtimeShift!!, 1e-8)
        assertEquals(0.0, result.wakeShift!!, 1e-8)
        assertEquals(5.5, result.lateSleep!!, 1e-8)
        assertEquals(0.9, result.lateRem!!, 1e-5)
        assertEquals(1.5, result.otherDeep!!, 1e-8)
    }

    private fun date(i: Int) = LocalDate.of(2026, 4, 1).plusDays(i.toLong()).toString()
    private fun clock(i: Int, bedMinutes: Int = 20, wakeMinutes: Int = 420): SleepClockPoint {
        val date = LocalDate.parse(date(i))
        return SleepClockPoint(date.toString(), date.atStartOfDay().plusMinutes(bedMinutes.toLong()).toString(), date.atStartOfDay().plusMinutes(wakeMinutes.toLong()).toString())
    }
}
