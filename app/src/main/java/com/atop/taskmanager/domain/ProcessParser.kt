package com.atop.taskmanager.domain

import com.atop.taskmanager.data.CpuTimes
import com.atop.taskmanager.data.ProcRawSnapshot

/**
 * DOMAIN VRSTVA — parser + výpočet CPU %.
 *
 * Tady je ta část, na které většina vlastních task managerů shoří:
 * CPU % se NESMÍ počítat z kumulativního času běhu, ale z DELTY jiffies
 * mezi dvěma snímky:
 *
 *   cpu% = (proc_jiffies_B - proc_jiffies_A) / (total_jiffies_B - total_jiffies_A)
 *          * 100 * počet_jader
 *
 * Parser je stavový (drží si předchozí snímek). Je záměrně bez Androidu,
 * takže se dá testovat čistě v JVM.
 */
class ProcessParser(private val coreCount: Int) {

    private var prevTotal = 0L
    private var prevIdle = 0L
    private var prevProc = HashMap<Int, Long>()
    private var prevPerCore = ArrayList<CpuTimes>()

    fun parse(snapshot: ProcRawSnapshot): ParseResult {
        val total = snapshot.aggregateCpu.totalJiffies
        val idle = snapshot.aggregateCpu.idleJiffies
        val dTotal = total - prevTotal
        val dIdle = idle - prevIdle
        val firstRun = prevTotal == 0L || dTotal <= 0

        // Celkové CPU systému (0..100 %) — z pohledu "celého nebe".
        val totalCpu = if (firstRun) 0.0
        else ((dTotal - dIdle).toDouble() / dTotal) * 100.0

        // Per-core (pro druhou obrazovku / hlavičku).
        val perCore = snapshot.perCoreCpu.mapIndexed { i, c ->
            val p = prevPerCore.getOrNull(i) ?: return@mapIndexed 0.0
            val dt = c.totalJiffies - p.totalJiffies
            val di = c.idleJiffies - p.idleJiffies
            if (dt <= 0) 0.0 else ((dt - di).toDouble() / dt) * 100.0
        }

        val parsed = snapshot.entries.mapNotNull { e ->
            val st = parseStat(e.statLine) ?: return@mapNotNull null
            Triple(e, st, st.utime + st.stime)
        }

        val jiffiesNow = HashMap<Int, Long>(parsed.size)
        val processes = ArrayList<ProcessInfo>(parsed.size)

        for ((entry, st, jiffies) in parsed) {
            jiffiesNow[entry.pid] = jiffies
            val prev = prevProc[entry.pid]
            val cpu = if (firstRun || prev == null) 0.0
            else ((jiffies - prev).toDouble() / dTotal) * 100.0 * coreCount

            processes.add(
                ProcessInfo(
                    pid = entry.pid,
                    name = entry.name ?: st.comm,
                    uid = entry.uid ?: -1,
                    cpuPercent = cpu.coerceIn(0.0, 100.0 * coreCount),
                    // VmRSS z /status je přesnější; fallback rss (v stránkách) * 4 kB.
                    rssKb = entry.vmRssKb ?: (st.rssPages * 4),
                    state = st.state,
                    nice = st.nice
                )
            )
        }

        // Uložit snímek pro příští kolo.
        prevProc = jiffiesNow
        prevTotal = total
        prevIdle = idle
        prevPerCore = ArrayList(snapshot.perCoreCpu)

        val stats = SystemStats(
            totalCpuPercent = totalCpu,
            perCorePercent = perCore,
            memTotalKb = snapshot.memTotalKb,
            memUsedKb = (snapshot.memTotalKb - snapshot.memAvailableKb).coerceAtLeast(0),
            swapTotalKb = snapshot.swapTotalKb,
            swapUsedKb = (snapshot.swapTotalKb - snapshot.swapFreeKb).coerceAtLeast(0)
        )
        return ParseResult(stats, processes)
    }

    /**
     * /proc/[pid]/stat: comm je v závorkách a MŮŽE obsahovat mezery i závorky,
     * proto se split nedělá na začátku, ale až za POSLEDNÍ ')'.
     */
    private fun parseStat(statLine: String): Stat? {
        val open = statLine.indexOf('(')
        val close = statLine.lastIndexOf(')')
        if (open < 0 || close <= open) return null
        val comm = statLine.substring(open + 1, close)
        val t = statLine.substring(close + 1).trim().split(Regex("\\s+"))
        if (t.size < 22) return null
        return Stat(
            comm = comm,
            state = t[0].firstOrNull() ?: '?',
            utime = t[11].toLongOrNull() ?: 0L,   // pole 14
            stime = t[12].toLongOrNull() ?: 0L,   // pole 15
            nice = t[16].toIntOrNull() ?: 0,      // pole 19
            rssPages = t[21].toLongOrNull() ?: 0L // pole 24
        )
    }

    private data class Stat(
        val comm: String,
        val state: Char,
        val utime: Long,
        val stime: Long,
        val nice: Int,
        val rssPages: Long
    )
}