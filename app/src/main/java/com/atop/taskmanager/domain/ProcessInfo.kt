package com.atop.taskmanager.domain

import android.graphics.drawable.Drawable

/**
 * DOMAIN VRSTVA — model.
 *
 * ProcessInfo je "jedna řádka htopu". Neobsahuje žádné UI ani shell detaily.
 * Pole label/packageName/isUserApp/icon doplňuje AppLabelResolver (a ViewModel),
 * takže parser zůstává čistý a testovatelný bez Androidu.
 */
data class ProcessInfo(
    val pid: Int,
    val name: String,
    val uid: Int,
    val cpuPercent: Double,
    val rssKb: Long,
    val state: Char,
    val nice: Int = 0,
    // --- obohacení z PackageManageru (volitelné) ---
    val label: String? = null,
    val packageName: String? = null,
    val isUserApp: Boolean = false,
    val icon: Drawable? = null
) {
    /** Co ukázat v seznamu: jméno appky, jinak raw process name. */
    val displayName: String get() = label ?: name
}

/** Celkový přehled nahoře (hlavička htopu). */
data class SystemStats(
    val totalCpuPercent: Double = 0.0,
    val perCorePercent: List<Double> = emptyList(),
    val memTotalKb: Long = 0,
    val memUsedKb: Long = 0,
    val swapTotalKb: Long = 0,
    val swapUsedKb: Long = 0
) {
    val memPercent: Float
        get() = if (memTotalKb <= 0) 0f else (memUsedKb.toFloat() / memTotalKb).coerceIn(0f, 1f)

    val swapPercent: Float
        get() = if (swapTotalKb <= 0) 0f else (swapUsedKb.toFloat() / swapTotalKb).coerceIn(0f, 1f)

    companion object {
        val EMPTY = SystemStats()
    }
}

/** Výsledek jednoho parsovacího kroku. */
data class ParseResult(
    val stats: SystemStats,
    val processes: List<ProcessInfo>
)