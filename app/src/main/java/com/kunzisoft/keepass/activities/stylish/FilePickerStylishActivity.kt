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
package com.kunzisoft.keepass.activities.stylish

import android.os.Bundle
import android.support.annotation.StyleRes
import android.util.Log

import com.nononsenseapps.filepicker.FilePickerActivity

/**
 * FilePickerActivity class with a style compatibility
 */
class FilePickerStylishActivity : FilePickerActivity() {

    @StyleRes
    private var themeId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        this.themeId = Stylish.getFilePickerThemeId(this)
        setTheme(themeId)
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (Stylish.getFilePickerThemeId(this) != this.themeId) {
            Log.d(this.javaClass.name, "Theme change detected, restarting activity")
            this.recreate()
        }
    }
}
