package jkml.scheduling;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.cassandraunit.spring.CassandraDataSet;
import org.cassandraunit.spring.CassandraUnitDependencyInjectionIntegrationTestExecutionListener;
import org.cassandraunit.spring.EmbeddedCassandra;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;

import jkml.data.entity.TaskLock;
import jkml.data.entity.TaskSchedule;
import jkml.data.repository.RepoTestHelper;
import jkml.data.repository.TaskLockRepository;
import jkml.data.repository.TaskScheduleRepository;

@SpringBootTest
@TestExecutionListeners(mergeMode = MergeMode.MERGE_WITH_DEFAULTS, listeners = CassandraUnitDependencyInjectionIntegrationTestExecutionListener.class)
@CassandraDataSet(keyspace = "keyspace1", value = { "schema.cql" })
@EmbeddedCassandra
class ScheduledDistributedTaskTests {

	private static final Logger logger = LoggerFactory.getLogger(ScheduledDistributedTaskTests.class);

	@Autowired
	private TaskLockRepository taskLockRepo;

	@Autowired
	private TaskScheduleRepository taskScheduleRepo;

	@Autowired
	private RepoTestHelper testHelper;

	@Test
	void testRun() {
		logger.info("Creating task lock entity...");
		var taskName = MyTask.class.getSimpleName();
		var lock = new TaskLock();
		lock.setName(taskName);
		lock.setTimeout(10);
		taskLockRepo.save(lock);

		logger.info("Creating scheduled task entity...");
		var maxTsOffset = 5;
		var schedTask = new TaskSchedule();
		schedTask.setName(taskName);
		schedTask.setMaxTsOffset(maxTsOffset);
		taskScheduleRepo.save(schedTask);

		logger.info("Creating scheduled distributed task...");
		var task = new MyTask();
		var distTask = new ScheduledDistributedTask(taskLockRepo, taskScheduleRepo, taskName, task);

		testHelper.logScheduledTaskState(taskName);

		logger.info("Running scheduled distributed task...");
		distTask.run();
		testHelper.logScheduledTaskState(taskName);
		assertTrue(task.isExecuted());
		task.setExecuted(false);

		logger.info("Running scheduled distributed task again immediately...");
		distTask.run();
		testHelper.logScheduledTaskState(taskName);
		assertTrue(!task.isExecuted());

		logger.info("Running scheduled distributed task again after timeout period ...");
		await().pollDelay(Duration.ofSeconds(maxTsOffset + 1)).until(() -> true);
		distTask.run();
		testHelper.logScheduledTaskState(taskName);
		assertTrue(task.isExecuted());
	}

}
