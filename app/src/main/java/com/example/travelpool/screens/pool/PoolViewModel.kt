import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelpool.data.MemberBalance
import com.example.travelpool.data.Settlement
import com.example.travelpool.data.TripMember
import com.example.travelpool.screens.notification.NotificationRepository
import com.example.travelpool.screens.pool.PoolUiState
import com.example.travelpool.screens.home.HomeRepository
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

class PoolViewModel(
    private val repo: PoolRepository = PoolRepository(),
    private val tripRepo: HomeRepository = HomeRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(PoolUiState())
    val uiState: StateFlow<PoolUiState> = _uiState.asStateFlow()
    private val membersFlow = MutableStateFlow<List<TripMember>>(emptyList())

    private val notificationRepo = NotificationRepository()

    fun observe(tripId: String) {
        loadMembers(tripId)

        viewModelScope.launch {
            repo.contributionsFlow(tripId)
                .combine(repo.expensesFlow(tripId)) { contributions, expenses ->
                    contributions to expenses
                }
                .combine(repo.settlementsFlow(tripId)) { (contributions, expenses), settlements ->
                    Triple(contributions, expenses, settlements)
                }
                .combine(membersFlow) { (contributions, expenses, settlements), members ->
                    buildState(contributions, expenses, settlements, members)
                }
                .onStart { _uiState.value = _uiState.value.copy(isLoading = true, error = null) }
                .catch { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
                .collect { state -> _uiState.value = state }
        }
    }

    private fun loadMembers(tripId: String) {
        viewModelScope.launch {
            val res = tripRepo.getTripMembers(tripId)
            if (res.isSuccess) {
                membersFlow.value = res.getOrNull().orEmpty()
            } else {
                _uiState.value = _uiState.value.copy(
                    error = res.exceptionOrNull()?.message ?: "Failed to load members"
                )
            }
        }
    }

    private fun buildState(
        contributions: List<com.example.travelpool.data.PoolContribution>,
        expenses: List<com.example.travelpool.data.PoolExpense>,
        settlements: List<Settlement>,
        members: List<TripMember>
    ): PoolUiState {

        val totalContributed = contributions.sumOf { it.amountCents }
        val totalSpent = expenses.sumOf { it.amountCents }

        val memberUids = members.map { it.uid }.distinct()
        val nameByUid = members.associate { it.uid to it.name }

        val paidByUid = mutableMapOf<String, Long>().withDefault { 0L }
        fun addPaid(uid: String, cents: Long) {
            paidByUid[uid] = paidByUid.getValue(uid) + cents
        }

        val owedByUid = mutableMapOf<String, Long>().withDefault { 0L }
        fun addOwed(uid: String, cents: Long) {
            owedByUid[uid] = owedByUid.getValue(uid) + cents
        }

        val contributedByUid = contributions
            .groupBy { it.uid }
            .mapValues { (_, items) -> items.sumOf { it.amountCents } }

        expenses.forEach { e ->
            addPaid(e.paidByUid, e.amountCents)

            val participants = e.splitBetweenUids.distinct().filter { it.isNotBlank() }
            if (participants.isEmpty()) return@forEach

            when (e.splitType.lowercase()) {
                "exact" -> {
                    participants.forEach { uid ->
                        val c = e.splitExactCents[uid] ?: 0L
                        if (c > 0) addOwed(uid, c)
                    }
                }

                "percent" -> {
                    val bpsMap = e.splitPercentBps
                    if (bpsMap.isEmpty()) return@forEach

                    var allocated = 0L
                    val temp = mutableListOf<Pair<String, Long>>()

                    participants.forEach { uid ->
                        val bps = bpsMap[uid] ?: 0
                        val cents = (e.amountCents * bps) / 10000L
                        temp.add(uid to cents)
                        allocated += cents
                    }

                    var rem = e.amountCents - allocated
                    var idx = 0
                    while (rem > 0 && temp.isNotEmpty()) {
                        val (uid, c) = temp[idx]
                        temp[idx] = uid to (c + 1)
                        rem--
                        idx = (idx + 1) % temp.size
                    }

                    temp.forEach { (uid, cents) -> addOwed(uid, cents) }
                }

                else -> {
                    val n = participants.size
                    val base = e.amountCents / n
                    val rem = (e.amountCents % n).toInt()

                    participants.forEachIndexed { idx, uid ->
                        val extra = if (idx < rem) 1L else 0L
                        addOwed(uid, base + extra)
                    }
                }
            }
        }

        val netByUid = mutableMapOf<String, Long>().withDefault { 0L }
        memberUids.forEach { uid ->
            val contributed = contributedByUid[uid] ?: 0L
            val owes = owedByUid[uid] ?: 0L
            netByUid[uid] = contributed - owes
        }

        settlements.forEach { s ->
            if (!memberUids.contains(s.fromUid) || !memberUids.contains(s.toUid)) return@forEach
            netByUid[s.fromUid] = netByUid.getValue(s.fromUid) + s.amountCents
            netByUid[s.toUid] = netByUid.getValue(s.toUid) - s.amountCents
        }

        val balances = memberUids.map { uid ->
            val contributed = contributedByUid[uid] ?: 0L
            val owes = owedByUid[uid] ?: 0L
            val net = netByUid[uid] ?: 0L
            MemberBalance(
                uid = uid,
                name = nameByUid[uid] ?: uid,
                contributedCents = contributed,
                owesCents = owes,
                netCents = net
            )
        }.sortedBy { it.netCents }

        val suggested = computeSettlements(balances)

        return _uiState.value.copy(
            contributions = contributions,
            expenses = expenses,
            members = members,

            balances = balances,
            suggestedSettlements = suggested,
            settlementHistory = settlements,

            totalContributedCents = totalContributed,
            totalSpentCents = totalSpent,
            balanceCents = totalContributed - totalSpent,

            isLoading = false,
            error = null
        )
    }


    private fun computeSettlements(balances: List<MemberBalance>): List<Settlement> {
        val debtors = balances.filter { it.netCents < 0 }
            .map { it.uid to abs(it.netCents) }.toMutableList()

        val creditors = balances.filter { it.netCents > 0 }
            .map { it.uid to it.netCents }.toMutableList()

        val nameByUid = balances.associate { it.uid to it.name }

        val result = mutableListOf<Settlement>()
        var i = 0
        var j = 0

        while (i < debtors.size && j < creditors.size) {
            val (duid, dAmt) = debtors[i]
            val (cuid, cAmt) = creditors[j]

            val pay = min(dAmt, cAmt)

            result.add(
                Settlement(
                    fromUid = duid,
                    fromName = nameByUid[duid] ?: duid,
                    toUid = cuid,
                    toName = nameByUid[cuid] ?: cuid,
                    amountCents = pay
                )
            )

            val newDAmt = dAmt - pay
            val newCAmt = cAmt - pay

            debtors[i] = duid to newDAmt
            creditors[j] = cuid to newCAmt

            if (newDAmt == 0L) i++
            if (newCAmt == 0L) j++
        }

        return result
    }

    fun addContribution(tripId: String, amountCents: Long, note: String) {
        viewModelScope.launch {
            val res = repo.addContribution(tripId, amountCents, note)
            if (res.isFailure) {
                _uiState.value = _uiState.value.copy(error = res.exceptionOrNull()?.message)
            }
            val senderUid = com.google.firebase.Firebase.auth.currentUser?.uid ?: return@launch
            val senderName = membersFlow.value.firstOrNull { it.uid == senderUid }?.name ?: "Someone"

            val recipients = membersFlow.value.map { it.uid }.distinct().filter { it != senderUid }
            if (recipients.isNotEmpty()) {
                notificationRepo.pushToUsers(
                    recipientUids = recipients,
                    tripId = tripId,
                    type = "contribution",
                    title = "New contribution",
                    body = "$senderName added a contribution.",
                    deepLink = "pool/$tripId"
                )
            }
        }
    }

    fun addSettlement(
        tripId: String,
        toUid: String,
        toName: String,
        amountCents: Long,
        note: String
    ) {
        viewModelScope.launch {
            val res = repo.addSettlement(tripId, toUid, toName, amountCents, note)
            if (res.isFailure) {
                _uiState.value = _uiState.value.copy(error = res.exceptionOrNull()?.message)
                return@launch
            }
            val senderUid = com.google.firebase.Firebase.auth.currentUser?.uid ?: return@launch
            val senderName = membersFlow.value.firstOrNull { it.uid == senderUid }?.name ?: "Someone"

            notificationRepo.pushToUsers(
                recipientUids = listOf(toUid),
                tripId = tripId,
                type = "settlement",
                title = "Settlement marked paid",
                body = "$senderName marked a payment to $toName.",
                deepLink = "settleup/$tripId"
            )
        }
    }

    fun addExpense(
        tripId: String,
        title: String,
        amountCents: Long,
        paidByUid: String,
        paidByName: String,
        splitBetweenUids: List<String>,
        splitType: String,
        splitExactCents: Map<String, Long>,
        splitPercentBps: Map<String, Int>
    ) {
        viewModelScope.launch {
            val res = repo.addExpense(
                tripId, title, amountCents, paidByUid, paidByName, splitBetweenUids,
                splitType = splitType,
                splitExactCents = splitExactCents,
                splitPercentBps = splitPercentBps
            )
            if (res.isFailure) {
                _uiState.value = _uiState.value.copy(error = res.exceptionOrNull()?.message)
            }
            val senderUid = com.google.firebase.Firebase.auth.currentUser?.uid ?: return@launch
            val recipients = membersFlow.value.map { it.uid }.distinct().filter { it != senderUid }
            if (recipients.isNotEmpty()) {
                notificationRepo.pushToUsers(
                    recipientUids = recipients,
                    tripId = tripId,
                    type = "expense",
                    title = "New expense added",
                    body = "$paidByName added \"$title\".",
                    deepLink = "pool/$tripId"
                )
            }
        }
    }

    fun deleteContribution(tripId: String, contributionId: String) {
        viewModelScope.launch {
            val res = repo.deleteContribution(tripId, contributionId)
            if (res.isFailure) _uiState.value = _uiState.value.copy(error = res.exceptionOrNull()?.message)
        }
    }

    fun updateContribution(tripId: String, contributionId: String, amountCents: Long, note: String) {
        viewModelScope.launch {
            val res = repo.updateContribution(tripId, contributionId, amountCents, note)
            if (res.isFailure) _uiState.value = _uiState.value.copy(error = res.exceptionOrNull()?.message)
        }
    }

    fun deleteExpense(tripId: String, expenseId: String) {
        viewModelScope.launch {
            val res = repo.deleteExpense(tripId, expenseId)
            if (res.isFailure) _uiState.value = _uiState.value.copy(error = res.exceptionOrNull()?.message)
        }
    }

    fun updateExpense(tripId: String, expenseId: String, title: String, amountCents: Long) {
        viewModelScope.launch {
            val res = repo.updateExpense(tripId, expenseId, title, amountCents)
            if (res.isFailure) _uiState.value = _uiState.value.copy(error = res.exceptionOrNull()?.message)
        }
    }
}