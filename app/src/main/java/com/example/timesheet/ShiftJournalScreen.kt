package com.example.timesheet.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timesheet.data.Employee
import com.example.timesheet.data.LedgerEntry
import com.example.timesheet.data.Organization
import com.example.timesheet.data.PayrollCalculator
import com.example.timesheet.data.TimeType
import com.example.timesheet.util.ReportExport
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftJournalScreen(
    entries: List<LedgerEntry>,
    employees: List<Employee>,
    organizations: List<Organization>,
    timeTypes: List<TimeType>,
    expenseCategories: List<com.example.timesheet.data.ExpenseCategory> = emptyList(),
    units: List<com.example.timesheet.data.UnitOfMeasure> = emptyList(),
    periodStart: LocalDate,
    periodEnd: LocalDate,
    filterOrganizationId: String?,
    filterEmployeeId: String?,
    onBack: () -> Unit,
    onFilterClick: () -> Unit,
    onEditEntry: (LedgerEntry) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onPeriodChange: (LocalDate, LocalDate) -> Unit = { _, _ -> },
    onRecalculateEntry: (String) -> Unit = {},
    onAttachmentsChanged: (String, List<String>) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    var showShareMenu by remember { mutableStateOf(false) }

    val filteredEntries = entries.filter { entry ->
        !entry.date.isBefore(periodStart) && !entry.date.isAfter(periodEnd) &&
                (filterOrganizationId == null || entry.organizationId == filterOrganizationId) &&
                (filterEmployeeId == null || entry.employeeId == filterEmployeeId)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Журнал смен",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "${periodStart.format(dateFormatter)} — ${periodEnd.format(dateFormatter)}",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Кнопка отправки
                    Box {
                        IconButton(onClick = { showShareMenu = true }) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Отправить",
                                tint = Color.White
                            )
                        }
                        DropdownMenu(
                            expanded = showShareMenu,
                            onDismissRequest = { showShareMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Экспорт в XLS") },
                                onClick = {
                                    showShareMenu = false
                                    exportToXls(context, filteredEntries, employees, organizations, periodStart, periodEnd)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Экспорт в PDF") },
                                onClick = {
                                    showShareMenu = false
                                    exportToPdf(context, filteredEntries, employees, organizations, periodStart, periodEnd)
                                }
                            )
                        }
                    }

                    // Кнопка фильтра
                    IconButton(onClick = onFilterClick) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = "Фильтр",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF5CA02F)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Фильтры отображения
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                val orgName = organizations.find { it.id == filterOrganizationId }?.name ?: "Все"
                val empName = employees.find { it.id == filterEmployeeId }?.name ?: "Все"

                OutlinedTextField(
                    value = orgName,
                    onValueChange = {},
                    label = { Text("Организация") },
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        TextButton(onClick = onFilterClick) {
                            Text("▼")
                        }
                    }
                )

                OutlinedTextField(
                    value = empName,
                    onValueChange = {},
                    label = { Text("Сотрудник") },
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        TextButton(onClick = onFilterClick) {
                            Text("▼")
                        }
                    }
                )
            }

            if (filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Нет записей за выбранный период", fontSize = 16.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    items(
                        filteredEntries.sortedWith(
                            compareByDescending<LedgerEntry> { it.date }
                                .thenByDescending { it.startTime ?: java.time.LocalTime.MIN }
                        ),
                        key = { it.id }) { entry ->
                        JournalEntryItem(
                            entry = entry,
                            employeeName = employees.find { it.id == entry.employeeId }?.name,
                            organizationName = organizations.find { it.id == entry.organizationId }?.name,
                            timeTypes = timeTypes, // Передаем справочник типов времени
                            expenseCategories = expenseCategories,
                            units = units,
                            onEdit = { onEditEntry(it) },
                            onDelete = { onDeleteEntry(it) },
                            onRecalculate = { onRecalculateEntry(it) },
                            onAttachmentsChanged = onAttachmentsChanged
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

// Функции экспорта
private fun exportToXls(
    context: android.content.Context,
    entries: List<LedgerEntry>,
    employees: List<Employee>,
    organizations: List<Organization>,
    start: LocalDate,
    end: LocalDate
) {
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val rows = mutableListOf<List<String>>()

    rows.add(listOf("Журнал смен", "${start.format(dateFormatter)} — ${end.format(dateFormatter)}"))
    rows.add(listOf("Дата", "Сотрудник", "Организация", "Тип", "Часы", "Сумма"))

    entries.sortedBy { it.date }.forEach { entry ->
        val empName = employees.find { it.id == entry.employeeId }?.name ?: ""
        val orgName = organizations.find { it.id == entry.organizationId }?.name ?: ""
        val hours = entry.calculateHours()
        rows.add(listOf(
            entry.date.toString(),
            empName,
            orgName,
            entry.type.name,
            String.format("%.2f", hours),
            String.format("%.2f", entry.amount)
        ))
    }

    val totalHours = entries.sumOf { it.calculateHours() }
    val totalAmount = entries.sumOf { it.amount }
    rows.add(listOf("Итого", "", "", "", String.format("%.2f", totalHours), String.format("%.2f", totalAmount)))

    ReportExport.shareAsXls(context, "Журнал_смен", rows)
}

private fun exportToPdf(
    context: android.content.Context,
    entries: List<LedgerEntry>,
    employees: List<Employee>,
    organizations: List<Organization>,
    start: LocalDate,
    end: LocalDate
) {
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val rows = mutableListOf<List<String>>()

    rows.add(listOf("Журнал смен", "${start.format(dateFormatter)} — ${end.format(dateFormatter)}"))
    rows.add(listOf("Дата", "Сотрудник", "Организация", "Тип", "Часы", "Сумма"))

    entries.sortedBy { it.date }.forEach { entry ->
        val empName = employees.find { it.id == entry.employeeId }?.name ?: ""
        val orgName = organizations.find { it.id == entry.organizationId }?.name ?: ""
        val hours = entry.calculateHours()
        rows.add(listOf(
            entry.date.toString(),
            empName,
            orgName,
            entry.type.name,
            String.format("%.2f", hours),
            String.format("%.2f", entry.amount)
        ))
    }

    val totalHours = entries.sumOf { it.calculateHours() }
    val totalAmount = entries.sumOf { it.amount }
    rows.add(listOf("Итого", "", "", "", String.format("%.2f", totalHours), String.format("%.2f", totalAmount)))

    ReportExport.shareAsPdf(context, "Журнал_смен", rows)
}