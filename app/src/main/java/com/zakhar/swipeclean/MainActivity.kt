package com.zakhar.swipeclean

import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val vm: SwipeViewModel by viewModels()

    private val queue = ArrayDeque<List<Media>>()
    private var current: List<Media> = emptyList()
    private var kind = 0 // 0 — корзина, 1 — избранное

    private val launcher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == RESULT_OK) {
            if (kind == 0) vm.onTrashed(current) else vm.onFavorited(current)
            next()
        } else {
            queue.clear()
            toast("Отменено")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Root(vm, onTrash = { start(0, it) }, onFav = { start(1, it) })
            }
        }
    }

    private fun start(k: Int, chunks: List<List<Media>>) {
        if (chunks.isEmpty()) return
        kind = k
        queue.clear()
        queue.addAll(chunks)
        next()
    }

    private fun next() {
        val chunk = queue.removeFirstOrNull()
        if (chunk == null) {
            toast(if (kind == 0) "Готово: файлы в корзине" else "Готово: добавлено в избранное")
            return
        }
        current = chunk
        val uris = chunk.map { it.uri }
        try {
            val pi = if (kind == 0) MediaStore.createTrashRequest(contentResolver, uris, true)
            else MediaStore.createFavoriteRequest(contentResolver, uris, true)
            launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } catch (e: Exception) {
            queue.clear()
            toast("Не получилось: ${e.message}")
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
