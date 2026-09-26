package com.example.timesheet.data

import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

// ========== ОСНОВНЫЕ МОДЕЛИ ==========
data class Employee(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val hourlyRate: Double = 0.0,
    val phone: String = "",
    val photoUri: String? = null,
    val contactLookupKey: String? = null
)

data class Organization(
    val id: String = UUID.randomUUID().toString(),
    val name: String = ""
)

/**
 * Привязка сотрудника (справочник «Физлица») к организации: ставка и остаток
 * на начало периода задаются отдельно для каждой пары сотрудник/организация,
 * поэтому один и тот же человек может числиться в нескольких организациях с
 * разными условиями. Наличие такой записи также означает, что сотрудник
 * показывается во вкладке «Работники» соответствующей организации.
 */
data class EmployeeOrgLink(
    val employeeId: String,
    val organizationId: String,
    val hourlyRate: Double = 0.0,
    val openingBalance: Double = 0.0
)

enum class EntryType {
    SHIFT,
    PAYMENT,
    TAX,
    ADJUSTMENT,
    EXPENSE
}

enum class ShiftType {
    DAY,
    NIGHT,
    HOLIDAY,
    OVERTIME,
    WEEKEND
}

fun inferShiftTypeFromTimeType(timeType: TimeType?): ShiftType {
    val name = timeType?.name?.lowercase() ?: return ShiftType.DAY
    return when {
        "сверхуроч" in name -> ShiftType.OVERTIME
        "ноч" in name -> ShiftType.NIGHT
        "выходн" in name || "празд" in name -> ShiftType.HOLIDAY
        "командиров" in name -> ShiftType.OVERTIME
        else -> ShiftType.DAY
    }
}

/**
 * Фолбэк только для СТАРЫХ записей, у которых ещё нет `timeTypeId` (созданы до
 * этого исправления) — чтобы у них тоже был хоть какой-то цвет/название из
 * справочника, пока их не пересохранят через диалог «Смена».
 */
fun shiftTypeTimeTypeId(shiftType: ShiftType): String = when (shiftType) {
    ShiftType.DAY -> "tt_day"
    ShiftType.NIGHT -> "tt_night"
    ShiftType.HOLIDAY -> "tt_holiday"
    ShiftType.OVERTIME -> "tt_business_trip"
    ShiftType.WEEKEND -> "tt_unpaid_vacation"
}

fun displayShiftLabel(baseName: String, overtimeEnabled: Boolean): String =
    if (overtimeEnabled) "$baseName · Сверхурочно" else baseName

fun moneyInputFilter(input: String): String =
    input.filter { c -> c.isDigit() || c == '.' || c == ',' }

/**
 * То же самое, но для полей, где допустим ведущий минус (например, «Остаток
 * на начало периода» может быть отрицательным).
 */
fun moneySignedInputFilter(input: String): String {
    val negative = input.startsWith("-")
    val digitsOnly = input.filter { c -> c.isDigit() || c == '.' || c == ',' }
    return if (negative) "-$digitsOnly" else digitsOnly
}

fun timeRangesOverlap(
    aStart: LocalTime?,
    aEnd: LocalTime?,
    bStart: LocalTime?,
    bEnd: LocalTime?
): Boolean {
    if (aStart == null || aEnd == null || bStart == null || bEnd == null) return false
    val aStartSec = aStart.toSecondOfDay()
    val aEndSecRaw = aEnd.toSecondOfDay()
    val aEndSec = if (aEndSecRaw > aStartSec) aEndSecRaw else aEndSecRaw + 86400
    val bStartSec = bStart.toSecondOfDay()
    val bEndSecRaw = bEnd.toSecondOfDay()
    val bEndSec = if (bEndSecRaw > bStartSec) bEndSecRaw else bEndSecRaw + 86400
    return aStartSec < bEndSec && bStartSec < aEndSec
}

