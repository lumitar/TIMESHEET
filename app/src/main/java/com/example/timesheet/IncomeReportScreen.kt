package com.example.timesheet.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timesheet.data.EntryType
import com.example.timesheet.data.LedgerEntry
import com.example.timesheet.data.PayrollBreakdown
import com.example.timesheet.data.ShiftType
import com.example.timesheet.data.TimeType
import com.example.timesheet.data.shiftTypeTimeTypeId
import com.example.timesheet.data.displayShiftLabel
import com.example.timesheet.data.moneySignedInputFilter
import com.example.timesheet.util.ReportExport
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val IncomeGreen = Color(0xFF5CA02F)
private val IncomeAccent = Color(0xFFFF5722)

private fun resolvedTimeType(entry: LedgerEntry, timeTypes: List<TimeType>): TimeType? =
    timeTypes.find { it.id == entry.timeTypeId }
        ?: timeTypes.find { it.id == shiftTypeTimeTypeId(entry.shiftType) }

private fun shiftTypeSummaryLabel(type: ShiftType): String = when (type) {
    ShiftType.DAY -> "Дневная смена"
    ShiftType.NIGHT -> "Ночная смена"
    ShiftType.HOLIDAY -> "Праздничная смена"
    ShiftType.OVERTIME -> "Сверхурочная смена"
    ShiftType.WEEKEND -> "Смена в выходной"
}

private fun shiftLabelFor(entry: LedgerEntry, timeTypes: List<TimeType>): String =
    displayShiftLabel(
        resolvedTimeType(entry, timeTypes)?.name ?: shiftTypeSummaryLabel(entry.shiftType),
        entry.overtimeEnabled
    )

private fun shiftColorFor(entry: LedgerEntry, timeTypes: List<TimeType>): Color {
    // Включённые сверхурочные всегда выделяются фиолетовым, даже если тип из
    // справочника — «Дневная смена».
    if (entry.overtimeEnabled) return Color(0xFF9C27B0)
    val timeType = resolvedTimeType(entry, timeTypes)
    if (timeType != null) {
        return try {
            Color(android.graphics.Color.parseColor(timeType.color))
        } catch (e: Exception) {
            Color(0xFF4CAF50)
        }
    }
    return when (entry.shiftType) {
        ShiftType.DAY -> Color(0xFF4CAF50)
        ShiftType.NIGHT -> Color(0xFF2196F3)
        ShiftType.HOLIDAY -> Color(0xFFFF9800)
        ShiftType.OVERTIME -> Color(0xFF9C27B0)
        ShiftType.WEEKEND -> Color(0xFFF44336)
    }
}

private data class CalendarDay(val date: LocalDate, val inMonth: Boolean)

private fun buildCalendarWeeks(month: YearMonth): List<List<CalendarDay>> {
    val firstDay = month.atDay(1)
    val startOffset = firstDay.dayOfWeek.value - 1 // Пн = 0
    val gridStart = firstDay.minusDays(startOffset.toLong())
    val cellsNeeded = startOffset + month.lengthOfMonth()
    val totalCells = if (cellsNeeded % 7 == 0) cellsNeeded else cellsNeeded + (7 - cellsNeeded % 7)
    val days = (0 until totalCells).map { offset ->
        val date = gridStart.plusDays(offset.toLong())
        CalendarDay(date, YearMonth.from(date) == month)
    }
    return days.chunked(7)
}

