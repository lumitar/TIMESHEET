package com.example.timesheet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.example.timesheet.data.AppViewModel
import com.example.timesheet.data.Employee
import com.example.timesheet.data.EntryType
import com.example.timesheet.data.Organization
import com.example.timesheet.data.PayrollBreakdown
import com.example.timesheet.data.PayrollCalculator
import com.example.timesheet.data.getQuickPeriods
import com.example.timesheet.util.ReportExport
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val BrandGreen = Color(0xFF5CA02F)

private fun periodLabel(quickId: String?, start: LocalDate, end: LocalDate): String {
    val name = getQuickPeriods().find { it.id == quickId }?.name
    if (name != null) return name
    val fmt = DateTimeFormatter.ofPattern("dd.MM.yy")
    return "${start.format(fmt)} – ${end.format(fmt)}"
}

fun formatHours(value: Double): String {
    val hours = value.toInt()
    val minutes = ((value - hours) * 60).toInt()
    return "${hours}ч ${minutes.toString().padStart(2, '0')}м"
}

/** Общая верхняя панель для отчётов: назад, выпадающий быстрый период, кнопка настроек (фильтр). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportTopBar(
    title: String,
    quickPeriodId: String?,
    periodStart: LocalDate,
    periodEnd: LocalDate,
    onBack: () -> Unit,
    onPeriodSelect: (id: String, start: LocalDate, end: LocalDate) -> Unit,
    onSettingsClick: () -> Unit
) {
    var showPeriodMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(title, color = Color.White) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
            }
        },
        actions = {
            Box {
                TextButton(onClick = { showPeriodMenu = true }) {
                    Text(periodLabel(quickPeriodId, periodStart, periodEnd), color = Color.White)
                }
                DropdownMenu(expanded = showPeriodMenu, onDismissRequest = { showPeriodMenu = false }) {
                    getQuickPeriods().forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name) },
                            onClick = {
                                val (s, e) = p.getRange()
                                onPeriodSelect(p.id, s, e)
                                showPeriodMenu = false
                            }
                        )
                    }
                }
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Filled.Tune, contentDescription = "Настройки", tint = Color.White)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandGreen)
    )
}

@Composable
private fun ReportBreakdownRow(label: String, value: Double, bold: Boolean = false, isHours: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(
            text = if (isHours) formatHours(value) else formatCurrency(value),
            color = when {
                isHours -> Color(0xFF2E7D32)
                value < 0 -> Color.Red
                else -> Color(0xFF2E7D32)
            },
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ИЗМЕНЕНО: было private (файл-скоуп в Kotlin) — теперь используется также
// из IncomeReportScreen.kt (выдвижная панель «Доход» с вкладками из ТЗ).
internal fun payslipRows(breakdown: PayrollBreakdown, periodLabelText: String): List<List<String>> {
    val totalIncome = breakdown.accrued - breakdown.payments - breakdown.taxes + breakdown.adjustments - breakdown.expenses
    return listOf(
        listOf("Период", periodLabelText),
        listOf("Начислено за смены", formatCurrency(breakdown.accrued)),
        listOf("Выплаты", formatCurrency(-breakdown.payments)),
        listOf("Налоги", formatCurrency(-breakdown.taxes)),
        listOf("Корректировки", formatCurrency(breakdown.adjustments)),
        listOf("Расходы", formatCurrency(-breakdown.expenses)),
        listOf("Отработано часов", formatHours(breakdown.totalHours)),
        listOf("Остаток на начало", formatCurrency(breakdown.openingBalance)),
        listOf("Доход за период", formatCurrency(totalIncome)),
        listOf("Остаток на конец", formatCurrency(breakdown.closingBalance))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayslipReportScreen(
    viewModel: AppViewModel,
    employees: List<Employee>,
    organizations: List<Organization>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val entries by viewModel.entries.collectAsState()
    val surcharges by viewModel.surcharges.collectAsState()
    val openingBalance by viewModel.openingBalance.collectAsState()
    val timeTypes by viewModel.timeTypes.collectAsState()

    val periodStart by viewModel.reportPeriodStart.collectAsState()
    val periodEnd by viewModel.reportPeriodEnd.collectAsState()
    val quickPeriodId by viewModel.reportQuickPeriodId.collectAsState()
    var filterOrgId by remember { mutableStateOf<String?>(null) }
    var filterEmpId by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showShareMenu by remember { mutableStateOf(false) }

    if (showSettings) {
        FilterMenuScreen(
            organizations = organizations,
            employees = employees,
            selectedOrganizationId = filterOrgId,
            selectedEmployeeId = filterEmpId,
            onBack = { showSettings = false },
            onApplyFilter = { orgId, empId ->
                filterOrgId = orgId
                filterEmpId = empId
            }
        )
        return
    }

    val breakdown = remember(entries, employees, periodStart, periodEnd, filterOrgId, filterEmpId, openingBalance, surcharges, timeTypes) {
        PayrollCalculator.calculateForPeriod(
            entries = entries,
            employees = employees,
            surcharges = surcharges,
            start = periodStart,
            end = periodEnd,
            employeeFilter = filterEmpId,
            organizationFilter = filterOrgId,
            openingBalance = openingBalance,
            timeTypes = timeTypes
        )
    }

    Scaffold(
        topBar = {
            ReportTopBar(
                title = "Расчетный лист",
                quickPeriodId = quickPeriodId,
                periodStart = periodStart,
                periodEnd = periodEnd,
                onBack = onBack,
                onPeriodSelect = { id, s, e -> viewModel.applyQuickReportPeriod(id) },
                onSettingsClick = { showSettings = true }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
            Text(
                text = "${periodStart.format(fmt)} — ${periodEnd.format(fmt)}",
                fontWeight = FontWeight.Medium,
                color = Color.Gray
            )
            Spacer(Modifier.height(12.dp))

            ReportBreakdownRow("Начислено за смены", breakdown.accrued, bold = true)
            if (breakdown.payments != 0.0) ReportBreakdownRow("Выплаты", -breakdown.payments)
            if (breakdown.taxes != 0.0) ReportBreakdownRow("Налоги", -breakdown.taxes)
            if (breakdown.adjustments != 0.0) ReportBreakdownRow("Корректировки", breakdown.adjustments)
            if (breakdown.expenses != 0.0) ReportBreakdownRow("Расходы", -breakdown.expenses)

            Divider(modifier = Modifier.padding(vertical = 8.dp))
            ReportBreakdownRow("Отработано часов", breakdown.totalHours, isHours = true)
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            ReportBreakdownRow("Остаток на начало", breakdown.openingBalance)
            val totalIncome = breakdown.accrued - breakdown.payments - breakdown.taxes + breakdown.adjustments - breakdown.expenses
            ReportBreakdownRow("Доход за период", totalIncome, bold = true)
            ReportBreakdownRow("Остаток на конец", breakdown.closingBalance, bold = true)

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { /* Превью уже отображается на экране выше */ }) {
                    Text("ПРЕДВ. ПРОСМОТР", color = Color(0xFF2196F3))
                }
                Box {
                    TextButton(onClick = { showShareMenu = true }) {
                        Text("ОТПРАВИТЬ", color = Color(0xFFFF9800))
                    }
                    DropdownMenu(expanded = showShareMenu, onDismissRequest = { showShareMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Excel (.xls)") },
                            onClick = {
                                showShareMenu = false
                                ReportExport.shareAsXls(
                                    context, "Расчетный лист",
                                    payslipRows(breakdown, "${periodStart.format(fmt)} — ${periodEnd.format(fmt)}")
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("PDF") },
                            onClick = {
                                showShareMenu = false
                                ReportExport.shareAsPdf(
                                    context, "Расчетный лист",
                                    payslipRows(breakdown, "${periodStart.format(fmt)} — ${periodEnd.format(fmt)}")
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

// ========================= РАБОЧЕЕ ВРЕМЯ ПО ПЕРИОДАМ =========================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkHoursReportScreen(
    viewModel: AppViewModel,
    employees: List<Employee>,
    organizations: List<Organization>,
    onBack: () -> Unit
) {
    val entries by viewModel.entries.collectAsState()
    val surcharges by viewModel.surcharges.collectAsState()
    val timeTypes by viewModel.timeTypes.collectAsState()

    val periodStart by viewModel.reportPeriodStart.collectAsState()
    val periodEnd by viewModel.reportPeriodEnd.collectAsState()
    val quickPeriodId by viewModel.reportQuickPeriodId.collectAsState()
    var filterOrgId by remember { mutableStateOf<String?>(null) }
    var filterEmpId by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        FilterMenuScreen(
            organizations = organizations,
            employees = employees,
            selectedOrganizationId = filterOrgId,
            selectedEmployeeId = filterEmpId,
            onBack = { showSettings = false },
            onApplyFilter = { orgId, empId -> filterOrgId = orgId; filterEmpId = empId }
        )
        return
    }

    val filtered = remember(entries, periodStart, periodEnd, filterOrgId, filterEmpId) {
        entries.filter { e ->
            e.type == EntryType.SHIFT &&
                    !e.date.isBefore(periodStart) && !e.date.isAfter(periodEnd) &&
                    (filterOrgId == null || e.organizationId == filterOrgId) &&
                    (filterEmpId == null || e.employeeId == filterEmpId)
        }
    }
    val breakdown = remember(filtered, employees, surcharges, timeTypes) {
        PayrollCalculator.calculateForPeriod(
            entries = filtered, employees = employees, surcharges = surcharges,
            start = periodStart, end = periodEnd,
            employeeFilter = null, organizationFilter = null, openingBalance = 0.0,
            timeTypes = timeTypes
        )
    }

    Scaffold(
        topBar = {
            ReportTopBar(
                title = "Рабочее время",
                quickPeriodId = quickPeriodId,
                periodStart = periodStart,
                periodEnd = periodEnd,
                onBack = onBack,
                onPeriodSelect = { id, s, e -> viewModel.applyQuickReportPeriod(id) },
                onSettingsClick = { showSettings = true }
            )
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE8F5E9))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Итого", fontWeight = FontWeight.Medium)
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatHours(breakdown.totalHours), fontWeight = FontWeight.Bold)
                    Text(formatCurrency(breakdown.accrued), color = Color(0xFF2E7D32), fontSize = 13.sp)
                }
            }

            val byProject = filtered.groupBy { it.projectName.ifBlank { null } }.entries.toList()
            if (byProject.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Нет записей за выбранный период")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                    items(byProject) { (project, list) ->
                        val name = project ?: "Без проекта"
                        val hours = list.sumOf { it.calculateHours() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(name, fontSize = 16.sp)
                            Text(formatHours(hours), color = Color(0xFF2E7D32))
                        }
                        Divider()
                    }
                }
            }
        }
    }
}

