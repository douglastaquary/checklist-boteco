package com.checklistboteco.data.sync

interface SyncScheduler {
    fun schedulePeriodic()
    fun scheduleImmediate()
}

object NoOpSyncScheduler : SyncScheduler {
    override fun schedulePeriodic() = Unit
    override fun scheduleImmediate() = Unit
}
