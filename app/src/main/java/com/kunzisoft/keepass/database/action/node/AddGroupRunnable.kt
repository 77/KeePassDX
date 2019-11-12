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
import com.kunzisoft.keepass.database.element.GroupVersioned
import com.kunzisoft.keepass.database.element.NodeVersioned

class AddGroupRunnable constructor(
        context: Context,
        database: Database,
        private val mNewGroup: GroupVersioned,
        private val mParent: GroupVersioned,
        save: Boolean,
        afterActionNodesFinish: AfterActionNodesFinish?)
    : ActionNodeDatabaseRunnable(context, database, afterActionNodesFinish, save) {

    override fun nodeAction() {
        mNewGroup.touch(modified = true, touchParents = true)
        mParent.touch(modified = true, touchParents = true)
        database.addGroupTo(mNewGroup, mParent)
    }

    override fun nodeFinish(): ActionNodesValues {
        if (!result.isSuccess) {
            database.removeGroupFrom(mNewGroup, mParent)
        }

        val oldNodesReturn = ArrayList<NodeVersioned>()
        val newNodesReturn = ArrayList<NodeVersioned>()
        newNodesReturn.add(mNewGroup)
        return ActionNodesValues(oldNodesReturn, newNodesReturn)
    }
}
