package com.example.travelpool.screens.search

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.travelpool.data.AirportSuggestion
import com.example.travelpool.data.HotelSuggestion
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelSearchScreen(
    tripId: String,
    onBack: () -> Unit,
    viewModel: TravelSearchViewModel = viewModel()
) {
    val state by viewModel.ui.collectAsState()

    LaunchedEffect(tripId) {
        viewModel.loadTripMembers(tripId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(12.dp)
        ) {
            TabRow(selectedTabIndex = state.tab) {
                Tab(
                    selected = state.tab == 0,
                    onClick = { viewModel.setTab(0) },
                    text = { Text("Flights") },
                    icon = { Icon(Icons.Filled.FlightTakeoff, contentDescription = null) }
                )
                Tab(
                    selected = state.tab == 1,
                    onClick = { viewModel.setTab(1) },
                    text = { Text("Hotels") },
                    icon = { Icon(Icons.Filled.Hotel, contentDescription = null) }
                )
            }

            Spacer(Modifier.height(12.dp))

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }

            if (state.tab == 0) {
                FlightSearchPanel(tripId, state, viewModel)
            } else {
                HotelSearchPanel(tripId, state, viewModel)
            }
        }
    }
}


@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun FlightSearchPanel(
    tripId: String,
    state: TravelSearchUiState,
    vm: TravelSearchViewModel
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.coordinatedOptions.size) {
        if (state.coordinatedOptions.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(1) }
        }
    }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.FlightTakeoff, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Find Flights", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                vm.resetMultiOriginFlights()
                                vm.setDestinationQuery("")
                                vm.setDepartDate("")
                                vm.setReturnDate("")
                            }
                        ) {
                            Text("Reset")
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Text("Travelers", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.tripMembers, key = { it.uid }) { m ->
                            val selected =
                                state.travelers.any { it.id == m.uid }
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (selected) vm.removeTravelerRow(m.uid)
                                    else vm.addTravelerFromMember(
                                        m.uid,
                                        m.name
                                    )
                                },
                                label = { Text(m.name, maxLines = 1) }
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    val selectedTravelers = state.travelers.filter { t ->
                        state.tripMembers.any { it.uid == t.id }
                    }

                    if (selectedTravelers.isEmpty()) {
                        Text(
                            "Select trip members above to coordinate multi-origin flights.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(6.dp))
                    } else {
                        selectedTravelers.forEach { t ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {

                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = t.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { vm.removeTravelerRow(t.id) }) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "Remove traveler"
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    AirportAutocompleteField(
                                        label = "From",
                                        placeholder = "Enter origin airport",
                                        value = t.query,
                                        onValueChange = { vm.setTravelerOriginQuery(t.id, it) },
                                        suggestions = t.suggestions,
                                        onPick = { vm.pickTravelerOrigin(t.id, it) }
                                    )
                                    AirportSuggestionList(t.suggestions) {
                                        vm.pickTravelerOrigin(t.id, it)
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }


                    Spacer(Modifier.height(10.dp))

                    AirportAutocompleteField(
                        label = "To",
                        placeholder = "Enter destination",
                        value = state.destinationQuery,
                        onValueChange = vm::setDestinationQuery,
                        suggestions = state.destinationSuggestions,
                        onPick = vm::pickDestination
                    )
                    AirportSuggestionList(state.destinationSuggestions) { vm.pickDestination(it) }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DatePickerTapField(
                            label = "Departure",
                            value = state.departDate,
                            onDatePicked = vm::setDepartDate,
                            allowClear = false,
                            modifier = Modifier.weight(1f)
                        )

                        DatePickerTapField(
                            label = "Return",
                            value = state.returnDate,
                            onDatePicked = vm::setReturnDate,
                            allowClear = true,
                            modifier = Modifier.weight(1f)
                        )
                    }


                    Spacer(Modifier.height(14.dp))

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = vm::searchMultiOriginFlights,
                            enabled = !state.isLoading,
                            modifier = Modifier
                                .height(48.dp)
                                .width(180.dp)
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.isLoading) "Searching..." else "Search")
                        }
                    }
                }
            }
        }

        if (state.coordinatedOptions.isNotEmpty()) {
            item {
                Text("Results", style = MaterialTheme.typography.titleMedium)
            }
        }

        items(state.coordinatedOptions, key = { it.id }) { opt ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Option",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { vm.addFlightOptionToItinerary(tripId, opt) }
                        ) {
                            Text("Add")
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    opt.travelers.forEach { choice ->
                        Text(
                            text = "${choice.travelerName} (${choice.originIata}) → ${choice.offer.summary} • ${choice.offer.price}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}


@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun HotelSearchPanel(
    tripId: String,
    state: TravelSearchUiState,
    vm: TravelSearchViewModel
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.hotelResults.size) {
        if (state.hotelResults.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(1) }
        }
    }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Hotel, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Find Hotels",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                vm.setHotelPlaceQuery("")
                                vm.setCheckIn("")
                                vm.setCheckOut("")
                            }
                        ) {
                            Text("Reset")
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    HotelAutocompleteField(
                        label = "Destination",
                        placeholder = "Hotel or city name",
                        value = state.hotelPlaceQuery,
                        onValueChange = vm::setHotelPlaceQuery,
                        suggestions = state.hotelSuggestions,
                        onPick = vm::pickHotel
                    )
                    HotelSuggestionList(state.hotelSuggestions) { vm.pickHotel(it) }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DatePickerTapField(
                            label = "Check-In",
                            value = state.checkIn,
                            onDatePicked = vm::setCheckIn,
                            allowClear = false,
                            modifier = Modifier.weight(1f)
                        )

                        DatePickerTapField(
                            label = "Check-Out",
                            value = state.checkOut,
                            onDatePicked = vm::setCheckOut,
                            allowClear = true,
                            modifier = Modifier.weight(1f)
                        )
                    }


                    Spacer(Modifier.height(14.dp))

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = vm::searchHotels,
                            enabled = !state.isLoading,
                            modifier = Modifier
                                .height(48.dp)
                                .width(180.dp)
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.isLoading) "Searching..." else "Search")
                        }
                    }
                }
            }
        }
        if (state.hotelResults.isNotEmpty()) {
            item {
                Text("Results", style = MaterialTheme.typography.titleMedium)
            }
        }

        items(
            state.hotelResults,
            key = { h -> h.id.takeIf { it.isNotBlank() } ?: "${h.name}-${h.price}" }
        ) { h ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            h.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(0.7f)
                        )
                        Button(
                            onClick = { vm.addHotelToItinerary(tripId, h) },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("Add")
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(h.price, style = MaterialTheme.typography.bodyMedium)

                    h.address?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

    }
}

