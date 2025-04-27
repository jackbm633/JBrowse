package jbrowse

class TaskRunner2(tab: Tab) {
    val tasks: MutableList<Runnable> = mutableListOf()

    fun scheduleTask(task: Runnable)
    {
        tasks.add(task)
    }


}