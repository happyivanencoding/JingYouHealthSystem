package com.thegreatnovel.jingyouhealth.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FranceHolidayCalendarTest {
    @Test fun nationalDatesAreCorrectFor2025And2026() {
        assertEquals(
            setOf(
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 4, 21),
                LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 8),
                LocalDate.of(2025, 5, 29), LocalDate.of(2025, 6, 9),
                LocalDate.of(2025, 7, 14), LocalDate.of(2025, 8, 15),
                LocalDate.of(2025, 11, 1), LocalDate.of(2025, 11, 11),
                LocalDate.of(2025, 12, 25),
            ),
            franceNationalHolidays(2025),
        )
        assertEquals(
            setOf(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 6),
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 8),
                LocalDate.of(2026, 5, 14), LocalDate.of(2026, 5, 25),
                LocalDate.of(2026, 7, 14), LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 11),
                LocalDate.of(2026, 12, 25),
            ),
            franceNationalHolidays(2026),
        )
        assertTrue(isFrenchNationalHoliday(LocalDate.of(2026, 5, 14)))
    }
}
