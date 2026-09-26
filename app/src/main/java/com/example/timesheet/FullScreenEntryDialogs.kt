package com.example.timesheet.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timesheet.data.Employee
import com.example.timesheet.data.EntryType
import com.example.timesheet.data.ExpenseCategory
import com.example.timesheet.data.LedgerEntry
import com.example.timesheet.data.Organization
import com.example.timesheet.data.TimeType
import com.example.timesheet.data.inferShiftTypeFromTimeType
import com.example.timesheet.data.moneyInputFilter
import com.example.timesheet.data.dateRangeDays
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private val EntryDialogGreen = Color(0xFF5CA02F)

private fun entryDateFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy 'г.' EEE", java.util.Locale("ru"))

private fun entryTimeFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", java.util.Locale("ru"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmountEntryDialog(
    title: String,
    entryType: EntryType,
    employees: List<Employee>,
    organizations: List<Organization>,
    preselectedEmployeeId: String?,
    preselectedOrganizationId: String?,
    initialEntry: LedgerEntry? = null,
    initialDate: LocalDate = LocalDate.now(),
    onClose: () -> Unit,
    onConfirm: (LedgerEntry) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    val isAddMode = onDelete == null
    val stableEntryId = remember { initialEntry?.id ?: UUID.randomUUID().toString() }
    var rangeEnabled by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    var date by remember { mutableStateOf(initialEntry?.date ?: initialDate) }
    var rangeEndDate by remember { mutableStateOf(initialEntry?.date ?: initialDate) }
    var time by remember { mutableStateOf(initialEntry?.startTime ?: LocalTime.now()) }
    var employeeId by remember { mutableStateOf(initialEntry?.employeeId ?: preselectedEmployeeId) }
    var organizationId by remember { mutableStateOf(initialEntry?.organizationId ?: preselectedOrganizationId) }
    // Применяем фильтр к денежному полю
    var amountText by remember {
        mutableStateOf(if ((initialEntry?.amount ?: 0.0) == 0.0) "" else initialEntry!!.amount.toString())
    }
    var comment by remember { mutableStateOf(initialEntry?.note ?: "") }

    var employeeMenuOpen by remember { mutableStateOf(false) }
    var organizationMenuOpen by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val employeeName = employees.firstOrNull { it.id == employeeId }?.name ?: ""
    val organizationName = organizations.firstOrNull { it.id == organizationId }?.name ?: ""
    val dateFormatter = entryDateFormatter()
    val timeFormatter = entryTimeFormatter()

    fun buildEntry() = LedgerEntry(
        id = stableEntryId,
        date = date,
        startTime = time,
        endTime = null,
        type = entryType,
        employeeId = employeeId,
        organizationId = organizationId,
        amount = amountText.replace(',', '.').toDoubleOrNull() ?: 0.0,
        note = comment
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(enabled = !isSaving, onClick = {
                        if (isSaving) return@IconButton
                        isSaving = true
                        if (rangeEnabled && isAddMode) {
                            dateRangeDays(date, rangeEndDate).forEach { d ->
                                onConfirm(buildEntry().copy(id = UUID.randomUUID().toString(), date = d))
                            }
                        } else {
                            onConfirm(buildEntry())
                        }
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = "Сохранить", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EntryDialogGreen)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = date.format(dateFormatter),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Дата") },
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        TextButton(onClick = { showDatePicker = true }) { Text("📅") }
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = time.format(timeFormatter),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Время") },
                    modifier = Modifier.width(120.dp),
                    trailingIcon = {
                        TextButton(onClick = { showTimePicker = true }) { Text("🕐") }
                    }
                )
            }

            if (isAddMode) {
                Spacer(modifier = Modifier.height(12.dp))
                DateRangeToggle(
                    rangeEnabled = rangeEnabled,
                    onRangeEnabledChange = {
                        rangeEnabled = it
                        if (it && rangeEndDate.isBefore(date)) rangeEndDate = date
                    },
                    startDate = date,
                    endDate = rangeEndDate,
                    onStartDateChange = { date = it },
                    onEndDateChange = { rangeEndDate = it },
                    dateFormatter = dateFormatter
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = employeeName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Сотрудник") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = { employeeMenuOpen = true }) { Text("▼") }
                    }
                )
                DropdownMenu(expanded = employeeMenuOpen, onDismissRequest = { employeeMenuOpen = false }) {
                    employees.forEach { e ->
                        DropdownMenuItem(
                            text = { Text(e.name) },
                            onClick = { employeeId = e.id; employeeMenuOpen = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = organizationName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Организация") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = { organizationMenuOpen = true }) { Text("▼") }
                    }
                )
                DropdownMenu(expanded = organizationMenuOpen, onDismissRequest = { organizationMenuOpen = false }) {
                    organizations.forEach { o ->
                        DropdownMenuItem(
                            text = { Text(o.name) },
                            onClick = { organizationId = o.id; organizationMenuOpen = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = moneyInputFilter(it) },
                label = { Text("Сумма") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("Комментарий") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            if (onDelete != null && initialEntry != null) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = { onDelete(initialEntry.id) }) {
                    Text("Удалить запись", color = Color.Red)
                }
            }
        }
    }

    if (showDatePicker) {
        AppDatePickerDialog(
            initialDate = date,
            onDismiss = { showDatePicker = false },
            onConfirm = { date = it; showDatePicker = false }
        )
    }
    if (showTimePicker) {
        AppTimePickerDialog(
            initialTime = time,
            onDismiss = { showTimePicker = false },
            onConfirm = { time = it; showTimePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimesheetEntryDialog(
    employees: List<Employee>,
    organizations: List<Organization>,
    timeTypes: List<TimeType>,
    preselectedEmployeeId: String?,
    preselectedOrganizationId: String?,
    initialDate: LocalDate = LocalDate.now(),
    onClose: () -> Unit,
    onConfirm: (List<LedgerEntry>) -> Unit
) {
    var startDate by remember { mutableStateOf(initialDate) }
    var endDate by remember { mutableStateOf(initialDate) }
    var employeeId by remember { mutableStateOf(preselectedEmployeeId) }
    var organizationId by remember { mutableStateOf(preselectedOrganizationId) }
    var timeTypeId by remember { mutableStateOf<String?>(null) }
    var comment by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    var employeeMenuOpen by remember { mutableStateOf(false) }
    var organizationMenuOpen by remember { mutableStateOf(false) }
    var timeTypeMenuOpen by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val employeeName = employees.firstOrNull { it.id == employeeId }?.name ?: ""
    val organizationName = organizations.firstOrNull { it.id == organizationId }?.name ?: ""
    val timeTypeName = timeTypes.firstOrNull { it.id == timeTypeId }?.name ?: ""
    val dateFormatter = entryDateFormatter()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text("Запись табеля", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(enabled = !isSaving, onClick = {
                        if (isSaving) return@IconButton
                        if (startDate.isAfter(endDate)) return@IconButton
                        isSaving = true
                        val days = generateSequence(startDate) { d ->
                            if (d.isBefore(endDate)) d.plusDays(1) else null
                        }.toList()
                        onConfirm(
                            days.map { d ->
                                LedgerEntry(
                                    date = d,
                                    type = EntryType.SHIFT,
                                    employeeId = employeeId,
                                    organizationId = organizationId,
                                    shiftType = inferShiftTypeFromTimeType(timeTypes.find { it.id == timeTypeId }),
                                    timeTypeId = timeTypeId,
                                    note = comment
                                )
                            }
                        )
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = "Сохранить", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EntryDialogGreen)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = startDate.format(dateFormatter),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("С") },
                    modifier = Modifier.weight(1f),
                    trailingIcon = { TextButton(onClick = { showStartDatePicker = true }) { Text("📅") } }
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = endDate.format(dateFormatter),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("По") },
                    modifier = Modifier.weight(1f),
                    trailingIcon = { TextButton(onClick = { showEndDatePicker = true }) { Text("📅") } }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = organizationName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Организация") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { TextButton(onClick = { organizationMenuOpen = true }) { Text("▼") } }
                )
                DropdownMenu(expanded = organizationMenuOpen, onDismissRequest = { organizationMenuOpen = false }) {
                    organizations.forEach { o ->
                        DropdownMenuItem(
                            text = { Text(o.name) },
                            onClick = { organizationId = o.id; organizationMenuOpen = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = employeeName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Сотрудник") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { TextButton(onClick = { employeeMenuOpen = true }) { Text("▼") } }
                )
                DropdownMenu(expanded = employeeMenuOpen, onDismissRequest = { employeeMenuOpen = false }) {
                    employees.forEach { e ->
                        DropdownMenuItem(
                            text = { Text(e.name) },
                            onClick = { employeeId = e.id; employeeMenuOpen = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = timeTypeName.ifEmpty { "Не выбран" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Тип времени") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { TextButton(onClick = { timeTypeMenuOpen = true }) { Text("▼") } }
                )
                DropdownMenu(expanded = timeTypeMenuOpen, onDismissRequest = { timeTypeMenuOpen = false }) {
                    timeTypes.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.name) },
                            onClick = { timeTypeId = t.id; timeTypeMenuOpen = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("Комментарий") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }
    }

    if (showStartDatePicker) {
        AppDatePickerDialog(
            initialDate = startDate,
            onDismiss = { showStartDatePicker = false },
            onConfirm = {
                startDate = it
                if (endDate.isBefore(startDate)) endDate = startDate
                showStartDatePicker = false
            }
        )
    }
    if (showEndDatePicker) {
        AppDatePickerDialog(
            initialDate = endDate,
            onDismiss = { showEndDatePicker = false },
            onConfirm = { endDate = it; showEndDatePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEntryDialog(
    employees: List<Employee>,
    organizations: List<Organization>,
    expenseCategories: List<ExpenseCategory>,
    preselectedEmployeeId: String?,
    preselectedOrganizationId: String?,
    initialEntry: LedgerEntry? = null,
    initialDate: LocalDate = LocalDate.now(),
    onClose: () -> Unit,
    onConfirm: (LedgerEntry) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    val isAddMode = onDelete == null
    var rangeEnabled by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    var date by remember { mutableStateOf(initialEntry?.date ?: initialDate) }
    var rangeEndDate by remember { mutableStateOf(initialEntry?.date ?: initialDate) }
    var time by remember { mutableStateOf(initialEntry?.startTime ?: LocalTime.now()) }
    var employeeId by remember { mutableStateOf(initialEntry?.employeeId ?: preselectedEmployeeId) }
    var organizationId by remember { mutableStateOf(initialEntry?.organizationId ?: preselectedOrganizationId) }
    var expenseCategoryId by remember { mutableStateOf(initialEntry?.expenseCategoryId) }
    var amountText by remember {
        mutableStateOf(if ((initialEntry?.amount ?: 0.0) == 0.0) "" else initialEntry!!.amount.toString())
    }
    var comment by remember { mutableStateOf(initialEntry?.note ?: "") }

    var employeeMenuOpen by remember { mutableStateOf(false) }
    var organizationMenuOpen by remember { mutableStateOf(false) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val employeeName = employees.firstOrNull { it.id == employeeId }?.name ?: ""
    val organizationName = organizations.firstOrNull { it.id == organizationId }?.name ?: ""
    val categoryName = expenseCategories.firstOrNull { it.id == expenseCategoryId }?.name ?: ""
    val dateFormatter = entryDateFormatter()
    val timeFormatter = entryTimeFormatter()

    fun buildEntry() = LedgerEntry(
        id = initialEntry?.id ?: UUID.randomUUID().toString(),
        date = date,
        startTime = time,
        type = EntryType.EXPENSE,
        employeeId = employeeId,
        organizationId = organizationId,
        expenseCategoryId = expenseCategoryId,
        amount = amountText.replace(',', '.').toDoubleOrNull() ?: 0.0,
        note = comment
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text("Расходы", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        enabled = !isSaving,
                        onClick = {
                            if (isSaving) return@IconButton
                            isSaving = true
                            if (rangeEnabled && isAddMode) {
                                dateRangeDays(date, rangeEndDate).forEach { d ->
                                    onConfirm(buildEntry().copy(id = UUID.randomUUID().toString(), date = d))
                                }
                            } else {
                                onConfirm(buildEntry())
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Сохранить", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EntryDialogGreen)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = date.format(dateFormatter),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Дата") },
                    modifier = Modifier.weight(1f),
                    trailingIcon = { TextButton(onClick = { showDatePicker = true }) { Text("📅") } }
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = time.format(timeFormatter),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Время") },
                    modifier = Modifier.width(120.dp),
                    trailingIcon = { TextButton(onClick = { showTimePicker = true }) { Text("🕐") } }
                )
            }

            if (isAddMode) {
                Spacer(modifier = Modifier.height(12.dp))
                DateRangeToggle(
                    rangeEnabled = rangeEnabled,
                    onRangeEnabledChange = {
                        rangeEnabled = it
                        if (it && rangeEndDate.isBefore(date)) rangeEndDate = date
                    },
                    startDate = date,
                    endDate = rangeEndDate,
                    onStartDateChange = { date = it },
                    onEndDateChange = { rangeEndDate = it },
                    dateFormatter = dateFormatter
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = organizationName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Организация") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { TextButton(onClick = { organizationMenuOpen = true }) { Text("▼") } }
                )
                DropdownMenu(expanded = organizationMenuOpen, onDismissRequest = { organizationMenuOpen = false }) {
                    organizations.forEach { o ->
                        DropdownMenuItem(
                            text = { Text(o.name) },
                            onClick = { organizationId = o.id; organizationMenuOpen = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = employeeName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Сотрудник") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { TextButton(onClick = { employeeMenuOpen = true }) { Text("▼") } }
                )
                DropdownMenu(expanded = employeeMenuOpen, onDismissRequest = { employeeMenuOpen = false }) {
                    employees.forEach { e ->
                        DropdownMenuItem(
                            text = { Text(e.name) },
                            onClick = { employeeId = e.id; employeeMenuOpen = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = categoryName.ifEmpty { "Не выбран" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Тип расходов") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { TextButton(onClick = { categoryMenuOpen = true }) { Text("▼") } }
                )
                DropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                    expenseCategories.forEach { c ->
                        DropdownMenuItem(
                            text = { Text(c.name) },
                            onClick = { expenseCategoryId = c.id; categoryMenuOpen = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = moneyInputFilter(it) },
                label = { Text("Сумма") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("Комментарий") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            if (onDelete != null && initialEntry != null) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = { onDelete(initialEntry.id) }) {
                    Text("Удалить запись", color = Color.Red)
                }
            }
        }
    }

    if (showDatePicker) {
        AppDatePickerDialog(
            initialDate = date,
            onDismiss = { showDatePicker = false },
            onConfirm = { date = it; showDatePicker = false }
        )
    }
    if (showTimePicker) {
        AppTimePickerDialog(
            initialTime = time,
            onDismiss = { showTimePicker = false },
            onConfirm = { time = it; showTimePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustmentEntryDialog(
    employees: List<Employee>,
    organizations: List<Organization>,
    projectSuggestions: List<String> = emptyList(),
    adjustmentTypeSuggestions: List<String> = emptyList(),
    preselectedEmployeeId: String?,
    preselectedOrganizationId: String?,
    initialEntry: LedgerEntry? = null,
    initialDate: LocalDate = LocalDate.now(),
    onClose: () -> Unit,
    onConfirm: (LedgerEntry) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    val isAddMode = onDelete == null
    var rangeEnabled by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    var date by remember { mutableStateOf(initialEntry?.date ?: initialDate) }
    var rangeEndDate by remember { mutableStateOf(initialEntry?.date ?: initialDate) }
    var time by remember { mutableStateOf(initialEntry?.startTime ?: LocalTime.now()) }
    var employeeId by remember { mutableStateOf(initialEntry?.employeeId ?: preselectedEmployeeId) }
    var organizationId by remember { mutableStateOf(initialEntry?.organizationId ?: preselectedOrganizationId) }
    var projectName by remember { mutableStateOf(initialEntry?.projectName ?: "") }
    var adjustmentType by remember { mutableStateOf(initialEntry?.adjustmentTypeName ?: "") }
    var amountText by remember {
        mutableStateOf(if ((initialEntry?.amount ?: 0.0) == 0.0) "" else initialEntry!!.amount.toString())
    }
    var comment by remember { mutableStateOf(initialEntry?.note ?: "") }

    var customAdjustmentTypes by remember { mutableStateOf(listOf<String>()) }
    var newAdjustmentType by remember { mutableStateOf("") }
    var showAddAdjustmentTypeDialog by remember { mutableStateOf(false) }

    val adjustmentTypes = (
            listOf(
                "Больничный",
                "Оплата сверхурочных",
                "Оплачиваемый отпуск",
                "Исполнительный лист"
            ) + adjustmentTypeSuggestions + customAdjustmentTypes
            ).distinct()

    var employeeMenuOpen by remember { mutableStateOf(false) }
    var organizationMenuOpen by remember { mutableStateOf(false) }
    var adjustmentTypeMenuOpen by remember { mutableStateOf(false) }
    var projectMenuOpen by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val employeeName = employees.firstOrNull { it.id == employeeId }?.name ?: ""
    val organizationName = organizations.firstOrNull { it.id == organizationId }?.name ?: ""
    val dateFormatter = entryDateFormatter()
    val timeFormatter = entryTimeFormatter()

    fun buildEntry() = LedgerEntry(
        id = initialEntry?.id ?: UUID.randomUUID().toString(),
        date = date,
        startTime = time,
        type = EntryType.ADJUSTMENT,
        employeeId = employeeId,
        organizationId = organizationId,
        projectName = projectName,
        adjustmentTypeName = adjustmentType,
        amount = amountText.replace(',', '.').toDoubleOrNull() ?: 0.0,
        note = comment
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text("Доплата удержание", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        enabled = !isSaving,
                        onClick = {
                            if (isSaving) return@IconButton
                            isSaving = true
                            if (rangeEnabled && isAddMode) {
                                dateRangeDays(date, rangeEndDate).forEach { d ->
                                    onConfirm(buildEntry().copy(id = UUID.randomUUID().toString(), date = d))
                                }
                            } else {
                                onConfirm(buildEntry())
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Сохранить", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EntryDialogGreen)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = date.format(dateFormatter),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Дата") },
                    modifier = Modifier.weight(1f),
                    trailingIcon = { TextButton(onClick = { showDatePicker = true }) { Text("📅") } }
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = time.format(timeFormatter),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Время") },
                    modifier = Modifier.width(120.dp),
                    trailingIcon = { TextButton(onClick = { showTimePicker = true }) { Text("🕐") } }
                )
            }

            if (isAddMode) {
                Spacer(modifier = Modifier.height(12.dp))
                DateRangeToggle(
                    rangeEnabled = rangeEnabled,
                    onRangeEnabledChange = {
                        rangeEnabled = it
                        if (it && rangeEndDate.isBefore(date)) rangeEndDate = date
                    },
                    startDate = date,
                    endDate = rangeEndDate,
                    onStartDateChange = { date = it },
                    onEndDateChange = { rangeEndDate = it },
                    dateFormatter = dateFormatter
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = organizationName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Организация") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { TextButton(onClick = { organizationMenuOpen = true }) { Text("▼") } }
                )
                DropdownMenu(expanded = organizationMenuOpen, onDismissRequest = { organizationMenuOpen = false }) {
                    organizations.forEach { o ->
                        DropdownMenuItem(
                            text = { Text(o.name) },
                            onClick = { organizationId = o.id; organizationMenuOpen = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = employeeName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Сотрудник") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { TextButton(onClick = { employeeMenuOpen = true }) { Text("▼") } }
                )
                DropdownMenu(expanded = employeeMenuOpen, onDismissRequest = { employeeMenuOpen = false }) {
                    employees.forEach { e ->
                        DropdownMenuItem(
                            text = { Text(e.name) },
                            onClick = { employeeId = e.id; employeeMenuOpen = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Проект") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (projectSuggestions.isNotEmpty()) {
                            TextButton(onClick = { projectMenuOpen = true }) { Text("▼") }
                        }
                    }
                )
                if (projectSuggestions.isNotEmpty()) {
                    DropdownMenu(expanded = projectMenuOpen, onDismissRequest = { projectMenuOpen = false }) {
                        projectSuggestions.distinct().forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p) },
                                onClick = { projectName = p; projectMenuOpen = false }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = adjustmentType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Тип доплаты и удержания") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { TextButton(onClick = { adjustmentTypeMenuOpen = true }) { Text("▼") } }
                )
                DropdownMenu(expanded = adjustmentTypeMenuOpen, onDismissRequest = { adjustmentTypeMenuOpen = false }) {
                    adjustmentTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = { adjustmentType = type; adjustmentTypeMenuOpen = false }
                        )
                    }
                    Divider()
                    DropdownMenuItem(
                        text = { Text("+ Добавить свой тип") },
                        onClick = {
                            adjustmentTypeMenuOpen = false
                            showAddAdjustmentTypeDialog = true
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = moneyInputFilter(it) },
                label = { Text("Сумма") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("Комментарий") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            if (onDelete != null && initialEntry != null) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = { onDelete(initialEntry.id) }) {
                    Text("Удалить запись", color = Color.Red)
                }
            }
        }
    }

    if (showDatePicker) {
        AppDatePickerDialog(
            initialDate = date,
            onDismiss = { showDatePicker = false },
            onConfirm = { date = it; showDatePicker = false }
        )
    }
    if (showTimePicker) {
        AppTimePickerDialog(
            initialTime = time,
            onDismiss = { showTimePicker = false },
            onConfirm = { time = it; showTimePicker = false }
        )
    }

    if (showAddAdjustmentTypeDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddAdjustmentTypeDialog = false
                newAdjustmentType = ""
            },
            title = { Text("Новый тип") },
            text = {
                OutlinedTextField(
                    value = newAdjustmentType,
                    onValueChange = { newAdjustmentType = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newType = newAdjustmentType.trim()
                    if (newType.isNotEmpty()) {
                        customAdjustmentTypes = customAdjustmentTypes + newType
                        adjustmentType = newType
                        newAdjustmentType = ""
                        showAddAdjustmentTypeDialog = false
                    }
                }) { Text("Добавить") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddAdjustmentTypeDialog = false
                    newAdjustmentType = ""
                }) { Text("Отмена") }
            }
        )
    }
}