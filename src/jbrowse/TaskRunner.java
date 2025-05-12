package jbrowse;

import java.util.ArrayList;
import java.util.List;

public class TaskRunner {
    private final List<Runnable> tasks;

    public TaskRunner(Tab tab) {
        if (tab == null) {
            throw new NullPointerException("Tab cannot be null");
        }
        this.tasks = new ArrayList<>();
    }

    public final List<Runnable> getTasks() {
        return tasks;
    }

    public final void scheduleTask(Runnable task) {
        if (task == null) {
            throw new NullPointerException("Task cannot be null");
        }
        this.tasks.add(task);
    }
}
