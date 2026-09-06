package jkml.scheduling;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.cassandraunit.spring.CassandraDataSet;
import org.cassandraunit.spring.CassandraUnitDependencyInjectionIntegrationTestExecutionListener;
import org.cassandraunit.spring.EmbeddedCassandra;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;

import jkml.data.entity.TaskLock;
import jkml.data.repository.TaskLockRepository;

@SpringBootTest
@TestExecutionListeners(mergeMode = MergeMode.MERGE_WITH_DEFAULTS, listeners = CassandraUnitDependencyInjectionIntegrationTestExecutionListener.class)
@CassandraDataSet(keyspace = "keyspace1", value = { "schema.cql" })
@EmbeddedCassandra
class DistributedTaskTests {

	private static final Logger logger = LoggerFactory.getLogger(DistributedTaskTests.class);

	@Autowired
	private TaskLockRepository taskLockRepo;

	@BeforeEach
	void beforeEach(TestInfo testInfo) {
		logger.info("# Start of {}", testInfo.getDisplayName());
	}

	@AfterEach
	void afterEach(TestInfo testInfo) {
		taskLockRepo.deleteAll();
	}

	@Test
	void testRun_notRunning() {
		var lock = new TaskLock("task1");
		lock.setTimeout(10);
		taskLockRepo.save(lock);

		var task = new MyTask();
		new DistributedTask(taskLockRepo, "task1", task).run();
		assertTrue(task.isExecuted());
	}

	@Test
	void testRun_running() {
		var lock = new TaskLock("task1");
		lock.setTimeout(10);
		lock.setOwner(UUID.randomUUID());
		lock.setAcquireTs(Instant.now());
		taskLockRepo.save(lock);

		var task = new MyTask();
		new DistributedTask(taskLockRepo, "task1", task).run();
		assertFalse(task.isExecuted());
	}

	@Test
	void testRun_groupMemberNotRunning() {
		var lock1 = new TaskLock("task1");
		lock1.setTimeout(10);
		taskLockRepo.save(lock1);

		var lock2 = new TaskLock("task2");
		lock2.setTimeout(10);
		taskLockRepo.save(lock2);

		var task1 = new MyTask();
		new DistributedTask(taskLockRepo, "task1", task1, "group1", Set.of("task2")).run();
		assertTrue(task1.isExecuted());
	}

	@Test
	void testRun_groupMemberRunning() {
		var lock1 = new TaskLock("task1");
		lock1.setTimeout(10);
		taskLockRepo.save(lock1);

		var lock2 = new TaskLock("task2");
		lock2.setTimeout(10);
		lock2.setOwner(UUID.randomUUID());
		lock2.setAcquireTs(Instant.now());
		taskLockRepo.save(lock2);

		var task1 = new MyTask();
		new DistributedTask(taskLockRepo, "task1", task1, "group1", Set.of("task2")).run();
		assertFalse(task1.isExecuted());
	}

}
