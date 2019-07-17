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
 */

package com.kunzisoft.keepass.database.element

import com.kunzisoft.keepass.crypto.finalkey.FinalKeyFactory
import com.kunzisoft.keepass.database.exception.InvalidKeyFileException
import com.kunzisoft.keepass.stream.NullOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*

/**
 * @author Naomaru Itoi <nao></nao>@phoneid.org>
 * @author Bill Zwicky <wrzwicky></wrzwicky>@pobox.com>
 * @author Dominik Reichl <dominik.reichl></dominik.reichl>@t-online.de>
 */
class PwDatabaseV3 : PwDatabase<PwGroupV3, PwEntryV3>() {

    private var numKeyEncRounds: Int = 0

    override val version: String
        get() = "KeePass 1"

    override val availableEncryptionAlgorithms: List<PwEncryptionAlgorithm>
        get() {
            val list = ArrayList<PwEncryptionAlgorithm>()
            list.add(PwEncryptionAlgorithm.AESRijndael)
            return list
        }

    val rootGroups: List<PwGroupV3>
        get() {
            val kids = ArrayList<PwGroupV3>()
            for ((_, value) in groupIndexes) {
                if (value.level == 0)
                    kids.add(value)
            }
            return kids
        }

    override val passwordEncoding: String
        get() = "ISO-8859-1"


    override var numberKeyEncryptionRounds: Long
        get() = numKeyEncRounds.toLong()
        @Throws(NumberFormatException::class)
        set(rounds) {
            if (rounds > Integer.MAX_VALUE || rounds < Integer.MIN_VALUE) {
                throw NumberFormatException()
            }
            numKeyEncRounds = rounds.toInt()
        }

    init {
        algorithm = PwEncryptionAlgorithm.AESRijndael
        numKeyEncRounds = DEFAULT_ENCRYPTION_ROUNDS
    }

    private fun assignGroupsChildren(parent: PwGroupV3) {
        val levelToCheck = parent.level + 1
        var startFromParentPosition = false
        for (groupToCheck in getGroupIndexes()) {
            rootGroup?.let { root ->
                if (root.nodeId == parent.nodeId || groupToCheck.nodeId == parent.nodeId) {
                    startFromParentPosition = true
                }
            }
            if (startFromParentPosition) {
                if (groupToCheck.level < levelToCheck)
                    break
                else if (groupToCheck.level == levelToCheck)
                    parent.addChildGroup(groupToCheck)
            }
        }
    }

    private fun assignEntriesChildren(parent: PwGroupV3) {
        for (entry in getEntryIndexes()) {
            if (entry.parent!!.nodeId == parent.nodeId)
                parent.addChildEntry(entry)
        }
    }

    private fun constructTreeFromIndex(currentGroup: PwGroupV3) {

        assignGroupsChildren(currentGroup)
        assignEntriesChildren(currentGroup)

        // set parent in child entries (normally useless but to be sure or to update parent metadata)
        for (childEntry in currentGroup.getChildEntries()) {
            childEntry.parent = currentGroup
        }
        // recursively construct child groups
        for (childGroup in currentGroup.getChildGroups()) {
            childGroup.parent = currentGroup
            constructTreeFromIndex(childGroup)
        }
    }

    fun constructTreeFromIndex() {
        rootGroup?.let {
            constructTreeFromIndex(it)
        }
    }

    /**
     * Generates an unused random tree id
     *
     * @return new tree id
     */
    override fun newGroupId(): PwNodeIdInt {
        var newId: PwNodeIdInt
        do {
            newId = PwNodeIdInt()
        } while (isGroupIdUsed(newId))

        return newId
    }

    /**
     * Generates an unused random tree id
     *
     * @return new tree id
     */
    override fun newEntryId(): PwNodeIdUUID {
        var newId: PwNodeIdUUID
        do {
            newId = PwNodeIdUUID()
        } while (isEntryIdUsed(newId))

        return newId
    }

    @Throws(InvalidKeyFileException::class, IOException::class)
    public override fun getMasterKey(key: String?, keyInputStream: InputStream?): ByteArray {

        return if (key != null && keyInputStream != null) {
            getCompositeKey(key, keyInputStream)
        } else key?.let { // key.length() >= 0
            getPasswordKey(it)
        } ?: (keyInputStream?.let { // key == null
            getFileKey(it)
        } ?: throw IllegalArgumentException("Key cannot be empty."))
    }

    @Throws(IOException::class)
    fun makeFinalKey(masterSeed: ByteArray, masterSeed2: ByteArray, numRounds: Long) {

        // Write checksum Checksum
        val md: MessageDigest
        try {
            md = MessageDigest.getInstance("SHA-256")
        } catch (e: NoSuchAlgorithmException) {
            throw IOException("SHA-256 not implemented here.")
        }

        val nos = NullOutputStream()
        val dos = DigestOutputStream(nos, md)

        val transformedMasterKey = transformMasterKey(masterSeed2, masterKey, numRounds)
        dos.write(masterSeed)
        dos.write(transformedMasterKey)

        finalKey = md.digest()
    }

    override fun loadXmlKeyFile(keyInputStream: InputStream): ByteArray? {
        return null
    }

    override fun createGroup(): PwGroupV3 {
        return PwGroupV3()
    }

    override fun createEntry(): PwEntryV3 {
        return PwEntryV3()
    }

    override fun isBackup(group: PwGroupV3): Boolean {
        var currentGroup: PwGroupV3? = group
        while (currentGroup != null) {
            if (currentGroup.level == 0 && currentGroup.title.equals("Backup", ignoreCase = true)) {
                return true
            }
            currentGroup = currentGroup.parent
        }
        return false
    }

    override fun isGroupSearchable(group: PwGroupV3?, omitBackup: Boolean): Boolean {
        return if (!super.isGroupSearchable(group, omitBackup)) {
            false
        } else !(omitBackup && isBackup(group!!))
    }

    companion object {

        private const val DEFAULT_ENCRYPTION_ROUNDS = 300

        /**
         * Encrypt the master key a few times to make brute-force key-search harder
         * @throws IOException
         */
        @Throws(IOException::class)
        private fun transformMasterKey(pKeySeed: ByteArray, pKey: ByteArray, rounds: Long): ByteArray {
            val key = FinalKeyFactory.createFinalKey()

            return key.transformMasterKey(pKeySeed, pKey, rounds)
        }
    }
}
