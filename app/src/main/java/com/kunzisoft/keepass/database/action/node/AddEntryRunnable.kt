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

import androidx.fragment.app.FragmentActivity
import com.kunzisoft.keepass.database.element.Database
import com.kunzisoft.keepass.database.element.EntryVersioned
import com.kunzisoft.keepass.database.element.GroupVersioned
import com.kunzisoft.keepass.database.element.NodeVersioned

class AddEntryRunnable constructor(
        context: FragmentActivity,
        database: Database,
        private val mNewEntry: EntryVersioned,
        private val mParent: GroupVersioned,
        finishRunnable: AfterActionNodeFinishRunnable?,
        save: Boolean)
    : ActionNodeDatabaseRunnable(context, database, finishRunnable, save) {

    override fun nodeAction() {
        mNewEntry.touch(modified = true, touchParents = true)
        mParent.touch(modified = true, touchParents = true)
        database.addEntryTo(mNewEntry, mParent)
    }

    override fun nodeFinish(result: Result): ActionNodeValues {
        if (!result.isSuccess) {
            mNewEntry.parent?.let {
                database.removeEntryFrom(mNewEntry, it)
            }
        }

        val oldNodesReturn = ArrayList<NodeVersioned>()
        val newNodesReturn = ArrayList<NodeVersioned>()
        newNodesReturn.add(mNewEntry)
        return ActionNodeValues(result, oldNodesReturn, newNodesReturn)
    }
}
