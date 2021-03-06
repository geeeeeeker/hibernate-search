/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.util.impl.integrationtest.mapper.stub;

import org.hibernate.search.engine.backend.schema.management.spi.IndexSchemaManager;
import org.hibernate.search.engine.backend.work.execution.DocumentCommitStrategy;
import org.hibernate.search.engine.backend.work.execution.spi.IndexIndexer;
import org.hibernate.search.engine.backend.work.execution.DocumentRefreshStrategy;
import org.hibernate.search.engine.backend.work.execution.spi.IndexWorkspace;
import org.hibernate.search.engine.backend.work.execution.spi.IndexIndexingPlan;
import org.hibernate.search.engine.mapper.mapping.spi.MappedIndexManager;
import org.hibernate.search.engine.mapper.scope.spi.MappedIndexScopeBuilder;
import org.hibernate.search.engine.backend.session.spi.DetachedBackendSessionContext;
import org.hibernate.search.engine.backend.common.DocumentReference;

/**
 * A wrapper around {@link MappedIndexManager} providing some syntactic sugar,
 * such as methods that do not force to provide a session context.
 */
public abstract class StubMappingIndexManager {

	public abstract String name();

	protected abstract MappedIndexManager delegate();

	public <T> T unwrapForTests(Class<T> clazz) {
		return clazz.cast( delegate().toAPI() );
	}

	public IndexSchemaManager getSchemaManager() {
		return delegate().getSchemaManager();
	}

	public IndexIndexingPlan<StubEntityReference> createIndexingPlan() {
		return createIndexingPlan( new StubBackendSessionContext() );
	}

	public IndexIndexingPlan<StubEntityReference> createIndexingPlan(StubBackendSessionContext sessionContext) {
		/*
		 * Use the same defaults as in the ORM mapper for the commit strategy,
		 * but force refreshes because it's more convenient for tests.
		 */
		return delegate().createIndexingPlan( sessionContext, StubEntityReference.FACTORY,
				DocumentCommitStrategy.FORCE, DocumentRefreshStrategy.FORCE );
	}

	public IndexIndexingPlan<StubEntityReference> createIndexingPlan(StubBackendSessionContext sessionContext,
			DocumentCommitStrategy commitStrategy, DocumentRefreshStrategy refreshStrategy) {
		return delegate().createIndexingPlan( sessionContext, StubEntityReference.FACTORY,
				commitStrategy, refreshStrategy );
	}

	public IndexIndexer createIndexer() {
		return createIndexer( new StubBackendSessionContext() );
	}

	public IndexIndexer createIndexer(StubBackendSessionContext sessionContext) {
		return delegate().createIndexer( sessionContext, StubEntityReference.FACTORY );
	}

	public IndexWorkspace createWorkspace() {
		return createWorkspace( new StubBackendSessionContext() );
	}

	public IndexWorkspace createWorkspace(StubBackendSessionContext sessionContext) {
		return createWorkspace( DetachedBackendSessionContext.of( sessionContext ) );
	}

	public IndexWorkspace createWorkspace(DetachedBackendSessionContext sessionContext) {
		return delegate().createWorkspace( sessionContext );
	}

	/**
	 * @return A scope containing this index only.
	 */
	public StubMappingScope createScope() {
		MappedIndexScopeBuilder<DocumentReference, DocumentReference> builder =
				delegate().createScopeBuilder( new StubBackendMappingContext() );
		return new StubMappingScope( builder.build() );
	}

	/**
	 * @return A scope containing this index and the given other indexes.
	 */
	public StubMappingScope createScope(StubMappingIndexManager... others) {
		MappedIndexScopeBuilder<DocumentReference, DocumentReference> builder =
				delegate().createScopeBuilder( new StubBackendMappingContext() );
		for ( StubMappingIndexManager other : others ) {
			other.delegate().addTo( builder );
		}
		return new StubMappingScope( builder.build() );
	}

	/**
	 * @return A scope containing this index and the given other indexes.
	 */
	public <R, E> GenericStubMappingScope<R, E> createGenericScope(StubMappingIndexManager... others) {
		MappedIndexScopeBuilder<R, E> builder =
				delegate().createScopeBuilder( new StubBackendMappingContext() );
		for ( StubMappingIndexManager other : others ) {
			other.delegate().addTo( builder );
		}
		return new GenericStubMappingScope<>( builder.build() );
	}
}
