package com.example.timesheet.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timesheet.data.EntryType
import com.example.timesheet.data.ExpenseCategory
import com.example.timesheet.data.LedgerEntry
import com.example.timesheet.data.ShiftType
import com.example.timesheet.data.TimeType
import com.example.timesheet.data.UnitOfMeasure
import com.example.timesheet.data.shiftTypeTimeTypeId
import com.example.timesheet.data.displayShiftLabel
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun JournalEntryItem(
    entry: LedgerEntry,
    employeeName: String?,
    organizationName: String?,
    timeTypes: List<TimeType> = emptyList(),
    expenseCategories: List<ExpenseCategory> = emptyList(),
    units: List<UnitOfMeasure> = emptyList(),
    onEdit: (LedgerEntry) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onRecalculate: (String) -> Unit = {},
    onAttachmentsChanged: (String, List<String>) -> Unit = { _, _ -> }
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            val merged = (entry.attachments + uris.map { it.toString() }).distinct()
            onAttachmentsChanged(entry.id, merged)
        }
    }

    val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy 'г.' EEE", Locale("ru"))
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale("ru"))

    fun formatEntryDate(date: java.time.LocalDate): String {
        val formatted = date.format(dateFormatter)
        val lastSpace = formatted.lastIndexOf(' ')
        return if (lastSpace == -1) formatted else {
            formatted.substring(0, lastSpace + 1) + formatted.substring(lastSpace + 1).uppercase(Locale("ru"))
        }
    }

    
    val selectedTimeType = if (entry.type == EntryType.SHIFT) {
        timeTypes.find { it.id == entry.timeTypeId }
            ?: timeTypes.find { it.id == shiftTypeTimeTypeId(entry.shiftType) }
    } else {
        null
    }

    fun getShiftColor(): Color {
        if (entry.type != EntryType.SHIFT) return Color.Transparent

        if (entry.overtimeEnabled) return Color(0xFF9C27B0)

        if (selectedTimeType != null && selectedTimeType.color.isNotBlank()) {
            return try {
                Color(android.graphics.Color.parseColor(selectedTimeType.color))
            } catch (e: Exception) {
                Color(0xFF4CAF50)
            }
        }

        // Если тип не найден в справочнике — используем стандартные цвета
        return when (entry.shiftType) {
            ShiftType.DAY -> Color(0xFF4CAF50)
            ShiftType.NIGHT -> Color(0xFF2196F3)
            ShiftType.HOLIDAY -> Color(0xFFFF9800)
            ShiftType.OVERTIME -> Color(0xFF9C27B0)
            ShiftType.WEEKEND -> Color(0xFFF44336)
        }
    }

    val shiftColor = getShiftColor()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        if (entry.type == EntryType.SHIFT) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        color = shiftColor,
                        shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                    )
            )
        }

        OutlinedCard(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (entry.type == EntryType.SHIFT) 0.dp else 0.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = Color.White
            ),
            shape = if (entry.type == EntryType.SHIFT) {
                RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
            } else {
                RoundedCornerShape(4.dp)
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                when (entry.type) {
                    EntryType.SHIFT -> {
                        val startStr = entry.startTime?.format(timeFormatter) ?: "--:--"
                        val endStr = entry.endTime?.format(timeFormatter) ?: "--:--"
                        val shiftTypeName = displayShiftLabel(
                            selectedTimeType?.name ?: when (entry.shiftType) {
                                ShiftType.DAY -> "Дневная"
                                ShiftType.NIGHT -> "Ночная"
                                ShiftType.HOLIDAY -> "Праздничная"
                                ShiftType.OVERTIME -> "Сверхурочная"
                                ShiftType.WEEKEND -> "Выходной день"
                            },
                            entry.overtimeEnabled
                        )
                        val hours = entry.calculateHours()
                        val hoursInt = hours.toInt()
                        val minutes = ((hours - hoursInt) * 60).toInt()

                        val paidHours = entry.calculatePaidHours()
                        val paidHoursInt = paidHours.toInt()
                        val paidMinutes = ((paidHours - paidHoursInt) * 60).toInt()
                        val breakHours = entry.unpaidBreakMinutes / 60
                        val breakMinutes = entry.unpaidBreakMinutes % 60

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${formatEntryDate(entry.date)}, $startStr - $endStr, ${hoursInt}ч ${minutes.toString().padStart(2, '0')}м",
                                fontSize = 14.sp,
                                color = Color(0xFF333333),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatCurrency(entry.amount),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = shiftTypeName,
                                fontSize = 14.sp,
                                color = if (entry.overtimeEnabled) Color(0xFF9C27B0) else Color.DarkGray,
                                fontWeight = if (entry.overtimeEnabled) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (entry.note.isNotBlank()) {
                                Text(
                                    text = entry.note,
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "${paidHoursInt}ч ${paidMinutes.toString().padStart(2, '0')}м",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF2E7D32)
                            )
                            Text(
                                text = "${breakHours}ч ${breakMinutes.toString().padStart(2, '0')}м",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }

                        if (employeeName != null || organizationName != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (employeeName != null) {
                                    Text(
                                        text = "👤 $employeeName",
                                        fontSize = 13.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (organizationName != null) {
                                    Text(
                                        text = "🏢 $organizationName",
                                        fontSize = 13.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    EntryType.PAYMENT -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${formatEntryDate(entry.date)}, ${entry.startTime?.format(timeFormatter) ?: "00:00"}",
                                fontSize = 14.sp,
                                color = Color(0xFF333333),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatCurrency(entry.amount),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Выплата",
                            fontSize = 14.sp,
                            color = Color.DarkGray
                        )
                        if (entry.note.isNotBlank()) {
                            Text(
                                text = entry.note,
                                fontSize = 13.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    EntryType.TAX -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${formatEntryDate(entry.date)}, ${entry.startTime?.format(timeFormatter) ?: "00:00"}",
                                fontSize = 14.sp,
                                color = Color(0xFF333333),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatCurrency(entry.amount),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Red,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Налог",
                            fontSize = 14.sp,
                            color = Color.DarkGray
                        )
                        if (entry.note.isNotBlank()) {
                            Text(
                                text = entry.note,
                                fontSize = 13.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    EntryType.ADJUSTMENT -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${formatEntryDate(entry.date)}, ${entry.startTime?.format(timeFormatter) ?: "00:00"}",
                                fontSize = 14.sp,
                                color = Color(0xFF333333),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatCurrency(entry.amount),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (entry.amount < 0) Color.Red else Color(0xFFFF9800),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (entry.amount < 0) "Удержание" else "Доплата",
                            fontSize = 14.sp,
                            color = Color.DarkGray
                        )
                        if (entry.adjustmentTypeName.isNotBlank()) {
                            Text(
                                text = entry.adjustmentTypeName,
                                fontSize = 13.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (entry.projectName.isNotBlank()) {
                            Text(
                                text = "Проект: ${entry.projectName}",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (entry.note.isNotBlank()) {
                            Text(
                                text = entry.note,
                                fontSize = 13.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    EntryType.EXPENSE -> {
                        val categoryName = expenseCategories.firstOrNull { it.id == entry.expenseCategoryId }?.name
                        val unitName = units.firstOrNull { it.id == entry.unitId }?.name ?: entry.unitId
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${formatEntryDate(entry.date)}, ${entry.startTime?.format(timeFormatter) ?: "00:00"}",
                                fontSize = 14.sp,
                                color = Color(0xFF333333),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatCurrency(entry.amount),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF5722),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Расход",
                            fontSize = 14.sp,
                            color = Color.DarkGray
                        )
                        if (!categoryName.isNullOrBlank()) {
                            Text(
                                text = "Категория: $categoryName",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (entry.unitId != null && entry.quantity > 0) {
                            Text(
                                text = "Количество: ${entry.quantity} ${unitName ?: ""}",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (entry.note.isNotBlank()) {
                            Text(
                                text = entry.note,
                                fontSize = 13.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (expanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = { attachmentPicker.launch(arrayOf("*/*")) }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AttachFile,
                                contentDescription = "Вложения",
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (entry.attachments.isEmpty()) {
                                    "Вложения"
                                } else {
                                    "Вложения (${entry.attachments.size})"
                                },
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = { onEdit(entry) }
                        ) {
                            Text(
                                "Редактировать",
                                fontSize = 12.sp,
                                color = Color(0xFF2196F3),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = { onRecalculate(entry.id) }
                        ) {
                            Text(
                                "Пересчитать",
                                fontSize = 12.sp,
                                color = Color(0xFFFF9800),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = { onDelete(entry.id) }
                        ) {
                            Text(
                                "Удалить",
                                fontSize = 12.sp,
                                color = Color.Red,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (entry.attachments.isNotEmpty()) {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            entry.attachments.forEach { uriString ->
                                val uri = remember(uriString) { Uri.parse(uriString) }
                                val displayName = remember(uriString) {
                                    uriString.substringAfterLast('/').substringBefore('?')
                                        .ifBlank { "Файл" }
                                }
                                TextButton(
                                    onClick = {
                                        runCatching {
                                            val type =
                                                context.contentResolver.getType(uri) ?: "*/*"
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, type)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.InsertDriveFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = displayName,
                                        fontSize = 12.sp,
                                        color = Color(0xFF5CA02F),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(
                        onClick = { expanded = !expanded }
                    ) {
                        Text(
                            text = if (expanded) "Скрыть" else "Подробнее",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}