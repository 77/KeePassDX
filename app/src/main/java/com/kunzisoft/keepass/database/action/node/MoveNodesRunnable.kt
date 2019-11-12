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
import android.util.Log
import com.kunzisoft.keepass.database.element.*
import com.kunzisoft.keepass.database.exception.LoadDatabaseException
import com.kunzisoft.keepass.database.exception.MoveDatabaseEntryException
import com.kunzisoft.keepass.database.exception.MoveDatabaseGroupException

class MoveNodesRunnable constructor(
        context: Context,
        database: Database,
        private val mNodesToMove: List<NodeVersioned>,
        private val mNewParent: GroupVersioned,
        save: Boolean,
        afterActionNodesFinish: AfterActionNodesFinish?)
    : ActionNodeDatabaseRunnable(context, database, afterActionNodesFinish, save) {

    private var mOldParent: GroupVersioned? = null

    override fun nodeAction() {

        foreachNode@ for(nodeToMove in mNodesToMove) {
            // Move node in new parent
            mOldParent = nodeToMove.parent

            when (nodeToMove.type) {
                Type.GROUP -> {
                    val groupToMove = nodeToMove as GroupVersioned
                    // Move group in new parent if not in the current group
                    if (groupToMove != mNewParent
                            && !mNewParent.isContainedIn(groupToMove)) {
                        nodeToMove.touch(modified = true, touchParents = true)
                        database.moveGroupTo(groupToMove, mNewParent)
                    } else {
                        // Only finish thread
                        setError(MoveDatabaseGroupException())
                        break@foreachNode
                    }
                }
                Type.ENTRY -> {
                    val entryToMove = nodeToMove as EntryVersioned
                    // Move only if the parent change
                    if (mOldParent != mNewParent
                            // and root can contains entry
                            && (mNewParent != database.rootGroup || database.rootCanContainsEntry())) {
                        nodeToMove.touch(modified = true, touchParents = true)
                        database.moveEntryTo(entryToMove, mNewParent)
                    } else {
                        // Only finish thread
                        setError(MoveDatabaseEntryException())
                        break@foreachNode
                    }
                }
            }
        }
    }

    override fun nodeFinish(): ActionNodesValues {
        if (!result.isSuccess) {
            try {
                mNodesToMove.forEach { nodeToMove ->
                    // If we fail to save, try to move in the first place
                    if (mOldParent != null &&
                            mOldParent != nodeToMove.parent) {
                        when (nodeToMove.type) {
                            Type.GROUP -> database.moveGroupTo(nodeToMove as GroupVersioned, mOldParent!!)
                            Type.ENTRY -> database.moveEntryTo(nodeToMove as EntryVersioned, mOldParent!!)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.i(TAG, "Unable to replace the node")
            }
        }
        return ActionNodesValues(ArrayList(), mNodesToMove)
    }

    companion object {
        private val TAG = MoveNodesRunnable::class.java.name
    }
}
