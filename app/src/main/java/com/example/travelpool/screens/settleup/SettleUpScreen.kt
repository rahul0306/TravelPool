package com.example.travelpool.screens.settleup

import PoolViewModel
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.travelpool.utils.MoneyUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleUpScreen(
    tripId: String,
    onBack: () -> Unit,
    viewModel: PoolViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentUid = Firebase.auth.currentUser?.uid.orEmpty()

    LaunchedEffect(tripId) { viewModel.observe(tripId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settle up") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val csv = buildCsvExport(state)
                            shareCsvFile(context, csv, fileName = "travelpool_settleup_$tripId.csv")
                        }
                    ) { Icon(Icons.Default.FileDownload, contentDescription = "Export CSV") }
                }
            )
        }
    ) { inner ->
        if (state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.error != null) {
                Text(
                    text = state.error ?: "",
                    color = MaterialTheme.colorScheme.error
                )
            }

            SectionCard(title = "Suggested payments") {
                if (state.suggestedSettlements.isEmpty()) {
                    Text("No suggested payments right now.")
                } else {
                    state.suggestedSettlements.forEach { s ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${s.fromName} pays ${s.toName} ₹ ${MoneyUtils.formatCents(s.amountCents)}",
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                enabled = currentUid == s.fromUid,
                                onClick = {
                                    viewModel.addSettlement(
                                        tripId = tripId,
                                        toUid = s.toUid,
                                        toName = s.toName,
                                        amountCents = s.amountCents,
                                        note = "Settled"
                                    )
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B74EA))
                            ) {
                                Text("Mark paid")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            SectionCard(title = "Settlement history") {
                if (state.settlementHistory.isEmpty()) {
                    Text("No settlements recorded yet.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.settlementHistory, key = { it.id }) { s ->
                            val date = DateFormat.format("MMM d, yyyy • h:mm a", s.createdAt).toString()
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "${s.fromName} → ${s.toName}  •  ₹ ${MoneyUtils.formatCents(s.amountCents)}",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(date, style = MaterialTheme.typography.bodySmall)
                                if (s.note.isNotBlank()) {
                                    Text(s.note, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }

            SectionCard(title = "Balances (after settlements)") {
                if (state.balances.isEmpty()) {
                    Text("No balances yet.")
                } else {
                    state.balances.sortedBy { it.netCents }.forEach { b ->
                        val label = when {
                            b.netCents > 0 -> "gets back"
                            b.netCents < 0 -> "owes"
                            else -> "settled"
                        }
                        Text(
                            "${b.name}: $label ₹ ${MoneyUtils.formatCents(kotlin.math.abs(b.netCents))}"
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

private fun buildShareText(state: com.example.travelpool.screens.pool.PoolUiState): String {
    return buildString {
        appendLine("Travel Pool • Settle up")
        appendLine()
        appendLine("Total contributed: ₹${MoneyUtils.formatCents(state.totalContributedCents)}")
        appendLine("Total spent: ₹${MoneyUtils.formatCents(state.totalSpentCents)}")
        appendLine("Balance: ₹${MoneyUtils.formatCents(state.balanceCents)}")
        appendLine()

        appendLine("Suggested payments:")
        if (state.suggestedSettlements.isEmpty()) {
            appendLine("- None")
        } else {
            state.suggestedSettlements.forEach {
                appendLine("- ${it.fromName} pays ${it.toName} ₹${MoneyUtils.formatCents(it.amountCents)}")
            }
        }

        appendLine()
        appendLine("Settlement history:")
        if (state.settlementHistory.isEmpty()) {
            appendLine("- None")
        } else {
            state.settlementHistory.take(10).forEach {
                appendLine("- ${it.fromName} → ${it.toName} ₹${MoneyUtils.formatCents(it.amountCents)}")
            }
            if (state.settlementHistory.size > 10) appendLine("- …and more")
        }
    }
}

private fun buildCsvExport(state: com.example.travelpool.screens.pool.PoolUiState): String {
    fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    val sb = StringBuilder()

    sb.appendLine("SECTION,FIELD,VALUE")

    sb.appendLine("SUMMARY,TotalContributedCents,${state.totalContributedCents}")
    sb.appendLine("SUMMARY,TotalSpentCents,${state.totalSpentCents}")
    sb.appendLine("SUMMARY,BalanceCents,${state.balanceCents}")
    sb.appendLine()

    sb.appendLine("BALANCES,Name,NetCents")
    state.balances.forEach { b ->
        sb.appendLine("BALANCES,${esc(b.name)},${b.netCents}")
    }
    sb.appendLine()

    sb.appendLine("SUGGESTED,From,To,AmountCents")
    state.suggestedSettlements.forEach { s ->
        sb.appendLine("SUGGESTED,${esc(s.fromName)},${esc(s.toName)},${s.amountCents}")
    }
    sb.appendLine()

    sb.appendLine("SETTLEMENT_HISTORY,From,To,AmountCents,Note,CreatedAt")
    state.settlementHistory.forEach { s ->
        sb.appendLine(
            "SETTLEMENT_HISTORY," +
                    "${esc(s.fromName)}," +
                    "${esc(s.toName)}," +
                    "${s.amountCents}," +
                    "${esc(s.note)}," +
                    "${s.createdAt}"
        )
    }

    return sb.toString()
}

private fun shareCsvFile(context: android.content.Context, csv: String, fileName: String) {
    val file = File(context.cacheDir, fileName)
    file.writeText(csv)

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(intent, "Export CSV"))
}
