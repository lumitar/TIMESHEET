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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.example.timesheet.data.ExpenseCategory
import com.example.timesheet.data.Tax
import com.example.timesheet.data.TimeType
import com.example.timesheet.data.UnitOfMeasure
import com.example.timesheet.data.moneyInputFilter

// ========== ЭКРАН ТИПОВ ВРЕМЕНИ ==========
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeTypesScreen(
    timeTypes: List<TimeType>,
    onMenuClick: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (TimeType) -> Unit,
    onDelete: (String) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Типы времени", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "Меню", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF5CA02F))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = Color(0xFFFF9800),
                contentColor = Color.White
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить")
            }
        }
    ) { innerPadding ->
        if (timeTypes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Список пуст. Нажмите \"+\", чтобы добавить.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                items(timeTypes, key = { it.id }) { type ->
                    TimeTypeItem(
                        timeType = type,
                        onEdit = { onEdit(type) },
                        onDelete = { onDelete(type.id) }
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
            }
        }
    }
}

/** Компактное отображение множителя оплаты типа времени (1.0 -> "1", 1.5 -> "1.5"). */
private fun formatMultiplier(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else value.toString().trimEnd('0').trimEnd('.')

@Composable
fun TimeTypeItem(
    timeType: TimeType,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = Color(android.graphics.Color.parseColor(
                                timeType.color.ifEmpty { "#45B7D1" }
                            )),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = timeType.code.ifEmpty { "?" },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.size(12.dp))
                Column {
                    Text(
                        text = timeType.name.ifEmpty { "Без названия" },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Код: ${timeType.code}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color(android.graphics.Color.parseColor(
                        timeType.color.ifEmpty { "#45B7D1" }
                    )).copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "× ${formatMultiplier(timeType.payMultiplier)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(android.graphics.Color.parseColor(
                            timeType.color.ifEmpty { "#45B7D1" }
                        )),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.size(4.dp))
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Редактировать", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Удалить", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun TimeTypeEditDialog(
    initial: TimeType,
    onDismiss: () -> Unit,
    onSave: (TimeType) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var code by remember(initial) { mutableStateOf(initial.code) }
    var selectedColor by remember(initial) { mutableStateOf(initial.color) }
    var multiplierText by remember(initial) { mutableStateOf(formatMultiplier(initial.payMultiplier)) }

    val colors = listOf(
        "#FF6B6B", "#FF9F43", "#FECA57", "#48DBFB", "#0ABDE3",
        "#10AC84", "#5F27CD", "#FF6FB7", "#8395A7", "#222F3E"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Тип времени")
                Text("новый", fontSize = 12.sp, color = Color.Gray)
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Наименование") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(3) },
                    label = { Text("Код") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Цвет",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colors.forEach { hex ->
                        val isSelected = selectedColor == hex
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { selectedColor = hex }
                                .background(
                                    color = Color(android.graphics.Color.parseColor(hex)),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Выбрано",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = multiplierText,
                    onValueChange = { multiplierText = moneyInputFilter(it) },
                    label = { Text("Множитель оплаты (например, 1.5)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Во сколько раз умножается ставка сотрудника для смен этого типа",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val multiplier = multiplierText.replace(',', '.').toDoubleOrNull()
                    if (name.isNotBlank() && code.isNotBlank() && multiplier != null && multiplier > 0.0) {
                        onSave(
                            initial.copy(
                                name = name,
                                code = code,
                                color = selectedColor,
                                payMultiplier = multiplier
                            )
                        )
                    }
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

// ========== ЭКРАН КАТЕГОРИЙ РАСХОДОВ ==========
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseCategoriesScreen(
    categories: List<ExpenseCategory>,
    onMenuClick: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (ExpenseCategory) -> Unit,
    onDelete: (String) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Категории расходов", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "Меню", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF5CA02F))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = Color(0xFFFF9800),
                contentColor = Color.White
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить")
            }
        }
    ) { innerPadding ->
        if (categories.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Список пуст. Нажмите \"+\", чтобы добавить.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                items(categories, key = { it.id }) { category ->
                    ExpenseCategoryItem(
                        category = category,
                        onEdit = { onEdit(category) },
                        onDelete = { onDelete(category.id) }
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
            }
        }
    }
}

@Composable
fun ExpenseCategoryItem(
    category: ExpenseCategory,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category.name.ifEmpty { "Без названия" },
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Редактировать", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Удалить", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun ExpenseCategoryEditDialog(
    initial: ExpenseCategory,
    onDismiss: () -> Unit,
    onSave: (ExpenseCategory) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Категория расходов")
                Text("новая", fontSize = 12.sp, color = Color.Gray)
            }
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Наименование") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(initial.copy(name = name))
                    }
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

// ========== ЭКРАН ЕДИНИЦ ИЗМЕРЕНИЯ ==========
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitsScreen(
    units: List<UnitOfMeasure>,
    onMenuClick: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Единицы измерения", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "Меню", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF5CA02F))
            )
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            items(units, key = { it.id }) { unit ->
                UnitItem(unit = unit)
                Spacer(modifier = Modifier.size(8.dp))
            }
        }
    }
}

@Composable
fun UnitItem(unit: UnitOfMeasure) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = unit.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = unit.shortName,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
            Text(
                text = "Встроенный",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

// ========== ЭКРАН НАЛОГОВ ==========
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxesScreen(
    taxes: List<Tax>,
    onMenuClick: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Tax) -> Unit,
    onDelete: (String) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Налоги", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "Меню", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF5CA02F))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = Color(0xFFFF9800),
                contentColor = Color.White
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить")
            }
        }
    ) { innerPadding ->
        if (taxes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Список пуст. Нажмите \"+\", чтобы добавить.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                items(taxes, key = { it.id }) { tax ->
                    TaxItem(
                        tax = tax,
                        onEdit = { onEdit(tax) },
                        onDelete = { onDelete(tax.id) }
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
            }
        }
    }
}

@Composable
fun TaxItem(
    tax: Tax,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = tax.name.ifEmpty { "Без названия" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${tax.rate}%",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Редактировать", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Удалить", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun TaxEditDialog(
    initial: Tax,
    onDismiss: () -> Unit,
    onSave: (Tax) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var rateText by remember(initial) { mutableStateOf(if (initial.rate == 0.0) "" else initial.rate.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Налог")
                Text("новый", fontSize = 12.sp, color = Color.Gray)
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Наименование") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = moneyInputFilter(it) },
                    label = { Text("Ставка, %") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val rate = rateText.replace(',', '.').toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && rate > 0) {
                        onSave(initial.copy(name = name, rate = rate))
                    }
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}