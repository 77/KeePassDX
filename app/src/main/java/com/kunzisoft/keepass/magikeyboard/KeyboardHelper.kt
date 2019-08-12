package com.kunzisoft.keepass.magikeyboard

import android.app.Activity
import android.content.Intent
import com.kunzisoft.keepass.activities.helpers.EntrySelectionHelper

object KeyboardHelper {

    fun startActivityForKeyboardSelection(activity: Activity, intent: Intent) {
        EntrySelectionHelper.addEntrySelectionModeExtraInIntent(intent)
        // only to avoid visible flickering when redirecting
        activity.startActivity(intent)
    }
}