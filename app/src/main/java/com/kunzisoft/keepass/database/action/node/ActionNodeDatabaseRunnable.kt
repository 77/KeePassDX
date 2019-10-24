package com.kunzisoft.keepass.database.action.node

import android.content.Context
import com.kunzisoft.keepass.database.action.SaveDatabaseRunnable
import com.kunzisoft.keepass.database.element.Database
import com.kunzisoft.keepass.database.exception.LoadDatabaseException

abstract class ActionNodeDatabaseRunnable(
        context: Context,
        database: Database,
        private val callbackRunnable: AfterActionNodeFinishRunnable?,
        save: Boolean)
    : SaveDatabaseRunnable(context, database, save) {

    /**
     * Function do to a node action, don't implements run() if used this
     */
    abstract fun nodeAction()

    protected fun saveDatabaseAndFinish() {
        if (result.isSuccess) {
            super.run()
            finishRun(true)
        }
    }

    protected fun throwErrorAndFinish(throwable: LoadDatabaseException) {
        saveDatabase = false
        super.run()
        finishRun(false, throwable)
    }

    override fun run() {
        nodeAction()
    }

    /**
     * Function do get the finish node action, don't implements onFinishRun() if used this
     */
    abstract fun nodeFinish(result: Result): ActionNodeValues

    override fun onFinishRun(result: Result) {
        callbackRunnable?.apply {
            onActionNodeFinish(nodeFinish(result))
        }
        super.onFinishRun(result)
    }
}
