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
package com.kunzisoft.keepass.database.action.node

import android.content.Context
import com.kunzisoft.keepass.database.element.Database
import com.kunzisoft.keepass.database.element.EntryVersioned
import com.kunzisoft.keepass.database.element.NodeVersioned

class UpdateEntryRunnable constructor(
        context: Context,
        database: Database,
        private val mOldEntry: EntryVersioned,
        private val mNewEntry: EntryVersioned,
        save: Boolean,
        finishRunnable: AfterActionNodeFinishRunnable?)
    : ActionNodeDatabaseRunnable(context, database, finishRunnable, save) {

    override fun nodeAction() {
        // Update entry with new values
        mNewEntry.touch(modified = true, touchParents = true)

        // Create an entry history (an entry history don't have history)
        mNewEntry.addEntryToHistory(EntryVersioned(mOldEntry, copyHistory = false))

        database.removeOldestHistory(mNewEntry)

        // Only change data un index
        database.updateEntry(mNewEntry)
    }

    override fun nodeFinish(result: Result): ActionNodeValues {
        if (!result.isSuccess) {
            // If we fail to save, back out changes to global structure
            database.updateEntry(mOldEntry)
        }

        val oldNodesReturn = ArrayList<NodeVersioned>()
        oldNodesReturn.add(mOldEntry)
        val newNodesReturn = ArrayList<NodeVersioned>()
        newNodesReturn.add(mNewEntry)
        return ActionNodeValues(result, oldNodesReturn, newNodesReturn)
    }
}
