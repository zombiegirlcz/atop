package com.atop.taskmanager.domain

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable

/** Co umíme zjistit o vlastníkovi UID. */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isUserApp: Boolean
)

/**
 * DOMAIN VRSTVA — UID → jméno/ikona appky.
 *
 * Tohle je vylepšení oproti klasickému htopu: ten zná jen raw process name,
 * my umíme přes PackageManager.getPackagesForUid() zobrazit jméno a ikonu
 * skutečné Android aplikace.
 *
 * Cache je klíčová — resolve() se volá pro každý proces v každém refreshi.
 */
class AppLabelResolver(context: Context) {

    private val pm = context.packageManager
    private val cache = HashMap<Int, AppInfo?>()

    /** Vrátí info o appce pro dané UID, nebo null (jádro, root, daemon). */
    fun resolve(uid: Int): AppInfo? {
        if (uid < 0) return null
        if (cache.containsKey(uid)) return cache[uid]
        val info = runCatching {
            val pkg = pm.getPackagesForUid(uid)?.firstOrNull() ?: return@runCatching null
            val ai = pm.getApplicationInfo(pkg, 0)
            val isUser = (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            AppInfo(
                packageName = pkg,
                label = pm.getApplicationLabel(ai).toString(),
                icon = pm.getApplicationIcon(ai),
                isUserApp = isUser
            )
        }.getOrNull()
        cache[uid] = info
        return info
    }

    fun clear() = cache.clear()
}