// ========================= РАСХОДЫ =========================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesReportScreen(
    viewModel: AppViewModel,
    employees: List<Employee>,
    organizations: List<Organization>,
    onBack: () -> Unit
) {
    val entries by viewModel.entries.collectAsState()
    val expenseCategories by viewModel.expenseCategories.collectAsState()

    val periodStart by viewModel.reportPeriodStart.collectAsState()
    val periodEnd by viewModel.reportPeriodEnd.collectAsState()
    val quickPeriodId by viewModel.reportQuickPeriodId.collectAsState()
    var filterOrgId by remember { mutableStateOf<String?>(null) }
    var filterEmpId by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        FilterMenuScreen(
            organizations = organizations,
            employees = employees,
            selectedOrganizationId = filterOrgId,
            selectedEmployeeId = filterEmpId,
            onBack = { showSettings = false },
            onApplyFilter = { orgId, empId -> filterOrgId = orgId; filterEmpId = empId }
        )
        return
    }

    val filtered = remember(entries, periodStart, periodEnd, filterOrgId, filterEmpId) {
        entries.filter { e ->
            e.type == EntryType.EXPENSE &&
                    !e.date.isBefore(periodStart) && !e.date.isAfter(periodEnd) &&
                    (filterOrgId == null || e.organizationId == filterOrgId) &&
                    (filterEmpId == null || e.employeeId == filterEmpId)
        }
    }
    val total = filtered.sumOf { it.amount }

    Scaffold(
        topBar = {
            ReportTopBar(
                title = "Расходы",
                quickPeriodId = quickPeriodId,
                periodStart = periodStart,
                periodEnd = periodEnd,
                onBack = onBack,
                onPeriodSelect = { id, s, e -> viewModel.applyQuickReportPeriod(id) },
                onSettingsClick = { showSettings = true }
            )
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF3E0))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Итого", fontWeight = FontWeight.Medium)
                Text(formatCurrency(total), color = Color(0xFFFF5722), fontWeight = FontWeight.Bold)
            }

            val byCategory = filtered.groupBy { it.expenseCategoryId }.entries.toList()
            if (byCategory.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Нет расходов за выбранный период")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                    items(byCategory) { (catId, list) ->
                        val name = expenseCategories.find { it.id == catId }?.name ?: "Без категории"
                        val sum = list.sumOf { it.amount }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(name, fontSize = 16.sp)
                            Text(formatCurrency(sum), color = Color(0xFFFF5722))
                        }
                        Divider()
                    }
                }
            }
        }
    }
}