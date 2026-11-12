package com.zakhar.swipeclean

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

val Lime = Color(0xFFB6F23A)
val Red = Color(0xFFFF6B5E)
val Blue = Color(0xFF5AB0FF)
val Surface1 = Color(0xFF151A22)
val Surface2 = Color(0xFF1D2430)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Lime,
            onPrimary = Color(0xFF11190A),
            background = Color(0xFF0E1116),
            onBackground = Color(0xFFEAF0F6),
            surface = Surface1,
            onSurface = Color(0xFFEAF0F6),
            surfaceVariant = Surface2,
            onSurfaceVariant = Color(0xFF8A99AB),
            error = Red,
        ),
        content = content,
    )
}

// ---------- Разрешения ----------

fun mediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

fun hasMediaPermission(c: Context): Boolean =
    mediaPermissions().all { ContextCompat.checkSelfPermission(c, it) == PackageManager.PERMISSION_GRANTED }

fun openMedia(ctx: Context, m: Media) {
    try {
        val type = m.mime.ifBlank { if (m.isVideo) "video/*" else "image/*" }
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(m.uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    } catch (_: Exception) {
    }
}

@Composable
fun Root(vm: SwipeViewModel, onTrash: (List<List<Media>>) -> Unit, onFav: (List<List<Media>>) -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasMediaPermission(ctx)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasMediaPermission(ctx)
    }
    LifecycleResumeEffect(Unit) {
        granted = hasMediaPermission(ctx)
        onPauseOrDispose { }
    }
    LaunchedEffect(granted) { if (granted) vm.load() }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Чистка галереи", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(12.dp))
            Text(
                "Нужен доступ к фото и видео, чтобы показывать их и отправлять ненужные в корзину. Выбери «Разрешить доступ ко всем фото и видео».",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { ask.launch(mediaPermissions()) }) { Text("Дать доступ") }
            TextButton(onClick = {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                )
            }) { Text("Открыть настройки приложения") }
        }
    } else {
        MainScaffold(vm, onTrash, onFav)
    }
}

@Composable
fun MainScaffold(vm: SwipeViewModel, onTrash: (List<List<Media>>) -> Unit, onFav: (List<List<Media>>) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = Surface1) {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) }, label = { Text("Чистка") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Favorite, contentDescription = null) }, label = { Text("Лайки ${vm.liked.size}") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Delete, contentDescription = null) }, label = { Text("Корзина ${vm.trash.size}") },
                )
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> SwipeScreen(vm, onTrash)
                1 -> LikesScreen(vm, onFav)
                else -> TrashScreen(vm, onTrash)
            }
        }
    }
}