fun dateRangeDays(start: LocalDate, end: LocalDate): List<LocalDate> {
    if (end.isBefore(start)) return listOf(start)
    return generateSequence(start) { d -> if (d.isBefore(end)) d.plusDays(1) else null }.toList()
}

data class LedgerEntry(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate = LocalDate.now(),
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val shiftType: ShiftType = ShiftType.DAY,
    val type: EntryType = EntryType.SHIFT,
    val employeeId: String? = null,
    val organizationId: String? = null,
    val amount: Double = 0.0,
    val hours: Double = 0.0,
    val note: String = "",
    val expenseCategoryId: String? = null,
    val unitId: String? = null,
    val quantity: Double = 0.0,

    val surchargeIds: List<String> = emptyList(),

    val unpaidBreakMinutes: Int = 0,
    val overtimeEnabled: Boolean = false,
    val projectName: String = "",

    val adjustmentTypeName: String = "",
    val timeTypeId: String? = null,

    val attachments: List<String> = emptyList()
) {
    /** Полная длительность смены по времени начала/конца (без вычета перерывов). */
    fun calculateHours(): Double {
        if (startTime == null || endTime == null) return hours
        val start = startTime.toSecondOfDay()
        val end = endTime.toSecondOfDay()
        val diffSeconds = if (end >= start) end - start else (end + 86400) - start
        return diffSeconds / 3600.0
    }

    /** Оплачиваемые часы = длительность смены минус неоплачиваемые перерывы. */
    fun calculatePaidHours(): Double {
        val raw = calculateHours() - unpaidBreakMinutes / 60.0
        return if (raw < 0.0) 0.0 else raw
    }
}

// ========== МОДЕЛИ ДЛЯ СПРАВОЧНИКОВ ==========

// 1. Типы времени

data class TimeType(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val code: String = "",
    val color: String = "#45B7D1",
    val isBuiltIn: Boolean = false,
    val payMultiplier: Double = 1.0
)

// 2. Категории расходов
data class ExpenseCategory(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val isBuiltIn: Boolean = false
)

// 3. Единицы измерения (ТОЛЬКО ПРЕДУСТАНОВЛЕННЫЕ)
data class UnitOfMeasure(
    val id: String,
    val name: String,
    val shortName: String,
    val isBuiltIn: Boolean = true
)

// 4. Налоги
data class Tax(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val rate: Double = 0.0,
    val isBuiltIn: Boolean = false
)

// ========== ФАБРИКИ ПРЕДУСТАНОВЛЕННЫХ ДАННЫХ ==========

fun defaultUnits(): List<UnitOfMeasure> = listOf(
    UnitOfMeasure("unit_km", "Километр", "км"),
    UnitOfMeasure("unit_hour", "Час", "ч"),
    UnitOfMeasure("unit_piece", "Штука", "шт")
)

fun defaultTimeTypes(): List<TimeType> = listOf(
    TimeType(id = "tt_sick", name = "Больничный", code = "Б", color = "#8395A7", isBuiltIn = true, payMultiplier = 1.0),
    TimeType(id = "tt_evening", name = "Вечерняя смена", code = "В", color = "#48DBFB", isBuiltIn = true, payMultiplier = 1.0),
    TimeType(id = "tt_holiday", name = "Выходные и нерабочие праздничные", code = "ВП", color = "#FF6FB7", isBuiltIn = true, payMultiplier = 2.0),
    TimeType(id = "tt_day", name = "Дневная смена", code = "Д", color = "#5CA02F", isBuiltIn = true, payMultiplier = 1.0),
    TimeType(id = "tt_unpaid_vacation", name = "Неоплачиваемый отпуск", code = "ДО", color = "#10AC84", isBuiltIn = true, payMultiplier = 1.0),
    TimeType(id = "tt_night", name = "Ночная смена", code = "Н", color = "#8395A7", isBuiltIn = true, payMultiplier = 1.4),
    TimeType(id = "tt_paid_vacation", name = "Оплачиваемый отпуск", code = "ОТ", color = "#5F27CD", isBuiltIn = true, payMultiplier = 1.0),
    TimeType(id = "tt_business_trip", name = "Служебная командировка", code = "К", color = "#FF9F43", isBuiltIn = true, payMultiplier = 1.5)
)