private fun timesheetRows(month: YearMonth, shiftEntries: List<LedgerEntry>, timeTypes: List<TimeType>): List<List<String>> {
    val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val rows = mutableListOf<List<String>>()
    rows.add(listOf("Табель", formatMonth(month)))
    rows.add(listOf("Дата", "День недели", "Тип смены", "Часы"))
    shiftEntries.sortedBy { it.date }.forEach { e ->
        rows.add(
            listOf(
                e.date.format(dateFmt),
                e.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru")).uppercase(Locale("ru")),
                shiftLabelFor(e, timeTypes),
                formatHours(e.calculatePaidHours())
            )
        )
    }
    rows.add(listOf("Итого", "", "", formatHours(shiftEntries.sumOf { it.calculatePaidHours() })))
    return rows
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeReportScreen(
    month: YearMonth,
    breakdown: PayrollBreakdown,
    timeTypes: List<TimeType>,
    onDismiss: () -> Unit,
    onOpeningBalanceChange: (Double) -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) }

    val shiftEntries = remember(breakdown.entries, month) {
        breakdown.entries.filter { it.type == EntryType.SHIFT && YearMonth.from(it.date) == month }
    }

    BackHandler(onBack = onDismiss)

    Scaffold(
        topBar = {
            Column(Modifier.background(Color.White)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.ExpandMore, contentDescription = "Свернуть", tint = Color.DarkGray)
                    }
                    TextButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = Color.DarkGray)
                        Spacer(Modifier.width(4.dp))
                        Text("Закрыть", color = Color.DarkGray, fontSize = 13.sp)
                    }
                }
                TabRow(selectedTabIndex = activeTab, containerColor = Color.White, contentColor = IncomeGreen) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("Расчетный лист") }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text("Табель") }
                    )
                }
                Text(
                    text = formatMonth(month),
                    modifier = Modifier.padding(16.dp),
                    fontSize = 14.sp,
                    color = Color.DarkGray
                )
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            if (activeTab == 0) {
                PayslipTabContent(
                    breakdown = breakdown,
                    onOpeningBalanceChange = onOpeningBalanceChange,
                    onPreview = {
                        ReportExport.previewAsPdf(context, "Расчетный лист", payslipRows(breakdown, formatMonth(month)))
                    },
                    onShareXls = {
                        ReportExport.shareAsXls(context, "Расчетный лист", payslipRows(breakdown, formatMonth(month)))
                    },
                    onSharePdf = {
                        ReportExport.shareAsPdf(context, "Расчетный лист", payslipRows(breakdown, formatMonth(month)))
                    }
                )
            } else {
                TimesheetTabContent(
                    month = month,
                    shiftEntries = shiftEntries,
                    timeTypes = timeTypes,
                    onPreview = {
                        ReportExport.previewAsPdf(context, "Табель", timesheetRows(month, shiftEntries, timeTypes))
                    },
                    onShareXls = {
                        ReportExport.shareAsXls(context, "Табель", timesheetRows(month, shiftEntries, timeTypes))
                    },
                    onSharePdf = {
                        ReportExport.shareAsPdf(context, "Табель", timesheetRows(month, shiftEntries, timeTypes))
                    }
                )
            }
        }
    }
}

@Composable
private fun PayslipTabContent(
    breakdown: PayrollBreakdown,
    onOpeningBalanceChange: (Double) -> Unit,
    onPreview: () -> Unit,
    onShareXls: () -> Unit,
    onSharePdf: () -> Unit
) {
    var showShareMenu by remember { mutableStateOf(false) }
    var editingBalance by remember { mutableStateOf(false) }
    var balanceText by remember(breakdown.openingBalance) { mutableStateOf(breakdown.openingBalance.toString()) }
    val totalIncome = breakdown.accrued - breakdown.payments - breakdown.taxes + breakdown.adjustments - breakdown.expenses

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        IncomeRow("Начислено", formatCurrency(breakdown.accrued))
        IncomeRow("Доход", formatCurrency(totalIncome), bold = true)

        Divider(Modifier.padding(vertical = 12.dp))

        if (editingBalance) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = moneySignedInputFilter(it) },
                    label = { Text("Остаток на начало") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    onOpeningBalanceChange(balanceText.replace(',', '.').toDoubleOrNull() ?: 0.0)
                    editingBalance = false
                }) { Text("ОК") }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { editingBalance = true },
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Остаток на начало", fontSize = 15.sp)
                Text(formatCurrency(breakdown.openingBalance), color = Color(0xFF2E7D32), fontSize = 15.sp)
            }
        }

        IncomeRow("Выплата", formatCurrency(breakdown.payments))
        IncomeRow("Остаток на конец", formatCurrency(breakdown.closingBalance))

        Spacer(Modifier.weight(1f))

        PreviewSendRow(
            onPreview = onPreview,
            showShareMenu = showShareMenu,
            onShareMenuChange = { showShareMenu = it },
            onShareXls = onShareXls,
            onSharePdf = onSharePdf
        )
    }
}

