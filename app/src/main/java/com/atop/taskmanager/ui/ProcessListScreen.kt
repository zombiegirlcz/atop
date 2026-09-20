package com.atop.taskmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atop.taskmanager.domain.ProcessInfo
import com.atop.taskmanager.domain.SystemStats
import com.atop.taskmanager.ui.components.LoadBar
import com.atop.taskmanager.ui.components.ProcessActionDialog
import com.atop.taskmanager.ui.components.ProcessRow
import com.atop.taskmanager.ui.components.formatKb
import com.atop.taskmanager.ui.components.formatPercent

/**
 * UI VRSTVA ("obrazovka ve věži").
 *
 * Tato vrstva je hloupá: nic nepočítá, jen vykresluje UiState z ViewModelu
 * a posílá mu události. Když chceš změnit vzhled, měníš jen tady.
 */
@Composable
fun ProcessListScreen(vm: ProcessListViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<ProcessInfo?>(null) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            HeaderBar(state.stats)

            when (state.rootState) {
                RootState.CHECKING -> CenterInfo("Žádám root oprávnění…", spinner = true)
                RootState.DENIED -> CenterInfo(
                    "Root není dostupný.\nBez rootu aplikace nevidí /proc ostatních procesů " +
                        "(hidepid=2)."
                )
                RootState.GRANTED -> {
                    SearchAndSort(state, vm)
                    ProcessList(
                        processes = state.processes,
                        coreCount = vm.coreCount,
                        onSelect = { selected = it }
                    )
                }
            }
        }
    }

    selected?.let { p ->
        ProcessActionDialog(
            process = p,
            onDismiss = { selected = null },
            onKill = { force -> vm.kill(p.pid, force); selected = null },
            onRenice = { prio -> vm.renice(p.pid, prio); selected = null }
        )
    }
}

/** Hlavička jako v htopu: celkové CPU, RAM, swap + per-core proužky. */
@Composable
private fun HeaderBar(stats: SystemStats) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Atop",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "CPU ${formatPercent(stats.totalCpuPercent)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace
                )
            }
            Box(modifier = Modifier.padding(top = 6.dp)) {
                LoadBar(fraction = (stats.totalCpuPercent / 100.0).toFloat())
            }
            Box(modifier = Modifier.padding(top = 6.dp)) {
                LoadBar(fraction = stats.memPercent)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "RAM ${formatKb(stats.memUsedKb)} / ${formatKb(stats.memTotalKb)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Swap ${formatKb(stats.swapUsedKb)} / ${formatKb(stats.swapTotalKb)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SearchAndSort(state: UiState, vm: ProcessListViewModel) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            singleLine = true,
            placeholder = { Text("Hledat (jméno, PID, package)") },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) {
                        Text("×", style = MaterialTheme.typography.titleMedium)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = state.filter == ProcessFilter.ALL,
                onClick = { vm.setFilter(ProcessFilter.ALL) },
                label = { Text("Vše") })
            FilterChip(
                selected = state.filter == ProcessFilter.USER_APPS,
                onClick = { vm.setFilter(ProcessFilter.USER_APPS) },
                label = { Text("Appky") })
            FilterChip(
                selected = state.filter == ProcessFilter.SYSTEM,
                onClick = { vm.setFilter(ProcessFilter.SYSTEM) },
                label = { Text("Systém") })
        }

        Row(
            modifier = Modifier.padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Řadit:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SortButton("CPU", state.sortColumn == SortColumn.CPU) { vm.setSort(SortColumn.CPU) }
            SortButton("RAM", state.sortColumn == SortColumn.RAM) { vm.setSort(SortColumn.RAM) }
            SortButton("PID", state.sortColumn == SortColumn.PID) { vm.setSort(SortColumn.PID) }
            SortButton("Jméno", state.sortColumn == SortColumn.NAME) { vm.setSort(SortColumn.NAME) }

            Box(modifier = Modifier.weight(1f))
            Text(
                "${state.processes.size}/${state.totalProcessCount}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SortButton(label: String, active: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProcessList(
    processes: List<ProcessInfo>,
    coreCount: Int,
    onSelect: (ProcessInfo) -> Unit
) {
    val maxRss = remember(processes) { processes.maxOfOrNull { it.rssKb } ?: 1L }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(processes, key = { it.pid }) { p ->
            ProcessRow(
                process = p,
                maxRssKb = maxRss,
                coreCount = coreCount,
                onClick = { },
                onLongClick = { onSelect(p) }
            )
        }
    }
}

@Composable
private fun CenterInfo(text: String, spinner: Boolean = false) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (spinner) {
                CircularProgressIndicator(modifier = Modifier.padding(bottom = 12.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}