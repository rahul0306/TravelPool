package com.example.travelpool.screens.itinerary

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.travelpool.data.ItineraryItem
import com.example.travelpool.navigation.BottomNavigation
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryScreen(
    tripId: String,
    navController: NavController,
    onOpenChat: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    vm: ItineraryViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var bottomSelected by remember { mutableIntStateOf(2) }

    LaunchedEffect(tripId) { vm.bind(tripId) }

    var editing by remember { mutableStateOf<ItineraryItem?>(null) }
    var confirmDelete by remember { mutableStateOf<ItineraryItem?>(null) }
    val filtered = remember(state.items, state.typeFilter) {
        val f = state.typeFilter
        if (f == null) state.items else state.items.filter { normalizeType(it.type) == f }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Itinerary") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                }
            )
        },
        bottomBar = {
            BottomNavigation(
                selectedIndex = bottomSelected,
                onSelected = { idx ->
                    when (idx) {
                        0 -> navController.navigate("home")
                        1 -> navController.navigate("trips")
                        2 -> navController.navigate("itinerary")
                        3 -> onOpenChat()
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = ItineraryItem(type = "activity") }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            ItineraryFilters(
                selected = state.typeFilter,
                onSelect = vm::setTypeFilter
            )

            if (state.isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            state.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { item ->
                    ItineraryRow(
                        item = item,
                        onClick = { onOpenDetail(item.id) },
                        onEdit = { editing = item },
                        onDelete = { confirmDelete = item }
                    )
                }
            }
        }
    }

    editing?.let { item ->
        ItineraryEditorDialog(
            initial = item,
            onDismiss = { editing = null },
            onSave = { updated ->
                scope.launch {
                    vm.save(updated).onFailure { }
                    editing = null
                }
            }
        )
    }

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete item?") },
            text = { Text("This will remove “${item.title.ifBlank { "Untitled" }}” from the itinerary.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        vm.delete(item.id)
                        confirmDelete = null
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ItineraryFilters(selected: String?, onSelect: (String?) -> Unit) {
    val chips = listOf(
        null to "All",
        "flight" to "Flights",
        "hotel" to "Hotels",
        "activity" to "Activities",
        "food" to "Food",
        "other" to "Other"
    )

    SecondaryScrollableTabRow(selectedTabIndex = chips.indexOfFirst { it.first == selected }
        .coerceAtLeast(0)) {
        chips.forEach { (key, label) ->
            Tab(
                selected = key == selected,
                onClick = { onSelect(key) },
                text = { Text(label) }
            )
        }
    }
}

@Composable
private fun ItineraryRow(
    item: ItineraryItem,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val df = remember { SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault()) }
    val whenText = if (item.startTime > 0) df.format(Date(item.startTime)) else "No time set"

    ElevatedCard(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(whenText, style = MaterialTheme.typography.bodyMedium)
                if (item.location.isNotBlank()) Text(
                    item.location,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit"
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryDetailScreen(
    tripId: String,
    itemId: String,
    onBack: () -> Unit,
    vm: ItineraryViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    val ctx = LocalContext.current

    LaunchedEffect(tripId) { vm.bind(tripId) }
    val item = state.items.firstOrNull { it.id == itemId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Itinerary Item") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { pad ->
        if (item == null) {
            Box(
                Modifier
                    .padding(pad)
                    .fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text("Item not found")
            }
            return@Scaffold
        }

        val df = remember { SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault()) }

        Column(
            Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(item.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.headlineSmall)
            Text("Type: ${item.type}")
            if (item.startTime > 0) Text("Starts: ${df.format(Date(item.startTime))}")
            item.endTime?.takeIf { it > 0 }?.let { Text("Ends: ${df.format(Date(it))}") }
            if (item.location.isNotBlank()) Text("Location: ${item.location}")
            if (item.confirmationNumber.isNotBlank()) Text("Confirmation: ${item.confirmationNumber}")
            if (item.notes.isNotBlank()) Text("Notes: ${item.notes}")

            if (item.url.isNotBlank()) {
                Button(
                    onClick = {
                        val i = Intent(Intent.ACTION_VIEW, item.url.toUri())
                        ctx.startActivity(i)
                    }
                ) {
                    Icon(Icons.Default.Link, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open link")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItineraryEditorDialog(
    initial: ItineraryItem,
    onDismiss: () -> Unit,
    onSave: (ItineraryItem) -> Unit
) {
    val typeOptions = listOf(
        "flight" to "Flight",
        "hotel" to "Hotel",
        "activity" to "Activity",
        "food" to "Food",
        "other" to "Other"
    )

    var typeKey by remember {
        mutableStateOf(
            normalizeType(initial.type).ifBlank { "activity" }
        )
    }

    var title by remember { mutableStateOf(initial.title) }
    var location by remember { mutableStateOf(initial.location) }
    var startTime by remember { mutableStateOf(if (initial.startTime == 0L) System.currentTimeMillis() else initial.startTime) }
    var confirmation by remember { mutableStateOf(initial.confirmationNumber) }
    var url by remember { mutableStateOf(initial.url) }
    var notes by remember { mutableStateOf(initial.notes) }

    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = typeOptions.firstOrNull { it.first == typeKey }?.second ?: "Activity"

    val cal = remember(startTime) {
        Calendar.getInstance().apply { timeInMillis = startTime }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startTime)
    val timePickerState = rememberTimePickerState(
        initialHour = cal.get(Calendar.HOUR_OF_DAY),
        initialMinute = cal.get(Calendar.MINUTE),
        is24Hour = false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "Add item" else "Edit item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        value = selectedLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        typeOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    typeKey = key
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    modifier = Modifier.fillMaxWidth()
                )


                TextButton(onClick = { showDatePicker = true }) {
                    Text(
                        "Date: ${
                            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(
                                Date(
                                    startTime
                                )
                            )
                        }"
                    )
                }
                TextButton(onClick = { showTimePicker = true }) {
                    Text(
                        "Time: ${
                            SimpleDateFormat("h:mm a", Locale.getDefault()).format(
                                Date(
                                    startTime
                                )
                            )
                        }"
                    )
                }
                if (showDatePicker) {
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                val selected = datePickerState.selectedDateMillis
                                if (selected != null) {
                                    val c = Calendar.getInstance()
                                        .apply { timeInMillis = startTime }
                                    val timeC =
                                        Calendar.getInstance().apply { timeInMillis = selected }
                                    timeC.set(Calendar.HOUR_OF_DAY, c.get(Calendar.HOUR_OF_DAY))
                                    timeC.set(Calendar.MINUTE, c.get(Calendar.MINUTE))
                                    timeC.set(Calendar.SECOND, 0)
                                    timeC.set(Calendar.MILLISECOND, 0)
                                    startTime = timeC.timeInMillis
                                }
                                showDatePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showDatePicker = false
                            }) { Text("Cancel") }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }

                if (showTimePicker) {
                    AlertDialog(
                        onDismissRequest = { showTimePicker = false },
                        title = { Text("Select time") },
                        text = { TimePicker(state = timePickerState) },
                        confirmButton = {
                            TextButton(onClick = {
                                val c =
                                    Calendar.getInstance().apply { timeInMillis = startTime }
                                c.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                c.set(Calendar.MINUTE, timePickerState.minute)
                                c.set(Calendar.SECOND, 0)
                                c.set(Calendar.MILLISECOND, 0)
                                startTime = c.timeInMillis
                                showTimePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showTimePicker = false
                            }) { Text("Cancel") }
                        }
                    )
                }

                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    label = { Text("Confirmation #") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Link (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    initial.copy(
                        type = typeKey,
                        title = title.trim(),
                        location = location.trim(),
                        startTime = startTime,
                        confirmationNumber = confirmation.trim(),
                        url = url.trim(),
                        notes = notes.trim()
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


private fun normalizeType(raw: String): String =
    raw.trim().lowercase().let {
        when (it) {
            "flights", "flight" -> "flight"
            "hotels", "hotel" -> "hotel"
            "activities", "activity" -> "activity"
            "foods", "food", "restaurant", "restaurants" -> "food"
            "others", "other" -> "other"
            else -> it
        }
    }
