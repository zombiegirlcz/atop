package com.atop.taskmanager.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atop.taskmanager.data.RootProcessDataSource
import com.atop.taskmanager.domain.AppLabelResolver
import com.atop.taskmanager.domain.ProcessInfo
import com.atop.taskmanager.domain.ProcessParser
import com.atop.taskmanager.domain.SystemStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Stav root přístupu — UI podle toho zobrazí hlášku. */
enum class RootState { CHECKING, GRANTED, DENIED }

enum class SortColumn { CPU, RAM, PID, NAME }

enum class ProcessFilter { ALL, USER_APPS, SYSTEM }

/** Co UI vykresluje. ViewModel drží JEDEN state objekt. */
data class UiState(
    val rootState: RootState = RootState.CHECKING,
    val stats: SystemStats = SystemStats.EMPTY,
    val processes: List<ProcessInfo> = emptyList(),
    val query: String = "",
    val sortColumn: SortColumn = SortColumn.CPU,
    val filter: ProcessFilter = ProcessFilter.ALL,
    val refreshIntervalMs: Long = 2000L,
    val totalProcessCount: Int = 0
)

/**
 * VIEWMODEL / BUSINESS LOGIC ("dispečer").
 *
 * - drží polling smyčku (coroutine) s nastavitelným intervalem
 * - drží poslední snapshot procesů
 * - řeší řazení a filtrování (UI jen kreslí)
 *
 * Když chceš přidat "zobrazit jen uživatelské appky", měníš tady
 * a v domain vrstvě. UI ani Root collector se nemění.
 *
 * POZOR na pořadí vlastností: `coreCount` musí být deklarován PŘED `parser`,
 * protože Kotlin inicializuje vlastnosti v pořadí zápisu.
 */
class ProcessListViewModel(app: Application) : AndroidViewModel(app) {

    /** Kolik jader má telefon (ukazujeme v hlavičce a škálujeme CPU %). */
    val coreCount: Int =
        Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    private val source = RootProcessDataSource()
    private val labels = AppLabelResolver(app)
    private val parser = ProcessParser(coreCount = coreCount)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        start()
    }

    /** Ověří root a rozjede polling. */
    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            _state.value = _state.value.copy(rootState = RootState.CHECKING)
            val rooted = source.ensureRoot()
            _state.value = _state.value.copy(
                rootState = if (rooted) RootState.GRANTED else RootState.DENIED
            )
            if (!rooted) return@launch

            while (isActive) {
                val started = System.currentTimeMillis()
                runCatching { source.readSnapshot() }
                    .onSuccess { snap ->
                        val result = parser.parse(snap)
                        val enriched = result.processes.map { p ->
                            val app = labels.resolve(p.uid)
                            p.copy(
                                label = app?.label,
                                packageName = app?.packageName,
                                isUserApp = app?.isUserApp ?: false,
                                icon = app?.icon
                            )
                        }
                        val current = _state.value
                        _state.value = current.copy(
                            stats = result.stats,
                            processes = applyView(enriched, current),
                            totalProcessCount = enriched.size
                        )
                    }
                // Odečteme dobu čtení, ať je interval skutečně ~2 s.
                val elapsed = System.currentTimeMillis() - started
                delay((_state.value.refreshIntervalMs - elapsed).coerceAtLeast(250L))
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    fun setQuery(q: String) = update { it.copy(query = q) }

    fun setSort(column: SortColumn) = update { it.copy(sortColumn = column) }

    fun setFilter(filter: ProcessFilter) = update { it.copy(filter = filter) }

    fun setRefreshInterval(ms: Long) =
        update { it.copy(refreshIntervalMs = ms.coerceIn(500L, 10_000L)) }

    fun kill(pid: Int, force: Boolean) {
        viewModelScope.launch { source.kill(pid, if (force) 9 else 15) }
    }

    fun renice(pid: Int, priority: Int) {
        viewModelScope.launch { source.renice(pid, priority) }
    }

    private fun update(f: (UiState) -> UiState) {
        val next = f(_state.value)
        _state.value = next.copy(processes = applyView(next.processes, next))
    }

    /** Filtrování + řazení. Patří do ViewModelu, ne do Compose. */
    private fun applyView(list: List<ProcessInfo>, s: UiState): List<ProcessInfo> {
        val q = s.query.trim().lowercase()

        var out = list.asSequence()
        when (s.filter) {
            ProcessFilter.USER_APPS -> out = out.filter { it.isUserApp }
            ProcessFilter.SYSTEM -> out = out.filter { !it.isUserApp }
            ProcessFilter.ALL -> Unit
        }
        if (q.isNotEmpty()) {
            out = out.filter {
                it.displayName.lowercase().contains(q) ||
                    it.name.lowercase().contains(q) ||
                    it.pid.toString().contains(q) ||
                    (it.packageName?.lowercase()?.contains(q) == true)
            }
        }
        out = when (s.sortColumn) {
            SortColumn.CPU -> out.sortedByDescending { it.cpuPercent }
            SortColumn.RAM -> out.sortedByDescending { it.rssKb }
            SortColumn.PID -> out.sortedBy { it.pid }
            SortColumn.NAME -> out.sortedBy { it.displayName.lowercase() }
        }
        return out.toList()
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }
}