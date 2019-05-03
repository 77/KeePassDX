/*
 * Copyright 2019 Jeremy Jamet / Kunzisoft.
 *
 * This file is part of KeePass DX.
 *
 *  KeePass DX is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  KeePass DX is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with KeePass DX.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.kunzisoft.keepass.database.element

import android.content.Context
import android.content.res.Resources
import android.database.Cursor
import android.net.Uri
import android.util.Log
import android.webkit.URLUtil
import com.kunzisoft.keepass.crypto.keyDerivation.KdfEngine
import com.kunzisoft.keepass.crypto.keyDerivation.KdfFactory
import com.kunzisoft.keepass.database.NodeHandler
import com.kunzisoft.keepass.database.cursor.EntryCursorV3
import com.kunzisoft.keepass.database.cursor.EntryCursorV4
import com.kunzisoft.keepass.database.exception.*
import com.kunzisoft.keepass.database.load.ImporterV3
import com.kunzisoft.keepass.database.load.ImporterV4
import com.kunzisoft.keepass.database.save.PwDbV3Output
import com.kunzisoft.keepass.database.save.PwDbV4Output
import com.kunzisoft.keepass.database.search.SearchDbHelper
import com.kunzisoft.keepass.icons.IconDrawableFactory
import com.kunzisoft.keepass.stream.LEDataInputStream
import com.kunzisoft.keepass.tasks.ProgressTaskUpdater
import com.kunzisoft.keepass.utils.EmptyUtils
import com.kunzisoft.keepass.utils.UriUtil
import org.apache.commons.io.FileUtils
import java.io.*
import java.util.*


class Database {

    // To keep a reference for specific methods provided by version
    private var pwDatabaseV3: PwDatabaseV3? = null
    private var pwDatabaseV4: PwDatabaseV4? = null

    private var mUri: Uri? = null
    private var searchHelper: SearchDbHelper? = null
    var isReadOnly = false
    var isPasswordEncodingError = false
        private set

    val drawFactory = IconDrawableFactory()

    var loaded = false

    val iconFactory: PwIconFactory?
        get() {
            return pwDatabaseV3?.getIconFactory() ?: pwDatabaseV4?.getIconFactory()
        }

    val name: String
        get() {
            return pwDatabaseV4?.name ?: ""
        }

    val description: String
        get() {
            return pwDatabaseV4?.description ?: ""
        }

    var defaultUsername: String
        get() {
            return pwDatabaseV4?.defaultUserName ?: ""
        }
        set(username) {
            pwDatabaseV4?.defaultUserName = username
            pwDatabaseV4?.defaultUserNameChanged = PwDate()
        }

    val encryptionAlgorithm: PwEncryptionAlgorithm?
        get() {
            return pwDatabaseV4?.encryptionAlgorithm
        }

    val availableEncryptionAlgorithms: List<PwEncryptionAlgorithm>
        get() = pwDatabaseV3?.availableEncryptionAlgorithms ?: pwDatabaseV4?.availableEncryptionAlgorithms ?: ArrayList()

    val availableKdfEngines: List<KdfEngine>
        get() {
            if (pwDatabaseV3 != null) {
                return KdfFactory.kdfListV3
            }
            if (pwDatabaseV4 != null) {
                return KdfFactory.kdfListV4
            }
            return ArrayList()
        }

    val kdfEngine: KdfEngine?
        get() {
            return pwDatabaseV4?.kdfEngine ?: return KdfFactory.aesKdf
        }

    val numberKeyEncryptionRoundsAsString: String
        get() = java.lang.Long.toString(numberKeyEncryptionRounds)

    var numberKeyEncryptionRounds: Long
        get() = pwDatabaseV3?.numberKeyEncryptionRounds ?: pwDatabaseV4?.numberKeyEncryptionRounds ?: 0
        @Throws(NumberFormatException::class)
        set(numberRounds) {
            pwDatabaseV3?.numberKeyEncryptionRounds = numberRounds
            pwDatabaseV4?.numberKeyEncryptionRounds = numberRounds
        }

    val memoryUsageAsString: String
        get() = java.lang.Long.toString(memoryUsage)

    var memoryUsage: Long
        get() {
            return pwDatabaseV4?.memoryUsage ?: return KdfEngine.UNKNOW_VALUE.toLong()
        }
        set(memory) {
            pwDatabaseV4?.memoryUsage = memory
        }

    val parallelismAsString: String
        get() = Integer.toString(parallelism)

    var parallelism: Int
        get() = pwDatabaseV4?.parallelism ?: KdfEngine.UNKNOW_VALUE
        set(parallelism) {
            pwDatabaseV4?.parallelism = parallelism
        }

    var masterKey: ByteArray
        get() = pwDatabaseV3?.getMasterKey() ?: pwDatabaseV4?.getMasterKey() ?: ByteArray(32)
        set(masterKey) {
            pwDatabaseV3?.masterKey = masterKey
            pwDatabaseV4?.masterKey = masterKey
        }

    val rootGroup: GroupVersioned?
        get() {
            pwDatabaseV3?.let {
                return GroupVersioned(it.rootGroup)
            }
            pwDatabaseV4?.let {
                return GroupVersioned(it.rootGroup)
            }
            return null
        }

    /**
     * Determine if RecycleBin is available or not for this version of database
     * @return true if RecycleBin available
     */
    val isRecycleBinAvailable: Boolean
        get() = pwDatabaseV4 != null

    val isRecycleBinEnabled: Boolean
        get() = if (pwDatabaseV4 != null) {
            pwDatabaseV4!!.isRecycleBinEnabled
        } else false

    val recycleBin: GroupVersioned?
        get() = if (pwDatabaseV4 != null) {
            GroupVersioned(pwDatabaseV4!!.recycleBin!!)
        } else null

    constructor() {}

    constructor(databasePath: String) {
        // TODO Test with kdb extension
        if (isKDBExtension(databasePath)) {
            setDatabaseV3(PwDatabaseV3())
        } else {
            val groupV4 = PwGroupV4()
            setDatabaseV4(PwDatabaseV4())

            groupV4.title = dbNameFromPath(databasePath)
            groupV4.setIconStandard(pwDatabaseV4!!.getIconFactory().folderIcon)
            this.pwDatabaseV4!!.rootGroup = groupV4
        }
    }

    private fun setDatabaseV3(pwDatabaseV3: PwDatabaseV3) {
        this.pwDatabaseV3 = pwDatabaseV3
        this.pwDatabaseV4 = null
    }

    private fun setDatabaseV4(pwDatabaseV4: PwDatabaseV4) {
        this.pwDatabaseV3 = null
        this.pwDatabaseV4 = pwDatabaseV4
    }

    private fun isKDBExtension(filename: String?): Boolean {
        if (filename == null) {
            return false
        }
        val extIdx = filename.lastIndexOf(".")
        return if (extIdx == -1) false else filename.substring(extIdx).equals(".kdb", ignoreCase = true)
    }

    private fun dbNameFromPath(dbPath: String): String {
        val filename = URLUtil.guessFileName(dbPath, null, null)
        if (EmptyUtils.isNullOrEmpty(filename)) {
            return "KeePass Database"
        }
        val lastExtDot = filename.lastIndexOf(".")
        return if (lastExtDot == -1) {
            filename
        } else filename.substring(0, lastExtDot)
    }

    fun setUri(mUri: Uri) {
        this.mUri = mUri
    }

    @Throws(IOException::class, InvalidDBException::class)
    fun loadData(ctx: Context, uri: Uri, password: String?, keyfile: Uri?, progressTaskUpdater: ProgressTaskUpdater) {

        mUri = uri
        isReadOnly = false
        if (uri.scheme == "file") {
            val file = File(uri.path!!)
            isReadOnly = !file.canWrite()
        }

        // Pass Uris as InputStreams
        val inputStream: InputStream
        try {
            inputStream = UriUtil.getUriInputStream(ctx, uri)
        } catch (e: Exception) {
            Log.e("KPD", "Database::loadData", e)
            throw ContentFileNotFoundException.getInstance(uri)
        }

        // Pass KeyFile Uri as InputStreams
        var keyFileInputStream: InputStream? = null
        keyfile?.let {
            try {
                keyFileInputStream = UriUtil.getUriInputStream(ctx, keyfile)
            } catch (e: Exception) {
                Log.e("KPD", "Database::loadData", e)
                throw ContentFileNotFoundException.getInstance(keyfile)
            }
        }

        // Load Data

        val bufferedInputStream = BufferedInputStream(inputStream)
        if (!bufferedInputStream.markSupported()) {
            throw IOException("Input stream does not support mark.")
        }

        // We'll end up reading 8 bytes to identify the header. Might as well use two extra.
        bufferedInputStream.mark(10)

        // Get the file directory to save the attachments
        val sig1 = LEDataInputStream.readInt(bufferedInputStream)
        val sig2 = LEDataInputStream.readInt(bufferedInputStream)

        // Return to the start
        bufferedInputStream.reset()
        when {
            // Header of database V3
            PwDbHeaderV3.matchesHeader(sig1, sig2) -> setDatabaseV3(ImporterV3().openDatabase(bufferedInputStream,
                    password,
                    keyFileInputStream,
                    progressTaskUpdater))

            // Header of database V4
            PwDbHeaderV4.matchesHeader(sig1, sig2) -> setDatabaseV4(ImporterV4(ctx.filesDir).openDatabase(bufferedInputStream,
                    password,
                    keyFileInputStream,
                    progressTaskUpdater))

            // Header not recognized
            else -> throw InvalidDBSignatureException()
        }

        try {
            isPasswordEncodingError = !(pwDatabaseV3?.validatePasswordEncoding(password) ?: pwDatabaseV4?.validatePasswordEncoding(password) ?: true)
            searchHelper = SearchDbHelper(ctx)
            loaded = true
        } catch (e: Exception) {
            Log.e(TAG, "Load can't be performed with this Database version", e)
            loaded = false
        }
    }

    fun isGroupSearchable(group: GroupVersioned, isOmitBackup: Boolean): Boolean {
        return pwDatabaseV3?.isGroupSearchable(group.pwGroupV3, isOmitBackup) ?:
        pwDatabaseV4?.isGroupSearchable(group.pwGroupV4, isOmitBackup) ?:
        false
    }

    @JvmOverloads
    fun search(str: String, max: Int = Integer.MAX_VALUE): GroupVersioned? {
        return if (searchHelper == null) null else searchHelper?.search(this, str, max)
    }

    fun searchEntry(query: String): Cursor? {
        if (pwDatabaseV3 != null) {
            val cursorV3 = EntryCursorV3()
            if (query.isNotEmpty()) {
                val searchResult = search(query, 6)
                if (searchResult != null) {
                    for (entry in searchResult.getChildEntries()) {
                        if (!entry.isMetaStream) { // TODO metastream
                            cursorV3.addEntry(entry.pwEntryV3)
                        }
                    }
                }
            }
            return cursorV3
        }

        if (pwDatabaseV4 != null) {
            val cursorV4 = EntryCursorV4()
            if (query.isNotEmpty()) {
                val searchResult = search(query, 6)
                if (searchResult != null) {
                    for (entry in searchResult.getChildEntries()) {
                        if (!entry.isMetaStream) { // TODO metastream
                            cursorV4.addEntry(entry.pwEntryV4)
                        }
                    }
                }
            }
            return cursorV4
        }
        return null
    }

    fun getEntryFrom(cursor: Cursor): EntryVersioned? {
        val iconFactory = pwDatabaseV3?.getIconFactory() ?: pwDatabaseV4?.getIconFactory()
        val entry = createEntry()

        // TODO invert field reference manager
        entry?.let { entryVersioned ->
            startManageEntry(entryVersioned)
            pwDatabaseV3?.let { (cursor as EntryCursorV3).populateEntry(entryVersioned.pwEntryV3, iconFactory) }
            pwDatabaseV4?.let { (cursor as EntryCursorV4).populateEntry(entryVersioned.pwEntryV4, iconFactory) }
            stopManageEntry(entryVersioned)
        }

        return entry
    }

    @Throws(IOException::class, PwDbOutputException::class)
    fun saveData(ctx: Context) {
        saveData(ctx, mUri!!)
    }

    @Throws(IOException::class, PwDbOutputException::class)
    private fun saveData(ctx: Context, uri: Uri) {
        val errorMessage = "Failed to store database."

        if (uri.scheme == "file") {
            val filename = uri.path
            val tempFile = File(filename!! + ".tmp")

            var fileOutputStream: FileOutputStream? = null
            try {
                fileOutputStream = FileOutputStream(tempFile)
                val pmo =
                        pwDatabaseV3?.let { PwDbV3Output(it, fileOutputStream) }
                        ?: pwDatabaseV4?.let { PwDbV4Output(it, fileOutputStream) }
                pmo?.output()
            } catch (e: Exception) {
                Log.e(TAG, errorMessage, e)
                throw IOException(errorMessage, e)
            } finally {
                fileOutputStream?.close()
            }

            // Force data to disk before continuing
            try {
                fileOutputStream!!.fd.sync()
            } catch (e: SyncFailedException) {
                // Ignore if fsync fails. We tried.
            }

            val orig = File(filename)

            if (!tempFile.renameTo(orig)) {
                throw IOException(errorMessage)
            }
        } else {
            var outputStream: OutputStream? = null
            try {
                outputStream = ctx.contentResolver.openOutputStream(uri)
                val pmo =
                        pwDatabaseV3?.let { PwDbV3Output(it, outputStream) }
                        ?: pwDatabaseV4?.let { PwDbV4Output(it, outputStream) }
                pmo?.output()
            } catch (e: Exception) {
                Log.e(TAG, errorMessage, e)
                throw IOException(errorMessage, e)
            } finally {
                outputStream?.close()
            }
        }
        mUri = uri
    }

    // TODO Clear database when lock broadcast is receive in backstage
    fun closeAndClear(context: Context) {
        drawFactory.clearCache()
        // Delete the cache of the database if present
        if (pwDatabaseV4 != null)
            pwDatabaseV4!!.clearCache()
        // In all cases, delete all the files in the temp dir
        try {
            FileUtils.cleanDirectory(context.filesDir)
        } catch (e: IOException) {
            Log.e(TAG, "Unable to clear the directory cache.", e)
        }

        pwDatabaseV4 = null
        mUri = null
        loaded = false
        isPasswordEncodingError = false
    }

    fun getVersion(): String {
        return pwDatabaseV3?.version?.toString() ?: pwDatabaseV4?.version?.toString() ?: PwVersion.UNKNOWN.toString()
    }

    fun containsName(): Boolean {
        pwDatabaseV4?.let { return true }
        return false
    }

    fun assignName(name: String) {
        pwDatabaseV4?.name = name
        pwDatabaseV4?.nameChanged = PwDate()
    }

    fun containsDescription(): Boolean {
        pwDatabaseV4?.let { return true }
        return false
    }

    fun assignDescription(description: String) {
        pwDatabaseV4?.description = description
        pwDatabaseV4?.descriptionChanged = PwDate()
    }

    fun allowEncryptionAlgorithmModification(): Boolean {
        return availableEncryptionAlgorithms.size > 1
    }

    fun assignEncryptionAlgorithm(algorithm: PwEncryptionAlgorithm) {
            pwDatabaseV4?.encryptionAlgorithm = algorithm
            pwDatabaseV4?.setDataEngine(algorithm.cipherEngine)
            pwDatabaseV4?.dataCipher = algorithm.dataCipher
    }

    fun getEncryptionAlgorithmName(resources: Resources): String {
        return pwDatabaseV3?.encryptionAlgorithm?.getName(resources) ?: pwDatabaseV4?.encryptionAlgorithm?.getName(resources) ?: ""
    }

    fun allowKdfModification(): Boolean {
        return availableKdfEngines.size > 1
    }

    fun assignKdfEngine(kdfEngine: KdfEngine) {
        if (pwDatabaseV4?.kdfParameters?.uuid != kdfEngine.defaultParameters.uuid)
            pwDatabaseV4?.kdfParameters = kdfEngine.defaultParameters
        numberKeyEncryptionRounds = kdfEngine.defaultKeyRounds
        memoryUsage = kdfEngine.defaultMemoryUsage
        parallelism = kdfEngine.defaultParallelism
    }

    fun getKeyDerivationName(resources: Resources): String {
        val kdfEngine = kdfEngine
        return if (kdfEngine != null) {
            kdfEngine.getName(resources)
        } else ""
    }

    fun validatePasswordEncoding(key: String): Boolean {
        return pwDatabaseV3?.validatePasswordEncoding(key) ?: pwDatabaseV4?.validatePasswordEncoding(key) ?: false
    }

    @Throws(InvalidKeyFileException::class, IOException::class)
    fun retrieveMasterKey(key: String, keyInputStream: InputStream) {
        pwDatabaseV3?.retrieveMasterKey(key, keyInputStream)
        pwDatabaseV4?.retrieveMasterKey(key, keyInputStream)
    }

    fun createEntry(): EntryVersioned? {

        pwDatabaseV3?.let { database ->
            return EntryVersioned(PwEntryV3()).apply {
                database.newEntryId()?.let {
                    nodeId = it
                }
            }
        }


        pwDatabaseV4?.let { database ->
            return EntryVersioned(PwEntryV4()).apply {
                database.newEntryId()?.let {
                    nodeId = it
                }
            }
        }

        return null
    }

    fun createGroup(): GroupVersioned? {

        pwDatabaseV3?.let { database ->
            return GroupVersioned(PwGroupV3()).apply {
                database.newGroupId()?.let {
                    setNodeId(it)
                }
            }
        }


        pwDatabaseV4?.let { database ->
            return GroupVersioned(PwGroupV4()).apply {
                database.newGroupId()?.let {
                    setNodeId(it)
                }
            }
        }

        return null
    }

    fun getEntryById(id: PwNodeId<*>): EntryVersioned? {
        pwDatabaseV3?.getEntryById(id)?.let {
            return EntryVersioned(it)
        }
        pwDatabaseV4?.getEntryById(id)?.let {
            return EntryVersioned(it)
        }
        return null
    }

    fun getGroupById(id: PwNodeId<*>): GroupVersioned? {
        pwDatabaseV3?.getGroupById(id)?.let {
            return GroupVersioned(it)
        }

        pwDatabaseV4?.getGroupById(id)?.let {
            return GroupVersioned(it)
        }

        return null
    }

    fun addEntryTo(entry: EntryVersioned, parent: GroupVersioned) {
        pwDatabaseV3?.addEntryTo(entry.pwEntryV3, parent.pwGroupV3)
        pwDatabaseV4?.addEntryTo(entry.pwEntryV4, parent.pwGroupV4)
    }

    fun removeEntryFrom(entry: EntryVersioned, parent: GroupVersioned) {
        pwDatabaseV3?.removeEntryFrom(entry.pwEntryV3, parent.pwGroupV3)
        pwDatabaseV4?.removeEntryFrom(entry.pwEntryV4, parent.pwGroupV4)
    }

    fun addGroupTo(group: GroupVersioned, parent: GroupVersioned) {
        pwDatabaseV3?.addGroupTo(group.pwGroupV3, parent.pwGroupV3)
        pwDatabaseV4?.addGroupTo(group.pwGroupV4, parent.pwGroupV4)
    }

    fun removeGroupFrom(group: GroupVersioned, parent: GroupVersioned) {
        pwDatabaseV3?.removeGroupFrom(group.pwGroupV3, parent.pwGroupV3)
        pwDatabaseV4?.removeGroupFrom(group.pwGroupV4, parent.pwGroupV4)
    }

    /**
     * @return A duplicate entry with the same values, a new UUID,
     * @param entryToCopy
     * @param newParent
     */
    fun copyEntry(entryToCopy: EntryVersioned, newParent: GroupVersioned): EntryVersioned? {
        try {
            val entryCopied = EntryVersioned(entryToCopy)
            entryCopied.nodeId = PwNodeIdUUID()
            entryCopied.parent = newParent
            addEntryTo(entryCopied, newParent)
            return entryCopied
        } catch (e: Exception) {
            Log.e(TAG, "This version of PwEntry can't be updated", e)
        }

        return null
    }

    fun moveEntry(entryToMove: EntryVersioned, newParent: GroupVersioned) {
        entryToMove.parent?.let {
            removeEntryFrom(entryToMove, it)
        }
        addEntryTo(entryToMove, newParent)
    }

    fun moveGroup(groupToMove: GroupVersioned, newParent: GroupVersioned) {
        groupToMove.parent?.let {
            removeGroupFrom(groupToMove, it)
        }
        addGroupTo(groupToMove, newParent)
    }

    fun deleteEntry(entry: EntryVersioned) {
        entry.parent?.let {
            removeEntryFrom(entry, it)
        }
    }

    fun deleteGroup(group: GroupVersioned) {
        group.doForEachChildAndForIt(
                object : NodeHandler<EntryVersioned>() {
                    override fun operate(entry: EntryVersioned): Boolean {
                        deleteEntry(entry)
                        return true
                    }
                },
                object : NodeHandler<GroupVersioned>() {
                    override fun operate(group: GroupVersioned): Boolean {
                        group.parent?.let {
                            removeGroupFrom(group, it)
                        }
                        return true
                    }
                })
    }

    fun undoDeleteEntry(entry: EntryVersioned, parent: GroupVersioned) {
        pwDatabaseV3?.undoDeleteEntryFrom(entry.pwEntryV3, parent.pwGroupV3)
        pwDatabaseV4?.undoDeleteEntryFrom(entry.pwEntryV4, parent.pwGroupV4)
    }

    fun undoDeleteGroup(group: GroupVersioned, parent: GroupVersioned) {
        pwDatabaseV3?.undoDeleteGroupFrom(group.pwGroupV3, parent.pwGroupV3)
        pwDatabaseV4?.undoDeleteGroupFrom(group.pwGroupV4, parent.pwGroupV4)
    }

    fun canRecycle(entry: EntryVersioned): Boolean {
        return pwDatabaseV4?.canRecycle(entry.pwEntryV4) ?: false
    }

    fun canRecycle(group: GroupVersioned): Boolean {
        return pwDatabaseV4?.canRecycle(group.pwGroupV4) ?: false
    }

    fun recycle(entry: EntryVersioned) {
        pwDatabaseV4?.recycle(entry.pwEntryV4)
    }

    fun recycle(group: GroupVersioned) {
        pwDatabaseV4?.recycle(group.pwGroupV4)
    }

    fun undoRecycle(entry: EntryVersioned, parent: GroupVersioned) {
        pwDatabaseV4?.undoRecycle(entry.pwEntryV4, parent.pwGroupV4)
    }

    fun undoRecycle(group: GroupVersioned, parent: GroupVersioned) {
        pwDatabaseV4?.undoRecycle(group.pwGroupV4, parent.pwGroupV4)
    }

    fun startManageEntry(entry: EntryVersioned) {
        pwDatabaseV4?.let {
            entry.startToManageFieldReferences(it)
        }
    }

    fun stopManageEntry(entry: EntryVersioned) {
        pwDatabaseV4?.let {
            entry.stopToManageFieldReferences()
        }
    }

    fun createBackupOf(entry: EntryVersioned) {
        pwDatabaseV4?.let {
            entry.createBackup(it)
        }
    }

    companion object {

        private val TAG = Database::class.java.name
    }
}
