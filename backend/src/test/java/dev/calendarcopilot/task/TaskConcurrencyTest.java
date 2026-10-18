package dev.calendarcopilot.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import dev.calendarcopilot.ApiTestSupport;

class TaskConcurrencyTest extends ApiTestSupport {

    @Autowired
    private TaskRepository tasks;
    @Autowired
    private TaskService service;

    @Test
    void staleWriteFailsInsteadOfOverwritingANewerChange() throws Exception {
        UUID workspace = UUID.fromString(createWorkspace());
        long id = createTask(workspace.toString(), "{\"title\":\"Original\"}");

        Task staleCopy = tasks.findById(id).orElseThrow(); // read before the other update
        service.update(workspace, id, new UpdateTaskRequest("Renamed elsewhere", null, null, null, null, null, null, null));

        staleCopy.setStatus(TaskStatus.DONE);
        assertThatThrownBy(() -> tasks.save(staleCopy)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(tasks.findById(id).orElseThrow().getTitle()).isEqualTo("Renamed elsewhere");
    }

    @Test
    void concurrentUpdatesNeverSilentlyLoseAField() throws Exception {
        UUID workspace = UUID.fromString(createWorkspace());
        long id = createTask(workspace.toString(), "{\"title\":\"Shared\"}");

        int writers = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < writers; i++) {
                String title = "Title " + i;
                Callable<Boolean> write = () -> {
                    start.await();
                    try {
                        service.update(workspace, id, new UpdateTaskRequest(title, null, null, null, null, null, null, null));
                        return true;
                    } catch (ObjectOptimisticLockingFailureException e) {
                        return false; // surfaced to clients as 409
                    }
                };
                results.add(pool.submit(write));
            }
            start.countDown();
        }

        long succeeded = 0;
        for (Future<Boolean> r : results) {
            if (r.get()) succeeded++;
        }
        Task saved = tasks.findById(id).orElseThrow();
        // Every successful write bumped the version exactly once: no update was lost silently.
        assertThat(succeeded).isGreaterThanOrEqualTo(1);
        assertThat(saved.getVersion()).isEqualTo(succeeded);
        assertThat(saved.getTitle()).startsWith("Title ");
    }
}
