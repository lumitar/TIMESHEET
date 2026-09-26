package com.example.timesheet.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import java.io.File
import java.time.Instant
import java.time.ZoneId

object LegacyImporter {

    // Ставки и оплата смены (shifts.hourly_rate, shifts.regular_pay/ot_pay,
    // employees_a.rate) — в присланных файлах реалистичны при делении на 1000.
    private const val RATE_SCALE = 1000.0

    // Суммы расходов, выплат и налогов (exp.amount, exp.rate, payouts.amount,
    // tax_doc.amount) — в присланных файлах реалистичны при делении на 100.
    private const val AMOUNT_SCALE = 100.0

    data class Summary(
        val organizations: Int,
        val employees: Int,
        val shifts: Int,
        val expenses: Int,
        val payments: Int,
        val taxes: Int,
        val timeTypes: Int
    ) {
        val totalEntries: Int get() = shifts + expenses + payments + taxes
    }

    /** Импорт из content:// Uri, выбранного через системный выбор файлов. */
    fun importFromUri(context: Context, uri: Uri, current: AppState): Pair<AppState, Summary> {
        val tmp = BackupManager.copyUriToTempFile(context, uri, ".db")
        try {
            return importFromFile(tmp, current)
        } finally {
            tmp.delete()
        }
    }

    private fun importFromFile(dbFile: File, current: AppState): Pair<AppState, Summary> {
        val db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
        val zone = ZoneId.systemDefault()
        try {
            // ---------- организации (employers) ----------
            val orgIdMap = mutableMapOf<Long, String>()
            val organizations = mutableListOf<Organization>()
            db.rawQuery("SELECT _id, name FROM employers", null).use { c ->
                while (c.moveToNext()) {
                    val legacyId = c.getLong(0)
                    val newId = "legacy_org_$legacyId"
                    orgIdMap[legacyId] = newId
                    organizations.add(Organization(id = newId, name = c.getString(1) ?: "Организация"))
                }
            }

            // ---------- ставка на каждую employees_a (связка исполнитель+заказчик) ----------
            val rateByEmployeesA = mutableMapOf<Long, Double>()
            val employerByEmployeesA = mutableMapOf<Long, Long>()
            val personByEmployeesA = mutableMapOf<Long, Long>() // employees_a._id -> employees._id
            db.rawQuery("SELECT _id, employer_id, employee_id, rate FROM employees_a", null).use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    employerByEmployeesA[id] = c.getLong(1)
                    personByEmployeesA[id] = c.getLong(2)
                    rateByEmployeesA[id] = c.getLong(3) / RATE_SCALE
                }
            }

            // ---------- исполнители (employees) ----------
            // В старом приложении это, как правило, один и тот же человек — владелец
            // бекапа, работающий на разных организациях. Ставку берём как максимальную
            // из его ставок у разных заказчиков (просто чтобы было ненулевое значение
            // по умолчанию для новых записей — на уже перенесённые смены это не влияет,
            // у них сумма проставляется напрямую).
            val employeeIdMap = mutableMapOf<Long, String>()
            val employees = mutableListOf<Employee>()
            db.rawQuery("SELECT _id, name FROM employees", null).use { c ->
                while (c.moveToNext()) {
                    val legacyId = c.getLong(0)
                    val newId = "legacy_emp_$legacyId"
                    val rate = rateByEmployeesA.filterKeys { personByEmployeesA[it] == legacyId }
                        .values.maxOrNull() ?: 0.0
                    employeeIdMap[legacyId] = newId
                    employees.add(
                        Employee(
                            id = newId,
                            name = c.getString(1)?.takeIf { it.isNotBlank() } ?: "Сотрудник",
                            hourlyRate = rate
                        )
                    )
                }
            }

            // shifts.employee_id/exp_doc.employee_id и т.п. ссылаются на employees_a._id,
            // а не на employees._id напрямую — приводим их к нашему Employee.id.
            fun resolveEmployeeId(employeesAId: Long?): String? {
                if (employeesAId == null || employeesAId == 0L) return null
                val personId = personByEmployeesA[employeesAId] ?: return null
                return employeeIdMap[personId]
            }

