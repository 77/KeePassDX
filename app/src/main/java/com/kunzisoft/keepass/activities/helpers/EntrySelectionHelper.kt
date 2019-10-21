package com.kunzisoft.keepass.activities.helpers

import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kunzisoft.keepass.autofill.AutofillHelper

object EntrySelectionHelper {

    private const val EXTRA_ENTRY_SELECTION_MODE = "com.kunzisoft.keepass.extra.ENTRY_SELECTION_MODE"
    private const val DEFAULT_ENTRY_SELECTION_MODE = false

    fun startActivityForEntrySelection(context: Context, intent: Intent) {
        addEntrySelectionModeExtraInIntent(intent)
        // only to avoid visible flickering when redirecting
        context.startActivity(intent)
    }

    fun addEntrySelectionModeExtraInIntent(intent: Intent) {
        intent.putExtra(EXTRA_ENTRY_SELECTION_MODE, true)
    }

    fun removeEntrySelectionModeFromIntent(intent: Intent) {
        intent.removeExtra(EXTRA_ENTRY_SELECTION_MODE)
    }

    fun retrieveEntrySelectionModeFromIntent(intent: Intent): Boolean {
        return intent.getBooleanExtra(EXTRA_ENTRY_SELECTION_MODE, DEFAULT_ENTRY_SELECTION_MODE)
    }

    fun doEntrySelectionAction(intent: Intent,
                               standardAction: () -> Unit,
                               keyboardAction: () -> Unit,
                               autofillAction: (assistStructure: AssistStructure) -> Unit) {
        var assistStructureInit = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AutofillHelper.retrieveAssistStructure(intent)?.let { assistStructure ->
                autofillAction.invoke(assistStructure)
                assistStructureInit = true
            }
        }
        if (!assistStructureInit) {
            if (intent.getBooleanExtra(EXTRA_ENTRY_SELECTION_MODE, DEFAULT_ENTRY_SELECTION_MODE)) {
                intent.removeExtra(EXTRA_ENTRY_SELECTION_MODE)
                keyboardAction.invoke()
            } else {
                standardAction.invoke()
            }
        }
    }
}
