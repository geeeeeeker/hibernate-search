/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.integrationtest.backend.tck.work;

import static org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMapperUtils.referenceProvider;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.hibernate.search.engine.backend.document.IndexFieldReference;
import org.hibernate.search.engine.backend.document.model.dsl.IndexSchemaElement;
import org.hibernate.search.engine.backend.work.execution.spi.IndexIndexer;
import org.hibernate.search.engine.backend.work.execution.spi.IndexWorkspace;
import org.hibernate.search.engine.backend.common.DocumentReference;
import org.hibernate.search.engine.search.query.SearchQuery;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.TckBackendHelper;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.rule.SearchSetupHelper;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.StubBackendSessionContext;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMappingIndexManager;

import org.junit.Rule;
import org.junit.Test;

import org.assertj.core.api.Assertions;

/**
 * Verify that the work executor operations:
 * {@link IndexWorkspace#mergeSegments()}, {@link IndexWorkspace#purge(java.util.Set)}, {@link IndexWorkspace#flush()}, {@link IndexWorkspace#refresh()}
 * work properly, in every backends.
 */
public class IndexWorkspaceIT {

	private static final String TENANT_1 = "tenant1";
	private static final String TENANT_2 = "tenant2";

	private static final String INDEX_NAME = "lordOfTheRingsChapters";
	private static final int NUMBER_OF_BOOKS = 200;

	@Rule
	public SearchSetupHelper setupHelper = new SearchSetupHelper();

	@Rule
	public SearchSetupHelper multiTenancySetupHelper = new SearchSetupHelper( TckBackendHelper::createMultiTenancyBackendSetupStrategy );

	private final StubBackendSessionContext noTenantSessionContext = new StubBackendSessionContext();
	private final StubBackendSessionContext tenant1SessionContext = new StubBackendSessionContext( TENANT_1 );
	private final StubBackendSessionContext tenant2SessionContext = new StubBackendSessionContext( TENANT_2 );

	private IndexMapping indexMapping;
	private StubMappingIndexManager indexManager;

	@Test
	public void runMergeSegmentsPurgeAndFlushAndRefreshInSequence() {
		setupHelper.start()
				.withIndex(
						INDEX_NAME,
						ctx -> this.indexMapping = new IndexMapping( ctx.getSchemaElement() ),
						indexManager -> this.indexManager = indexManager
				)
				.setup();

		// Do not provide a tenant
		IndexWorkspace workspace = indexManager.createWorkspace();
		createBookIndexes( noTenantSessionContext );

		workspace.refresh().join();
		assertBookNumberIsEqualsTo( NUMBER_OF_BOOKS, noTenantSessionContext );

		workspace.mergeSegments().join();
		assertBookNumberIsEqualsTo( NUMBER_OF_BOOKS, noTenantSessionContext );

		// purge without providing a tenant
		workspace.purge( Collections.emptySet() ).join();
		workspace.flush().join();
		workspace.refresh().join();

		assertBookNumberIsEqualsTo( 0, noTenantSessionContext );
	}

	@Test
	public void runMergeSegmentsPurgeAndFlushAndRefreshWithMultiTenancy() {
		multiTenancySetupHelper.start()
				.withIndex(
						INDEX_NAME,
						ctx -> this.indexMapping = new IndexMapping( ctx.getSchemaElement() ),
						indexManager -> this.indexManager = indexManager
				)
				.withMultiTenancy()
				.setup();

		// Do provide a tenant ID
		IndexWorkspace workspace = indexManager.createWorkspace( tenant1SessionContext );

		createBookIndexes( tenant1SessionContext );
		createBookIndexes( tenant2SessionContext );
		workspace.refresh().join();

		assertBookNumberIsEqualsTo( NUMBER_OF_BOOKS, tenant1SessionContext );
		assertBookNumberIsEqualsTo( NUMBER_OF_BOOKS, tenant2SessionContext );

		workspace.mergeSegments().join();
		assertBookNumberIsEqualsTo( NUMBER_OF_BOOKS, tenant1SessionContext );
		assertBookNumberIsEqualsTo( NUMBER_OF_BOOKS, tenant2SessionContext );

		workspace.purge( Collections.emptySet() ).join();
		workspace.flush().join();
		workspace.refresh().join();

		// check that only TENANT_1 is affected by the purge
		assertBookNumberIsEqualsTo( 0, tenant1SessionContext );
		assertBookNumberIsEqualsTo( NUMBER_OF_BOOKS, tenant2SessionContext );
	}

	private void createBookIndexes(StubBackendSessionContext sessionContext) {
		IndexIndexer indexer =
				indexManager.createIndexer( sessionContext );
		CompletableFuture<?>[] tasks = new CompletableFuture<?>[NUMBER_OF_BOOKS];

		for ( int i = 0; i < NUMBER_OF_BOOKS; i++ ) {
			final String id = i + "";
			tasks[i] = indexer.add( referenceProvider( id ), document -> {
				document.addValue( indexMapping.title, "The Lord of the Rings cap. " + id );
			} );
		}
		CompletableFuture.allOf( tasks ).join();
	}

	private void assertBookNumberIsEqualsTo(long bookNumber, StubBackendSessionContext sessionContext) {
		SearchQuery<DocumentReference> query = indexManager.createScope().query( sessionContext )
				.where( f -> f.matchAll() )
				.toQuery();

		Assertions.assertThat( query.fetchTotalHitCount() ).isEqualTo( bookNumber );
	}

	private static class IndexMapping {
		final IndexFieldReference<String> title;

		IndexMapping(IndexSchemaElement root) {
			title = root.field( "title", f -> f.asString() )
					.toReference();
		}
	}
}