            // ---------- типы времени (time_types) ----------
            val timeTypeIdMap = mutableMapOf<Long, String>()
            val timeTypes = mutableListOf<TimeType>()
            db.rawQuery("SELECT _id, name, code, color FROM time_types", null).use { c ->
                while (c.moveToNext()) {
                    val legacyId = c.getLong(0)
                    val newId = "legacy_tt_$legacyId"
                    timeTypeIdMap[legacyId] = newId
                    timeTypes.add(
                        TimeType(
                            id = newId,
                            name = c.getString(1) ?: "Тип времени",
                            code = c.getString(2) ?: "",
                            color = colorToHex(c.getInt(3)),
                            isBuiltIn = false
                        )
                    )
                }
            }

            // ---------- типы смен (shift_types) -> какой time_type использовать ----------
            val shiftTypeToTimeType = mutableMapOf<Long, Long>()
            db.rawQuery("SELECT _id, timetype_id FROM shift_types", null).use { c ->
                while (c.moveToNext()) shiftTypeToTimeType[c.getLong(0)] = c.getLong(1)
            }

            // ---------- проекты (projects_a) -> LedgerEntry.projectName ----------
            val projectNameById = mutableMapOf<Long, String>()
            db.rawQuery("SELECT _id, name FROM projects_a", null).use { c ->
                while (c.moveToNext()) projectNameById[c.getLong(0)] = c.getString(1) ?: ""
            }

            // ---------- налоги (taxes) ----------
            val taxIdMap = mutableMapOf<Long, String>()
            val taxes = mutableListOf<Tax>()
            db.rawQuery("SELECT _id, name, percent FROM taxes", null).use { c ->
                while (c.moveToNext()) {
                    val legacyId = c.getLong(0)
                    val newId = "legacy_tax_$legacyId"
                    taxIdMap[legacyId] = newId
                    taxes.add(Tax(id = newId, name = c.getString(1) ?: "Налог", rate = c.getDouble(2)))
                }
            }

            // ---------- категории расходов (ecats) ----------
            val expenseCategories = mutableListOf<ExpenseCategory>()
            db.rawQuery("SELECT _id, name FROM ecats", null).use { c ->
                while (c.moveToNext()) {
                    expenseCategories.add(
                        ExpenseCategory(id = "legacy_ecat_${c.getLong(0)}", name = c.getString(1) ?: "Категория")
                    )
                }
            }

            // ---------- единицы измерения (m_units) ----------
            val units = mutableListOf<UnitOfMeasure>()
            db.rawQuery("SELECT _id, code, name FROM m_units", null).use { c ->
                while (c.moveToNext()) {
                    units.add(
                        UnitOfMeasure(
                            id = "legacy_unit_${c.getLong(0)}",
                            name = c.getString(2) ?: "",
                            shortName = c.getString(1) ?: ""
                        )
                    )
                }
            }

            val entries = mutableListOf<LedgerEntry>()

