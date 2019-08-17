/*
 * Copyright 2018 Jeremy Jamet / Kunzisoft.
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

package com.kunzisoft.keepass.database

import com.kunzisoft.keepass.database.element.NodeVersioned
import com.kunzisoft.keepass.database.element.Type
import java.util.*

enum class SortNodeEnum {
    DB, TITLE, USERNAME, CREATION_TIME, LAST_MODIFY_TIME, LAST_ACCESS_TIME;

    fun getNodeComparator(ascending: Boolean, groupsBefore: Boolean): Comparator<NodeVersioned> {
        return when (this) {
            DB -> NodeNaturalComparator(ascending, groupsBefore)
            TITLE -> NodeTitleComparator(ascending, groupsBefore)
            USERNAME -> NodeCreationComparator(ascending, groupsBefore) // TODO Sort
            CREATION_TIME -> NodeCreationComparator(ascending, groupsBefore)
            LAST_MODIFY_TIME -> NodeLastModificationComparator(ascending, groupsBefore)
            LAST_ACCESS_TIME -> NodeLastAccessComparator(ascending, groupsBefore)
        }
    }

    abstract class NodeComparator(var ascending: Boolean, var groupsBefore: Boolean) : Comparator<NodeVersioned> {

        abstract fun compareBySpecificOrder(object1: NodeVersioned, object2: NodeVersioned): Int

        private fun specificOrderOrHashIfEquals(object1: NodeVersioned, object2: NodeVersioned): Int {
            val specificOrderComp = compareBySpecificOrder(object1, object2)

            return if (specificOrderComp == 0) {
                object1.hashCode() - object2.hashCode()
            } else if (!ascending) -specificOrderComp else specificOrderComp // If descending, revert
        }

        override fun compare(object1: NodeVersioned,object2: NodeVersioned): Int {
            if (object1 == object2)
                return 0

            if (object1.type == Type.GROUP) {
                return if (object2.type == Type.GROUP) {
                    specificOrderOrHashIfEquals(object1, object2)
                } else if (object2.type == Type.ENTRY) {
                    if (groupsBefore)
                        -1
                    else
                        1
                } else {
                    -1
                }
            } else if (object1.type == Type.ENTRY) {
                return if (object2.type == Type.ENTRY) {
                    specificOrderOrHashIfEquals(object1, object2)
                } else if (object2.type == Type.GROUP) {
                    if (groupsBefore)
                        1
                    else
                        -1
                } else {
                    -1
                }
            }

            // Type not known
            return -1
        }
    }

    /**
     * Comparator of node by natural database placement
     */
    class NodeNaturalComparator(ascending: Boolean, groupsBefore: Boolean) : NodeComparator(ascending, groupsBefore) {

        override fun compareBySpecificOrder(object1: NodeVersioned, object2: NodeVersioned): Int {
            return object1.nodePositionInParent.compareTo(object2.nodePositionInParent)
        }
    }

    /**
     * Comparator of Node by Title, Groups first, Entries second
     */
    class NodeTitleComparator(ascending: Boolean, groupsBefore: Boolean) : NodeComparator(ascending, groupsBefore) {

        override fun compareBySpecificOrder(object1: NodeVersioned, object2: NodeVersioned): Int {
            return object1.title.compareTo(object2.title, ignoreCase = true)
        }
    }

    /**
     * Comparator of node by creation, Groups first, Entries second
     */
    class NodeCreationComparator(ascending: Boolean, groupsBefore: Boolean) : NodeComparator(ascending, groupsBefore) {

        override fun compareBySpecificOrder(object1: NodeVersioned, object2: NodeVersioned): Int {
            return object1.creationTime.date
                    ?.compareTo(object2.creationTime.date) ?: 0
        }
    }

    /**
     * Comparator of node by last modification, Groups first, Entries second
     */
    class NodeLastModificationComparator(ascending: Boolean, groupsBefore: Boolean) : NodeComparator(ascending, groupsBefore) {

        override fun compareBySpecificOrder(object1: NodeVersioned, object2: NodeVersioned): Int {
            return object1.lastModificationTime.date
                    ?.compareTo(object2.lastModificationTime.date) ?: 0
        }
    }

    /**
     * Comparator of node by last access, Groups first, Entries second
     */
    class NodeLastAccessComparator(ascending: Boolean, groupsBefore: Boolean) : NodeComparator(ascending, groupsBefore) {

        override fun compareBySpecificOrder(object1: NodeVersioned, object2: NodeVersioned): Int {
            return object1.lastAccessTime.date
                    ?.compareTo(object2.lastAccessTime.date) ?: 0
        }
    }
}
