/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.integrationtest.backend.elasticsearch.search.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.hibernate.search.util.impl.integrationtest.backend.elasticsearch.ElasticsearchIndexMetadataTestUtils.defaultReadAlias;


import org.hibernate.search.backend.elasticsearch.ElasticsearchExtension;
import org.hibernate.search.backend.elasticsearch.cfg.spi.ElasticsearchBackendSpiSettings;
import org.hibernate.search.backend.elasticsearch.client.impl.Paths;
import org.hibernate.search.backend.elasticsearch.client.spi.ElasticsearchRequest;
import org.hibernate.search.backend.elasticsearch.search.query.ElasticsearchSearchRequestTransformer;
import org.hibernate.search.backend.elasticsearch.util.spi.URLEncodedString;
import org.hibernate.search.engine.backend.common.DocumentReference;
import org.hibernate.search.engine.backend.document.IndexFieldReference;
import org.hibernate.search.engine.backend.document.model.dsl.IndexSchemaElement;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.search.query.SearchQuery;
import org.hibernate.search.integrationtest.backend.elasticsearch.testsupport.util.ElasticsearchClientSpy;
import org.hibernate.search.integrationtest.backend.elasticsearch.testsupport.util.ElasticsearchRequestAssertionMode;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.rule.SearchSetupHelper;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMappingIndexManager;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMappingScope;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Test the content of generated Elasticsearch search queries when the
 * {@link ElasticsearchSearchRequestTransformer}
 * is used.
 */
public class ElasticsearchSearchQueryRequestTransformerIT {

	private static final String INDEX_NAME = "indexname";
	private static final String SECOND_INDEX_NAME = "secondindexname";

	@Rule
	public SearchSetupHelper setupHelper = new SearchSetupHelper();

	@Rule
	public ElasticsearchClientSpy clientSpy = new ElasticsearchClientSpy();

	private StubMappingIndexManager indexManager;
	private StubMappingIndexManager secondIndexManager;

	@Before
	public void setup() {
		setupHelper.start()
				.withBackendProperty(
						ElasticsearchBackendSpiSettings.CLIENT_FACTORY, clientSpy.getFactory()
				)
				.withIndex(
						INDEX_NAME,
						ctx -> new IndexMapping( ctx.getSchemaElement() ),
						indexManager -> this.indexManager = indexManager
				)
				.withIndex(
						SECOND_INDEX_NAME,
						ctx -> new IndexMapping( ctx.getSchemaElement() ),
						indexManager -> this.secondIndexManager = indexManager
				)
				.setup();
	}

	@Test
	public void path() {
		StubMappingScope scope = indexManager.createScope();

		SearchQuery<DocumentReference> query = scope.query().extension( ElasticsearchExtension.get() )
				.where( f -> f.matchAll() )
				.requestTransformer( context -> {
					assertThat( context.getPath() ).isEqualTo( "/" + defaultReadAlias( INDEX_NAME ).original + "/_search" );
					String newPath = "/" + defaultReadAlias( SECOND_INDEX_NAME ).original + "/_search";
					context.setPath( newPath );
					// Changes should be visible immediately
					assertThat( context.getPath() ).isEqualTo( newPath );
				} )
				.toQuery();

		clientSpy.expectNext(
				ElasticsearchRequest.post()
						.pathComponent( defaultReadAlias( SECOND_INDEX_NAME ) )
						.pathComponent( URLEncodedString.fromString( "_search" ) )
						.body( new Gson().fromJson( "{}", JsonObject.class ) )
						.build(),
				ElasticsearchRequestAssertionMode.EXTENSIBLE
		);

		query.fetchAll();
	}

	@Test
	public void queryParameters() {
		StubMappingScope scope = indexManager.createScope();

		SearchQuery<DocumentReference> query = scope.query().extension( ElasticsearchExtension.get() )
				.where( f -> f.matchAll() )
				.requestTransformer( context -> {
					assertThat( context.getParametersMap() )
							.doesNotContainKeys( "search_type" );
					context.getParametersMap().put( "search_type", "dfs_query_then_fetch" );
					// Changes should be visible immediately
					assertThat( context.getParametersMap() )
							.contains( entry( "search_type", "dfs_query_then_fetch" ) );
				} )
				.toQuery();

		clientSpy.expectNext(
				ElasticsearchRequest.post()
						.pathComponent( defaultReadAlias( INDEX_NAME ) )
						.pathComponent( Paths._SEARCH )
						.body( new Gson().fromJson( "{}", JsonObject.class ) )
						.param( "search_type", "dfs_query_then_fetch" )
						.build(),
				ElasticsearchRequestAssertionMode.EXTENSIBLE
		);

		query.fetchAll();
	}

	@Test
	public void body() {
		StubMappingScope scope = indexManager.createScope();

		SearchQuery<DocumentReference> query = scope.query().extension( ElasticsearchExtension.get() )
				.where( f -> f.matchAll() )
				.requestTransformer( context -> {
					assertThat( context.getBody() )
							.isNotNull()
							.extracting( body -> body.get( "min_score" ) ).isNull();
					context.getBody().addProperty( "min_score", 0.5f );
					// Changes should be visible immediately
					assertThat( context.getBody() )
							.isNotNull()
							.extracting( body -> body.get( "min_score" ) )
							.extracting( JsonElement::getAsFloat ).isEqualTo( 0.5f );
				} )
				.toQuery();

		clientSpy.expectNext(
				ElasticsearchRequest.post()
						.pathComponent( defaultReadAlias( INDEX_NAME ) )
						.pathComponent( Paths._SEARCH )
						.body( new Gson().fromJson( "{'min_score':0.5}", JsonObject.class ) )
						.build(),
				ElasticsearchRequestAssertionMode.EXTENSIBLE
		);

		query.fetchAll();
	}

	@SuppressWarnings("unused")
	private static class IndexMapping {
		final IndexFieldReference<Integer> integer;
		final IndexFieldReference<String> string;

		IndexMapping(IndexSchemaElement root) {
			integer = root.field(
					"integer",
					f -> f.asInteger().projectable( Projectable.YES )
			)
					.toReference();
			string = root.field(
					"string",
					f -> f.asString().projectable( Projectable.YES )
			)
					.toReference();
		}
	}


}
