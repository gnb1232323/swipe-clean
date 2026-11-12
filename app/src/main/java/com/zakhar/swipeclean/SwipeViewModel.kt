package com.zakhar.swipeclean

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SwipeViewModel(app: Application) : AndroidViewModel(app) {
    private val store = DecisionStore(app)

    var all by mutableStateOf<List<Media>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var filter by mutableStateOf(Filter.ALL)
        private set

    /** Ещё не просмотренные (с учётом фильтра), pos — текущая карточка. */
    var queue by mutableStateOf<List<Media>>(emptyList())
        private set
    var pos by mutableIntStateOf(0)
        private set

    val trash = mutableStateListOf<Media>()
    val liked = mutableStateListOf<Media>()
    var favDone by mutableStateOf<Set<Long>>(emptySet())
        private set
    var canUndo by mutableStateOf(false)
        private set

    private val history = ArrayList<Pair<Media, Decision>>()
    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        loading = true
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { MediaRepo.load(getApplication<Application>().contentResolver) }
            all = list
            rebuild()
            loading = false
        }
    }

    private fun rebuild() {
        queue = all.filter { filter.matches(it) && store.map[it.id] == null }
        pos = 0
        trash.clear()
        trash.addAll(all.filter { store.map[it.id] == Decision.TRASH })
        liked.clear()
        liked.addAll(all.filter { store.map[it.id] == Decision.LIKE || store.map[it.id] == Decision.FAVORITED })
        favDone = all.filter { store.map[it.id] == Decision.FAVORITED }.map { it.id }.toSet()
    }

    fun setFilter(f: Filter) {
        if (f == filter) return
        filter = f
        history.clear()
        canUndo = false
        rebuild()
    }

    fun decide(d: Decision) {
        val m = queue.getOrNull(pos) ?: return
        store.set(m.id, d)
        history.add(m to d)
        when (d) {
            Decision.TRASH -> trash.add(0, m)
            Decision.LIKE -> liked.add(0, m)
            else -> {}
        }
        pos += 1
        canUndo = true
    }

    fun undo() {
        val (m, d) = history.removeLastOrNull() ?: return
        store.set(m.id, null)
        when (d) {
            Decision.TRASH -> trash.remove(m)
            Decision.LIKE -> liked.remove(m)
            else -> {}
        }
        pos = (pos - 1).coerceAtLeast(0)
        canUndo = history.isNotEmpty()
    }

    /** Вернуть файл из корзины/лайков обратно в очередь просмотра. */
    fun restore(m: Media) {
        store.set(m.id, null)
        history.clear()
        canUndo = false
        rebuild()
    }

    fun trashSize(): Long = trash.sumOf { it.size }

    // Системные запросы показывают окно подтверждения, поэтому отправляем пачками.
    fun trashChunks(): List<List<Media>> = trash.toList().chunked(400)

    fun likeChunks(): List<List<Media>> =
        liked.filter { !it.favorite && it.id !in favDone }.chunked(400)

    fun pendingFavorites(): Int = liked.count { !it.favorite && it.id !in favDone }

    fun onTrashed(items: List<Media>) {
        val ids = items.map { it.id }.toSet()
        ids.forEach { store.set(it, null) }
        all = all.filter { it.id !in ids }
        history.clear()
        canUndo = false
        rebuild()
    }

    fun onFavorited(items: List<Media>) {
        items.forEach { store.set(it.id, Decision.FAVORITED) }
        favDone = favDone + items.map { it.id }
    }

    fun resetAll() {
        store.clear()
        history.clear()
        canUndo = false
        rebuild()
    }
}
