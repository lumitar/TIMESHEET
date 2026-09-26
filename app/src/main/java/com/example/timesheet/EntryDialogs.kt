package com.example.timesheet.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timesheet.data.Employee
import com.example.timesheet.data.EntryType
import com.example.timesheet.data.ExpenseCategory
import com.example.timesheet.data.LedgerEntry
import com.example.timesheet.data.Organization
import com.example.timesheet.data.ShiftType
import com.example.timesheet.data.UnitOfMeasure
import com.example.timesheet.data.moneyInputFilter
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun AddEntryDialog(
    title: String,
    entryType: EntryType,
    showHours: Boolean,
    employees: List<Employee>,
    organizations: List<Organization>,
    expenseCategories: List<ExpenseCategory> = emptyList(),
    units: List<UnitOfMeasure> = emptyList(),
    preselectedEmployeeId: String?,
    preselectedOrganizationId: String?,
    initialNote: String = "",
    initialDate: LocalDate = LocalDate.now(),
    initialStartTime: LocalTime? = LocalTime.of(8, 0),
    initialEndTime: LocalTime? = LocalTime.of(17, 0),
    initialShiftType: ShiftType = ShiftType.DAY,
    initialSurchargeIds: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (LedgerEntry) -> Unit
) {
    var date by remember(initialDate) { mutableStateOf(initialDate) }
    var startTime by remember(initialStartTime) { mutableStateOf(initialStartTime) }
    var endTime by remember(initialEndTime) { mutableStateOf(initialEndTime) }
    var shiftType by remember(initialShiftType) { mutableStateOf(initialShiftType) }
    var amountText by remember { mutableStateOf("") }
    var noteText by remember(initialNote) { mutableStateOf(initialNote) }
    var employeeId by remember(preselectedEmployeeId) { mutableStateOf(preselectedEmployeeId) }
    var organizationId by remember(preselectedOrganizationId) { mutableStateOf(preselectedOrganizationId) }

    var expenseCategoryId by remember { mutableStateOf<String?>(null) }
    var expenseCategoryName by remember { mutableStateOf("") }
    var unitId by remember { mutableStateOf<String?>(null) }
    var unitName by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("") }

    var employeeMenuOpen by remember { mutableStateOf(false) }
    var organizationMenuOpen by remember { mutableStateOf(false) }
    var shiftTypeMenuOpen by remember { mutableStateOf(false) }
    var expenseCategoryMenuOpen by remember { mutableStateOf(false) }
    var unitMenuOpen by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val employeeName = employees.firstOrNull { it.id == employeeId }?.name ?: "Работник"
    val organizationName = organizations.firstOrNull { it.id == organizationId }?.name ?: "Организация"
    val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy 'г.' EEE", java.util.Locale("ru"))
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", java.util.Locale("ru"))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title)
                Text("новая", fontSize = 12.sp, color = Color.Gray)
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = date.format(dateFormatter),
                    onValueChange = {},
                    label = { Text("Дата") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = { showDatePicker = true }) {
                            Text("Выбрать")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (entryType == EntryType.SHIFT) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = startTime?.format(timeFormatter) ?: "",
                            onValueChange = {},
                            label = { Text("Начало") },
                            readOnly = true,
                            modifier = Modifier.weight(1f),
                            trailingIcon = {
                                TextButton(onClick = { showStartTimePicker = true }) {
                                    Text("🕐")
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = endTime?.format(timeFormatter) ?: "",
                            onValueChange = {},
                            label = { Text("Конец") },
                            readOnly = true,
                            modifier = Modifier.weight(1f),
                            trailingIcon = {
                                TextButton(onClick = { showEndTimePicker = true }) {
                                    Text("🕐")
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = when (shiftType) {
                                ShiftType.DAY -> "Дневная"
                                ShiftType.NIGHT -> "Ночная"
                                ShiftType.HOLIDAY -> "Праздничная"
                                ShiftType.OVERTIME -> "Сверхурочная"
                                ShiftType.WEEKEND -> "Выходной день"
                            },
                            onValueChange = {},
                            label = { Text("Тип смены") },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                TextButton(onClick = { shiftTypeMenuOpen = true }) {
                                    Text("▼")
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = shiftTypeMenuOpen,
                            onDismissRequest = { shiftTypeMenuOpen = false }
                        ) {
                            ShiftType.values().forEach { type ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (type) {
                                                ShiftType.DAY -> "Дневная"
                                                ShiftType.NIGHT -> "Ночная"
                                                ShiftType.HOLIDAY -> "Праздничная"
                                                ShiftType.OVERTIME -> "Сверхурочная"
                                                ShiftType.WEEKEND -> "Выходной день"
                                            }
                                        )
                                    },
                                    onClick = {
                                        shiftType = type
                                        shiftTypeMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (showHours) {
                    val calculatedHours = if (startTime != null && endTime != null) {
                        val start = startTime!!.toSecondOfDay()
                        val end = endTime!!.toSecondOfDay()
                        val diff = if (end >= start) end - start else (end + 86400) - start
                        diff / 3600.0
                    } else 0.0

                    OutlinedTextField(
                        value = "%.2f ч".format(calculatedHours),
                        onValueChange = {},
                        label = { Text("Часы") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = moneyInputFilter(it) },
                        label = { Text("Сумма, ₽") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = employeeName,
                        onValueChange = {},
                        label = { Text("Сотрудник") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            TextButton(onClick = { employeeMenuOpen = true }) {
                                Text("▼")
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = employeeMenuOpen,
                        onDismissRequest = { employeeMenuOpen = false }
                    ) {
                        employees.forEach { e ->
                            DropdownMenuItem(
                                text = { Text(e.name) },
                                onClick = {
                                    employeeId = e.id
                                    employeeMenuOpen = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = organizationName,
                        onValueChange = {},
                        label = { Text("Организация") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            TextButton(onClick = { organizationMenuOpen = true }) {
                                Text("▼")
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = organizationMenuOpen,
                        onDismissRequest = { organizationMenuOpen = false }
                    ) {
                        organizations.forEach { o ->
                            DropdownMenuItem(
                                text = { Text(o.name) },
                                onClick = {
                                    organizationId = o.id
                                    organizationMenuOpen = false
                                }
                            )
                        }
                    }
                }

                if (entryType == EntryType.EXPENSE) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = expenseCategoryName.ifEmpty { "Не выбрана" },
                            onValueChange = {},
                            label = { Text("Категория расхода") },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                TextButton(onClick = { expenseCategoryMenuOpen = true }) {
                                    Text("▼")
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = expenseCategoryMenuOpen,
                            onDismissRequest = { expenseCategoryMenuOpen = false }
                        ) {
                            expenseCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        expenseCategoryId = cat.id
                                        expenseCategoryName = cat.name
                                        expenseCategoryMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = unitName.ifEmpty { "Не выбрана" },
                            onValueChange = {},
                            label = { Text("Единица измерения") },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                TextButton(onClick = { unitMenuOpen = true }) {
                                    Text("▼")
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = unitMenuOpen,
                            onDismissRequest = { unitMenuOpen = false }
                        ) {
                            units.forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text("${unit.name} (${unit.shortName})") },
                                    onClick = {
                                        unitId = unit.id
                                        unitName = unit.name
                                        unitMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = moneyInputFilter(it) },
                        label = { Text("Количество") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Комментарий") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val calculatedHours = if (startTime != null && endTime != null) {
                    val start = startTime!!.toSecondOfDay()
                    val end = endTime!!.toSecondOfDay()
                    val diff = if (end >= start) end - start else (end + 86400) - start
                    diff / 3600.0
                } else 0.0

                val amount = amountText.replace(',', '.').toDoubleOrNull() ?: 0.0
                val quantity = quantityText.replace(',', '.').toDoubleOrNull() ?: 0.0

                onConfirm(
                    LedgerEntry(
                        date = date,
                        startTime = startTime,
                        endTime = endTime,
                        shiftType = shiftType,
                        type = entryType,
                        employeeId = employeeId,
                        organizationId = organizationId,
                        amount = if (showHours) 0.0 else amount,
                        hours = if (showHours) calculatedHours else 0.0,
                        note = noteText,
                        expenseCategoryId = expenseCategoryId,
                        unitId = unitId,
                        quantity = quantity,
                        surchargeIds = initialSurchargeIds
                    )
                )
            }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )

    if (showDatePicker) {
        AppDatePickerDialog(
            initialDate = date,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                date = it
                showDatePicker = false
            }
        )
    }

    if (showStartTimePicker) {
        AppTimePickerDialog(
            initialTime = startTime ?: LocalTime.of(8, 0),
            onDismiss = { showStartTimePicker = false },
            onConfirm = {
                startTime = it
                showStartTimePicker = false
            }
        )
    }

    if (showEndTimePicker) {
        AppTimePickerDialog(
            initialTime = endTime ?: LocalTime.of(17, 0),
            onDismiss = { showEndTimePicker = false },
            onConfirm = {
                endTime = it
                showEndTimePicker = false
            }
        )
    }
}