@Composable
private fun TimesheetTabContent(
    month: YearMonth,
    shiftEntries: List<LedgerEntry>,
    timeTypes: List<TimeType>,
    onPreview: () -> Unit,
    onShareXls: () -> Unit,
    onSharePdf: () -> Unit
) {
    var showShareMenu by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val entriesByDate = remember(shiftEntries) { shiftEntries.groupBy { it.date } }
    val weeks = remember(month) { buildCalendarWeeks(month) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Row(Modifier.fillMaxWidth()) {
                listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { d ->
                    Text(
                        d,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        items(weeks) { week ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                week.forEach { day ->
                    val dayEntries = entriesByDate[day.date].orEmpty()
                    val hasShift = dayEntries.isNotEmpty() && day.inMonth
                    val paidHours = dayEntries.sumOf { it.calculatePaidHours() }
                    val cellColor = if (hasShift) shiftColorFor(dayEntries.first(), timeTypes) else Color.Transparent

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .height(52.dp)
                            .background(
                                color = if (hasShift) cellColor.copy(alpha = 0.18f) else Color.Transparent,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .then(
                                if (day.date == today) {
                                    Modifier.border(1.dp, IncomeGreen, RoundedCornerShape(4.dp))
                                } else Modifier
                            )
                            .alpha(if (day.inMonth) 1f else 0.35f),
                        contentAlignment = Alignment.TopStart
                    ) {
                        Column(Modifier.padding(4.dp)) {
                            Text(
                                text = day.date.dayOfMonth.toString(),
                                fontSize = 12.sp,
                                color = if (day.inMonth) Color.Black else Color.Gray
                            )
                            if (day.inMonth) {
                                if (hasShift) {
                                    Text(
                                        text = String.format(Locale("ru"), "%.2f", paidHours),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                } else {
                                    Text("–", fontSize = 12.sp, color = Color.LightGray)
                                }
                            }
                        }
                    }
                }
                // Дополняем неполную последнюю строку пустыми ячейками, чтобы сетка не съезжала.
                repeat(7 - week.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(8.dp))

            val totalPaidHours = shiftEntries.sumOf { it.calculatePaidHours() }
            val totalDays = shiftEntries.map { it.date }.distinct().size
            SummaryRow("Смены", formatHours(totalPaidHours), "$totalDays д.")

            shiftEntries.groupBy {
                val baseKey = resolvedTimeType(it, timeTypes)?.id ?: it.timeTypeId ?: it.shiftType.name
                // Сверхурочные группируем отдельно от обычных смен того же типа —
                // иначе они сливались бы в одну строку с одинаковым названием.
                if (it.overtimeEnabled) "$baseKey#overtime" else baseKey
            }
                .forEach { (_, list) ->
                    val hours = list.sumOf { it.calculatePaidHours() }
                    val days = list.map { it.date }.distinct().size
                    SummaryRow(
                        label = shiftLabelFor(list.first(), timeTypes),
                        value1 = formatHours(hours),
                        value2 = "$days д.",
                        dotColor = shiftColorFor(list.first(), timeTypes)
                    )
                }

            val totalBreakMinutes = shiftEntries.sumOf { it.unpaidBreakMinutes }
            if (totalBreakMinutes > 0) {
                SummaryRow(
                    "Неоплачиваемые перерывы",
                    "${totalBreakMinutes / 60}ч ${(totalBreakMinutes % 60).toString().padStart(2, '0')}м",
                    null
                )
            }

            Spacer(Modifier.height(16.dp))

            PreviewSendRow(
                onPreview = onPreview,
                showShareMenu = showShareMenu,
                onShareMenuChange = { showShareMenu = it },
                onShareXls = onShareXls,
                onSharePdf = onSharePdf
            )
        }
    }
}

@Composable
private fun PreviewSendRow(
    onPreview: () -> Unit,
    showShareMenu: Boolean,
    onShareMenuChange: (Boolean) -> Unit,
    onShareXls: () -> Unit,
    onSharePdf: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        TextButton(onClick = onPreview) {
            Text("ПРЕДВ. ПРОСМОТР", color = IncomeAccent, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }
        Box {
            TextButton(onClick = { onShareMenuChange(true) }) {
                Text("ОТПРАВИТЬ", color = IncomeAccent, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
            DropdownMenu(expanded = showShareMenu, onDismissRequest = { onShareMenuChange(false) }) {
                DropdownMenuItem(
                    text = { Text("Excel (.xls)") },
                    onClick = { onShareMenuChange(false); onShareXls() }
                )
                DropdownMenuItem(
                    text = { Text("PDF") },
                    onClick = { onShareMenuChange(false); onSharePdf() }
                )
            }
        }
    }
}

@Composable
private fun IncomeRow(label: String, value: String, bold: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp)
        Text(
            value,
            color = Color(0xFF2E7D32),
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun SummaryRow(label: String, value1: String, value2: String?, dotColor: Color? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dotColor != null) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(dotColor, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(label, fontSize = 14.sp)
        }
        Row {
            Text(value1, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (value2 != null) {
                Spacer(Modifier.width(8.dp))
                Text(value2, fontSize = 14.sp, color = Color.Gray)
            }
        }
    }
}
