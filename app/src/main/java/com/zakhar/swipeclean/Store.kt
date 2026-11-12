package com.zakhar.swipeclean

import android.content.Context
import java.io.File
import java.io.FileWriter

/**
 * Решения хранятся в журнале filesDir/decisions.log: строки «id код».
 * Дописываем по одной строке на каждый свайп, при запуске сворачиваем журнал.
 */
class DecisionStore(context: Context) {
    private val file = File(context.filesDir, "decisions.log")
    val map = HashMap<Long, Decision>()
    private var out: FileWriter? = null

    init {
        if (file.exists()) {
            file.forEachLine { line ->
                val p = line.trim().split(' ')
                if (p.size == 2) {
                    val id = p[0].toLongOrNull()
                    val d = Decision.from(p[1].firstOrNull() ?: ' ')
                    if (id != null) {
                        if (d != null) map[id] = d else map.remove(id)
                    }
                }
            }
        }
        rewrite()
    }

    @Synchronized
    private fun rewrite() {
        out?.close()
        file.bufferedWriter().use { w -> map.forEach { (id, d) -> w.write("$id ${d.code}\n") } }
        out = FileWriter(file, true)
    }

    @Synchronized
    fun set(id: Long, d: Decision?) {
        if (d == null) map.remove(id) else map[id] = d
        out?.apply {
            write("$id ${d?.code ?: 'N'}\n")
            flush()
        }
    }

    @Synchronized
    fun clear() {
        map.clear()
        rewrite()
    }
}
