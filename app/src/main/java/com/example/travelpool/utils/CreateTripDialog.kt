package com.example.travelpool.utils

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTripDialog(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    defaultAirport: String? = null,
    onCreate: (
        name: String,
        destination: String,
        startDateMillis: Long,
        endDateMillis: Long,
        startingAirport: String?
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var startingAirport by remember { mutableStateOf(defaultAirport.orEmpty()) }

    var startDateMillis by remember { mutableStateOf<Long?>(null) }
    var endDateMillis by remember { mutableStateOf<Long?>(null) }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    fun fmt(m: Long?): String = m?.let { dateFmt.format(Date(it)) } ?: ""

    if (showStartPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDateMillis ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        startDateMillis = pickerState.selectedDateMillis
                        if (
                            endDateMillis != null &&
                            startDateMillis != null &&
                            endDateMillis!! < startDateMillis!!
                        ) {
                            endDateMillis = null
                        }
                        showStartPicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showEndPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = endDateMillis ?: (startDateMillis ?: System.currentTimeMillis())
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        endDateMillis = pickerState.selectedDateMillis
                        showEndPicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    val datesValid =
        startDateMillis != null &&
                endDateMillis != null &&
                endDateMillis!! >= startDateMillis!!

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text("New trip") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Trip name") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("Destination") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = fmt(startDateMillis),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Start date") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { showStartPicker = true }) {
                            Icon(Icons.Filled.DateRange, contentDescription = "Pick start date")
                        }
                    },
                    placeholder = { Text("Select start date") }
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = fmt(endDateMillis),
                    onValueChange = {},
                    readOnly = true,
                    enabled = startDateMillis != null,
                    label = { Text("End date") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { showEndPicker = true }) {
                            Icon(Icons.Filled.DateRange, contentDescription = "Pick end date")
                        }
                    },
                    placeholder = { Text("Select end date") },
                    isError = startDateMillis != null && endDateMillis != null && !datesValid
                )

                if (startDateMillis != null && endDateMillis != null && !datesValid) {
                    Text("End date must be on/after start date")
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = startingAirport,
                    onValueChange = { startingAirport = it },
                    label = { Text("Starting airport") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g., JFK / LGA / EWR") },
                    trailingIcon = {
                        Icon(Icons.Filled.LocationOn, contentDescription = null)
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        name.trim(),
                        destination.trim(),
                        startDateMillis!!,
                        endDateMillis!!,
                        startingAirport.trim().ifBlank { null }
                    )
                },
                enabled = name.isNotBlank() && destination.isNotBlank() && datesValid
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
