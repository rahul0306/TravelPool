package com.example.travelpool.screens.pool

import PoolViewModel
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.travelpool.R
import com.example.travelpool.utils.MoneyUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoolScreen(
    modifier: Modifier = Modifier,
    tripId: String,
    onBack: () -> Unit,
    onOpenSettleUp: (String) -> Unit,
    viewModel: PoolViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(tripId) {
        viewModel.observe(tripId)
    }

    var contributionAmount by remember { mutableStateOf("") }
    var contributionNote by remember { mutableStateOf("") }

    var expenseTitle by remember { mutableStateOf("") }
    var expenseAmount by remember { mutableStateOf("") }

    var splitType by remember { mutableStateOf("equal") }
    var exactInputs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var percentInputs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val currentUid = Firebase.auth.currentUser?.uid ?: ""
    val isOrganizer = state.members.firstOrNull { it.uid == currentUid }?.role == "organizer"

    var editContributionId by remember { mutableStateOf<String?>(null) }
    var editContributionAmount by remember { mutableStateOf("") }
    var editContributionNote by remember { mutableStateOf("") }

    var editExpenseId by remember { mutableStateOf<String?>(null) }
    var editExpenseTitle by remember { mutableStateOf("") }
    var editExpenseAmount by remember { mutableStateOf("") }

    var payerExpanded by remember { mutableStateOf(false) }
    var selectedPayerUid by remember { mutableStateOf(currentUid) }
    val members = state.members
    val allMemberUids = members.map { it.uid }
    var selectedSplitUids by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(allMemberUids) {
        if (selectedSplitUids.isEmpty() && allMemberUids.isNotEmpty()) {
            selectedSplitUids = allMemberUids.toSet()
        }
        if (allMemberUids.isNotEmpty() && !allMemberUids.contains(selectedPayerUid)) {
            selectedPayerUid = allMemberUids.first()
        }
    }

    LaunchedEffect(selectedSplitUids, splitType) {
        val uids = selectedSplitUids.toList()
        if (splitType == "exact") {
            exactInputs = uids.associateWith { exactInputs[it] ?: "" }
        }
        if (splitType == "percent") {
            val default = if (uids.isNotEmpty()) (100.0 / uids.size) else 0.0
            percentInputs = uids.associateWith { percentInputs[it] ?: default.toString() }
        }
    }

    val totalNet = state.balances.sumOf { it.netCents }
    val memberCount = state.members.size.coerceAtLeast(1)
    val perPersonTopUp = if (totalNet < 0) (-totalNet) / memberCount else 0L

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Trip Pool") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val summary = buildString {
                            appendLine("Travel Pool Summary")
                            appendLine()
                            appendLine("Total contributed: ₹${MoneyUtils.formatCents(state.totalContributedCents)}")
                            appendLine("Total spent: ₹${MoneyUtils.formatCents(state.totalSpentCents)}")
                            appendLine("Balance: ₹${MoneyUtils.formatCents(state.balanceCents)}")
                            appendLine()

                            when {
                                totalNet < 0 -> appendLine(
                                    "Pool is short by ₹${
                                        MoneyUtils.formatCents(
                                            -totalNet
                                        )
                                    }"
                                )

                                totalNet > 0 -> appendLine(
                                    "Pool has extra ₹${
                                        MoneyUtils.formatCents(
                                            totalNet
                                        )
                                    }"
                                )

                                else -> appendLine("Pool is settled")
                            }

                            appendLine()
                            appendLine("Balances:")
                            state.balances.forEach { b ->
                                appendLine(
                                    "- ${b.name}: contributed ₹${MoneyUtils.formatCents(b.contributedCents)}, " +
                                            "owes ₹${MoneyUtils.formatCents(b.owesCents)}, " +
                                            "net ₹${MoneyUtils.formatCents(b.netCents)}"
                                )
                            }

                            appendLine()
                            appendLine("Suggested settlements:")
                            if (state.suggestedSettlements.isEmpty()) {
                                appendLine("- None")
                            } else {
                                state.suggestedSettlements.forEach { s ->
                                    appendLine(
                                        "- ${s.fromName} pays ${s.toName} ₹${
                                            MoneyUtils.formatCents(
                                                s.amountCents
                                            )
                                        }"
                                    )
                                }
                            }
                        }

                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, summary)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Trip Pool"))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }

            state.error != null -> {
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                ) {
                    ScenicHeaderBand()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp)
                            .padding(top = 12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CardSection(title = "Summary",
                            trailing = {
                                Button(
                                    onClick = { onOpenSettleUp(tripId) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF1B74EA)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Open settle up")
                                }
                            }
                            ) {
                            KeyValue(
                                "Total contributed",
                                "₹ ${MoneyUtils.formatCents(state.totalContributedCents)}"
                            )
                            KeyValue(
                                "Total spent",
                                "₹ ${MoneyUtils.formatCents(state.totalSpentCents)}"
                            )
                            KeyValue("Balance", "₹ ${MoneyUtils.formatCents(state.balanceCents)}")

                            Spacer(Modifier.height(8.dp))
                            when {
                                totalNet < 0 -> {
                                    Text(
                                        "Pool is short by ₹ ${MoneyUtils.formatCents(-totalNet)}",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Suggested equal top-up: ₹ ${
                                            MoneyUtils.formatCents(
                                                perPersonTopUp
                                            )
                                        } per person",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(onClick = {
                                        if (perPersonTopUp > 0) {
                                            contributionAmount =
                                                MoneyUtils.formatCents(perPersonTopUp)
                                            contributionNote = "Top-up (equal split)"
                                        }
                                    }) { Text("Prefill my top-up") }
                                }

                                totalNet > 0 -> {
                                    Text(
                                        "Pool has extra ₹ ${MoneyUtils.formatCents(totalNet)}",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                else -> Text("Pool is settled 🎉")
                            }
                        }

                        CardSection(title = "Add contribution") {
                            OutlinedTextField(
                                value = contributionAmount,
                                onValueChange = { contributionAmount = it },
                                label = { Text("Amount (e.g. 500 or 500.00)") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = contributionNote,
                                onValueChange = { contributionNote = it },
                                label = { Text("Note (optional)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    val cents = MoneyUtils.parseToCents(contributionAmount)
                                    if (cents != null && cents > 0) {
                                        viewModel.addContribution(
                                            tripId,
                                            cents,
                                            contributionNote.trim()
                                        )
                                        contributionAmount = ""
                                        contributionNote = ""
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF7A2F)
                                )
                            ) {
                                Text("Add contribution")
                            }
                        }

                        CardSection(title = "Add expense") {

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = expenseTitle,
                                    onValueChange = { expenseTitle = it },
                                    label = { Text("What was this for?") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = expenseAmount,
                                    onValueChange = { expenseAmount = it },
                                    label = {
                                        Text(
                                            "Amount (e.g. 500)",
                                            maxLines = 1,
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Text("Paid by", style = MaterialTheme.typography.bodyMedium)

                            val selectedPayerName =
                                members.firstOrNull { it.uid == selectedPayerUid }?.name
                                    ?: "Traveler"

                            ExposedDropdownMenuBox(
                                expanded = payerExpanded,
                                onExpandedChange = { payerExpanded = !payerExpanded },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = selectedPayerName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Select payer") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = payerExpanded)
                                    },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )

                                ExposedDropdownMenu(
                                    expanded = payerExpanded,
                                    onDismissRequest = { payerExpanded = false }
                                ) {
                                    members.forEach { m ->
                                        DropdownMenuItem(
                                            text = { Text(m.name) },
                                            onClick = {
                                                selectedPayerUid = m.uid
                                                payerExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Text("Split type", style = MaterialTheme.typography.bodyMedium)


                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    SplitTypeChips(
                                        selected = splitType,
                                        onSelect = { splitType = it }
                                    )
                                }
                                Button(
                                    onClick = {
                                        val cents = MoneyUtils.parseToCents(expenseAmount)
                                        val splitList = selectedSplitUids.toList()
                                        if (expenseTitle.isBlank() || cents == null || cents <= 0 || splitList.isEmpty()) return@Button

                                        val payerName =
                                            members.firstOrNull { it.uid == selectedPayerUid }?.name
                                                ?: "Traveler"

                                        val exactMap = mutableMapOf<String, Long>()
                                        val percentMap = mutableMapOf<String, Int>()

                                        if (splitType == "exact") {
                                            var sum = 0L
                                            splitList.forEach { uid ->
                                                val v = exactInputs[uid] ?: ""
                                                val c = MoneyUtils.parseToCents(v) ?: 0L
                                                exactMap[uid] = c
                                                sum += c
                                            }
                                            if (sum != cents) return@Button
                                        }

                                        if (splitType == "percent") {
                                            var sumBps = 0
                                            splitList.forEach { uid ->
                                                val v = (percentInputs[uid] ?: "").trim()
                                                val d = v.toDoubleOrNull() ?: 0.0
                                                val bps = (d * 100).toInt()
                                                percentMap[uid] = bps
                                                sumBps += bps
                                            }
                                            if (sumBps != 10000) return@Button
                                        }

                                        viewModel.addExpense(
                                            tripId = tripId,
                                            title = expenseTitle.trim(),
                                            amountCents = cents,
                                            paidByUid = selectedPayerUid,
                                            paidByName = payerName,
                                            splitBetweenUids = splitList,
                                            splitType = splitType,
                                            splitExactCents = exactMap,
                                            splitPercentBps = percentMap
                                        )

                                        expenseTitle = ""
                                        expenseAmount = ""
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF1B74EA)
                                    )
                                ) {
                                    Text("Add expense")
                                }
                            }

                            Text("Split between", style = MaterialTheme.typography.bodyMedium)


                            members.forEach { m ->
                                val checked = selectedSplitUids.contains(m.uid)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth(),

                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { isChecked ->
                                            selectedSplitUids = if (isChecked) {
                                                selectedSplitUids + m.uid
                                            } else {
                                                selectedSplitUids - m.uid
                                            }
                                        }
                                    )
                                    Text(m.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }

                            if (splitType == "exact") {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Enter exact amounts per person",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                selectedSplitUids.forEach { uid ->
                                    val name = members.firstOrNull { it.uid == uid }?.name ?: uid
                                    OutlinedTextField(
                                        value = exactInputs[uid] ?: "",
                                        onValueChange = { v ->
                                            exactInputs = exactInputs + (uid to v)
                                        },
                                        label = { Text("$name amount") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            if (splitType == "percent") {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Enter percent per person (total must be 100)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                selectedSplitUids.forEach { uid ->
                                    val name = members.firstOrNull { it.uid == uid }?.name ?: uid
                                    OutlinedTextField(
                                        value = percentInputs[uid] ?: "",
                                        onValueChange = { v ->
                                            percentInputs = percentInputs + (uid to v)
                                        },
                                        label = { Text("$name %") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }



                        }

                        CardSection(title = "Recent contributions") {
                            val recent = state.contributions.take(3)

                            if (recent.isEmpty()) {
                                Text("No contributions yet.")
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 260.dp),
                                    userScrollEnabled = false,
                                    verticalArrangement = Arrangement.spacedBy(0.dp)
                                ) {
                                    items(recent, key = { it.id }) { c ->
                                        val canManage = isOrganizer || (c.uid == currentUid)

                                        ListRow(
                                            title = c.name,
                                            subtitle = c.note.takeIf { it.isNotBlank() },
                                            amount = "₹ ${MoneyUtils.formatCents(c.amountCents)}",
                                            trailing = {
                                                if (canManage) {
                                                    Row {
                                                        TextButton(onClick = {
                                                            editContributionId = c.id
                                                            editContributionAmount =
                                                                MoneyUtils.formatCents(c.amountCents)
                                                            editContributionNote = c.note
                                                        }) { Text("Edit") }

                                                        TextButton(onClick = {
                                                            viewModel.deleteContribution(
                                                                tripId,
                                                                c.id
                                                            )
                                                        }) { Text("Delete") }
                                                    }
                                                }
                                            }
                                        )
                                        DividerSoft()
                                    }
                                }
                            }
                        }

                        CardSection(title = "Recent expenses") {
                            val recent = state.expenses.take(3)

                            if (recent.isEmpty()) {
                                Text("No expenses yet.")
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 260.dp),
                                    userScrollEnabled = false,
                                    verticalArrangement = Arrangement.spacedBy(0.dp)
                                ) {
                                    items(recent, key = { it.id }) { e ->
                                        val canManageExpense =
                                            isOrganizer || (e.paidByUid == currentUid)

                                        ListRow(
                                            title = e.title,
                                            subtitle = "Paid by ${e.paidByName}",
                                            amount = "₹ ${MoneyUtils.formatCents(e.amountCents)}",
                                            trailing = {
                                                if (canManageExpense) {
                                                    Row {
                                                        TextButton(onClick = {
                                                            editExpenseId = e.id
                                                            editExpenseTitle = e.title
                                                            editExpenseAmount =
                                                                MoneyUtils.formatCents(e.amountCents)
                                                        }) { Text("Edit") }

                                                        TextButton(onClick = {
                                                            viewModel.deleteExpense(tripId, e.id)
                                                        }) { Text("Delete") }
                                                    }
                                                }
                                            }
                                        )
                                        DividerSoft()
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))
                    }
                }

                if (editContributionId != null) {
                    AlertDialog(
                        onDismissRequest = { editContributionId = null },
                        title = { Text("Edit contribution") },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = editContributionAmount,
                                    onValueChange = { editContributionAmount = it },
                                    label = { Text("Amount") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editContributionNote,
                                    onValueChange = { editContributionNote = it },
                                    label = { Text("Note") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val id = editContributionId
                                val cents = MoneyUtils.parseToCents(editContributionAmount)
                                if (id != null && cents != null && cents > 0) {
                                    viewModel.updateContribution(
                                        tripId,
                                        id,
                                        cents,
                                        editContributionNote.trim()
                                    )
                                    editContributionId = null
                                }
                            }) { Text("Save") }
                        },
                        dismissButton = {
                            TextButton(onClick = { editContributionId = null }) { Text("Cancel") }
                        }
                    )
                }

                if (editExpenseId != null) {
                    AlertDialog(
                        onDismissRequest = { editExpenseId = null },
                        title = { Text("Edit expense") },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = editExpenseTitle,
                                    onValueChange = { editExpenseTitle = it },
                                    label = { Text("Title") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editExpenseAmount,
                                    onValueChange = { editExpenseAmount = it },
                                    label = { Text("Amount") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val id = editExpenseId
                                val cents = MoneyUtils.parseToCents(editExpenseAmount)
                                if (id != null && cents != null && cents > 0 && editExpenseTitle.isNotBlank()) {
                                    viewModel.updateExpense(
                                        tripId,
                                        id,
                                        editExpenseTitle.trim(),
                                        cents
                                    )
                                    editExpenseId = null
                                }
                            }) { Text("Save") }
                        },
                        dismissButton = {
                            TextButton(onClick = { editExpenseId = null }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }
}


@Composable
private fun ScenicHeaderBand() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.travelpool_header),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillBounds
        )
    }
}

@Composable
private fun CardSection(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (trailing != null) trailing()
            }

            content()
        }
    }
}


@Composable
private fun KeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

@Composable
private fun DividerSoft() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ListRow(
    title: String,
    subtitle: String?,
    amount: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(amount, style = MaterialTheme.typography.bodyLarge)
            if (trailing != null) trailing()
        }
    }
}

@Composable
private fun SplitTypeChips(
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactChip(
            text = "Equal",
            selected = selected == "equal",
            onClick = { onSelect("equal") }
        )
        CompactChip(
            text = "Exact",
            selected = selected == "exact",
            onClick = { onSelect("exact") }
        )
        CompactChip(
            text = "%",
            selected = selected == "percent",
            onClick = { onSelect("percent") }
        )
    }
}

@Composable
private fun CompactChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, maxLines = 1) },
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else null,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        )
    )
}
