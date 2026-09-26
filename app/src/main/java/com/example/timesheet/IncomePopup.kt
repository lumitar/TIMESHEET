package com.example.timesheet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timesheet.data.EntryType
import com.example.timesheet.data.PayrollBreakdown
import com.example.timesheet.data.moneySignedInputFilter

@Composable
fun IncomeBar(accrued: Double, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8F5E9)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Доход",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF2E7D32)
            )
            Text(
                text = formatCurrency(accrued),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B5E20)
            )
        }
    }
}

@Composable
fun IncomeBreakdownDialog(
    monthLabel: String,
    breakdown: PayrollBreakdown,
    onDismiss: () -> Unit,
    onOpeningBalanceChange: (Double) -> Unit
) {
    var editingBalance by remember { mutableStateOf(false) }
    var balanceText by remember { mutableStateOf(breakdown.openingBalance.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(monthLabel.replaceFirstChar { it.uppercase() }) },
        text = {
            Column {
                BreakdownRow("Начислено за смены", breakdown.accrued, bold = true)

                if (breakdown.payments != 0.0) {
                    BreakdownRow("Выплаты", -breakdown.payments)
                }

                if (breakdown.taxes != 0.0) {
                    BreakdownRow("Налоги", -breakdown.taxes)
                }

                if (breakdown.adjustments != 0.0) {
                    BreakdownRow("Корректировки", breakdown.adjustments)
                }

                if (breakdown.expenses != 0.0) {
                    BreakdownRow("Расходы", -breakdown.expenses)
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                BreakdownRow("Отработано часов", breakdown.totalHours, isHours = true)

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                if (editingBalance) {
                    OutlinedTextField(
                        value = balanceText,
                        onValueChange = { balanceText = moneySignedInputFilter(it) },
                        label = { Text("Остаток на начало") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        onClick = {
                            val newBalance = balanceText.replace(',', '.').toDoubleOrNull() ?: 0.0
                            onOpeningBalanceChange(newBalance)
                            editingBalance = false
                        }
                    ) {
                        Text("Обновить")
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingBalance = true },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Остаток на начало")
                        Text(formatCurrency(breakdown.openingBalance))
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                val totalIncome = breakdown.accrued - breakdown.payments - breakdown.taxes + breakdown.adjustments - breakdown.expenses
                BreakdownRow("Доход за месяц", totalIncome, bold = true)
                BreakdownRow("Остаток на конец", breakdown.closingBalance, bold = true)

                if (breakdown.entries.isNotEmpty()) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Записи за месяц:",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    breakdown.entries.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${entry.date} ${entry.type.name}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = formatCurrency(
                                    when (entry.type) {
                                        EntryType.SHIFT -> entry.amount
                                        EntryType.PAYMENT -> -entry.amount
                                        EntryType.TAX -> -entry.amount
                                        EntryType.ADJUSTMENT -> entry.amount
                                        EntryType.EXPENSE -> -entry.amount
                                    }
                                ),
                                fontSize = 12.sp,
                                color = when (entry.type) {
                                    EntryType.SHIFT -> Color(0xFF2E7D32)
                                    EntryType.PAYMENT, EntryType.TAX, EntryType.EXPENSE -> Color.Red
                                    EntryType.ADJUSTMENT -> if (entry.amount < 0) Color.Red else Color(0xFFFF9800)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Готово")
            }
        }
    )
}

@Composable
private fun BreakdownRow(
    label: String,
    value: Double,
    bold: Boolean = false,
    isHours: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = if (isHours) {
                val hours = value.toInt()
                val minutes = ((value - hours) * 60).toInt()
                "${hours}ч ${minutes.toString().padStart(2, '0')}м"
            } else {
                formatCurrency(value)
            },
            color = when {
                isHours -> Color(0xFF2E7D32)
                value < 0 -> Color.Red
                else -> Color(0xFF2E7D32)
            },
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }
}