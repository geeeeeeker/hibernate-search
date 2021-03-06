/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.integrationtest.backend.tck.search.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMapperUtils.referenceProvider;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.hibernate.search.engine.backend.document.IndexFieldReference;
import org.hibernate.search.engine.backend.document.model.dsl.IndexSchemaElement;
import org.hibernate.search.engine.backend.work.execution.spi.IndexIndexer;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.engine.backend.session.spi.BackendSessionContext;
import org.hibernate.search.engine.backend.common.DocumentReference;
import org.hibernate.search.engine.search.query.dsl.SearchQueryDslExtension;
import org.hibernate.search.engine.search.query.dsl.SearchQueryWhereStep;
import org.hibernate.search.engine.search.query.dsl.SearchQuerySelectStep;
import org.hibernate.search.engine.backend.scope.spi.IndexScope;
import org.hibernate.search.engine.search.loading.context.spi.LoadingContext;
import org.hibernate.search.engine.search.loading.context.spi.LoadingContextBuilder;
import org.hibernate.search.engine.search.query.SearchQuery;
import org.hibernate.search.engine.search.query.SearchQueryExtension;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.rule.SearchSetupHelper;
import org.hibernate.search.util.common.SearchException;
import org.hibernate.search.util.impl.integrationtest.common.assertion.SearchResultAssert;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.StubLoadingContext;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMappingIndexManager;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMappingScope;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import org.assertj.core.api.Assertions;

public class SearchQueryBaseIT {

	private static final String INDEX_NAME = "IndexName";

	@Rule
	public SearchSetupHelper setupHelper = new SearchSetupHelper();

	private StubMappingIndexManager indexManager;
	private IndexMapping indexMapping;

	@Before
	public void setup() {
		setupHelper.start()
				.withIndex(
						INDEX_NAME,
						ctx -> this.indexMapping = new IndexMapping( ctx.getSchemaElement() ),
						indexManager -> this.indexManager = indexManager
				)
				.setup();
	}

	@Test
	public void getQueryString() {
		StubMappingScope scope = indexManager.createScope();

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.match().field( "string" ).matching( "platypus" ) )
				.toQuery();

