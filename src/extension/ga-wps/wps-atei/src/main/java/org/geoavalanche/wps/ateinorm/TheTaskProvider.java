package org.geoavalanche.wps.ateinorm;

import com.github.dexecutor.core.task.Task;
import com.github.dexecutor.core.task.TaskProvider;
import java.util.Map;

public class TheTaskProvider implements TaskProvider<String, Boolean> {

    Map<String, Task<String, Boolean>> tasks;

    public TheTaskProvider(Map<String, Task<String, Boolean>> tasks) {
        this.tasks = tasks;
    }

    @Override
    public Task<String, Boolean> provideTask(final String id) {
        Task theTask = (Task) this.tasks.get(id);
        return theTask;
    }
}
