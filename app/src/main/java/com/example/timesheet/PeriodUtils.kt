package com.example.timesheet.data

import java.time.LocalDate
import java.time.YearMonth

data class QuickPeriod(
    val id: String,
    val name: String,
    val getRange: () -> Pair<LocalDate, LocalDate>
)

fun getQuickPeriods(): List<QuickPeriod> = listOf(
    QuickPeriod("today", "Сегодня") {
        val today = LocalDate.now()
        today to today
    },
    QuickPeriod("this_week", "Эта неделя") {
        val today = LocalDate.now()
        val start = today.minusDays(today.dayOfWeek.value - 1L)
        start to today
    },
    QuickPeriod("this_month", "Этот месяц") {
        val today = LocalDate.now()
        val start = today.withDayOfMonth(1)
        start to today
    },
    QuickPeriod("this_year", "Этот год") {
        val today = LocalDate.now()
        val start = today.withDayOfYear(1)
        start to today
    },
    QuickPeriod("yesterday", "Вчера") {
        val yesterday = LocalDate.now().minusDays(1)
        yesterday to yesterday
    },
    QuickPeriod("last_month", "Прошлый месяц") {
        val lastMonth = YearMonth.now().minusMonths(1)
        val start = lastMonth.atDay(1)
        val end = lastMonth.atEndOfMonth()
        start to end
    },
    QuickPeriod("last_year", "Прошлый год") {
        val lastYear = YearMonth.now().minusYears(1)
        val start = lastYear.atDay(1)
        val end = lastYear.atEndOfMonth()
        start to end
    },
    QuickPeriod("all_time", "Весь период") {
        val start = LocalDate.of(2000, 1, 1)
        val end = LocalDate.now()
        start to end
    }
)
