/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.integrationtest.backend.tck.search.spatial;

import static org.hibernate.search.util.impl.integrationtest.common.assertion.SearchResultAssert.assertThat;
import static org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMapperUtils.referenceProvider;

import org.hibernate.search.engine.backend.document.IndexFieldReference;
import org.hibernate.search.engine.backend.document.model.dsl.IndexSchemaElement;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.engine.backend.work.execution.spi.IndexIndexingPlan;
import org.hibernate.search.engine.backend.common.DocumentReference;
import org.hibernate.search.engine.search.query.SearchQuery;
import org.hibernate.search.engine.spatial.GeoPoint;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.rule.SearchSetupHelper;
import org.hibernate.search.util.common.SearchException;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMappingIndexManager;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMappingScope;
import org.assertj.core.api.Assertions;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DistanceSearchSearchableSortableIT {

	private static final String INDEX_NAME = "IndexName";

	private static final String OURSON_QUI_BOIT_ID = "ourson qui boit";
	private static final GeoPoint OURSON_QUI_BOIT_GEO_POINT = GeoPoint.of( 45.7705687, 4.835233 );

	private static final String IMOUTO_ID = "imouto";
	private static final GeoPoint IMOUTO_GEO_POINT = GeoPoint.of( 45.7541719, 4.8386221 );

	private static final String CHEZ_MARGOTTE_ID = "chez margotte";
	private static final GeoPoint CHEZ_MARGOTTE_GEO_POINT = GeoPoint.of( 45.7530374, 4.8510299 );

	private static final GeoPoint METRO_GARIBALDI = GeoPoint.of( 45.7515926, 4.8514779 );

	@Rule
	public SearchSetupHelper setupHelper = new SearchSetupHelper();

	protected IndexMapping indexMapping;
	protected StubMappingIndexManager indexManager;

	@Before
	public void setup() {
		setupHelper.start()
				.withIndex(
						INDEX_NAME,
						ctx -> this.indexMapping = new IndexMapping( ctx.getSchemaElement() ),
						indexManager -> this.indexManager = indexManager
				)
				.setup();

		initData();
	}

	@Test
	public void searchableSortable() {
		StubMappingScope scope = indexManager.createScope();
		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.spatial().within().field( "searchableSortable" ).circle( METRO_GARIBALDI, 1_500 ) )
				.sort( f -> f.distance( "searchableSortable", METRO_GARIBALDI ) )
				.toQuery();

		assertThat( query ).hasDocRefHitsAnyOrder( INDEX_NAME, CHEZ_MARGOTTE_ID, IMOUTO_ID );
	}

	@Test
	public void searchableNotSortable() {
		StubMappingScope scope = indexManager.createScope();
		String fieldPath = "searchableNotSortable";

		Assertions.assertThatThrownBy( () ->
				scope.query()
						.where( f -> f.spatial().within().field( fieldPath ).circle( METRO_GARIBALDI, 1_500 ) )
						.sort( f -> f.distance( fieldPath, METRO_GARIBALDI ) )
						.toQuery()

		)
				.isInstanceOf( SearchException.class )
				.hasMessageContaining( "Sorting is not enabled for field" )
				.hasMessageContaining( "Make sure the field is marked as sortable" )
				.hasMessageContaining( fieldPath );

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.spatial().within().field( fieldPath ).circle( METRO_GARIBALDI, 1_500 ) )
				.toQuery();

		assertThat( query ).hasDocRefHitsAnyOrder( INDEX_NAME, CHEZ_MARGOTTE_ID, IMOUTO_ID );
	}

	@Test
	public void searchableDefaultSortable() {
		StubMappingScope scope = indexManager.createScope();
		String fieldPath = "searchableDefaultSortable";

		Assertions.assertThatThrownBy( () ->
				scope.query()
						.where( f -> f.spatial().within().field( fieldPath ).circle( METRO_GARIBALDI, 1_500 ) )
						.sort( f -> f.distance( fieldPath, METRO_GARIBALDI ) )
						.toQuery()

		)
				.isInstanceOf( SearchException.class )
				.hasMessageContaining( "Sorting is not enabled for field" )
				.hasMessageContaining( "Make sure the field is marked as sortable" )
				.hasMessageContaining( fieldPath );

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.spatial().within().field( fieldPath ).circle( METRO_GARIBALDI, 1_500 ) )
				.toQuery();

		assertThat( query ).hasDocRefHitsAnyOrder( INDEX_NAME, CHEZ_MARGOTTE_ID, IMOUTO_ID );
	}

	@Test
	public void notSearchableSortable() {
		StubMappingScope scope = indexManager.createScope();
		String fieldPath = "notSearchableSortable";

		Assertions.assertThatThrownBy( () ->
				scope.query()
						.where( f -> f.spatial().within().field( fieldPath ).circle( METRO_GARIBALDI, 1_500 ) )
						.sort( f -> f.distance( fieldPath, METRO_GARIBALDI ) )
						.toQuery()

		)
				.isInstanceOf( SearchException.class )
				.hasMessageContaining( "Field 'notSearchableSortable' is not searchable" )
				.hasMessageContaining( "Make sure the field is marked as searchable" )
				.hasMessageContaining( fieldPath );

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.matchAll() )
				.sort( f -> f.distance( fieldPath, METRO_GARIBALDI ) )
				.toQuery();

		assertThat( query ).hasDocRefHitsAnyOrder( INDEX_NAME, CHEZ_MARGOTTE_ID, IMOUTO_ID, OURSON_QUI_BOIT_ID );
	}

	@Test
	public void defaultSearchableSortable() {
		StubMappingScope scope = indexManager.createScope();
		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.spatial().within().field( "defaultSearchableSortable" ).circle( METRO_GARIBALDI, 1_500 ) )
				.sort( f -> f.distance( "defaultSearchableSortable", METRO_GARIBALDI ) )
				.toQuery();

		assertThat( query ).hasDocRefHitsAnyOrder( INDEX_NAME, CHEZ_MARGOTTE_ID, IMOUTO_ID );
	}

	private void initData() {
		IndexIndexingPlan<?> plan = indexManager.createIndexingPlan();
		plan.add( referenceProvider( OURSON_QUI_BOIT_ID ), document -> {
			document.addValue( indexMapping.searchableSortable, OURSON_QUI_BOIT_GEO_POINT );
			document.addValue( indexMapping.searchableNotSortable, OURSON_QUI_BOIT_GEO_POINT );
			document.addValue( indexMapping.searchableDefaultSortable, OURSON_QUI_BOIT_GEO_POINT );
			document.addValue( indexMapping.notSearchableSortable, OURSON_QUI_BOIT_GEO_POINT );
			document.addValue( indexMapping.defaultSearchableSortable, OURSON_QUI_BOIT_GEO_POINT );
		} );
		plan.add( referenceProvider( IMOUTO_ID ), document -> {
			document.addValue( indexMapping.searchableSortable, IMOUTO_GEO_POINT );
			document.addValue( indexMapping.searchableNotSortable, IMOUTO_GEO_POINT );
			document.addValue( indexMapping.searchableDefaultSortable, IMOUTO_GEO_POINT );
			document.addValue( indexMapping.notSearchableSortable, IMOUTO_GEO_POINT );
			document.addValue( indexMapping.defaultSearchableSortable, IMOUTO_GEO_POINT );
		} );
		plan.add( referenceProvider( CHEZ_MARGOTTE_ID ), document -> {
			document.addValue( indexMapping.searchableSortable, CHEZ_MARGOTTE_GEO_POINT );
			document.addValue( indexMapping.searchableNotSortable, CHEZ_MARGOTTE_GEO_POINT );
			document.addValue( indexMapping.searchableDefaultSortable, CHEZ_MARGOTTE_GEO_POINT );
			document.addValue( indexMapping.notSearchableSortable, CHEZ_MARGOTTE_GEO_POINT );
			document.addValue( indexMapping.defaultSearchableSortable, CHEZ_MARGOTTE_GEO_POINT );
		} );
		plan.execute().join();

		// Check that all documents are searchable
		StubMappingScope scope = indexManager.createScope();
		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.matchAll() )
				.toQuery();
		assertThat( query ).hasDocRefHitsAnyOrder( INDEX_NAME, OURSON_QUI_BOIT_ID, IMOUTO_ID, CHEZ_MARGOTTE_ID );
	}

	protected static class IndexMapping {
		final IndexFieldReference<GeoPoint> searchableSortable;
		final IndexFieldReference<GeoPoint> searchableNotSortable;
		final IndexFieldReference<GeoPoint> searchableDefaultSortable;
		final IndexFieldReference<GeoPoint> notSearchableSortable;
		final IndexFieldReference<GeoPoint> defaultSearchableSortable;

		IndexMapping(IndexSchemaElement root) {
			searchableSortable = root.field( "searchableSortable", f -> f.asGeoPoint().searchable( Searchable.YES ).sortable( Sortable.YES ) ).toReference();
			searchableNotSortable = root.field( "searchableNotSortable", f -> f.asGeoPoint().searchable( Searchable.YES ).sortable( Sortable.NO ) ).toReference();
			searchableDefaultSortable = root.field( "searchableDefaultSortable", f -> f.asGeoPoint().searchable( Searchable.YES ) ).toReference();
			notSearchableSortable = root.field( "notSearchableSortable", f -> f.asGeoPoint().searchable( Searchable.NO ).sortable( Sortable.YES ) ).toReference();
			defaultSearchableSortable = root.field( "defaultSearchableSortable", f -> f.asGeoPoint().sortable( Sortable.YES ) ).toReference();
		}
	}

}
