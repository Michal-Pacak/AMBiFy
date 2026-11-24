package pl.ambif.ambify.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pl.ambif.ambify.core.network.ServerApi

/**
 * Profile photos on the phone (0.7.6): the small ones of the people on screen and, after a tap, the big one.
 * A photo's path changes with every new photo, so a kept copy is never out of date: memory first, then the
 * phone's cache folder (Android clears it when short of space), then the server.
 */
object AvatarImages {
    private var api: ServerApi? = null
    private var dir: File? = null
    private val memory = LruCache<String, ImageBitmap>(80)
    private val lock = Mutex()

    /** Photos hidden from this phone a moment ago (before the next data from the server). */
    private val _hidden = MutableStateFlow<Set<String>>(emptySet())
    val hidden: StateFlow<Set<String>> = _hidden

    fun init(context: Context, api: ServerApi) {
        this.api = api
        dir = File(context.cacheDir, "avatars")
    }

    fun hide(path: String) {
        _hidden.value = _hidden.value + path
    }

    /** The owner of a photo path ("/people/<user>/photo/<id>"). */
    fun ownerOf(path: String): String? = path.split('/').getOrNull(2)

    fun cached(path: String, small: Boolean): ImageBitmap? = memory.get(key(path, small))

    /** The photo, or null without a connection (then the initials stay). */
    suspend fun load(path: String, small: Boolean): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = key(path, small)
        memory.get(key)?.let { return@withContext it }
        lock.withLock {
            memory.get(key)?.let { return@withLock it }
            val folder = dir ?: return@withLock null
            val file = File(folder, key.replace('/', '_').trimStart('_') + ".jpg")
            if (!file.isFile) {
                val api = api ?: return@withLock null
                folder.mkdirs()
                val part = File(folder, file.name + ".part")
                val ok = runCatching { api.downloadPhoto(path, small, part) }.isSuccess
                if (!ok || !part.renameTo(file)) {
                    part.delete()
                    return@withLock null
                }
            }
            val image = BitmapFactory.decodeFile(file.path)?.asImageBitmap() ?: return@withLock null.also { file.delete() }
            memory.put(key, image)
            image
        }
    }

    private fun key(path: String, small: Boolean) = if (small) "$path-s" else path
}

/** A person's photo for drawing: null until it is here (or when it cannot come). */
@Composable
fun rememberAvatarImage(path: String?, small: Boolean = true): ImageBitmap? {
    var image by remember(path, small) { mutableStateOf(path?.let { AvatarImages.cached(it, small) }) }
    LaunchedEffect(path, small) {
        if (path != null && image == null) image = AvatarImages.load(path, small)
    }
    return image
}
