package jkml.data.repository.support;

import org.jspecify.annotations.Nullable;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.repository.support.CassandraRepositoryFactoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.util.Assert;

import com.datastax.oss.driver.api.core.CqlSession;

/**
 * A custom factory bean for passing {@link CqlSession} to our custom repository
 * base class.
 *
 * @see https://stackoverflow.com/a/45345597
 */
public class CustomCassandraRepositoryFactoryBean<T extends Repository<S, ID>, S, ID>
		extends CassandraRepositoryFactoryBean<T, S, ID> {

	private @Nullable CassandraOperations cassandraOperations;

	private final CqlSession session;

	public CustomCassandraRepositoryFactoryBean(Class<? extends T> repositoryInterface, CqlSession session) {
		super(repositoryInterface);
		this.session = session;
	}

	@Override
	protected RepositoryFactorySupport createRepositoryFactory() {
		// Same implementation as base class except that CqlSession is passed as well

		Assert.state(cassandraOperations != null, "CassandraOperations must not be null");

		return new CustomCassandraRepositoryFactory(cassandraOperations, this.session);

	}

	@Override
	public void setCassandraTemplate(CassandraTemplate cassandraTemplate) {
		super.setCassandraTemplate(cassandraTemplate);

		// Keep a reference here because base class stores it in a private field
		this.cassandraOperations = cassandraTemplate;
	}

}
