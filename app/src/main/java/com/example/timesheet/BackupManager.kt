package com.example.timesheet.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class BackupFile(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val createdAtMillis: Long
)

object BackupManager {

    private const val BACKUP_EXTENSION = "bd"

    private fun backupsDir(context: Context): File =
        File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }

    fun listBackups(context: Context): List<BackupFile> =
        backupsDir(context).listFiles { f -> f.extension == BACKUP_EXTENSION || f.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?.map { BackupFile(it, it.nameWithoutExtension, it.length(), it.lastModified()) }
            ?: emptyList()

    fun createBackup(
        context: Context,
        state: AppState,
        settings: JSONObject,
        label: String? = null
    ): File {
        val root = JSONObject()
        root.put("version", 1)
        root.put("state", stateToJson(state))
        root.put("settings", settings)

        val safeLabel = (label?.takeIf { it.isNotBlank() } ?: "Зеленый ТабельBackup")
            .replace(Regex("[^A-Za-zА-Яа-я0-9 _-]"), "")
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
        val file = File(backupsDir(context), "${safeLabel}_$timestamp.bd")
        file.writeText(root.toString(2))

        runCatching { copyToPublicDownloads(context, file) }
            .onSuccess { lastPublicExportFolder = it }
        return file
    }

    /** Путь папки (внутри "Download"), куда был скопирован последний бекап. */
    var lastPublicExportFolder: String? = null
        private set

    fun restoreBackup(file: File): Pair<AppState, JSONObject> {
        val root = JSONObject(file.readText())
        val state = jsonToState(root.getJSONObject("state"))
        val settings = root.optJSONObject("settings") ?: JSONObject()
        return state to settings
    }

    fun deleteBackup(file: File) = file.delete()

    private const val PUBLIC_BACKUP_ROOT = "ТабельБекапы"

    /**
     * Копирует файл бекапа в общедоступную папку "Download/ТабельБекапы/<дата и
     * время>" — при каждом экспорте создаётся своя новая подпапка, чтобы бекапы
     * разных дней не путались между собой. На Android 10+ используется
     * MediaStore (разрешений не требует), на более старых версиях — прямая
     * запись в публичную папку "Загрузки".
     */
    private fun copyToPublicDownloads(context: Context, file: File): String {
        val folderName = DateTimeFormatter.ofPattern("dd.MM.yyyy_HH-mm-ss").format(LocalDateTime.now())
        val relativeFolder = "$PUBLIC_BACKUP_ROOT/$folderName"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$relativeFolder")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Не удалось создать файл в Загрузках")
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), relativeFolder)
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, file.name)
            file.copyTo(outFile, overwrite = true)
        }
        return relativeFolder
    }




    fun exportBackupToUri(context: Context, file: File, targetUri: Uri): Boolean = runCatching {
        context.contentResolver.openOutputStream(targetUri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        } ?: return false
        true
    }.getOrDefault(false)


    fun copyUriToTempFile(context: Context, uri: Uri, suffix: String): File {
        val tmp = File.createTempFile("import_", suffix, context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Не удалось прочитать выбранный файл")
        return tmp
    }

    fun stateToJson(state: AppState): JSONObject {
        val root = JSONObject()
        root.put("openingBalance", state.openingBalance)

        val employees = JSONArray()
        state.employees.forEach {
            employees.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("hourlyRate", it.hourlyRate)
                put("phone", it.phone)
                put("photoUri", it.photoUri ?: JSONObject.NULL)
                put("contactLookupKey", it.contactLookupKey ?: JSONObject.NULL)
            })
        }
        root.put("employees", employees)

        val employeeOrgLinks = JSONArray()
        state.employeeOrgLinks.forEach {
            employeeOrgLinks.put(JSONObject().apply {
                put("employeeId", it.employeeId)
                put("organizationId", it.organizationId)
                put("hourlyRate", it.hourlyRate)
                put("openingBalance", it.openingBalance)
            })
        }
        root.put("employeeOrgLinks", employeeOrgLinks)

        val projects = JSONArray()
        state.projects.forEach {
            projects.put(JSONObject().apply {
                put("id", it.id)
                put("organizationId", it.organizationId)
                put("name", it.name)
            })
        }
        root.put("projects", projects)

        val organizations = JSONArray()
        state.organizations.forEach {
            organizations.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
            })
        }
        root.put("organizations", organizations)

        val entries = JSONArray()
        state.entries.forEach {
            entries.put(JSONObject().apply {
                put("id", it.id)
                put("date", it.date.toString())
                put("type", it.type.name)
                put("employeeId", it.employeeId ?: JSONObject.NULL)
                put("organizationId", it.organizationId ?: JSONObject.NULL)
                put("amount", it.amount)
                put("hours", it.hours)
                put("note", it.note)
                put("expenseCategoryId", it.expenseCategoryId ?: JSONObject.NULL)
                put("unitId", it.unitId ?: JSONObject.NULL)
                put("quantity", it.quantity)

                put("surchargeIds", JSONArray(it.surchargeIds))

                put("startTime", it.startTime?.toString() ?: JSONObject.NULL)
                put("endTime", it.endTime?.toString() ?: JSONObject.NULL)
                put("shiftType", it.shiftType.name)

                put("unpaidBreakMinutes", it.unpaidBreakMinutes)
                put("overtimeEnabled", it.overtimeEnabled)
                put("projectName", it.projectName)

                put("attachments", JSONArray(it.attachments))

                put("timeTypeId", it.timeTypeId ?: JSONObject.NULL)
                put("adjustmentTypeName", it.adjustmentTypeName)
            })
        }
        root.put("entries", entries)

        val surcharges = JSONArray()
        state.surcharges.forEach {
            surcharges.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("kind", it.kind.name)
                put("calcType", it.calcType.name)
                put("amount", it.amount)
                put("taxable", it.taxable)
                put("activeWeekdays", JSONArray(it.activeWeekdays.toList()))
                put("isBuiltIn", it.isBuiltIn)
            })
        }
        root.put("surcharges", surcharges)

        val shiftTemplates = JSONArray()
        state.shiftTemplates.forEach {
            shiftTemplates.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("employeeId", it.employeeId ?: JSONObject.NULL)
                put("organizationId", it.organizationId ?: JSONObject.NULL)
                put("shiftTypeName", it.shiftTypeName)
                put("projectName", it.projectName)
                put("surchargeIds", JSONArray(it.surchargeIds))
                put("comment", it.comment)
            })
        }
        root.put("shiftTemplates", shiftTemplates)

        val timeTypes = JSONArray()
        state.timeTypes.forEach {
            timeTypes.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("code", it.code)
                put("color", it.color)
                put("isBuiltIn", it.isBuiltIn)
            })
        }
        root.put("timeTypes", timeTypes)

        val expenseCategories = JSONArray()
        state.expenseCategories.forEach {
            expenseCategories.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("isBuiltIn", it.isBuiltIn)
            })
        }
        root.put("expenseCategories", expenseCategories)

        val units = JSONArray()
        state.units.forEach {
            units.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("shortName", it.shortName)
                put("isBuiltIn", it.isBuiltIn)
            })
        }
        root.put("units", units)

        val taxes = JSONArray()
        state.taxes.forEach {
            taxes.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("rate", it.rate)
                put("isBuiltIn", it.isBuiltIn)
            })
        }
        root.put("taxes", taxes)

        return root
    }

    fun jsonToState(root: JSONObject): AppState {
        val employees = mutableListOf<Employee>()
        root.optJSONArray("employees")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                employees.add(
                    Employee(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        hourlyRate = o.optDouble("hourlyRate", 0.0),
                        phone = o.optString("phone", ""),
                        photoUri = if (o.isNull("photoUri")) null else o.optString("photoUri"),
                        contactLookupKey = if (o.isNull("contactLookupKey")) null else o.optString("contactLookupKey")
                    )
                )
            }
        }

        val employeeOrgLinks = mutableListOf<EmployeeOrgLink>()
        root.optJSONArray("employeeOrgLinks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                employeeOrgLinks.add(
                    EmployeeOrgLink(
                        employeeId = o.getString("employeeId"),
                        organizationId = o.getString("organizationId"),
                        hourlyRate = o.optDouble("hourlyRate", 0.0),
                        openingBalance = o.optDouble("openingBalance", 0.0)
                    )
                )
            }
        }

        val organizations = mutableListOf<Organization>()
        root.optJSONArray("organizations")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                organizations.add(Organization(id = o.getString("id"), name = o.getString("name")))
            }
        }

        val entries = mutableListOf<LedgerEntry>()
        root.optJSONArray("entries")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val surchargeIds = mutableListOf<String>()
                o.optJSONArray("surchargeIds")?.let { ids ->
                    for (j in 0 until ids.length()) surchargeIds.add(ids.getString(j))
                }
                val attachments = mutableListOf<String>()
                o.optJSONArray("attachments")?.let { atts ->
                    for (j in 0 until atts.length()) attachments.add(atts.getString(j))
                }
                entries.add(
                    LedgerEntry(
                        id = o.getString("id"),
                        date = LocalDate.parse(o.getString("date")),
                        type = EntryType.valueOf(o.getString("type")),
                        employeeId = if (o.isNull("employeeId")) null else o.getString("employeeId"),
                        organizationId = if (o.isNull("organizationId")) null else o.getString("organizationId"),
                        amount = o.optDouble("amount", 0.0),
                        hours = o.optDouble("hours", 0.0),
                        note = o.optString("note", ""),
                        expenseCategoryId = if (o.isNull("expenseCategoryId")) null else o.getString("expenseCategoryId"),
                        unitId = if (o.isNull("unitId")) null else o.getString("unitId"),
                        quantity = o.optDouble("quantity", 0.0),
                        surchargeIds = surchargeIds,
                        startTime = if (o.isNull("startTime")) null else runCatching { LocalTime.parse(o.getString("startTime")) }.getOrNull(),
                        endTime = if (o.isNull("endTime")) null else runCatching { LocalTime.parse(o.getString("endTime")) }.getOrNull(),
                        shiftType = runCatching { ShiftType.valueOf(o.optString("shiftType", "DAY")) }.getOrDefault(ShiftType.DAY),
                        unpaidBreakMinutes = o.optInt("unpaidBreakMinutes", 0),
                        overtimeEnabled = o.optBoolean("overtimeEnabled", false),
                        projectName = o.optString("projectName", ""),
                        attachments = attachments
                    )
                )
            }
        }

        val surcharges = mutableListOf<Surcharge>()
        root.optJSONArray("surcharges")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val weekdays = mutableSetOf<Int>()
                o.optJSONArray("activeWeekdays")?.let { wd ->
                    for (j in 0 until wd.length()) weekdays.add(wd.getInt(j))
                }
                surcharges.add(
                    Surcharge(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        kind = runCatching { SurchargeKind.valueOf(o.getString("kind")) }.getOrDefault(SurchargeKind.BONUS),
                        calcType = runCatching { SurchargeCalcType.valueOf(o.getString("calcType")) }.getOrDefault(SurchargeCalcType.FIXED_PER_SHIFT),
                        amount = o.optDouble("amount", 0.0),
                        taxable = o.optBoolean("taxable", true),
                        activeWeekdays = weekdays,
                        isBuiltIn = o.optBoolean("isBuiltIn", false)
                    )
                )
            }
        }

        val shiftTemplates = mutableListOf<ShiftTemplate>()
        root.optJSONArray("shiftTemplates")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val surchargeIds = mutableListOf<String>()
                o.optJSONArray("surchargeIds")?.let { ids ->
                    for (j in 0 until ids.length()) surchargeIds.add(ids.getString(j))
                }
                shiftTemplates.add(
                    ShiftTemplate(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        employeeId = if (o.isNull("employeeId")) null else o.getString("employeeId"),
                        organizationId = if (o.isNull("organizationId")) null else o.getString("organizationId"),
                        shiftTypeName = o.optString("shiftTypeName", ""),
                        projectName = o.optString("projectName", ""),
                        surchargeIds = surchargeIds,
                        comment = o.optString("comment", "")
                    )
                )
            }
        }

        val timeTypes = mutableListOf<TimeType>()
        root.optJSONArray("timeTypes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                timeTypes.add(
                    TimeType(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        code = o.optString("code", ""),
                        color = o.optString("color", "#45B7D1"),
                        isBuiltIn = o.optBoolean("isBuiltIn", false)
                    )
                )
            }
        }

        val expenseCategories = mutableListOf<ExpenseCategory>()
        root.optJSONArray("expenseCategories")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                expenseCategories.add(
                    ExpenseCategory(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        isBuiltIn = o.optBoolean("isBuiltIn", false)
                    )
                )
            }
        }

        val units = mutableListOf<UnitOfMeasure>()
        root.optJSONArray("units")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                units.add(
                    UnitOfMeasure(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        shortName = o.optString("shortName", ""),
                        isBuiltIn = o.optBoolean("isBuiltIn", true)
                    )
                )
            }
        }

        val taxes = mutableListOf<Tax>()
        root.optJSONArray("taxes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                taxes.add(
                    Tax(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        rate = o.optDouble("rate", 0.0),
                        isBuiltIn = o.optBoolean("isBuiltIn", false)
                    )
                )
            }
        }

        val projects = mutableListOf<Project>()
        root.optJSONArray("projects")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                projects.add(
                    Project(
                        id = o.getString("id"),
                        organizationId = o.optString("organizationId", ""),
                        name = o.optString("name", "")
                    )
                )
            }
        }

        return AppState(
            employees = employees,
            organizations = organizations,
            entries = entries,
            openingBalance = root.optDouble("openingBalance", 0.0),
            surcharges = surcharges,
            shiftTemplates = shiftTemplates,
            timeTypes = timeTypes,
            expenseCategories = expenseCategories,
            units = units,
            taxes = taxes,
            employeeOrgLinks = employeeOrgLinks,
            projects = projects
        )
    }
}