            // ---------- смены (shifts) ----------
            db.rawQuery(
                "SELECT _id, start_date, end_date, employer_id, employee_id, shift_type_id, project_id, " +
                    "regular_hours, regular_pay, comment, ot_pay, ot_pay2, overtime_pay FROM shifts",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    val legacyId = c.getLong(0)
                    val start = Instant.ofEpochMilli(c.getLong(1)).atZone(zone)
                    val end = Instant.ofEpochMilli(c.getLong(2)).atZone(zone)
                    val employerId = c.getLong(3)
                    val employeesAId = c.getLong(4)
                    val shiftTypeId = c.getLong(5)
                    val projectId = c.getLong(6)
                    val regularHoursMin = c.getInt(7)
                    val totalPayRaw = c.getLong(8) + c.getLong(10) + c.getLong(11) + c.getLong(12)
                    val comment = c.getString(9) ?: ""

                    val timeTypeId = shiftTypeToTimeType[shiftTypeId]?.let { timeTypeIdMap[it] }
                    val shiftType = inferShiftTypeFromTimeType(timeTypes.find { it.id == timeTypeId })

                    entries.add(
                        LedgerEntry(
                            id = "legacy_shift_$legacyId",
                            date = start.toLocalDate(),
                            startTime = start.toLocalTime(),
                            endTime = end.toLocalTime(),
                            shiftType = shiftType,
                            type = EntryType.SHIFT,
                            employeeId = resolveEmployeeId(employeesAId),
                            organizationId = orgIdMap[employerId],
                            amount = totalPayRaw / RATE_SCALE,
                            hours = regularHoursMin / 60.0,
                            note = comment,
                            projectName = projectNameById[projectId] ?: "",
                            timeTypeId = timeTypeId
                        )
                    )
                }
            }

            // ---------- расходы (exp + exp_doc) ----------
            db.rawQuery(
                "SELECT e._id, e.doc_date, e.amount, e.comment, e.merchant, d.employer_id, d.employee_id " +
                    "FROM exp e LEFT JOIN exp_doc d ON e.exp_doc_id = d._id",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    val legacyId = c.getLong(0)
                    val date = Instant.ofEpochMilli(c.getLong(1)).atZone(zone).toLocalDate()
                    val amount = kotlin.math.abs(c.getLong(2) / AMOUNT_SCALE)
                    val merchant = c.getString(4) ?: ""
                    val comment = c.getString(3) ?: ""
                    val employerId = c.getLong(5)
                    val employeesAId = c.getLong(6)

                    entries.add(
                        LedgerEntry(
                            id = "legacy_exp_$legacyId",
                            date = date,
                            type = EntryType.EXPENSE,
                            employeeId = resolveEmployeeId(employeesAId),
                            organizationId = orgIdMap[employerId],
                            amount = amount,
                            note = listOf(merchant, comment).filter { it.isNotBlank() }.joinToString(" · ")
                        )
                    )
                }
            }

            // ---------- выплаты (payouts) ----------
            db.rawQuery(
                "SELECT _id, doc_date, employer_id, employee_id, amount, comment FROM payouts",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    val legacyId = c.getLong(0)
                    val date = Instant.ofEpochMilli(c.getLong(1)).atZone(zone).toLocalDate()
                    val employerId = c.getLong(2)
                    val employeesAId = c.getLong(3)
                    entries.add(
                        LedgerEntry(
                            id = "legacy_payout_$legacyId",
                            date = date,
                            type = EntryType.PAYMENT,
                            employeeId = resolveEmployeeId(employeesAId),
                            organizationId = orgIdMap[employerId],
                            amount = c.getLong(4) / AMOUNT_SCALE,
                            note = c.getString(5) ?: ""
                        )
                    )
                }
            }

            // ---------- налоговые документы (tax_doc) ----------
            db.rawQuery(
                "SELECT _id, doc_date, employer_id, employee_id, amount, comment FROM tax_doc",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    val legacyId = c.getLong(0)
                    val date = Instant.ofEpochMilli(c.getLong(1)).atZone(zone).toLocalDate()
                    val employerId = c.getLong(2)
                    val employeesAId = c.getLong(3)
                    entries.add(
                        LedgerEntry(
                            id = "legacy_taxdoc_$legacyId",
                            date = date,
                            type = EntryType.TAX,
                            employeeId = resolveEmployeeId(employeesAId),
                            organizationId = orgIdMap[employerId],
                            amount = c.getLong(4) / AMOUNT_SCALE,
                            note = c.getString(5) ?: ""
                        )
                    )
                }
            }

            val merged = current.copy(
                organizations = mergeById(current.organizations, organizations) { it.id },
                employees = mergeById(current.employees, employees) { it.id },
                entries = mergeById(current.entries, entries) { it.id },
                timeTypes = mergeById(current.timeTypes, timeTypes) { it.id },
                taxes = mergeById(current.taxes, taxes) { it.id },
                expenseCategories = mergeById(current.expenseCategories, expenseCategories) { it.id },
                units = mergeById(current.units, units) { it.id }
            )

            val summary = Summary(
                organizations = organizations.size,
                employees = employees.size,
                shifts = entries.count { it.type == EntryType.SHIFT },
                expenses = entries.count { it.type == EntryType.EXPENSE },
                payments = entries.count { it.type == EntryType.PAYMENT },
                taxes = entries.count { it.type == EntryType.TAX },
                timeTypes = timeTypes.size
            )
            return merged to summary
        } finally {
            if (db.isOpen) db.close()
        }
    }

    /** Добавляет только те записи, id которых ещё нет — повторный импорт того же файла безопасен. */
    private fun <T> mergeById(existing: List<T>, imported: List<T>, idOf: (T) -> String): List<T> {
        val existingIds = existing.map(idOf).toSet()
        return existing + imported.filter { idOf(it) !in existingIds }
    }

    private fun colorToHex(colorInt: Int): String = "#%06X".format(colorInt and 0xFFFFFF)
}
