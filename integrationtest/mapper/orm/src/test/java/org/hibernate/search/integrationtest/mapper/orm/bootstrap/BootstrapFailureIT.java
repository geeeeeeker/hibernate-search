/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.integrationtest.mapper.orm.bootstrap;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToOne;

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.util.common.SearchException;
import org.hibernate.search.util.impl.integrationtest.common.rule.BackendMock;
import org.hibernate.search.util.impl.integrationtest.orm.OrmSetupHelper;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Check that a failing boot correctly propagates exceptions,
 * despite the complex asynchronous code used during boot.
 */
public class BootstrapFailureIT {

	private static final String INDEX_NAME = "IndexName";

	@Rule
	public BackendMock backendMock = new BackendMock( "stubBackend" );

	@Rule
	public OrmSetupHelper ormSetupHelper = OrmSetupHelper.withBackendMock( backendMock );

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Test
	public void propagateException() {
		thrown.expect( SearchException.class );
		thrown.expectMessage( "Unable to find a default value bridge implementation" );
		thrown.expectMessage( ContainedEntity.class.getName() );

		ormSetupHelper.start()
				.setup( FailingIndexedEntity.class, ContainedEntity.class );
	}

	@Entity(name = "failingindexed")
	@Indexed(index = INDEX_NAME)
	private static class FailingIndexedEntity {
		@Id
		private Integer id;

		@OneToOne
		@GenericField // This should fail, because there isn't any bridge for ContainedEntity
		private ContainedEntity field;
	}

	@Entity(name = "contained")
	private static class ContainedEntity {
		@Id
		private Integer id;
	}
}