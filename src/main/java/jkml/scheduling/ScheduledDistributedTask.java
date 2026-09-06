package jkml.scheduling;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jkml.data.entity.TaskSchedule;
import jkml.data.repository.TaskLockRepository;
import jkml.data.repository.TaskScheduleRepository;

/**
 * This is a wrapper of {@link Runnable}. When the {@link Runnable#run()} method
 * of the underlying {@link Runnable} instance is executed by multiple
 * concurrent threads, only one of the threads will be able to acquire the lock
 * of the task and allowed to execute the method. In addition, the previous
 * start time of the task is compared against the current time. If the duration
 * is within the maximum offset, the task is considered already started and will
 * not be executed.
 * <p>
 * Use case: multiple application instances schedule the same task to be started
 * at a specific time but only one instance of the task should at that time. In
 * addition, the most recent start time of the task is checked against a maximum
 * offset and the current time to determine if the task has started. This
 * prevents the same day-end batch to be executed twice, for example.
 */
public final class ScheduledDistributedTask extends DistributedTask {

	private static final Logger logger = LoggerFactory.getLogger(ScheduledDistributedTask.class);

	private final TaskScheduleRepository taskScheduleRepo;

	public ScheduledDistributedTask(TaskLockRepository taskLockRepo, TaskScheduleRepository taskScheduleRepo,
			String name, Runnable task) {
		super(taskLockRepo, name, task);
		this.taskScheduleRepo = taskScheduleRepo;
	}

	/**
	 * Check if this instance of the task has been started, i.e. within the offset
	 * of the last start time.
	 */
	private boolean isStarted(TaskSchedule schedule) {
		// Check if task was started previously
		var lastStartTs = schedule.getLastStartTs();
		if (lastStartTs == null) {
			logger.debug("Task ({}) was not started previously", name);
			return false;
		}

		logger.debug("Task ({}) was most recently started at {}", name, lastStartTs);

		// Check if task was started a long time ago (more than the max offset)
		var maxTsOffset = schedule.getMaxTsOffset();
		var maxOffsetDuration = Duration.ofSeconds(maxTsOffset).abs();
		var offsetDuration = Duration.between(Instant.now(), lastStartTs).abs();

		if (offsetDuration.compareTo(maxOffsetDuration) > 0) {
			logger.debug(
					"This instance of the task ({}) is considered not started as the most recent start time is more than {} seconds ago",
					name, maxTsOffset);
			return false;
		}

		logger.debug(
				"This instance of the task ({}) is considered started as the most recent start time is less than or equal to {} seconds ago",
				name, maxTsOffset);
		return true;
	}

	@Override
	protected void executeTask() {
		var schedule = taskScheduleRepo.findById(name).orElse(null);
		if (schedule == null) {
			logger.error("Task configuration not found: {}", name);
			return;
		}
		if (isStarted(schedule)) {
			logger.debug("Skipping task execution as it has already been started");
			return;
		}
		logger.debug("Executing task: {}", name);
		schedule.setLastStartTs(Instant.now());
		schedule.setLastEndTs(null);
		schedule = taskScheduleRepo.save(schedule);
		try {
			task.run();
		} finally {
			schedule.setLastEndTs(Instant.now());
			taskScheduleRepo.save(schedule);
		}
	}

}
