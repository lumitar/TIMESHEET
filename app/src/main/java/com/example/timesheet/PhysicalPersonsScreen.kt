package com.example.timesheet.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timesheet.data.Employee

data class ContactPick(
    val name: String,
    val phone: String,
    val photoUri: String?,
    val lookupKey: String?
)

/**
 * Копирует фото контакта во внутреннее хранилище приложения и возвращает путь
 * к копии. Доступ к content://-адресу фото, который отдаёт выбор контакта,
 * гарантирован только сразу после выбора — если сохранить сам этот адрес, он
 * может перестать открываться после перезапуска приложения. Поэтому фото
 * забирается один раз, в момент выбора контакта, и дальше сотрудник хранит
 * уже свою собственную копию.
 */
private fun persistContactPhoto(context: android.content.Context, contactPhotoUri: String): String? = runCatching {
    val input = context.contentResolver.openInputStream(Uri.parse(contactPhotoUri)) ?: return null
    val dir = java.io.File(context.filesDir, "employee_photos").apply { if (!exists()) mkdirs() }
    val outFile = java.io.File(dir, "${java.util.UUID.randomUUID()}.jpg")
    input.use { inp -> outFile.outputStream().use { out -> inp.copyTo(out) } }
    outFile.absolutePath
}.getOrNull()

private fun readContactFromUri(context: android.content.Context, uri: Uri): ContactPick? {
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
        ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
    )
    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)) ?: ""
            val phone = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)) ?: ""
            val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
            val rawPhoto = if (photoIdx >= 0) cursor.getString(photoIdx) else null
            val persistedPhoto = rawPhoto?.let { persistContactPhoto(context, it) }
            val lookupIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
            val lookupKey = if (lookupIdx >= 0) cursor.getString(lookupIdx) else null
            return ContactPick(name = name, phone = phone, photoUri = persistedPhoto, lookupKey = lookupKey)
        }
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhysicalPersonsScreen(
    employees: List<Employee>,
    onMenuClick: () -> Unit,
    onAddFromContact: (ContactPick) -> Unit,
    onAddManual: () -> Unit,
    onEdit: (Employee) -> Unit,
    onDelete: (String) -> Unit
) {
    val context = LocalContext.current
    var addMenuExpanded by remember { mutableStateOf(false) }
    var expandedEmployeeId by remember { mutableStateOf<String?>(null) }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            readContactFromUri(context, uri)?.let { onAddFromContact(it) }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Физлица", color = Color.White) },
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
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { addMenuExpanded = true },
                    containerColor = Color(0xFFFF9800),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Добавить сотрудника")
                }
                DropdownMenu(expanded = addMenuExpanded, onDismissRequest = { addMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Из контактов") },
                        leadingIcon = { Icon(Icons.Filled.Contacts, contentDescription = null) },
                        onClick = {
                            addMenuExpanded = false
                            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                            contactPickerLauncher.launch(intent)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Вручную") },
                        leadingIcon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                        onClick = {
                            addMenuExpanded = false
                            onAddManual()
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        if (employees.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Список пуст. Нажмите \"+\", чтобы добавить сотрудника.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)
            ) {
                items(employees, key = { it.id }) { employee ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedEmployeeId = if (expandedEmployeeId == employee.id) null else employee.id
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PersonAvatar(photoUri = employee.photoUri, size = 44.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(employee.name, fontSize = 18.sp)
                                    if (employee.phone.isNotBlank()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Filled.Phone,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = Color.Gray
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(employee.phone, fontSize = 13.sp, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                            if (expandedEmployeeId == employee.id) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(onClick = { onEdit(employee) }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Редактировать")
                                    }
                                    IconButton(onClick = { onDelete(employee.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                }
            }
        }
    }
}

/** Круглый аватар сотрудника: фото (если есть) или иконка-заглушка. */
@Composable
fun PersonAvatar(photoUri: String?, size: Dp = 44.dp) {
    val context = LocalContext.current
    var bitmap by remember(photoUri) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(photoUri) {
        bitmap = null
        if (!photoUri.isNullOrBlank()) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }.getOrNull()?.let { bitmap = it }
        }
    }

    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF5CA02F)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size / 1.6f)
            )
        }
    }
}

data class PhysicalPersonDialogState(
    val editingId: String?,
    val initialName: String,
    val initialPhone: String,
    val initialPhotoUri: String?,
    val initialLookupKey: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhysicalPersonEditDialog(
    title: String,
    state: PhysicalPersonDialogState,
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String, photoUri: String?) -> Unit
) {
    val context = LocalContext.current
    var name by remember(state) { mutableStateOf(state.initialName) }
    var phone by remember(state) { mutableStateOf(state.initialPhone) }
    var photoUri by remember(state) { mutableStateOf(state.initialPhotoUri) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            photoUri = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.clickable { photoPickerLauncher.launch(arrayOf("image/*")) }) {
                        PersonAvatar(photoUri = photoUri, size = 56.dp)
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(Color(0xFFFF9800)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.PhotoCamera,
                                contentDescription = "Изменить фото",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Имя") },
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Телефон") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name, phone, photoUri) }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
