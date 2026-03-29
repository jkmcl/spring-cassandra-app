package jkml.data.repository;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.RandomStringUtils;
import org.cassandraunit.spring.CassandraDataSet;
import org.cassandraunit.spring.CassandraUnitDependencyInjectionIntegrationTestExecutionListener;
import org.cassandraunit.spring.EmbeddedCassandra;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestExecutionListeners.MergeMode;

import com.google.common.base.Stopwatch;

import jkml.data.entity.User;
import jkml.data.entity.User.Role;

@SpringBootTest
@TestExecutionListeners(mergeMode=MergeMode.MERGE_WITH_DEFAULTS, listeners=CassandraUnitDependencyInjectionIntegrationTestExecutionListener.class)
@CassandraDataSet(keyspace="keyspace1", value={ "schema.cql" })
@EmbeddedCassandra
class UserRepositoryTests {

	private final Logger log = LoggerFactory.getLogger(UserRepositoryTests.class);

	@Autowired
	private UserRepository repo;

	@BeforeEach
	void beforeEach() {
		repo.deleteAll();
	}

	@Test
	void test() {
		log.info("Sanity tests...");

		UUID id1 = UUID.randomUUID();
		UUID id2 = UUID.randomUUID();

		repo.save(new User(id1, "Bill", "Gates"));
		repo.save(new User(id2, "Steve", "Jobs", Role.CHECKER));

		assertEquals(2, repo.count());

		assertEquals(Role.MAKER, repo.findById(id1).orElse(null).getRole());
		assertEquals(Role.CHECKER, repo.findById(id2).orElse(null).getRole());
	}

	@Test
	void testRepository() {
		log.info("Testing repository methods...");

		// Save a couple of users
		repo.save(new User(UUID.randomUUID(), "Alice", "Smith"));
		repo.save(new User(UUID.randomUUID(), "Bob", "Smith"));

		// Fetch users by first name
		assertEquals(1, repo.findByFirstName("Alice").size());

		// Fetch users by last name
		assertEquals(2, repo.findByLastName("Smith").size());
	}

	private static List<User> createRandomUsers(int count) {
		List<User> list = new ArrayList<>(count);
		RandomStringUtils rsu = RandomStringUtils.secure();
		for (int i = 0; i < count; ++i) {
			list.add(new User(UUID.randomUUID(), rsu.nextAlphanumeric(10), rsu.nextAlphanumeric(10)));
		}
		return list;
	}

	@Test
	void testCustomRepository() {
		log.info("Testing custom repository functionality...");

		// Warm up
		repo.ingest(createRandomUsers(1));
		repo.deleteAll();

		// Create users
		int userCount = 100;
		List<User> users = createRandomUsers(userCount);

		// Insert
		Stopwatch sw = Stopwatch.createStarted();
		repo.ingest(users);
		sw.stop();
		log.info("Time for inserting using async insert: {} ms", sw.elapsed(TimeUnit.MILLISECONDS));

		// Wait for the records to be completely inserted in the background
		await().until(() -> repo.count() == userCount);
	}

	@Test
	void testInsertIfNotExists() {
		User user = new User(UUID.randomUUID(), "Bob", "Smith");

		Optional<User> result = repo.insertIfNotExists(user);
		assertTrue(result.isPresent());
		result = repo.insertIfNotExists(user);
		assertFalse(result.isPresent());
	}

	@Test
	void testByPartition() {
		var userId = UUID.randomUUID();

		repo.insert(new User(userId, "Bob", "Smith"));
		assertEquals(1, repo.findByPartition(userId).size());

		var partitions = repo.findPartitions();
		assertEquals(1, partitions.size());
		assertEquals(userId, partitions.get(0));

		repo.deleteByPartition(userId);
		assertEquals(0, repo.findByPartition(userId).size());
	}

}
