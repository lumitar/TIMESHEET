package com.example.timesheet.data

import java.time.LocalDate
import java.time.YearMonth

data class PayrollBreakdown(
    val accrued: Double,
    val openingBalance: Double,
    val payments: Double,
    val taxes: Double,
    val adjustments: Double,
    val expenses: Double,
    val closingBalance: Double,
    val entries: List<LedgerEntry> = emptyList(),
    val totalHours: Double = 0.0
)

object PayrollCalculator {

    /**
     * Сохранён старый метод (расчёт за календарный месяц), используется на главном экране.
     */
    fun calculate(
        entries: List<LedgerEntry>,
        employees: List<Employee>,
        month: YearMonth,
        employeeFilter: String?,
        organizationFilter: String?,
        openingBalance: Double,
        surcharges: List<Surcharge> = emptyList(),
        timeTypes: List<TimeType> = emptyList()
    ): PayrollBreakdown {
        return calculateForPeriod(
            entries = entries,
            employees = employees,
            surcharges = surcharges,
            start = month.atDay(1),
            end = month.atEndOfMonth(),
            employeeFilter = employeeFilter,
            organizationFilter = organizationFilter,
            openingBalance = openingBalance,
            timeTypes = timeTypes
        )
    }

    
    fun calculateForPeriod(
        entries: List<LedgerEntry>,
        employees: List<Employee>,
        surcharges: List<Surcharge>,
        start: LocalDate,
        end: LocalDate,
        employeeFilter: String?,
        organizationFilter: String?,
        openingBalance: Double,
        timeTypes: List<TimeType> = emptyList()
    ): PayrollBreakdown {
        val filtered = entries.filter { entry ->
            !entry.date.isBefore(start) && !entry.date.isAfter(end) &&
                    (employeeFilter == null || entry.employeeId == employeeFilter) &&
                    (organizationFilter == null || entry.organizationId == organizationFilter)
        }

        val rateByEmployee = employees.associateBy({ it.id }, { it.hourlyRate })

        var accrued = 0.0
        var payments = 0.0
        var taxes = 0.0
        var adjustments = 0.0
        var expenses = 0.0
        var totalHours = 0.0

        filtered.forEach { entry ->
            when (entry.type) {
                EntryType.SHIFT -> {
                    totalHours += entry.calculatePaidHours()
                    val rate = entry.employeeId?.let { rateByEmployee[it] } ?: 0.0
                    accrued += shiftTotalAmount(entry, rate, surcharges, timeTypes)
                }
                EntryType.PAYMENT -> payments += entry.amount
                EntryType.TAX -> taxes += entry.amount
                EntryType.ADJUSTMENT -> adjustments += entry.amount
                EntryType.EXPENSE -> expenses += entry.amount
            }
        }

        val total = accrued - payments - taxes + adjustments - expenses
        val closing = openingBalance + total

        return PayrollBreakdown(
            accrued = accrued,
            openingBalance = openingBalance,
            payments = payments,
            taxes = taxes,
            adjustments = adjustments,
            expenses = expenses,
            closingBalance = closing,
            entries = filtered,
            totalHours = totalHours
        )
    }

    
    fun shiftMultiplier(entry: LedgerEntry, timeTypes: List<TimeType> = emptyList()): Double {
        val timeType = timeTypes.find { it.id == entry.timeTypeId }
        val base = if (timeType != null) {
            timeType.payMultiplier
        } else {
            when (entry.shiftType) {
                ShiftType.DAY -> 1.0
                ShiftType.NIGHT -> 1.4
                ShiftType.HOLIDAY -> 2.0
                ShiftType.OVERTIME -> 1.5
                ShiftType.WEEKEND -> 1.5
            }
        }
        return if (entry.overtimeEnabled) maxOf(base, 1.5) else base
    }

    /**
     * Основная оплата за смену (без доплат/удержаний): оплачиваемые часы × ставка × множитель.
     * Если пользователь вручную ввёл сумму в поле «Основная оплата» в диалоге (entry.amount > 0),
     * используется она — это ручное переопределение расчёта, как показано на макете
     * (редактируемое поле рядом с «07ч 00м · 0,00 ₽»).
     */
    fun shiftBaseAmount(entry: LedgerEntry, hourlyRate: Double, timeTypes: List<TimeType> = emptyList()): Double {
        if (entry.amount > 0.0) return entry.amount
        return entry.calculatePaidHours() * hourlyRate * shiftMultiplier(entry, timeTypes)
    }

    /** Сумма всех доплат/удержаний, привязанных к смене (с учётом условия по дням недели). */
    fun shiftSurchargeAmount(entry: LedgerEntry, surcharges: List<Surcharge>, baseAmount: Double): Double {
        val surchargeById = surcharges.associateBy { it.id }
        var total = 0.0
        val hours = entry.calculatePaidHours()
        entry.surchargeIds.forEach surchargeLoop@{ surchargeId ->
            val surcharge = surchargeById[surchargeId] ?: return@surchargeLoop
            val weekdayOk = surcharge.activeWeekdays.isEmpty() ||
                    surcharge.activeWeekdays.contains(entry.date.dayOfWeek.value)
            if (!weekdayOk) return@surchargeLoop

            val contribution = when (surcharge.calcType) {
                SurchargeCalcType.FIXED_PER_SHIFT -> surcharge.amount
                SurchargeCalcType.PER_HOUR -> surcharge.amount * hours
                SurchargeCalcType.PERCENT -> baseAmount * (surcharge.amount / 100.0)
            }
            total += if (surcharge.kind == SurchargeKind.BONUS) contribution else -contribution
        }
        return total
    }

    /** Итоговая сумма за смену: основная оплата + доплаты − удержания. */
    fun shiftTotalAmount(
        entry: LedgerEntry,
        hourlyRate: Double,
        surcharges: List<Surcharge>,
        timeTypes: List<TimeType> = emptyList()
    ): Double {
        val base = shiftBaseAmount(entry, hourlyRate, timeTypes)
        return base + shiftSurchargeAmount(entry, surcharges, base)
    }
}