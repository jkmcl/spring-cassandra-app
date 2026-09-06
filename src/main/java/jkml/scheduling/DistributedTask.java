package jkml.scheduling;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jkml.data.repository.TaskLockRepository;

/**
 * This is a wrapper of {@link Runnable}. When the {@link Runnable#run()} method
 * of the underlying {@link Runnable} instance is executed by multiple
 * concurrent threads, only one of the threads will be able to acquire the lock
 * of the task and allowed to execute the method.
 * <p>
 * Use case: multiple application instances schedule the same fixed-delay task
 * to be started but only one instance of the task should run at any time.
 */
public sealed class DistributedTask implements Runnable permits ScheduledDistributedTask {

	private final Logger logger = LoggerFactory.getLogger(DistributedTask.class);

	private final TaskLockRepository taskLockRepo;

	protected final String name;

	protected final Runnable task;

	private final String group;

	private final Set<String> otherGroupMembers;

	public DistributedTask(TaskLockRepository taskLockRepo, String name, Runnable task) {
		this.taskLockRepo = taskLockRepo;
		this.name = name;
		this.task = task;
		group = name;
		otherGroupMembers = Set.of();
	}

	public DistributedTask(TaskLockRepository taskLockRepo, String name, Runnable task, String group,
			Set<String> otherGroupMembers) {
		this.taskLockRepo = taskLockRepo;
		this.name = name;
		this.task = task;
		this.group = group;
		this.otherGroupMembers = Set.copyOf(otherGroupMembers);
	}

	protected void executeTask() {
		logger.debug("Executing task: {}", name);
		task.run();
	}

	private String isAnyLocked(Iterable<String> names) {
		for (var lock : taskLockRepo.findAllById(names)) {
			if (lock.getOwner() != null) {
				return lock.getName();
			}
		}
		return null;
	}

	@Override
	public void run() {
		// Acquire lock
		logger.debug("Trying to acquire task lock: {}", name);
		var lock = taskLockRepo.tryLock(name);
		if (lock == null) {
			logger.debug("Unable to acquire task lock: {}", name);
			return;
		}

		// Run underlying task and release lock when done
		try {
			logger.debug("Acquired task lock: {}", name);
			var otherName = isAnyLocked(otherGroupMembers);
			if (otherName != null) {
				logger.debug("Skipping task execution as another task ({}) in the same group ({}) is being executed",
						otherName, group);
				return;
			}
			executeTask();
		} finally {
			logger.debug("Releasing task lock: {}", name);
			taskLockRepo.unlock(lock);
		}
	}

}
