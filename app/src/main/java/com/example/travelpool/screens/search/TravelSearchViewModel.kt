package com.example.travelpool.screens.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelpool.data.AirportSuggestion
import com.example.travelpool.data.CoordinatedFlightOption
import com.example.travelpool.data.FlightOfferUi
import com.example.travelpool.data.HotelOfferUi
import com.example.travelpool.data.HotelSuggestion
import com.example.travelpool.data.ItineraryItem
import com.example.travelpool.data.TravelerFlightChoice
import com.example.travelpool.data.TravelerOriginRow
import com.example.travelpool.screens.itinerary.ItineraryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import kotlin.collections.emptyList

class TravelSearchViewModel(
    private val repo: TravelSearchRepository = TravelSearchRepository(),

) : ViewModel() {

    private val _ui = MutableStateFlow(TravelSearchUiState())
    val ui: StateFlow<TravelSearchUiState> = _ui.asStateFlow()

    private val itineraryRepo = ItineraryRepository()

    private var flightSearchJob: Job? = null
    private var hotelSearchJob: Job? = null

    private val originQueryFlow = MutableStateFlow("")
    private val destQueryFlow = MutableStateFlow("")
    private val hotelQueryFlow = MutableStateFlow("")

    private data class TravelerQueryEvent(val travelerId: String, val query: String)

    private val travelerQueryFlow = MutableStateFlow(TravelerQueryEvent(travelerId = "", query = ""))

    private val travelerAutocompleteJobs = mutableMapOf<String, Job>()

    init {
        observeOriginAutocomplete()
        observeDestinationAutocomplete()
        observeHotelAutocomplete()
        observeTravelerOriginAutocomplete()
        testWorkerAuthOnce()
    }

    fun addHotelToItinerary(tripId: String, h: HotelOfferUi) {
        viewModelScope.launch {
            val item = ItineraryItem(
                type = "hotel",
                title = h.name,
                location = h.address.orEmpty(),
                startTime = dateToStartMillis(_ui.value.checkIn),
                endTime = _ui.value.checkOut.takeIf { it.isNotBlank() }?.let { dateToStartMillis(it) },
                url = h.deepLink.orEmpty(),
                notes = "Price: ${h.price}"
            )

            val res = itineraryRepo.upsert(tripId, item)
            _ui.value = _ui.value.copy(
                error = if (res.isSuccess) "Added to itinerary." else res.exceptionOrNull()?.message
            )
        }
    }

    fun addFlightOptionToItinerary(tripId: String, opt: CoordinatedFlightOption) {
        viewModelScope.launch {
            val title = "Flights (${_ui.value.destinationSelected?.iataCode.orEmpty()})"
            val notes = opt.travelers.joinToString("\n") { c ->
                "${c.travelerName}: ${c.originIata} → ${c.offer.summary} • ${c.offer.price}"
            }

            val item = ItineraryItem(
                type = "flight",
                title = title.ifBlank { "Flight" },
                location = _ui.value.destinationSelected?.cityName.orEmpty(),
                startTime = dateToStartMillis(_ui.value.departDate),
                endTime = _ui.value.returnDate.takeIf { it.isNotBlank() }
                    ?.let { dateToStartMillis(it) },
                url = opt.travelers.firstOrNull()?.offer?.deepLink.orEmpty(),
                notes = notes
            )

            val res = itineraryRepo.upsert(tripId, item)
            _ui.value = _ui.value.copy(
                error = if (res.isSuccess) "Added to itinerary." else res.exceptionOrNull()?.message
            )
        }
    }
    private fun dateToStartMillis(date: String): Long {
        if (date.isBlank()) return System.currentTimeMillis()
        val parts = date.split("-").mapNotNull { it.toIntOrNull() }
        if (parts.size != 3) return System.currentTimeMillis()

        val (y, m, d) = parts
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, m - 1)
            set(Calendar.DAY_OF_MONTH, d)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }


    private fun observeOriginAutocomplete() {
        viewModelScope.launch {
            originQueryFlow
                .map { it.trim() }
                .distinctUntilChanged()
                .debounce(350)
                .collect { q ->
                    if (_ui.value.originSelected != null) return@collect

                    if (q.length < 2) {
                        _ui.value = _ui.value.copy(originSuggestions = emptyList())
                        return@collect
                    }

                    val items = runCatching { repo.autocompleteAirports(q) }
                        .onFailure { e -> Log.e("AUTO", "Origin autocomplete failed for '$q'", e) }
                        .getOrElse { emptyList() }

                    if (_ui.value.originSelected == null && _ui.value.originQuery.trim() == q) {
                        _ui.value = _ui.value.copy(originSuggestions = items)
                    }
                }
        }
    }

    private fun observeHotelAutocomplete() {
        viewModelScope.launch {
            hotelQueryFlow
                .map { it.trim() }
                .distinctUntilChanged()
                .debounce(350)
                .collect { q ->

                    if (_ui.value.hotelSelected != null) return@collect

                    if (q.length < 2) {
                        _ui.value = _ui.value.copy(hotelSuggestions = emptyList())
                        return@collect
                    }

                    val items = runCatching { repo.autocompleteHotels(q) }
                        .onFailure { e -> Log.e("AUTO", "Hotel autocomplete failed for '$q'", e) }
                        .getOrElse { emptyList() }

                    if (_ui.value.hotelSelected == null && _ui.value.hotelPlaceQuery.trim() == q) {
                        _ui.value = _ui.value.copy(hotelSuggestions = items)
                    }
                }
        }
    }

    private fun observeTravelerOriginAutocomplete() {
        viewModelScope.launch {
            travelerQueryFlow
                .map { it.copy(query = it.query.trim()) }
                .distinctUntilChanged()
                .debounce(350)
                .collect { ev ->
                    val travelerId = ev.travelerId
                    val q = ev.query

                    if (travelerId.isBlank()) return@collect

                    if (q.length < 2) {
                        _ui.value = _ui.value.copy(
                            travelers = _ui.value.travelers.map { t ->
                                if (t.id == travelerId) t.copy(suggestions = emptyList()) else t
                            }
                        )
                        return@collect
                    }

                    val row = _ui.value.travelers.firstOrNull { it.id == travelerId } ?: return@collect
                    if (row.selected != null) return@collect

                    val items = runCatching { repo.autocompleteAirports(q) }
                        .onFailure { e -> Log.e("AUTO", "Traveler origin autocomplete failed for '$q'", e) }
                        .getOrElse { emptyList() }

                    val stillSame = _ui.value.travelers.firstOrNull { it.id == travelerId }?.query?.trim() == q
                    if (stillSame) {
                        _ui.value = _ui.value.copy(
                            travelers = _ui.value.travelers.map { t ->
                                if (t.id == travelerId) t.copy(suggestions = items) else t
                            }
                        )
                    }
                }
        }
    }
    private fun observeDestinationAutocomplete() {
        viewModelScope.launch {
            destQueryFlow
                .map { it.trim() }
                .distinctUntilChanged()
                .debounce(350)
                .collect { q ->
                    if (_ui.value.destinationSelected != null) return@collect

                    if (q.length < 2) {
                        _ui.value = _ui.value.copy(destinationSuggestions = emptyList())
                        return@collect
                    }

                    val items = runCatching { repo.autocompleteAirports(q) }
                        .onFailure { e -> Log.e("AUTO", "Dest autocomplete failed for '$q'", e) }
                        .getOrElse { emptyList() }

                    if (_ui.value.destinationSelected == null && _ui.value.destinationQuery.trim() == q) {
                        _ui.value = _ui.value.copy(destinationSuggestions = items)
                    }
                }
        }
    }

    fun setTab(tab: Int) {
        _ui.value = _ui.value.copy(tab = tab, error = null)
    }

    fun setOriginQuery(v: String) {
        Log.d("AUTO", "Typing origin: $v")
        _ui.value = _ui.value.copy(originQuery = v, originSelected = null)
        if (v.trim().length < 2) _ui.value = _ui.value.copy(originSuggestions = emptyList())
        originQueryFlow.value = v
    }

    fun setDestinationQuery(v: String) {
        Log.d("AUTO", "Typing dest: $v")
        _ui.value = _ui.value.copy(destinationQuery = v, destinationSelected = null)
        if (v.trim().length < 2) _ui.value = _ui.value.copy(destinationSuggestions = emptyList())
        destQueryFlow.value = v
    }

    fun pickOrigin(a: AirportSuggestion) {
        _ui.value = _ui.value.copy(
            originSelected = a,
            originQuery = a.label,
            originSuggestions = emptyList()
        )
        originQueryFlow.value = ""
    }

    fun pickDestination(a: AirportSuggestion) {
        _ui.value = _ui.value.copy(
            destinationSelected = a,
            destinationQuery = a.label,
            destinationSuggestions = emptyList()
        )
        destQueryFlow.value = ""
    }

    fun setDepartDate(v: String) { _ui.value = _ui.value.copy(departDate = v) }
    fun setReturnDate(v: String) { _ui.value = _ui.value.copy(returnDate = v) }
    fun setAdults(v: Int) { _ui.value = _ui.value.copy(adults = v.coerceIn(1, 9)) }
    fun setHotelPlaceQuery(v: String) {
        _ui.value = _ui.value.copy(
            hotelPlaceQuery = v,
            hotelSelected = null,
            hotelPlaceId = ""
        )
        if (v.trim().length < 2) _ui.value = _ui.value.copy(hotelSuggestions = emptyList())
        hotelQueryFlow.value = v
    }

    fun setCheckIn(v: String) { _ui.value = _ui.value.copy(checkIn = v) }
    fun setCheckOut(v: String) { _ui.value = _ui.value.copy(checkOut = v) }
    fun setRooms(v: Int) { _ui.value = _ui.value.copy(rooms = v.coerceIn(1, 9)) }

    fun searchFlights() {
        val s = _ui.value
        val o = s.originSelected?.iataCode.orEmpty()
        val d = s.destinationSelected?.iataCode.orEmpty()
        if (o.isBlank() || d.isBlank() || s.departDate.isBlank()) {
            _ui.value = s.copy(error = "Pick origin, destination, and departure date.")
            return
        }

        flightSearchJob?.cancel()
        flightSearchJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(isLoading = true, error = null)
            val res = runCatching {
                repo.searchFlights(
                    originIata = o,
                    destIata = d,
                    departDate = s.departDate,
                    returnDate = s.returnDate.takeIf { it.isNotBlank() },
                    adults = s.adults
                )
            }.onFailure { e -> Log.e("SEARCH", "Flight search failed", e) }

            _ui.value = _ui.value.copy(
                isLoading = false,
                flightResults = res.getOrDefault(emptyList()),
                error = res.exceptionOrNull()?.message
            )
        }
    }

    fun searchHotels() {
        val s = _ui.value
        if (s.hotelPlaceId.isBlank() || s.checkIn.isBlank() || s.checkOut.isBlank()) {
            _ui.value = s.copy(error = "Enter place, check-in and check-out.")
            return
        }

        hotelSearchJob?.cancel()
        hotelSearchJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(isLoading = true, error = null)
            val res = runCatching {
                repo.searchHotels(
                    cityOrPlaceId = s.hotelPlaceId,
                    checkIn = s.checkIn,
                    checkOut = s.checkOut,
                   // adults = s.adults,
                    rooms = s.rooms
                )
            }.onFailure { e -> Log.e("SEARCH", "Hotel search failed", e) }

            _ui.value = _ui.value.copy(
                isLoading = false,
                hotelResults = res.getOrDefault(emptyList()),
                error = res.exceptionOrNull()?.message
            )
        }
    }
    fun pickHotel(h: HotelSuggestion) {
        val pid = h.placeId?.takeIf { it.isNotBlank() }
        _ui.value = _ui.value.copy(
            hotelSelected = h,
            hotelPlaceQuery = h.name,
            hotelPlaceId = pid ?: h.hotelId,
            hotelSuggestions = emptyList()
        )
        hotelQueryFlow.value = ""
    }

    private fun testWorkerAuthOnce() {
        viewModelScope.launch {
            val uid = runCatching { repo.whoAmI() }
                .onFailure { e -> Log.e("AUTH_TEST", "Worker whoami failed", e) }
                .getOrDefault("")

            if (uid.isNotBlank()) {
                Log.d("AUTH_TEST", "Firebase auth OK. Worker sees uid=$uid")
            } else {
                Log.w("AUTH_TEST", " Firebase auth NOT confirmed (empty uid)")
            }
        }
    }

    fun addTravelerRow() {
        val nextNum = (_ui.value.travelers.size + 1).coerceAtMost(12)
        val id = UUID.randomUUID().toString()
        _ui.value = _ui.value.copy(
            travelers = _ui.value.travelers + TravelerOriginRow(id = id, name = "Traveler $nextNum")
        )
    }

    fun removeTravelerRow(travelerId: String) {
        val current = _ui.value.travelers
        if (current.size <= 1) return // keep at least one
        _ui.value = _ui.value.copy(
            travelers = current.filterNot { it.id == travelerId }
        )
    }

    fun setTravelerName(travelerId: String, name: String) {
        _ui.value = _ui.value.copy(
            travelers = _ui.value.travelers.map { t ->
                if (t.id == travelerId) t.copy(name = name) else t
            }
        )
    }

    fun setTravelerOriginQuery(travelerId: String, v: String) {
        _ui.value = _ui.value.copy(
            travelers = _ui.value.travelers.map { t ->
                if (t.id == travelerId) t.copy(query = v, selected = null) else t
            }
        )
        travelerQueryFlow.value = TravelerQueryEvent(travelerId = travelerId, query = v)
    }

    fun pickTravelerOrigin(travelerId: String, a: AirportSuggestion) {
        _ui.value = _ui.value.copy(
            travelers = _ui.value.travelers.map { t ->
                if (t.id == travelerId) {
                    t.copy(selected = a, query = a.label, suggestions = emptyList())
                } else t
            }
        )
        travelerQueryFlow.value = TravelerQueryEvent(travelerId = travelerId, query = "")
    }

    fun searchMultiOriginFlights() {
        val s = _ui.value
        val destIata = s.destinationSelected?.iataCode?.trim().orEmpty()

        val travelerOrigins = s.travelers.mapNotNull { t ->
            val o = t.selected?.iataCode?.trim()
            if (!o.isNullOrBlank()) t.id to o else null
        }

        if (travelerOrigins.isEmpty() || destIata.isBlank() || s.departDate.isBlank()) {
            _ui.value = s.copy(error = "Pick at least one traveler origin, destination, and departure date.")
            return
        }

        fun parsePrice(p: String): Double {
            val cleaned = p.replace(Regex("[^0-9.]"), "")
            return cleaned.toDoubleOrNull() ?: Double.POSITIVE_INFINITY
        }

        flightSearchJob?.cancel()
        flightSearchJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(
                isLoading = true,
                error = null,
                coordinatedOptions = emptyList(),
                flightResultsByTraveler = emptyMap()
            )

            val byTraveler = mutableMapOf<String, List<FlightOfferUi>>()

            travelerOrigins.forEach { (travelerId, originIata) ->

                val results = runCatching {
                    repo.searchFlights(
                        originIata = originIata,
                        destIata = destIata,
                        departDate = s.departDate,
                        returnDate = s.returnDate.takeIf { it.isNotBlank() },
                        adults = s.adults
                    )
                }.getOrElse {
                    Log.e("SEARCH", "searchFlights failed for traveler=$travelerId", it)
                    emptyList()
                }

                Log.d(
                    "FLIGHTS",
                    "traveler=$travelerId origin=$originIata dest=$destIata " +
                            "raw=${results.size} uniquePrices=${results.map { it.price }.toSet().size}"
                )
                val deduped = results.distinctBy { offer ->
                    offer.id.takeIf { it.isNotBlank() } ?: "${offer.summary}::${offer.price}"
                }

                val sorted = deduped.sortedBy { parsePrice(it.price) }

                val uniqueByNumericPrice = sorted.distinctBy { parsePrice(it.price) }

                byTraveler[travelerId] = uniqueByNumericPrice.take(10)
            }

            val coordinated = buildCoordinatedOptions(
                state = _ui.value,
                byTraveler = byTraveler
            )

            _ui.value = _ui.value.copy(
                isLoading = false,
                flightResultsByTraveler = byTraveler,
                coordinatedOptions = coordinated
            )
        }
    }


    private fun buildCoordinatedOptions(
        state: TravelSearchUiState,
        byTraveler: Map<String, List<FlightOfferUi>>
    ): List<CoordinatedFlightOption> {

        val activeRows = state.travelers.filter { byTraveler[it.id]?.isNotEmpty() == true }
        if (activeRows.isEmpty()) return emptyList()

        fun parsePrice(p: String): Double {
            val cleaned = p.replace(Regex("[^0-9.]"), "")
            return cleaned.toDoubleOrNull() ?: Double.POSITIVE_INFINITY
        }

        fun normSummary(s: String): String {
            return s
                .lowercase()
                .replace("→", "->")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun offerKey(o: FlightOfferUi): String {
            val idPart = o.id.trim().takeIf { it.isNotBlank() }
            // normalize to collapse cosmetic formatting differences
            val priceNorm = "%.2f".format(parsePrice(o.price))
            return idPart ?: "${normSummary(o.summary)}::$priceNorm"
        }

        fun priceToScoreMinutes(price: String): Long {
            val v = parsePrice(price)
            if (!v.isFinite()) return Long.MAX_VALUE
            return (v * 100).toLong() // $800.00 -> 80000
        }

        if (activeRows.size == 1) {
            val row = activeRows.first()
            val offers = byTraveler[row.id].orEmpty()

            Log.d(
                "FLIGHTS",
                "solo offers=${offers.size} uniqueKeys=${offers.map { offerKey(it) }.toSet().size} " +
                        "uniqueNumericPrices=${offers.map { parsePrice(it.price) }.toSet().size}"
            )

            return offers
                .distinctBy { offerKey(it) }
                .sortedBy { parsePrice(it.price) }
                .mapIndexed { idx, offer ->
                    CoordinatedFlightOption(
                        id = "solo_$idx",
                        scoreMinutes = priceToScoreMinutes(offer.price),
                        travelers = listOf(
                            TravelerFlightChoice(
                                travelerId = row.id,
                                travelerName = row.name,
                                originIata = row.selected?.iataCode.orEmpty(),
                                offer = offer
                            )
                        )
                    )
                }
                .take(10)
        }

        val topN = 3

        val rows: List<Pair<TravelerOriginRow, List<FlightOfferUi>>> = activeRows.map { row ->
            row to byTraveler[row.id].orEmpty()
                .distinctBy { offerKey(it) }
                .sortedBy { parsePrice(it.price) }
                .take(topN)
        }

        val combos = mutableListOf<List<Pair<TravelerOriginRow, FlightOfferUi>>>()

        fun dfs(i: Int, acc: MutableList<Pair<TravelerOriginRow, FlightOfferUi>>) {
            if (i == rows.size) {
                combos += acc.toList()
                return
            }
            val (row, offers) = rows[i]
            for (o in offers) {
                acc.add(row to o)
                dfs(i + 1, acc)
                acc.removeAt(acc.size - 1)
            }
        }
        dfs(0, mutableListOf())

        return combos
            .mapIndexed { idx, combo ->
                val totalScoreMinutes: Long = combo.sumOf { (_, offer) ->
                    priceToScoreMinutes(offer.price)
                }
                CoordinatedFlightOption(
                    id = "co_$idx",
                    scoreMinutes = totalScoreMinutes,
                    travelers = combo.map { (row, offer) ->
                        TravelerFlightChoice(
                            travelerId = row.id,
                            travelerName = row.name,
                            originIata = row.selected?.iataCode.orEmpty(),
                            offer = offer
                        )
                    }
                )
            }
            .distinctBy { opt ->
                opt.travelers
                    .sortedBy { it.travelerId }
                    .joinToString("|") { choice ->
                        "${choice.travelerId}->${offerKey(choice.offer)}"
                    }
            }
            .sortedBy { it.scoreMinutes }
            .take(10)
    }
    fun resetMultiOriginFlights() {
        _ui.value = _ui.value.copy(
            travelers = listOf(TravelerOriginRow(id = "t1", name = "Traveler 1")),
            flightResultsByTraveler = emptyMap(),
            coordinatedOptions = emptyList()
        )
    }

    fun loadTripMembers(tripId: String) {
        viewModelScope.launch {
            try {
                val members = repo.getTripMembers(tripId)
                _ui.value = _ui.value.copy(tripMembers = members, error = null)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(
                    tripMembers = emptyList(),
                    error = "Can't load trip members: ${e.message}"
                )
            }
        }
    }

    fun toggleTraveler(uid: String) {
        val cur = _ui.value.selectedTravelerUids
        val next = if (uid in cur) cur - uid else cur + uid

        val origins = _ui.value.originByTraveler.toMutableMap()
        if (uid !in next) origins.remove(uid)

        _ui.value = _ui.value.copy(
            selectedTravelerUids = next,
            originByTraveler = origins
        )
    }

    fun setTravelerOrigin(uid: String, origin: AirportSuggestion) {
        val next = _ui.value.originByTraveler.toMutableMap()
        next[uid] = origin
        _ui.value = _ui.value.copy(originByTraveler = next)
    }

    fun addTravelerFromMember(uid: String, name: String) {
        if (_ui.value.travelers.any { it.id == uid }) return

        val next = _ui.value.travelers + TravelerOriginRow(
            id = uid,
            name = name,
            query = "",
            suggestions = emptyList(),
        )
        _ui.value = _ui.value.copy(travelers = next)
    }


}
