package com.example.timesheet.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timesheet.data.Employee
import com.example.timesheet.data.LedgerEntry
import com.example.timesheet.data.Organization
import com.example.timesheet.data.PayrollCalculator
import com.example.timesheet.data.ShiftType
import com.example.timesheet.data.Surcharge
import com.example.timesheet.data.TimeType
import com.example.timesheet.data.inferShiftTypeFromTimeType
import com.example.timesheet.data.shiftTypeTimeTypeId
import com.example.timesheet.data.moneyInputFilter
import com.example.timesheet.data.timeRangesOverlap
import com.example.timesheet.data.dateRangeDays
import com.example.timesheet.data.EntryType
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val ShiftDialogGreen = Color(0xFF5CA02F)

private fun shiftTypeLabel(type: ShiftType): String = when (type) {
    ShiftType.DAY -> "Дневная"
    ShiftType.NIGHT -> "Ночная"
    ShiftType.HOLIDAY -> "Праздничная"
    ShiftType.OVERTIME -> "Сверхурочная"
    ShiftType.WEEKEND -> "Выходной день"
}

private fun colorForShiftType(type: ShiftType, timeTypes: List<TimeType>): Color {
    val timeType = timeTypes.find { it.id == shiftTypeTimeTypeId(type) }
    if (timeType != null && timeType.color.isNotBlank()) {
        try {
            return Color(android.graphics.Color.parseColor(timeType.color))
        } catch (e: Exception) {
            // падаем на стандартный цвет ниже
        }
    }
    return when (type) {
        ShiftType.DAY -> Color(0xFF4CAF50)
        ShiftType.NIGHT -> Color(0xFF2196F3)
        ShiftType.HOLIDAY -> Color(0xFFFF9800)
        ShiftType.OVERTIME -> Color(0xFF9C27B0)
        ShiftType.WEEKEND -> Color(0xFFF44336)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftEntryDialog(
    employees: List<Employee>,
    organizations: List<Organization>,
    surcharges: List<Surcharge>,
    timeTypes: List<TimeType> = emptyList(),
    preselectedEmployeeId: String?,
    preselectedOrganizationId: String?,
    initialEntry: LedgerEntry? = null,
    initialDate: LocalDate = LocalDate.now(),
    projectSuggestions: List<String> = emptyList(),
    existingEntries: List<LedgerEntry> = emptyList(),
    onClose: () -> Unit,
    onConfirm: (LedgerEntry) -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    var activeTab by remember { mutableStateOf(0) }

    val stableEntryId = remember { initialEntry?.id ?: java.util.UUID.randomUUID().toString() }

    val isAddMode = onDelete == null
    var rangeEnabled by remember { mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }

    var date by remember {
        mutableStateOf(initialEntry?.date ?: initialDate)
    }

    var rangeEndDate by remember { mutableStateOf(initialEntry?.date ?: initialDate) }

    var startTime by remember {
        mutableStateOf(initialEntry?.startTime ?: LocalTime.of(8, 0))
    }

    var endTime by remember {
        mutableStateOf(initialEntry?.endTime ?: LocalTime.of(17, 0))
    }

    var timeTypeId by remember {
        mutableStateOf(
            initialEntry?.timeTypeId
                ?: timeTypes.find { it.id == shiftTypeTimeTypeId(initialEntry?.shiftType ?: ShiftType.DAY) }?.id
                ?: timeTypes.firstOrNull()?.id
        )
    }
    val selectedTimeType = timeTypes.find { it.id == timeTypeId }
    // shiftType теперь не выбирается вручную — подбирается автоматически по
    // названию выбранного в справочнике типа (нужен только для формулы
    // надбавки к оплате в PayrollCalculator).
    val shiftType = inferShiftTypeFromTimeType(selectedTimeType)

    var employeeId by remember {
        mutableStateOf(initialEntry?.employeeId ?: preselectedEmployeeId)
    }

    var organizationId by remember {
        mutableStateOf(
            initialEntry?.organizationId ?: preselectedOrganizationId
        )
    }

    var unpaidBreakText by remember {
        mutableStateOf(
            if ((initialEntry?.unpaidBreakMinutes ?: 0) == 0) {
                ""
            } else {
                initialEntry!!.unpaidBreakMinutes.toString()
            }
        )
    }

    var overtimeEnabled by remember {
        mutableStateOf(initialEntry?.overtimeEnabled ?: false)
    }

    var manualAmountText by remember {
        mutableStateOf(
            if ((initialEntry?.amount ?: 0.0) == 0.0) {
                ""
            } else {
                initialEntry!!.amount.toString()
            }
        )
    }

    var projectName by remember {
        mutableStateOf(initialEntry?.projectName ?: "")
    }

    var note by remember {
        mutableStateOf(initialEntry?.note ?: "")
    }

    var selectedSurchargeIds by remember {
        mutableStateOf(
            initialEntry?.surchargeIds ?: emptyList()
        )
    }

    var employeeMenuOpen by remember { mutableStateOf(false) }
    var organizationMenuOpen by remember { mutableStateOf(false) }
    var shiftTypeMenuOpen by remember { mutableStateOf(false) }
    var projectMenuOpen by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val dateFormatter =
        DateTimeFormatter.ofPattern("d MMMM yyyy 'г.' EEE", java.util.Locale("ru"))

    val timeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", java.util.Locale("ru"))

    val employeeName =
        employees.firstOrNull { it.id == employeeId }?.name ?: "Работник"

    val organizationName =
        organizations.firstOrNull { it.id == organizationId }?.name ?: "Организация"

    val hourlyRate =
        employees.firstOrNull { it.id == employeeId }?.hourlyRate ?: 0.0

    fun buildDraftEntry(): LedgerEntry {
        val breakMinutes =
            unpaidBreakText.toIntOrNull() ?: 0

        val manualAmount =
            manualAmountText
                .replace(',', '.')
                .toDoubleOrNull() ?: 0.0

        return LedgerEntry(
            id = stableEntryId,

            date = date,

            startTime = startTime,

            endTime = endTime,

            shiftType = shiftType,

            timeTypeId = timeTypeId,

            employeeId = employeeId,

            organizationId = organizationId,

            amount = manualAmount,

            note = note,

            surchargeIds = selectedSurchargeIds,

            unpaidBreakMinutes = breakMinutes,

            overtimeEnabled = overtimeEnabled,

            projectName = projectName
        )
    }

    val draft = buildDraftEntry()

    val paidHours =
        draft.calculatePaidHours()

    val baseAmount =
        PayrollCalculator.shiftBaseAmount(
            draft,
            hourlyRate,
            timeTypes
        )

    val totalAmount =
        PayrollCalculator.shiftTotalAmount(
            draft,
            hourlyRate,
            surcharges,
            timeTypes
        )

    val overlapDates: List<LocalDate> = run {
        val daysToCheck = if (rangeEnabled && isAddMode) dateRangeDays(date, rangeEndDate) else listOf(date)
        daysToCheck.filter { checkedDate ->
            existingEntries.any { other ->
                other.id != stableEntryId &&
                        other.type == EntryType.SHIFT &&
                        other.employeeId == employeeId &&
                        employeeId != null &&
                        other.date == checkedDate &&
                        timeRangesOverlap(startTime, endTime, other.startTime, other.endTime)
            }
        }
    }
    val hasOverlap = overlapDates.isNotEmpty()

    Scaffold(
        modifier = Modifier.fillMaxSize(),

        topBar = {
            Column {

                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "Смена",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                formatCurrency(totalAmount),
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 13.sp
                            )
                        }
                    },

                    navigationIcon = {
                        IconButton(
                            onClick = onClose
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Закрыть",
                                tint = Color.White
                            )
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
                                        onConfirm(
                                            buildDraftEntry().copy(
                                                id = java.util.UUID.randomUUID().toString(),
                                                date = d
                                            )
                                        )
                                    }
                                } else {
                                    onConfirm(buildDraftEntry())
                                }
                            }
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Сохранить",
                                tint = Color.White
                            )
                        }
                    },

                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ShiftDialogGreen
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ShiftDialogGreen)
                ) {

                    ShiftDialogTab(
                        text = "ОСНОВНАЯ ОПЛАТА",
                        selected = activeTab == 0,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeTab = 0
                        }
                    )

                    ShiftDialogTab(
                        text = "ДОПЛАТЫ И УДЕРЖАНИЯ",
                        selected = activeTab == 1,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            activeTab = 1
                        }
                    )
                }
            }
        }
    ) { innerPadding ->

        if (activeTab == 0) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {

                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    OutlinedTextField(
                        value = employeeName,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),

                        trailingIcon = {
                            TextButton(
                                onClick = {
                                    employeeMenuOpen = true
                                }
                            ) {
                                Text("▼")
                            }
                        }
                    )

                    DropdownMenu(
                        expanded = employeeMenuOpen,
                        onDismissRequest = {
                            employeeMenuOpen = false
                        }
                    ) {

                        employees.forEach { e ->

                            DropdownMenuItem(
                                text = {
                                    Text(e.name)
                                },

                                onClick = {
                                    employeeId = e.id
                                    employeeMenuOpen = false
                                }
                            )
                        }
                    }
                }

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    OutlinedTextField(
                        value = organizationName,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),

                        trailingIcon = {
                            TextButton(
                                onClick = {
                                    organizationMenuOpen = true
                                }
                            ) {
                                Text("▼")
                            }
                        }
                    )

                    DropdownMenu(
                        expanded = organizationMenuOpen,
                        onDismissRequest = {
                            organizationMenuOpen = false
                        }
                    ) {

                        organizations.forEach { o ->

                            DropdownMenuItem(
                                text = {
                                    Text(o.name)
                                },

                                onClick = {
                                    organizationId = o.id
                                    organizationMenuOpen = false
                                }
                            )
                        }
                    }
                }

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    OutlinedTextField(
                        value = selectedTimeType?.name ?: "Выберите тип",
                        onValueChange = {},
                        label = {
                            Text("Тип смены")
                        },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),

                        leadingIcon = {
                            val dotColor = selectedTimeType?.color?.let {
                                runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
                            } ?: Color.LightGray
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(
                                        color = dotColor,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                        },

                        trailingIcon = {
                            TextButton(
                                onClick = {
                                    shiftTypeMenuOpen = true
                                }
                            ) {
                                Text("▼")
                            }
                        }
                    )

                    DropdownMenu(
                        expanded = shiftTypeMenuOpen,
                        onDismissRequest = {
                            shiftTypeMenuOpen = false
                        }
                    ) {
                        if (timeTypes.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Справочник «Типы времени» пуст") },
                                onClick = { shiftTypeMenuOpen = false }
                            )
                        }
                        timeTypes.forEach { type ->

                            DropdownMenuItem(
                                leadingIcon = {
                                    val dotColor = runCatching {
                                        Color(android.graphics.Color.parseColor(type.color))
                                    }.getOrNull() ?: Color.LightGray
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .background(
                                                color = dotColor,
                                                shape = androidx.compose.foundation.shape.CircleShape
                                            )
                                    )
                                },
                                text = {
                                    Text(type.name)
                                },

                                onClick = {
                                    timeTypeId = type.id
                                    shiftTypeMenuOpen = false
                                }
                            )
                        }
                    }
                }

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    "С",
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    OutlinedTextField(
                        value = date.format(dateFormatter),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.weight(1f),

                        trailingIcon = {
                            TextButton(
                                onClick = {
                                    showDatePicker = true
                                }
                            ) {
                                Text("📅")
                            }
                        }
                    )

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    OutlinedTextField(
                        value = startTime.format(timeFormatter),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.width(110.dp),

                        trailingIcon = {
                            TextButton(
                                onClick = {
                                    showStartTimePicker = true
                                }
                            ) {
                                Text("🕐")
                            }
                        }
                    )
                }

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    "По",
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    OutlinedTextField(
                        value = date.format(dateFormatter),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.weight(1f),

                        trailingIcon = {
                            TextButton(
                                onClick = {
                                    showDatePicker = true
                                }
                            ) {
                                Text("📅")
                            }
                        }
                    )

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    OutlinedTextField(
                        value = endTime.format(timeFormatter),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.width(110.dp),

                        trailingIcon = {
                            TextButton(
                                onClick = {
                                    showEndTimePicker = true
                                }
                            ) {
                                Text("🕐")
                            }
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

                if (hasOverlap) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠ Пересекается по времени с другой сменой этого сотрудника" +
                                if (overlapDates.size > 1) " (${overlapDates.size} дн.)" else " в этот день",
                        fontSize = 12.sp,
                        color = Color(0xFFE65100)
                    )
                }

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                OutlinedTextField(
                    value = unpaidBreakText,

                    onValueChange = {
                        unpaidBreakText =
                            it.filter { c ->
                                c.isDigit()
                            }
                    },

                    label = {
                        Text(
                            "Неоплачиваемые перерывы, мин."
                        )
                    },

                    singleLine = true,

                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                Divider()

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {

                        Text(
                            "Основная оплата",
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            "${formatHours(paidHours)} · " +
                                    "${formatCurrency(hourlyRate)}/ч",

                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    OutlinedTextField(
                        value = manualAmountText,

                        onValueChange = {
                            manualAmountText = moneyInputFilter(it)
                        },

                        placeholder = {
                            Text(
                                formatCurrency(baseAmount)
                            )
                        },

                        singleLine = true,

                        modifier = Modifier.width(130.dp)
                    )
                }

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.SpaceBetween,

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        "Оплата сверхурочных часов"
                    )

                    Switch(
                        checked = overtimeEnabled,

                        onCheckedChange = {
                            overtimeEnabled = it
                        }
                    )
                }

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {

                    Text(
                        "Итого",
                        fontWeight = FontWeight.Bold
                    )

                    Column(
                        horizontalAlignment =
                            Alignment.End
                    ) {

                        Text(
                            formatHours(paidHours),
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            formatCurrency(totalAmount),
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                Divider()

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    OutlinedTextField(
                        value = projectName,

                        onValueChange = {
                            projectName = it
                        },

                        label = {
                            Text("Проект")
                        },

                        singleLine = true,

                        modifier = Modifier.fillMaxWidth(),

                        trailingIcon = {

                            if (projectSuggestions.isNotEmpty()) {

                                TextButton(
                                    onClick = {
                                        projectMenuOpen = true
                                    }
                                ) {
                                    Text("▼")
                                }
                            }
                        }
                    )

                    if (projectSuggestions.isNotEmpty()) {

                        DropdownMenu(
                            expanded = projectMenuOpen,

                            onDismissRequest = {
                                projectMenuOpen = false
                            }
                        ) {

                            projectSuggestions
                                .distinct()
                                .forEach { p ->

                                    DropdownMenuItem(
                                        text = {
                                            Text(p)
                                        },

                                        onClick = {
                                            projectName = p
                                            projectMenuOpen = false
                                        }
                                    )
                                }
                        }
                    }
                }

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                OutlinedTextField(
                    value = note,

                    onValueChange = {
                        note = it
                    },

                    label = {
                        Text("Комментарий")
                    },

                    modifier = Modifier.fillMaxWidth()
                )

                if (onDelete != null) {

                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )

                    TextButton(
                        onClick = {
                            onDelete(draft.id)
                            onClose()
                        }
                    ) {

                        Text(
                            "Удалить запись",
                            color = Color.Red
                        )
                    }
                }
            }

        } else {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {

                if (surcharges.isEmpty()) {

                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {

                        Text(
                            "Список доплат пуст. " +
                                    "Добавьте их в Справочниках.",
                            modifier = Modifier.padding(24.dp)
                        )
                    }

                } else {

                    LazyColumn(
                        Modifier.fillMaxSize()
                    ) {

                        items(
                            surcharges,
                            key = {
                                it.id
                            }
                        ) { s ->

                            val isSelected =
                                s.id in selectedSurchargeIds

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {

                                        selectedSurchargeIds =
                                            if (isSelected) {

                                                selectedSurchargeIds -
                                                        s.id

                                            } else {

                                                selectedSurchargeIds +
                                                        s.id
                                            }
                                    }
                                    .padding(
                                        horizontal = 16.dp,
                                        vertical = 14.dp
                                    ),

                                verticalAlignment =
                                    Alignment.CenterVertically,

                                horizontalArrangement =
                                    Arrangement.SpaceBetween
                            ) {

                                Column(
                                    modifier =
                                        Modifier.weight(1f)
                                ) {

                                    Text(
                                        s.name,
                                        fontSize = 16.sp
                                    )

                                    Text(
                                        if (
                                            s.kind ==
                                            com.example.timesheet.data.SurchargeKind.BONUS
                                        ) {
                                            "Доплата"
                                        } else {
                                            "Удержание"
                                        },

                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }

                                if (isSelected) {

                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription =
                                            "Выбрано",
                                        tint =
                                            ShiftDialogGreen
                                    )
                                }
                            }

                            Divider()
                        }
                    }
                }
            }
        }
    }

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
            initialTime = startTime,
            onDismiss = { showStartTimePicker = false },
            onConfirm = {
                startTime = it
                showStartTimePicker = false
            }
        )
    }

    if (showEndTimePicker) {
        AppTimePickerDialog(
            initialTime = endTime,
            onDismiss = { showEndTimePicker = false },
            onConfirm = {
                endTime = it
                showEndTimePicker = false
            }
        )
    }
}

@Composable
private fun ShiftDialogTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {

    Column(
        modifier = modifier
            .clickable {
                onClick()
            }
            .padding(vertical = 12.dp),

        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Text(
            text = text,

            color =
                if (selected) {
                    Color.White
                } else {
                    Color.White.copy(alpha = 0.6f)
                },

            fontSize = 13.sp,

            fontWeight =
                if (selected) {
                    FontWeight.Bold
                } else {
                    FontWeight.Normal
                }
        )

        if (selected) {

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(2.dp)
                    .background(Color.White)
            )
        }
    }
}