// ---------- Экран свайпов ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeScreen(vm: SwipeViewModel, onTrash: (List<List<Media>>) -> Unit) {
    val ctx = LocalContext.current
    var command by remember { mutableStateOf<Decision?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val queue = vm.queue
    val pos = vm.pos
    val cur = queue.getOrNull(pos)
    val next = queue.getOrNull(pos + 1)
    val remaining = (queue.size - pos).coerceAtLeast(0)

    LaunchedEffect(pos, queue) {
        for (i in 0..3) queue.getOrNull(pos + i)?.let { Thumbs.load(ctx, it, 1280) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Осталось $remaining", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "из ${queue.size}",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (vm.trash.isEmpty()) Surface2 else Red.copy(alpha = 0.18f))
                    .clickable(enabled = vm.trash.isNotEmpty()) { confirm = true }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Delete, null, tint = if (vm.trash.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Red, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${vm.trash.size} · ${formatSize(vm.trashSize())}",
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    color = if (vm.trash.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Red,
                )
            }
            Box {
                Text(
                    "⋮", fontSize = 26.sp,
                    modifier = Modifier.clickable { menu = true }.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Начать заново (сбросить решения)") }, onClick = {
                        menu = false
                        vm.resetAll()
                    })
                }
            }
        }
        if (queue.isNotEmpty()) {
            LinearProgressIndicator(
                progress = { pos.toFloat() / queue.size },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = Lime, trackColor = Surface2,
            )
        }
        LazyRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Filter.entries.toList()) { f ->
                FilterChip(selected = vm.filter == f, onClick = { vm.selectFilter(f) }, label = { Text(f.label) })
            }
        }

        Box(
            Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                vm.loading -> CircularProgressIndicator(color = Lime)
                cur == null -> DoneCard(vm, onAsk = { confirm = true })
                else -> {
                    if (next != null) {
                        Box(
                            Modifier.fillMaxSize()
                                .graphicsLayer { scaleX = 0.94f; scaleY = 0.94f }
                                .clip(RoundedCornerShape(28.dp)),
                        ) { Thumb(next, 1280, Modifier.fillMaxSize()) }
                    }
                    key(cur.id) {
                        SwipeCard(cur, command) { d ->
                            command = null
                            vm.decide(d)
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundButton(Red, 64) { if (cur != null && command == null) command = Decision.TRASH }
            RoundGlyph("↶", 48, enabled = vm.canUndo) { vm.undo() }
            RoundButton(Blue, 64, like = true) { if (cur != null && command == null) command = Decision.LIKE }
            RoundButton(Lime, 64, keep = true) { if (cur != null && command == null) command = Decision.KEEP }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Переместить в корзину?") },
            text = {
                Text(
                    "${vm.trash.size} файлов, освободится около ${formatSize(vm.trashSize())}. " +
                        "Android попросит подтвердить. Удалённое можно восстановить примерно 30 дней."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onTrash(vm.trashChunks())
                }) { Text("Удалить", color = Red) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun RoundButton(color: Color, size: Int, like: Boolean = false, keep: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.16f))
            .border(2.dp, color, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val ic = when {
            like -> Icons.Filled.Favorite
            keep -> Icons.Filled.Check
            else -> Icons.Filled.Close
        }
        Icon(ic, null, tint = color, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun RoundGlyph(glyph: String, size: Int, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Surface2).alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, fontSize = 24.sp) }
}

@Composable
private fun DoneCard(vm: SwipeViewModel, onAsk: () -> Unit) {
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(Surface1).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Всё просмотрено", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "В корзине ${vm.trash.size} (${formatSize(vm.trashSize())}), лайков ${vm.liked.size}.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        if (vm.trash.isNotEmpty()) Button(onClick = onAsk) { Text("Очистить корзину") }
    }
}

@Composable
fun SwipeCard(media: Media, command: Decision?, onDecision: (Decision) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ox = remember(media.id) { Animatable(0f) }
    val oy = remember(media.id) { Animatable(0f) }
    var w by remember(media.id) { mutableFloatStateOf(1f) }
    var h by remember(media.id) { mutableFloatStateOf(1f) }
    var gone by remember(media.id) { mutableStateOf(false) }

    fun fling(d: Decision) {
        if (gone) return
        gone = true
        scope.launch {
            val tx = when (d) {
                Decision.TRASH -> -w * 1.6f
                Decision.KEEP -> w * 1.6f
                else -> 0f
            }
            val ty = if (d == Decision.LIKE) h * 1.3f else 0f
            launch { ox.animateTo(tx, tween(220)) }
            oy.animateTo(ty, tween(220))
            onDecision(d)
        }
    }

    fun springBack() {
        scope.launch {
            launch { ox.animateTo(0f, spring()) }
            oy.animateTo(0f, spring())
        }
    }

    LaunchedEffect(command) { if (command != null) fling(command) }

    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { w = it.width.toFloat(); h = it.height.toFloat() }
            .offset { IntOffset(ox.value.roundToInt(), oy.value.roundToInt()) }
            .graphicsLayer { rotationZ = (ox.value / w).coerceIn(-1f, 1f) * 12f }
            .clip(RoundedCornerShape(28.dp))
            .background(Surface1)
            .pointerInput(media.id) {
                detectTapGestures(onTap = { if (media.isVideo) openMedia(ctx, media) })
            }
            .pointerInput(media.id) {
                detectDragGestures(
                    onDragEnd = {
                        val dx = ox.value
                        val dy = oy.value
                        val th = w * 0.25f
                        when {
                            abs(dx) >= abs(dy) && dx < -th -> fling(Decision.TRASH)
                            abs(dx) >= abs(dy) && dx > th -> fling(Decision.KEEP)
                            dy > th * 0.9f && dy > abs(dx) -> fling(Decision.LIKE)
                            else -> springBack()
                        }
                    },
                    onDragCancel = { springBack() },
                ) { change, drag ->
                    change.consume()
                    if (!gone) {
                        scope.launch {
                            ox.snapTo(ox.value + drag.x)
                            oy.snapTo((oy.value + drag.y).coerceAtLeast(-h * 0.06f))
                        }
                    }
                }
            },
    ) {
        Thumb(media, 1280, Modifier.fillMaxSize())

        if (media.isVideo) {
            Box(
                Modifier.align(Alignment.Center).size(64.dp).clip(CircleShape).background(Color(0x99000000)),
                contentAlignment = Alignment.Center,
            ) { Text("▶", fontSize = 28.sp, color = Color.White) }
        }

        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                .padding(16.dp),
        ) {
            Text(formatDate(media.taken), fontWeight = FontWeight.SemiBold, color = Color.White)
            val extra = buildString {
                append(formatSize(media.size))
                if (media.bucket.isNotBlank()) append(" · ").append(media.bucket)
                if (media.isVideo && media.durationMs > 0) append(" · ").append(formatDuration(media.durationMs))
            }
            Text(extra, fontSize = 13.sp, color = Color(0xCCFFFFFF))
        }

        Stamp("В КОРЗИНУ", Red, Alignment.TopStart, -12f) { (-ox.value / (w * 0.25f)).coerceIn(0f, 1f) }
        Stamp("ОСТАВИТЬ", Lime, Alignment.TopEnd, 12f) { (ox.value / (w * 0.25f)).coerceIn(0f, 1f) }
        Stamp("ЛАЙК", Blue, Alignment.Center, 0f) {
            if (oy.value > abs(ox.value)) (oy.value / (w * 0.25f)).coerceIn(0f, 1f) else 0f
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Stamp(
    text: String, color: Color, at: Alignment, angle: Float, alphaOf: () -> Float,
) {
    Box(
        Modifier.align(at).padding(24.dp)
            .graphicsLayer { alpha = alphaOf() }
            .rotate(angle)
            .border(3.dp, color, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(text, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) }
}

// ---------- Лайки и корзина ----------

@Composable
fun LikesScreen(vm: SwipeViewModel, onFav: (List<List<Media>>) -> Unit) {
    val pending = vm.pendingFavorites()
    MediaGrid(
        items = vm.liked,
        empty = "Лайков пока нет. Свайпни фото вниз, чтобы лайкнуть.",
        onRemove = { vm.restore(it) },
        header = {
            Column(Modifier.padding(bottom = 8.dp)) {
                Text("Лайки: ${vm.liked.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (pending > 0) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onFav(vm.likeChunks()) }) { Text("Добавить в избранное галереи ($pending)") }
                }
            }
        },
    )
}

@Composable
fun TrashScreen(vm: SwipeViewModel, onTrash: (List<List<Media>>) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    MediaGrid(
        items = vm.trash,
        empty = "Корзина пуста. Свайпни фото влево, чтобы отправить его сюда.",
        onRemove = { vm.restore(it) },
        header = {
            Column(Modifier.padding(bottom = 8.dp)) {
                Text("В корзине: ${vm.trash.size} · ${formatSize(vm.trashSize())}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (vm.trash.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { confirm = true }) { Text("Удалить всё") }
                }
            }
        },
    )
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Переместить в корзину?") },
            text = {
                Text("${vm.trash.size} файлов, освободится около ${formatSize(vm.trashSize())}. Удалённое можно восстановить примерно 30 дней.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onTrash(vm.trashChunks())
                }) { Text("Удалить", color = Red) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun MediaGrid(items: List<Media>, empty: String, onRemove: (Media) -> Unit, header: @Composable () -> Unit) {
    val ctx = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { header() }
        if (items.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
            }
        }
        items(items, key = { it.id }) { m ->
            Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).clickable { openMedia(ctx, m) }) {
                Thumb(m, 360, Modifier.fillMaxSize(), ContentScale.Crop)
                if (m.isVideo) {
                    Text(
                        "▶ ${formatDuration(m.durationMs)}", fontSize = 11.sp, color = Color.White,
                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                            .background(Color(0x99000000), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp).clip(CircleShape)
                        .background(Color(0xAA000000)).clickable { onRemove(m) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            }
        }
    }
}