@Composable
private fun ClearableOutlinedTextField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        trailingIcon = {
            if (value.isNotBlank()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear")
                }
            }
        }
    )
}

@Composable
private fun AirportSuggestionList(
    items: List<AirportSuggestion>,
    onPick: (AirportSuggestion) -> Unit
) {
    if (items.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 6.dp)) {
            items.take(6).forEach { a ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(a) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(a.label)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun HotelSuggestionList(items: List<HotelSuggestion>, onPick: (HotelSuggestion) -> Unit) {
    if (items.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 6.dp)) {
            items.take(6).forEach { h ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(h) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = buildString {
                            append(h.name)
                            if (h.cityName.isNotBlank()) append(" — ").append(h.cityName)
                            if (h.countryName.isNotBlank()) append(", ").append(h.countryName)
                        }
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AirportAutocompleteField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<AirportSuggestion>,
    onPick: (AirportSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        expanded = value.trim().length >= 2
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = it.trim().length >= 2
            },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (value.isNotBlank()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            }
        )

        DropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            suggestions.take(8).forEach { s ->
                DropdownMenuItem(
                    text = { Text("${s.iataCode} — ${s.cityName}") },
                    onClick = {
                        onPick(s)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HotelAutocompleteField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<HotelSuggestion>,
    onPick: (HotelSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        expanded = value.trim().length >= 2
    }

    ExposedDropdownMenuBox(
        expanded = expanded && suggestions.isNotEmpty(),
        onExpandedChange = {  },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = it.trim().length >= 2
            },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (value.isNotBlank()) {
                        IconButton(onClick = { onValueChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            suggestions.take(8).forEach { h ->
                DropdownMenuItem(
                    text = { Text("${h.name} — ${h.cityName}") },
                    onClick = {
                        onPick(h)
                        expanded = false
                    }
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerTapField(
    label: String,
    value: String,
    onDatePicked: (String) -> Unit,
    allowClear: Boolean = true,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }

    val utc = remember { java.time.ZoneOffset.UTC }
    fun todayStrUtc(): String = java.time.LocalDate.now(utc).toString()

    LaunchedEffect(value) {
        if (value.isBlank()) onDatePicked(todayStrUtc())
    }

    val initialMillis = remember(value) {
        val dateStr = value.ifBlank { todayStrUtc() }
        runCatching {
            java.time.LocalDate.parse(dateStr)
                .atStartOfDay(utc)
                .toInstant()
                .toEpochMilli()
        }.getOrElse {
            java.time.LocalDate.now(utc).atStartOfDay(utc).toInstant().toEpochMilli()
        }
    }

    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis
    )

    if (open) {
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = pickerState.selectedDateMillis
                        if (millis != null) {
                            val picked = java.time.Instant.ofEpochMilli(millis)
                                .atZone(utc)
                                .toLocalDate()
                                .toString()
                            onDatePicked(picked)
                        }
                        open = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (allowClear) {
                        TextButton(
                            onClick = {
                                onDatePicked("")
                                open = false
                            }
                        ) { Text("Clear") }
                    }
                    TextButton(onClick = { open = false }) { Text("Cancel") }
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { open = true }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = value.ifBlank { todayStrUtc() },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
            }
        }
    }
}



