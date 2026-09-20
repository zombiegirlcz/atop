package com.atop.taskmanager.data

/**
 * DATA VRSTVA — surová data.
 *
 * Tady se ještě nic nepočítá. Jen se přenese text z /proc do struktury,
 * které rozumí domain vrstva.
 */

/** Čas CPU v jiffies: celkem (user..steal) a z toho idle+iowait. */
data class CpuTimes(
    val totalJiffies: Long,
    val idleJiffies: Long
)

/** Jeden proces tak, jak byl přečten z /proc/[pid]. */
data class RawProcEntry(
    val pid: Int,
    val statLine: String,
    val name: String?,
    val uid: Int?,
    val vmRssKb: Long?,
    val cmdline: String?
)

/** Jeden snímek celého systému. Dva takové snímky → CPU %. */
data class ProcRawSnapshot(
    val aggregateCpu: CpuTimes,
    val perCoreCpu: List<CpuTimes>,
    val memTotalKb: Long,
    val memAvailableKb: Long,
    val swapTotalKb: Long,
    val swapFreeKb: Long,
    val entries: List<RawProcEntry>
)