package pl.ambif.ambify.features.lists.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.ambif.ambify.core.network.ServerApi
import pl.ambif.ambify.core.network.ServerError
import pl.ambif.ambify.shared.ItemFile
import pl.ambif.ambify.shared.ListItem
import pl.ambif.ambify.shared.Lists

/** A photo or a file added on this phone and not on the server yet ("czeka na wysłanie", board 25). */
@Serializable
data class PendingFile(
    val id: String,
    val itemId: String,
    val listId: String,
    val name: String,
    val mime: String,
    val size: Long,
    val photo: Boolean,
    val addedAt: Long,
) {
    fun asItemFile(me: String) = ItemFile(id, name, mime, size, photo, me, addedAt)
}

/**
 * Photos and files of the lists of one community on this phone (0.7.3, boards 25–26). Added ones wait in the app's
 * files (also offline) and go to the server after a sync, when their item is there; the server's ones are downloaded
 * only when someone opens them and stay in the cache (Android clears it when it needs the room).
 */
class ListFiles(
    private val dir: File,
    private val cache: File,
    private val api: ServerApi,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()
    private val index = File(dir, "pending.json")
    private val _pending = MutableStateFlow(load())
    val pending: StateFlow<List<PendingFile>> = _pending.asStateFlow()
    /** Bytes of the file being sent now, by its id. */
    private val _sending = MutableStateFlow<Pair<String, Long>?>(null)
    val sending: StateFlow<Pair<String, Long>?> = _sending.asStateFlow()

    val hasPending: Boolean get() = _pending.value.isNotEmpty()

    fun pendingOf(itemId: String): List<PendingFile> = _pending.value.filter { it.itemId == itemId }

    /**
     * A photo from the camera or the gallery: turned (as the camera held it), at most [Lists.PHOTO_SIDE] on the longer
     * side, saved again as a JPEG — which leaves out the place and the other data the camera wrote. Null when it is
     * queued, else why not (Polish).
     */
    suspend fun addPhoto(context: Context, uri: Uri, item: ListItem): String? = withContext(Dispatchers.IO) {
        if (photosOf(item) >= Lists.MAX_PHOTOS) return@withContext "Pozycja ma już ${Lists.MAX_PHOTOS} zdjęć — to limit. Usuń któreś, żeby dodać nowe."
        val id = UUID.randomUUID().toString()
        val target = bytes(id)
        try {
            dir.mkdirs()
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val (w, h) = info.size.width to info.size.height
                val longer = maxOf(w, h)
                if (longer > Lists.PHOTO_SIDE) {
                    decoder.setTargetSize(w * Lists.PHOTO_SIDE / longer, h * Lists.PHOTO_SIDE / longer)
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            bitmap.recycle()
        } catch (e: Exception) {
            target.delete()
            return@withContext "Nie udało się odczytać tego zdjęcia."
        }
        if (target.length() > Lists.MAX_FILE_BYTES) {
            target.delete()
            return@withContext "To zdjęcie jest za duże nawet po zmniejszeniu."
        }
        val name = "zdjecie-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(java.util.Date(clock()))}.jpg"
        queue(PendingFile(id, item.id, item.listId, name, Lists.PHOTO_MIME, target.length(), photo = true, addedAt = clock()))
        null
    }

    /** A file (PDF, a document…); a picture picked as a file goes as a photo. Null when queued, else why not. */
    suspend fun addFile(context: Context, uri: Uri, item: ListItem): String? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        if (mime.startsWith("image/") && mime != "image/gif" && mime != "image/svg+xml") return@withContext addPhoto(context, uri, item)
        var name = "plik"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)?.let { name = it }
                if (!cursor.isNull(1)) size = cursor.getLong(1)
            }
        }
        name = Lists.cleanFileName(name)
        if (size > 0) Lists.fileRefusal(name, mime, size)?.let { return@withContext it }
        if (filesOf(item) >= Lists.MAX_FILES) return@withContext "Pozycja ma już ${Lists.MAX_FILES} plików — to limit. Usuń któryś, żeby dodać nowy."
        val id = UUID.randomUUID().toString()
        val target = bytes(id)
        try {
            dir.mkdirs()
            val stream = resolver.openInputStream(uri) ?: return@withContext "Nie udało się otworzyć tego pliku."
            stream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > Lists.MAX_FILE_BYTES) break
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: Exception) {
            target.delete()
            return@withContext "Nie udało się odczytać tego pliku."
        }
        val real = target.length()
        Lists.fileRefusal(name, mime, if (real >= Lists.MAX_FILE_BYTES) maxOf(size, real + 1) else real)?.let {
            target.delete()
            return@withContext it
        }
        queue(PendingFile(id, item.id, item.listId, name, mime, real, photo = false, addedAt = clock()))
        null
    }

    /** Removes a waiting one before it is sent. */
    suspend fun cancel(id: String) = lock.withLock {
        bytes(id).delete()
        save(_pending.value.filterNot { it.id == id })
    }

    /**
     * Sends the waiting ones (call after a sync with nothing waiting in the lists). Never throws: refused ones go away
     * and are told through [tell]; without a network they wait. True when something reached the server.
     */
    suspend fun upload(tell: (String, String) -> Unit): Boolean = lock.withLock {
        var sent = false
        for (file in _pending.value.toList()) {
            val source = bytes(file.id)
            if (!source.exists()) {
                save(_pending.value.filterNot { it.id == file.id })
                continue
            }
            try {
                _sending.value = file.id to 0L
                api.uploadListFile(file.itemId, file.id, file.name, file.mime, source) { _sending.value = file.id to it }
                sent = true
                // Seen once, it opens at once (and offline): the sent bytes are the server's file.
                runCatching { source.copyTo(cached(file.id, file.name), overwrite = true) }
                drop(file)
            } catch (e: ServerError) {
                if (e.code == "network") break
                drop(file)
                tell(if (file.photo) "Nie wysłano zdjęcia" else "Nie wysłano pliku „${file.name}”", e.message)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Something unexpected on this phone: the file stays and is tried again with the next sync.
                android.util.Log.w("ListFiles", "upload failed", e)
                break
            } finally {
                _sending.value = null
            }
        }
        sent
    }

    /** The file on this phone, downloaded first if needed ([onProgress]: bytes so far). Throws [ServerError]. */
    suspend fun open(file: ItemFile, onProgress: (Long) -> Unit = {}): File {
        _pending.value.firstOrNull { it.id == file.id }?.let { return bytes(it.id) }
        val target = cached(file.id, file.name)
        if (target.exists() && target.length() == file.size) return target
        api.downloadListFile(file.id, target, onProgress)
        return target
    }

    /** Already on this phone (opens without a network). */
    fun isHere(file: ItemFile): Boolean =
        _pending.value.any { it.id == file.id } || cached(file.id, file.name).let { it.exists() && it.length() == file.size }

    /** Removes it from the server (needs a network); throws [ServerError]. */
    suspend fun delete(file: ItemFile) {
        if (_pending.value.any { it.id == file.id }) return cancel(file.id)
        api.deleteListFile(file.id)
        cached(file.id, file.name).parentFile?.deleteRecursively()
    }

    private fun photosOf(item: ListItem) = item.files.count { it.photo } + pendingOf(item.id).count { it.photo }
    private fun filesOf(item: ListItem) = item.files.count { !it.photo } + pendingOf(item.id).count { !it.photo }

    /** In its own folder under its real name, so the app that opens it sees "faktura.pdf". */
    private fun cached(id: String, name: String) = File(File(cache, id), Lists.cleanFileName(name))

    private fun bytes(id: String) = File(dir, "$id.bin")

    private suspend fun queue(file: PendingFile) = lock.withLock { save(_pending.value + file) }

    private fun drop(file: PendingFile) {
        bytes(file.id).delete()
        save(_pending.value.filterNot { it.id == file.id })
    }

    private fun load(): List<PendingFile> =
        index.takeIf { it.exists() }?.let { runCatching { json.decodeFromString<List<PendingFile>>(it.readText()) }.getOrNull() }.orEmpty()

    private fun save(files: List<PendingFile>) {
        _pending.value = files
        dir.mkdirs()
        val tmp = File(dir, "pending.json.tmp")
        tmp.writeText(json.encodeToString(files))
        tmp.renameTo(index)
    }

    companion object {
        private const val JPEG_QUALITY = 90
        private const val DIR = "list-uploads"
        const val CACHE = "list-files"
        private val open = mutableMapOf<String, ListFiles>()

        /** One per community for the whole app process. */
        @Synchronized
        fun of(context: Context, api: ServerApi, communityId: String): ListFiles = open.getOrPut(communityId) {
            ListFiles(File(File(context.filesDir, DIR), communityId), File(File(context.cacheDir, CACHE), communityId), api.forCommunity(communityId))
        }

        /** Signing out: waiting and downloaded files go away. */
        @Synchronized
        fun clear(context: Context) {
            open.clear()
            File(context.filesDir, DIR).deleteRecursively()
            File(context.cacheDir, CACHE).deleteRecursively()
        }
    }
}
