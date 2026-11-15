package ru.azimut.app.core.sync

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import ru.azimut.app.core.platform.AndroidApp
import java.io.File

/**
 * SAF document-tree storage. The config string is the tree URI returned by the
 * ACTION_OPEN_DOCUMENT_TREE picker (persisted permission must be taken by the UI).
 */
actual class LocalFolderStorage actual constructor(private val config: String) : RemoteStorage {
    private val treeUri: Uri = Uri.parse(config)
    private val context: Context = AndroidApp.context
    override val name: String
        get() = "SAF: $config"

    private fun treeDocUri(): Uri = DocumentsContract.buildDocumentUriUsingTree(
        treeUri,
        DocumentsContract.getTreeDocumentId(treeUri),
    )

    private fun childUri(docUri: Uri, path: String): Uri {
        // Path components are resolved one by one; missing intermediate folders are
        // created as directories (MIME_TYPE_DIR), only the last segment is a file.
        var current = docUri
        val segments = path.split('/').filter { it.isNotBlank() }
        for ((idx, segment) in segments.withIndex()) {
            val next = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getDocumentId(current) + "/" + segment,
            )
            current = if (exists(next)) next else create(next, segment, dir = idx < segments.lastIndex)
        }
        return current
    }

    private fun exists(docUri: Uri): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(docUri, "r")?.use { it.statSize >= 0 } ?: false
    }.getOrDefault(false)

    private fun create(docUri: Uri, displayName: String, dir: Boolean): Uri = runCatching {
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getDocumentId(docUri).substringBeforeLast('/'),
        )
        val mime = if (dir) DocumentsContract.Document.MIME_TYPE_DIR else "application/octet-stream"
        DocumentsContract.createDocument(context.contentResolver, parent, mime, displayName)
            ?: docUri
    }.getOrDefault(docUri)

    override suspend fun read(relativePath: String): ByteArray? = runCatching {
        context.contentResolver.openInputStream(childUri(treeDocUri(), relativePath))?.use { it.readBytes() }
    }.getOrNull()

    override suspend fun write(relativePath: String, bytes: ByteArray): Boolean = runCatching {
        val uri = childUri(treeDocUri(), relativePath)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: return false
        true
    }.getOrDefault(false)

    override suspend fun delete(relativePath: String): Boolean = runCatching {
        DocumentsContract.deleteDocument(context.contentResolver, childUri(treeDocUri(), relativePath))
    }.getOrDefault(false)

    override suspend fun listAll(): List<SyncEntry> = runCatching {
        val entries = mutableListOf<SyncEntry>()
        fun walk(docUri: Uri, prefix: String) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getDocumentId(docUri),
            )
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                ),
                null, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1)
                    val mime = cursor.getString(2)
                    val size = cursor.getLong(3)
                    val mtime = cursor.getLong(4)
                    val docId = cursor.getString(0)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        walk(DocumentsContract.buildDocumentUriUsingTree(treeUri, docId), "$prefix$name/")
                    } else {
                        entries.add(SyncEntry("$prefix$name", size, mtime))
                    }
                }
            }
        }
        walk(treeDocUri(), "")
        entries
    }.getOrDefault(emptyList())
}