		assertThat( query.getQueryString() ).contains( "platypus" );
	}

	@Test
	public void tookAndTimedOut() {
		StubMappingScope scope = indexManager.createScope();

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.matchAll() )
				.toQuery();

		SearchResult<DocumentReference> result = query.fetchAll();

		assertNotNull( result.getTook() );
		assertNotNull( result.isTimedOut() );
		assertFalse( result.isTimedOut() );
	}

	@Test
	public void extension() {
		initData( 2 );

		StubMappingScope scope = indexManager.createScope();

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.matchAll() )
				.toQuery();

		// Mandatory extension, supported
		QueryWrapper<DocumentReference> extendedQuery = query.extension( new SupportedQueryExtension<>() );
		SearchResultAssert.assertThat( extendedQuery.extendedFetch() ).fromQuery( query )
				.hasDocRefHitsAnyOrder( INDEX_NAME, "0", "1" );

		// Mandatory extension, unsupported
		Assertions.assertThatThrownBy(
				() -> query.extension( new UnSupportedQueryExtension<>() )
		)
				.isInstanceOf( SearchException.class );
	}

	@Test
	public void context_extension() {
		initData( 5 );

		StubMappingScope scope = indexManager.createScope();
		SearchQuery<DocumentReference> query;

		// Mandatory extension, supported
		query = scope.query()
				.extension( new SupportedQueryDslExtension<>() )
				.extendedFeature( "string", "value1", "value2" );
		SearchResultAssert.assertThat( query )
				.hasDocRefHitsExactOrder( INDEX_NAME, "1","2" );

		// Mandatory extension, unsupported
		Assertions.assertThatThrownBy(
				() -> scope.query()
				.extension( new UnSupportedQueryDslExtension<>() )
		)
				.isInstanceOf( SearchException.class );
	}

	private void initData(int documentCount) {
		IndexIndexer executor =
				indexManager.createIndexer();
		List<CompletableFuture<?>> futures = new ArrayList<>();
		for ( int i = 0; i < documentCount; i++ ) {
			int intValue = i;
			futures.add( executor.add( referenceProvider( String.valueOf( intValue ) ), document -> {
				document.addValue( indexMapping.string, "value" + intValue );
			} ) );
		}

		CompletableFuture.allOf( futures.toArray( new CompletableFuture<?>[0] ) ).join();
		indexManager.createWorkspace().refresh().join();

		// Check that all documents are searchable
		StubMappingScope scope = indexManager.createScope();
		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.matchAll() )
				.toQuery();
		SearchResultAssert.assertThat( query ).hasTotalHitCount( documentCount );
	}

	private static class IndexMapping {
		final IndexFieldReference<String> string;

		IndexMapping(IndexSchemaElement root) {
			string = root.field( "string", f -> f.asString().sortable( Sortable.YES ) )
					.toReference();
		}
	}

	private static class QueryWrapper<H> {
		private final SearchQuery<H> query;

		private QueryWrapper(SearchQuery<H> query) {
			this.query = query;
		}

		public SearchResult<H> extendedFetch() {
			return query.fetchAll();
		}
	}

	private static class SupportedQueryExtension<H> implements SearchQueryExtension<QueryWrapper<H>, H> {
		@Override
		public Optional<QueryWrapper<H>> extendOptional(SearchQuery<H> original,
				LoadingContext<?, ?> loadingContext) {
			Assertions.assertThat( original ).isNotNull();
			Assertions.assertThat( loadingContext ).isNotNull().isInstanceOf( StubLoadingContext.class );
			return Optional.of( new QueryWrapper<>( original ) );
		}
	}

	private static class UnSupportedQueryExtension<H> implements SearchQueryExtension<QueryWrapper<H>, H> {
		@Override
		public Optional<QueryWrapper<H>> extendOptional(SearchQuery<H> original,
				LoadingContext<?, ?> loadingContext) {
			Assertions.assertThat( original ).isNotNull();
			Assertions.assertThat( loadingContext ).isNotNull().isInstanceOf( StubLoadingContext.class );
			return Optional.empty();
		}
	}

	private static class SupportedQueryDslExtension<R, E, LOS> implements
			SearchQueryDslExtension<MyExtendedDslContext<R>, R, E, LOS> {
		@Override
		public Optional<MyExtendedDslContext<R>> extendOptional(SearchQuerySelectStep<?, R, E, LOS, ?, ?> original,
				IndexScope<?> indexScope, BackendSessionContext sessionContext,
				LoadingContextBuilder<R, E, LOS> loadingContextBuilder) {
			Assertions.assertThat( original ).isNotNull();
			Assertions.assertThat( indexScope ).isNotNull();
			Assertions.assertThat( sessionContext ).isNotNull();
			Assertions.assertThat( loadingContextBuilder ).isNotNull();
			return Optional.of( new MyExtendedDslContext<R>( original.selectEntityReference() ) );
		}
	}

	private static class UnSupportedQueryDslExtension<R, E, LOS> implements
			SearchQueryDslExtension<MyExtendedDslContext<R>, R, E, LOS> {
		@Override
		public Optional<MyExtendedDslContext<R>> extendOptional(SearchQuerySelectStep<?, R, E, LOS, ?, ?> original,
				IndexScope<?> indexScope, BackendSessionContext sessionContext,
				LoadingContextBuilder<R, E, LOS> loadingContextBuilder) {
			Assertions.assertThat( original ).isNotNull();
			Assertions.assertThat( indexScope ).isNotNull();
			Assertions.assertThat( sessionContext ).isNotNull();
			Assertions.assertThat( loadingContextBuilder ).isNotNull();
			return Optional.empty();
		}
	}

	private static class MyExtendedDslContext<T> {
		private final SearchQueryWhereStep<?, T, ?> delegate;

		MyExtendedDslContext(SearchQueryWhereStep<?, T, ?> delegate) {
			this.delegate = delegate;
		}

		public SearchQuery<T> extendedFeature(String fieldName, String value1, String value2) {
			return delegate.where( f -> f.bool()
					.should( f.match().field( fieldName ).matching( value1 ) )
					.should( f.match().field( fieldName ).matching( value2 ) )
			)
					.sort( f -> f.field( fieldName ) )
					.toQuery();
		}
	}
}
