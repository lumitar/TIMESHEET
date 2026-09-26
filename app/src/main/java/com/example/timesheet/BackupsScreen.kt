package com.example.timesheet.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.timesheet.data.AppViewModel
import com.example.timesheet.data.BackupFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupsScreen(
    viewModel: AppViewModel,
    cloudSyncEnabled: Boolean,
    syncStatus: String?,
    onMenuClick: () -> Unit
) {
    var backups by remember { mutableStateOf(viewModel.listBackups()) }
    var backupToActOn by remember { mutableStateOf<BackupFile?>(null) }
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy г. HH:mm:ss", Locale("ru")) }

    var resultMessage by remember { mutableStateOf<String?>(null) }
    var backupToSaveToPhone by remember { mutableStateOf<BackupFile?>(null) }

    fun refresh() { backups = viewModel.listBackups() }

    // Импорт: пользователь сам открывает "Мои файлы" и выбирает файл бекапа
    // (.bd — наш формат, или .db — бекап старого приложения); формат
    // определяется автоматически.
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            resultMessage = when (val result = viewModel.importAnyBackup(uri)) {
                is AppViewModel.ImportResult.OwnFormat -> "Бекап восстановлен."
                is AppViewModel.ImportResult.LegacyFormat -> {
                    val summary = result.summary
                    "Импортировано из старого приложения:\n" +
                            "• организаций: ${summary.organizations}\n" +
                            "• сотрудников: ${summary.employees}\n" +
                            "• смен: ${summary.shifts}\n" +
                            "• расходов: ${summary.expenses}\n" +
                            "• выплат: ${summary.payments}\n" +
                            "• налоговых записей: ${summary.taxes}"
                }
                is AppViewModel.ImportResult.Failed ->
                    "Не удалось прочитать этот файл как бекап. Выберите файл .bd или .db."
            }
            refresh()
        }
    }

    // Сохранение конкретного бекапа туда, куда укажет пользователь (SAF "Сохранить как").
    val saveToPhoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val backup = backupToSaveToPhone
        if (uri != null && backup != null) {
            val ok = viewModel.exportBackupToUri(backup.file, uri)
            resultMessage = if (ok) "Бекап сохранён на телефон." else "Не удалось сохранить файл."
        }
        backupToSaveToPhone = null
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Бекапы", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onMenuClick) {
                            Icon(Icons.Filled.Menu, contentDescription = "Меню", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF5CA02F),
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Синхронизация с облаком (Firebase)", fontWeight = FontWeight.Medium)
                        if (syncStatus != null) {
                            Text(syncStatus, fontSize = 12.sp)
                        }
                    }
                    Switch(checked = cloudSyncEnabled, onCheckedChange = { viewModel.setCloudSyncEnabled(it) })
                }
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.createBackup()
                        refresh()
                        val folder = viewModel.lastExportFolder()
                        resultMessage = if (folder != null) {
                            "Бекап создан и сохранён в новую папку:\n$folder"
                        } else {
                            "Бекап создан."
                        }
                    },
                    containerColor = Color(0xFFFF9800),
                    contentColor = Color.White,
                    icon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                    text = { Text("Экспорт") }
                )
                Spacer(modifier = Modifier.size(12.dp))
                ExtendedFloatingActionButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    containerColor = Color(0xFF5CA02F),
                    contentColor = Color.White,
                    icon = { Icon(Icons.Filled.CloudUpload, contentDescription = null) },
                    text = { Text("Импорт") }
                )
            }
        }
    ) { innerPadding ->
        if (backups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Бекапов пока нет. Нажмите \"Экспорт\", чтобы создать.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                items(backups, key = { it.file.absolutePath }) { backup ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clickable { backupToActOn = backup }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(backup.name, fontWeight = FontWeight.Medium)
                                Text(
                                    dateFormat.format(Date(backup.createdAtMillis)),
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                "${backup.sizeBytes / 1024} Кб",
                                color = Color(0xFFFF9800)
                            )
                        }
                    }
                }
            }
        }
    }

    backupToActOn?.let { backup ->
        AlertDialog(
            onDismissRequest = { backupToActOn = null },
            title = { Text(backup.name) },
            text = { Text("Восстановить данные из этого бекапа, сохранить его на телефон или удалить?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restoreBackup(backup.file)
                    backupToActOn = null
                }) { Text("Восстановить") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        backupToSaveToPhone = backup
                        backupToActOn = null
                        saveToPhoneLauncher.launch(backup.file.name)
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" На телефон")
                    }
                    TextButton(onClick = {
                        viewModel.deleteBackup(backup.file)
                        backupToActOn = null
                        refresh()
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" Удалить")
                    }
                    TextButton(onClick = { backupToActOn = null }) { Text("Отмена") }
                }
            }
        )
    }

    resultMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { resultMessage = null },
            title = { Text("Бекапы") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { resultMessage = null }) { Text("Ок") }
            }
        )
    }
}