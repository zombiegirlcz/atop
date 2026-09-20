package com.atop.taskmanager.data

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ROOT DATA COLLECTOR ("radar") — jediné místo, které mluví s root shellem.
 *
 * Proč root: od Androidu 7+ (hidepid=2) vidí běžná appka v /proc jen svůj PID.
 * Root shell tohle omezení obchází.
 *
 * Persistentní shell: `Shell.getShell()` se volá jednou a drží se v paměti,
 * takže se su prompt neukazuje při každém refreshi.
 *
 * Vrstva záměrně NEVÍ nic o CPU %, řazení ani o UI — to je práce domain/ui.
 * Když chceš změnit, ODKUD se data berou (např. přidat /proc/[pid]/io),
 * měníš jen tuhle třídu (PROC_SCRIPT + parse).
 */
class RootProcessDataSource {

    @Volatile
    private var shell: Shell? = null

    /** Požádá o root shell. Vrací true, jen když jsme skutečně root. */
    suspend fun ensureRoot(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val s = Shell.getShell()
            shell = s
            s.isRoot
        }.getOrDefault(false)
    }

    /** Přečte jeden snímek /proc jedním root příkazem. */
    suspend fun readSnapshot(): ProcRawSnapshot = withContext(Dispatchers.IO) {
        val s = shell ?: Shell.getShell().also { shell = it }
        val result = s.newJob().add(PROC_SCRIPT).exec()
        parse(result.out)
    }

    /** Pošle signál procesu (15 = SIGTERM, 9 = SIGKILL). */
    suspend fun kill(pid: Int, signal: Int): Boolean = exec("kill -$signal $pid")

    /** Změní nice hodnotu procesu (-20 .. 19). */
    suspend fun renice(pid: Int, priority: Int): Boolean = exec("renice $priority -p $pid")

    private suspend fun exec(command: String): Boolean = withContext(Dispatchers.IO) {
        val s = shell ?: return@withContext false
        runCatching { s.newJob().add(command).exec().isSuccess }.getOrDefault(false)
    }

    // ------------------------------------------------------------------
    // Parsování výstupu shellu.
    // Markery: "@@<pid>", "@S" (následuje stat), "@E", "@C<cmdline>".
    // Používáme shell builtiny (read/printf), aby refresh neforkoval
    // proces na každý PID — na telefonu s 600+ procesy je to znát.
    // ------------------------------------------------------------------

    private fun parse(lines: List<String>): ProcRawSnapshot {
        val cpuLines = ArrayList<String>()
        val memLines = ArrayList<String>()
        val entries = ArrayList<RawProcEntry>()

        var section = 0
        var pid = -1
        var statLine: String? = null
        var name: String? = null
        var uid: Int? = null
        var rss: Long? = null
        var cmd: String? = null
        var statPending = false

        fun flush() {
            val s = statLine
            if (pid >= 0 && s != null) entries.add(RawProcEntry(pid, s, name, uid, rss, cmd))
            pid = -1; statLine = null; name = null; uid = null; rss = null; cmd = null
            statPending = false
        }

        for (raw in lines) {
            val line = raw.trimEnd('\r')
            when (line) {
                "##CPU" -> { section = 1; continue }
                "##MEM" -> { section = 2; continue }
                "##PROC" -> { section = 3; continue }
            }
            when (section) {
                1 -> cpuLines.add(line)
                2 -> memLines.add(line)
                else -> when {
                    line.startsWith("@@") -> {
                        flush()
                        pid = line.substring(2).trim().toIntOrNull() ?: -1
                    }
                    line == "@S" -> statPending = true
                    line.startsWith("@C") -> cmd = line.substring(2).trim().ifEmpty { null }
                    statPending -> { statLine = line; statPending = false }
                    line.startsWith("Name:") -> name = line.substringAfter(':').trim()
                    line.startsWith("Uid:") -> uid = line.substringAfter(':')
                        .trim().split(Regex("\\s+")).firstOrNull()?.toIntOrNull()
                    line.startsWith("VmRSS:") -> rss = line.substringAfter(':')
                        .trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull()
                }
            }
        }
        flush()

        return ProcRawSnapshot(
            aggregateCpu = parseCpuLine(cpuLines.firstOrNull { it.startsWith("cpu ") }),
            perCoreCpu = cpuLines.filter { it.startsWith("cpu") && !it.startsWith("cpu ") }
                .map { parseCpuLine(it) },
            memTotalKb = parseMem(memLines, "MemTotal"),
            memAvailableKb = parseMem(memLines, "MemAvailable"),
            swapTotalKb = parseMem(memLines, "SwapTotal"),
            swapFreeKb = parseMem(memLines, "SwapFree"),
            entries = entries
        )
    }

    /** "cpu  1 2 3 4 ..." → total (user..steal) a idle (idle+iowait). */
    private fun parseCpuLine(line: String?): CpuTimes {
        if (line == null) return CpuTimes(0, 0)
        val v = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        val idle = (v.getOrNull(3) ?: 0L) + (v.getOrNull(4) ?: 0L) // idle + iowait
        val total = v.take(8).sum()                                // user..steal
        return CpuTimes(total, idle)
    }

    private fun parseMem(lines: List<String>, key: String): Long {
        val l = lines.firstOrNull { it.startsWith("$key:") } ?: return 0
        return l.substringAfter(':').trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: 0
    }

    companion object {
        /** Jeden shell skript = jeden root round-trip. */
        private const val PROC_SCRIPT = """
printf '##CPU\n'
cat /proc/stat
printf '##MEM\n'
cat /proc/meminfo
printf '##PROC\n'
for d in /proc/[0-9]*; do
  pid=${'$'}{d#/proc/}
  [ -r "${'$'}d/stat" ] || continue
  printf '@@%s\n' "${'$'}pid"
  printf '@S\n'
  line=""
  IFS= read -r line < "${'$'}d/stat"
  printf '%s\n' "${'$'}line"
  printf '@E\n'
  while IFS= read -r l; do
    case "${'$'}l" in
      Name:*|Uid:*|VmRSS:*) printf '%s\n' "${'$'}l";;
    esac
  done < "${'$'}d/status"
  c=""
  IFS= read -r c < "${'$'}d/cmdline" 2>/dev/null
  printf '@C%s\n' "${'$'}c"
done
"""
    }
}