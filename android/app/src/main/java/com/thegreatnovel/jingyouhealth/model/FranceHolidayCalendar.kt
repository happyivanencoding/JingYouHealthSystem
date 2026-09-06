package com.thegreatnovel.jingyouhealth.model

import java.time.LocalDate

/**
 * France national holiday calendar used as known calendar context. No regional or individual
 * leave days are inferred. Easter-related dates use the Gregorian computus.
 */
fun franceNationalHolidays(year: Int): Set<LocalDate> {
    val easter = gregorianEaster(year)
    return setOf(
        LocalDate.of(year, 1, 1),
        easter.plusDays(1), // Easter Monday
        LocalDate.of(year, 5, 1),
        LocalDate.of(year, 5, 8),
        easter.plusDays(39), // Ascension
        easter.plusDays(50), // Whit/Pentecost Monday
        LocalDate.of(year, 7, 14),
        LocalDate.of(year, 8, 15),
        LocalDate.of(year, 11, 1),
        LocalDate.of(year, 11, 11),
        LocalDate.of(year, 12, 25),
    )
}

fun isFrenchNationalHoliday(date: LocalDate): Boolean = date in franceNationalHolidays(date.year)

/** English adjective alias for callers that prefer the conventional function spelling. */
fun frenchNationalHolidays(year: Int): Set<LocalDate> = franceNationalHolidays(year)

private fun gregorianEaster(year: Int): LocalDate {
    // Meeus/Jones/Butcher Gregorian algorithm.
    val a = year % 19
    val b = year / 100
    val c = year % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val month = (h + l - 7 * m + 114) / 31
    val day = (h + l - 7 * m + 114) % 31 + 1
    return LocalDate.of(year, month, day)
}
