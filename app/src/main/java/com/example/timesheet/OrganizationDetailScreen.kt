package com.example.timesheet.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.example.timesheet.data.EmployeeOrgLink
import com.example.timesheet.data.Project
import com.example.timesheet.data.Surcharge
import com.example.timesheet.data.SurchargeKind
import com.example.timesheet.data.TimeType
import com.example.timesheet.data.moneySignedInputFilter

private val BrandGreen = Color(0xFF5CA02F)
private val BrandOrange = Color(0xFFFF9800)

private val orgTabs = listOf("Работники", "Типы смен", "Доплаты и удержания", "Проекты")

/**
 * Карточка организации: имя редактируется через карандаш в шапке, ниже —
 * четыре вкладки. «Работники» показывает всех сотрудников из «Физлица» со
 * ставкой и остатком, действующими именно для этой организации; «Типы
 * смен» и «Доплаты и удержания» — общие справочники приложения; «Проекты» —
 * список, привязанный к этой организации.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationDetailScreen(
    organizationName: String,
    employees: List<Employee>,
    employeeOrgLinks: List<EmployeeOrgLink>,
    timeTypes: List<TimeType>,
    surcharges: List<Surcharge>,
    projects: List<Project>,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onDeleteOrganization: () -> Unit,
    onEmployeeClick: (Employee) -> Unit,
    onAddTimeType: () -> Unit,
    onEditTimeType: (TimeType) -> Unit,
    onDeleteTimeType: (String) -> Unit,
    onAddSurcharge: () -> Unit,
    onEditSurcharge: (Surcharge) -> Unit,
    onDeleteSurcharge: (String) -> Unit,
    onAddProject: () -> Unit,
    onEditProject: (Project) -> Unit,
    onDeleteProject: (String) -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(organizationName, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = onRename) {
                            Icon(Icons.Filled.Edit, contentDescription = "Переименовать", tint = Color.White)
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Ещё", tint = Color.White)
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("Удалить организацию") },
                                    onClick = {
                                        menuExpanded = false
                                        confirmDelete = true
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandGreen)
                )
                TabRow(selectedTabIndex = tab, containerColor = BrandGreen, contentColor = Color.White) {
                    orgTabs.forEachIndexed { index, label ->
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            text = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            when (tab) {
                1 -> FloatingActionButton(onClick = onAddTimeType, containerColor = BrandOrange, contentColor = Color.White) {
                    Icon(Icons.Filled.Add, contentDescription = "Добавить тип смены")
                }
                2 -> FloatingActionButton(onClick = onAddSurcharge, containerColor = BrandOrange, contentColor = Color.White) {
                    Icon(Icons.Filled.Add, contentDescription = "Добавить доплату/удержание")
                }
                3 -> FloatingActionButton(onClick = onAddProject, containerColor = BrandOrange, contentColor = Color.White) {
                    Icon(Icons.Filled.Add, contentDescription = "Добавить проект")
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (tab) {
                0 -> EmployeesTab(employees, employeeOrgLinks, onEmployeeClick)
                1 -> TimeTypesTab(timeTypes, onEditTimeType, onDeleteTimeType)
                2 -> SurchargesTab(surcharges, onEditSurcharge, onDeleteSurcharge)
                3 -> ProjectsTab(projects, onEditProject, onDeleteProject)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить организацию?") },
            text = { Text("«$organizationName» и её связи с сотрудниками будут удалены. Смены, привязанные к этой организации, останутся в журнале.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteOrganization()
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun EmployeesTab(
    employees: List<Employee>,
    links: List<EmployeeOrgLink>,
    onEmployeeClick: (Employee) -> Unit
) {
    if (employees.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("В «Физлица» пока никого нет.")
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(employees, key = { it.id }) { employee ->
            val link = links.find { it.employeeId == employee.id }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEmployeeClick(employee) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PersonAvatar(photoUri = employee.photoUri, size = 40.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(employee.name, fontSize = 16.sp)
                    }
                    Text(
                        text = "${formatMoney(link?.hourlyRate ?: 0.0)} ₽ / ч",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TimeTypesTab(
    timeTypes: List<TimeType>,
    onEdit: (TimeType) -> Unit,
    onDelete: (String) -> Unit
) {
    if (timeTypes.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Список пуст. Нажмите \"+\", чтобы добавить.")
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(timeTypes, key = { it.id }) { type ->
            TimeTypeItem(timeType = type, onEdit = { onEdit(type) }, onDelete = { onDelete(type.id) })
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SurchargesTab(
    surcharges: List<Surcharge>,
    onEdit: (Surcharge) -> Unit,
    onDelete: (String) -> Unit
) {
    if (surcharges.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Список пуст. Нажмите \"+\", чтобы добавить.")
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(surcharges, key = { it.id }) { s ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onEdit(s) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(s.name.ifEmpty { "Без названия" }, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (s.kind == SurchargeKind.BONUS) "Доплата" else "Удержание",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    if (!s.isBuiltIn) {
                        IconButton(onClick = { onDelete(s.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Удалить", modifier = Modifier.width(20.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ProjectsTab(
    projects: List<Project>,
    onEdit: (Project) -> Unit,
    onDelete: (String) -> Unit
) {
    if (projects.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Список пуст. Нажмите \"+\", чтобы добавить.")
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(projects, key = { it.id }) { project ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(project.name, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onEdit(project) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Редактировать", modifier = Modifier.width(20.dp))
                    }
                    IconButton(onClick = { onDelete(project.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить", modifier = Modifier.width(20.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private fun formatMoney(value: Double): String =
    if (value == value.toLong().toDouble()) "%,d".format(value.toLong()).replace(',', ' ')
    else "%,.2f".format(value).replace(',', ' ')

data class EmployeeOrgRateDialogState(
    val employeeId: String,
    val employeeName: String,
    val organizationId: String,
    val initialRate: Double,
    val initialOpeningBalance: Double,
    val hasLink: Boolean
)

@Composable
fun EmployeeOrgRateDialog(
    state: EmployeeOrgRateDialogState,
    onDismiss: () -> Unit,
    onSave: (rate: Double, openingBalance: Double) -> Unit,
    onRemoveFromOrganization: () -> Unit
) {
    var rateText by remember(state) { mutableStateOf(if (state.initialRate == 0.0) "" else state.initialRate.toString()) }
    var balanceText by remember(state) { mutableStateOf(if (state.initialOpeningBalance == 0.0) "" else state.initialOpeningBalance.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.employeeName) },
        text = {
            Column {
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = moneySignedInputFilter(it) },
                    label = { Text("Ставка в час, ₽") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = moneySignedInputFilter(it) },
                    label = { Text("Остаток на начало, ₽") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val rate = rateText.replace(',', '.').toDoubleOrNull() ?: 0.0
                val balance = balanceText.replace(',', '.').toDoubleOrNull() ?: 0.0
                onSave(rate, balance)
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (state.hasLink) {
                    TextButton(onClick = onRemoveFromOrganization) { Text("Убрать из организации") }
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        }
    )
}
