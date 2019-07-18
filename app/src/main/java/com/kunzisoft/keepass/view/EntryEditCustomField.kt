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
package com.kunzisoft.keepass.view

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.RelativeLayout
import android.widget.TextView

import com.kunzisoft.keepass.R
import com.kunzisoft.keepass.database.element.security.ProtectedString
import com.kunzisoft.keepass.utils.Util

class EntryEditCustomField @JvmOverloads constructor(context: Context,
                                                     attrs: AttributeSet? = null,
                                                     defStyle: Int = 0)
    : RelativeLayout(context, attrs, defStyle) {

    private val labelView: TextView
    private val valueView: EditText
    private val protectionCheckView: CompoundButton

    val label: String
        get() = labelView.text.toString()

    val value: String
        get() = valueView.text.toString()

    val isProtected: Boolean
        get() = protectionCheckView.isChecked

    init {

        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.entry_edit_new_field, this)

        val deleteView = findViewById<View>(R.id.entry_edit_new_field_delete)
        deleteView.setOnClickListener { deleteViewFromParent() }

        labelView = findViewById(R.id.entry_edit_new_field_label)
        valueView = findViewById(R.id.entry_edit_new_field_value)
        protectionCheckView = findViewById(R.id.protection)
    }

    fun setData(label: String?, value: ProtectedString?) {
        if (label != null)
            labelView.text = label
        if (value != null) {
            valueView.setText(value.toString())
            protectionCheckView.isChecked = value.isProtected
        }
    }

    fun setFontVisibility(applyFontVisibility: Boolean) {
        if (applyFontVisibility)
            Util.applyFontVisibilityTo(context, valueView)
    }

    private fun deleteViewFromParent() {
        try {
            val parent = parent as ViewGroup
            parent.removeView(this)
            parent.invalidate()
        } catch (e: ClassCastException) {
            Log.e(javaClass.name, e.message)
        }
    }
}