fun defaultExpenseCategories(): List<ExpenseCategory> = listOf(
    ExpenseCategory(id = "ec_transport", name = "Проезд", isBuiltIn = true),
    ExpenseCategory(id = "ec_accommodation", name = "Проживание", isBuiltIn = true),
    ExpenseCategory(id = "ec_other", name = "Прочее", isBuiltIn = true),
    ExpenseCategory(id = "ec_fuel", name = "Топливо/километраж", isBuiltIn = true)
)

fun defaultTaxes(): List<Tax> = listOf(
    Tax(id = "tax_ndfl", name = "НДФЛ", rate = 13.0, isBuiltIn = true)
)

data class AppState(
    val employees: List<Employee> = emptyList(),
    val organizations: List<Organization> = emptyList(),
    val entries: List<LedgerEntry> = emptyList(),
    val openingBalance: Double = 0.0,
    val surcharges: List<Surcharge> = emptyList(),
    val shiftTemplates: List<ShiftTemplate> = emptyList(),
    val timeTypes: List<TimeType> = defaultTimeTypes(),
    val expenseCategories: List<ExpenseCategory> = defaultExpenseCategories(),
    val units: List<UnitOfMeasure> = defaultUnits(),
    val taxes: List<Tax> = defaultTaxes(),
    val employeeOrgLinks: List<EmployeeOrgLink> = emptyList(),
    val projects: List<Project> = emptyList()
)

// ========== ДОПЛАТЫ И ШАБЛОНЫ ==========

enum class SurchargeKind { BONUS, DEDUCTION }
enum class SurchargeCalcType { FIXED_PER_SHIFT, PER_HOUR, PERCENT }

data class Surcharge(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val kind: SurchargeKind = SurchargeKind.BONUS,
    val calcType: SurchargeCalcType = SurchargeCalcType.FIXED_PER_SHIFT,
    val amount: Double = 0.0,
    val taxable: Boolean = true,
    val activeWeekdays: Set<Int> = emptySet(),
    val isBuiltIn: Boolean = false
)

data class ShiftTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val employeeId: String? = null,
    val organizationId: String? = null,
    val shiftTypeName: String = "",
    val projectName: String = "",
    val surchargeIds: List<String> = emptyList(),
    val comment: String = ""
)

/** Проект в рамках конкретной организации (вкладка «Проекты» в карточке организации). */
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val organizationId: String = "",
    val name: String = ""
)

fun defaultSurchargeLibrary(): List<Surcharge> = listOf(
    Surcharge(
        id = "builtin_premium", name = "Премия",
        kind = SurchargeKind.BONUS, calcType = SurchargeCalcType.FIXED_PER_SHIFT, isBuiltIn = true
    ),
    Surcharge(
        id = "builtin_weekend", name = "Выходные дни",
        kind = SurchargeKind.BONUS, calcType = SurchargeCalcType.PERCENT, isBuiltIn = true
    ),
    Surcharge(
        id = "builtin_night", name = "Ночные часы",
        kind = SurchargeKind.BONUS, calcType = SurchargeCalcType.PER_HOUR, isBuiltIn = true
    ),
    Surcharge(
        id = "builtin_holiday", name = "Праздничные дни",
        kind = SurchargeKind.BONUS, calcType = SurchargeCalcType.PERCENT, isBuiltIn = true
    ),
    Surcharge(
        id = "builtin_piecework", name = "Пример сдельной работы",
        kind = SurchargeKind.BONUS, calcType = SurchargeCalcType.PER_HOUR, isBuiltIn = true
    )
)