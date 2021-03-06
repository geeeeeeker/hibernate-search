/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.integrationtest.backend.tck.search.predicate;

import static org.hibernate.search.util.impl.integrationtest.common.assertion.SearchResultAssert.assertThat;
import static org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMapperUtils.referenceProvider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hibernate.search.engine.backend.common.DocumentReference;
import org.hibernate.search.engine.backend.document.DocumentElement;
import org.hibernate.search.engine.backend.document.IndexFieldReference;
import org.hibernate.search.engine.backend.document.model.dsl.IndexSchemaElement;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.dsl.IndexFieldTypeFactory;
import org.hibernate.search.engine.backend.types.dsl.StandardIndexFieldTypeOptionsStep;
import org.hibernate.search.engine.backend.work.execution.spi.IndexIndexingPlan;
import org.hibernate.search.engine.reporting.spi.EventContexts;
import org.hibernate.search.engine.search.common.ValueConvert;
import org.hibernate.search.engine.search.query.SearchQuery;
import org.hibernate.search.integrationtest.backend.tck.testsupport.types.FieldModelConsumer;
import org.hibernate.search.integrationtest.backend.tck.testsupport.types.FieldTypeDescriptor;
import org.hibernate.search.integrationtest.backend.tck.testsupport.types.expectations.RangePredicateExpectations;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.InvalidType;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.StandardFieldMapper;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.TckConfiguration;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.ValueWrapper;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.rule.SearchSetupHelper;
import org.hibernate.search.util.common.SearchException;
import org.hibernate.search.util.common.data.RangeBoundInclusion;
import org.hibernate.search.util.impl.integrationtest.common.FailureReportUtils;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMappingIndexManager;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMappingScope;
import org.assertj.core.api.Assertions;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class RangeSearchPredicateIT {

	private static final String INDEX_NAME = "IndexName";
	private static final String COMPATIBLE_INDEX_NAME = "IndexWithCompatibleFields";
	private static final String RAW_FIELD_COMPATIBLE_INDEX_NAME = "IndexWithCompatibleRawFields";
	private static final String INCOMPATIBLE_INDEX_NAME = "IndexWithIncompatibleFields";
	private static final String INCOMPATIBLE_DECIMAL_SCALE_INDEX_NAME = "IndexWithIncompatibleDecimalScale";
	private static final String UNSEARCHABLE_FIELDS_INDEX_NAME = "IndexWithUnsearchableFields";

	private static final String DOCUMENT_1 = "1";
	private static final String DOCUMENT_2 = "2";
	private static final String DOCUMENT_3 = "3";
	private static final String EMPTY_ID = "empty";

	private static final String COMPATIBLE_INDEX_DOCUMENT_1 = "compatible_1";
	private static final String RAW_FIELD_COMPATIBLE_INDEX_DOCUMENT_1 = "raw_field_compatible_1";
	private static final String INCOMPATIBLE_DECIMAL_SCALE_INDEX_DOCUMENT_1 = "incompatible_decimal_scale_1";

	@Rule
	public SearchSetupHelper setupHelper = new SearchSetupHelper();

	private IndexMapping indexMapping;
	private StubMappingIndexManager indexManager;

	private StubMappingIndexManager compatibleIndexManager;

	private RawFieldCompatibleIndexMapping rawFieldCompatibleIndexMapping;
	private StubMappingIndexManager rawFieldCompatibleIndexManager;

	private StubMappingIndexManager incompatibleIndexManager;

	private IncompatibleDecimalScaleIndexMapping incompatibleDecimalScaleIndexMapping;
	private StubMappingIndexManager incompatibleDecimalScaleIndexManager;

	private StubMappingIndexManager unsearchableFieldsIndexManager;

	@Before
	public void setup() {
		setupHelper.start()
				.withIndex(
						INDEX_NAME,
						ctx -> this.indexMapping = new IndexMapping( ctx.getSchemaElement() ),
						indexManager -> this.indexManager = indexManager
				)
				.withIndex(
						COMPATIBLE_INDEX_NAME,
						ctx -> new IndexMapping( ctx.getSchemaElement() ),
						indexManager -> this.compatibleIndexManager = indexManager
				)
				.withIndex(
						RAW_FIELD_COMPATIBLE_INDEX_NAME,
						ctx -> this.rawFieldCompatibleIndexMapping = new RawFieldCompatibleIndexMapping( ctx.getSchemaElement() ),
						indexManager -> this.rawFieldCompatibleIndexManager = indexManager
				)
				.withIndex(
						INCOMPATIBLE_INDEX_NAME,
						ctx -> new NotCompatibleIndexMapping( ctx.getSchemaElement() ),
						indexManager -> this.incompatibleIndexManager = indexManager
				)
				.withIndex(
						INCOMPATIBLE_DECIMAL_SCALE_INDEX_NAME,
						ctx -> this.incompatibleDecimalScaleIndexMapping = new IncompatibleDecimalScaleIndexMapping( ctx.getSchemaElement() ),
						indexManager -> this.incompatibleDecimalScaleIndexManager = indexManager
				)
				.withIndex(
						UNSEARCHABLE_FIELDS_INDEX_NAME,
						ctx -> new UnsearchableFieldsIndexMapping( ctx.getSchemaElement() ),
						indexManager -> this.unsearchableFieldsIndexManager = indexManager
				)
				.setup();

		initData();
	}

	@Test
	public void unsearchable() {
		StubMappingScope scope = unsearchableFieldsIndexManager.createScope();

		for ( ByTypeFieldModel<?> fieldModel : indexMapping.supportedFieldModels ) {
			String absoluteFieldPath = fieldModel.relativeFieldName;

			Assertions.assertThatThrownBy( () ->
					scope.predicate().range().field( absoluteFieldPath )
			)
					.isInstanceOf( SearchException.class )
					.hasMessageContaining( "is not searchable" )
					.hasMessageContaining( "Make sure the field is marked as searchable" )
					.hasMessageContaining( absoluteFieldPath );
		}
	}

	@Test
	public void atLeast() {
		StubMappingScope scope = indexManager.createScope();

		for ( ByTypeFieldModel<?> fieldModel : indexMapping.supportedFieldModels ) {
			String absoluteFieldPath = fieldModel.relativeFieldName;
			Object lowerValueToMatch = fieldModel.predicateLowerBound;

			SearchQuery<DocumentReference> query = scope.query()
					.where( f -> f.range().field( absoluteFieldPath ).atLeast( lowerValueToMatch ) )
					.toQuery();

			assertThat( query )
					.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_2, DOCUMENT_3 );
		}
	}

	@Test
	public void atLeast_withDslConverter_dslConverterEnabled() {
		StubMappingScope scope = indexManager.createScope();

		for ( ByTypeFieldModel<?> fieldModel : indexMapping.supportedFieldWithDslConverterModels ) {
			String absoluteFieldPath = fieldModel.relativeFieldName;
			Object lowerValueToMatch = new ValueWrapper<>( fieldModel.predicateLowerBound );

			SearchQuery<DocumentReference> query = scope.query()
					.where( f -> f.range().field( absoluteFieldPath ).atLeast( lowerValueToMatch ) )
					.toQuery();

			assertThat( query )
					.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_2, DOCUMENT_3 );
		}
	}

	@Test
	public void atLeast_withDslConverter_dslConverterDisabled() {
		StubMappingScope scope = indexManager.createScope();

		for ( ByTypeFieldModel<?> fieldModel : indexMapping.supportedFieldWithDslConverterModels ) {
			String absoluteFieldPath = fieldModel.relativeFieldName;

			SearchQuery<DocumentReference> query = scope.query()
					.where( f -> f.range().field( absoluteFieldPath )
							.atLeast( fieldModel.predicateLowerBound, ValueConvert.NO ) )
					.toQuery();

			assertThat( query )
					.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_2, DOCUMENT_3 );
		}
	}

	@Test
	public void greaterThan() {
		StubMappingScope scope = indexManager.createScope();

		for ( ByTypeFieldModel<?> fieldModel : indexMapping.supportedFieldModels ) {
			String absoluteFieldPath = fieldModel.relativeFieldName;
			Object lowerValueToMatch = fieldModel.document2Value.indexedValue;

			if ( BigDecimal.class.equals( fieldModel.javaType )
					&& !TckConfiguration.get().getBackendFeatures().worksFineWithStrictAboveRangedQueriesOnDecimalScaledField() ) {
				continue;
			}

			SearchQuery<DocumentReference> query = scope.query()
					.where( f -> f.range().field( absoluteFieldPath ).greaterThan( lowerValueToMatch ) )
					.toQuery();

			assertThat( query )
					.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_3 );
		}
	}

	@Test
	public void atMost() {
		StubMappingScope scope = indexManager.createScope();

		for ( ByTypeFieldModel<?> fieldModel : indexMapping.supportedFieldModels ) {
			String absoluteFieldPath = fieldModel.relativeFieldName;
			Object upperValueToMatch = fieldModel.predicateUpperBound;

			SearchQuery<DocumentReference> query = scope.query()
					.where( f -> f.range().field( absoluteFieldPath ).atMost( upperValueToMatch ) )
					.toQuery();

			assertThat( query )
					.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_2 );
		}
	}

	@Test
	public void atMost_withDslConverter_dslConverterEnabled() {
		StubMappingScope scope = indexManager.createScope();

		for ( ByTypeFieldModel<?> fieldModel : indexMapping.supportedFieldWithDslConverterModels ) {
			String absoluteFieldPath = fieldModel.relativeFieldName;
			Object upperValueToMatch = new ValueWrapper<>( fieldModel.predicateUpperBound );

			SearchQuery<DocumentReference> query = scope.query()
					.where( f -> f.range().field( absoluteFieldPath ).atMost( upperValueToMatch ) )
					.toQuery();

			assertThat( query )
					.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_2 );
		}
	}

	@Test
	public void atMost_withDslConverter_dslConverterDisabled() {
		StubMappingScope scope = indexManager.createScope();

		for ( ByTypeFieldModel<?> fieldModel : indexMapping.supportedFieldWithDslConverterModels ) {
			String absoluteFieldPath = fieldModel.relativeFieldName;

			SearchQuery<DocumentReference> query = scope.query()
					.where( f -> f.range().field( absoluteFieldPath )
							.atMost( fieldModel.predicateUpperBound, ValueConvert.NO ) )
					.toQuery();

			assertThat( query )
					.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_2 );
		}
	}

	@Test
	public void lessThan() {
		StubMappingScope scope = indexManager.createScope();

		for ( ByTypeFieldModel<?> fieldModel : indexMapping.supportedFieldModels ) {
			String absoluteFieldPath = fieldModel.relativeFieldName;
			Object upperValueToMatch = fieldModel.document2Value.indexedValue;

			SearchQuery<DocumentReference> query = scope.query()
					.where( f -> f.range().field( absoluteFieldPath ).lessThan( upperValueToMatch ) )
					.toQuery();

			assertThat( query )
					.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1 );
		}
	}

	@Test
	public void between() {
		StubMappingScope scope = indexManager.createScope();

		for ( ByTypeFieldModel<?> fieldModel : indexMapping.supportedFieldModels ) {
			String absoluteFieldPath = fieldModel.relativeFieldName;
			Object lowerValueToMatch = fieldModel.predicateLowerBound;
			Object upperValueToMatch = fieldModel.predicateUpperBound;

			SearchQuery<DocumentReference> query = scope.query()
					.where( f -> f.range().field( absoluteFieldPath ).between( lowerValueToMatch, upperValueToMatch ) )
					.toQuery();

			assertThat( query )
					.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_2 );
		}
	}

	@Test
	public void between_withDslConverter_dslConverterEnabled() {
		StubMappingScope scope = indexManager.createScope();

		for ( ByTypeFieldModel<?> fieldModel : indexMapping.supportedFieldWithDslConverterModels ) {
			String absoluteFieldPath = fieldModel.relativeFieldName;
			Object lowerValueToMatch = new ValueWrapper<>( fieldModel.predicateLowerBound );
			Object upperValueToMatch = new ValueWrapper<>( fieldModel.predicateUpperBound );

			SearchQuery<DocumentReference> query = scope.query()
					.where( f -> f.range().field( absoluteFieldPath ).between( lowerValueToMatch, upperValueToMatch ) )
					.toQuery();

			assertThat( query )
					.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_2 );
		}
	}

	@Test
	public void between_withDslConverter_dslConverterDisabled() {
		StubMappingScope scope = indexManager.createScope();

		for ( ByTypeFieldModel<?> fieldModel : indexMapping.supportedFieldWithDslConverterModels ) {
			String absoluteFieldPath = fieldModel.relativeFieldName;

			SearchQuery<DocumentReference> query = scope.query()
					.where( f -> f.range().field( absoluteFieldPath )
							.between(
									fieldModel.predicateLowerBound, fieldModel.predicateUpperBound,
									ValueConvert.NO
							)
					)
					.toQuery();

			assertThat( query )
					.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_2 );
		}
	}

	@Test
	public void between_boundInclusion() {
		StubMappingScope scope = indexManager.createScope();

		for ( ByTypeFieldModel<?> fieldModel : indexMapping.supportedFieldModels ) {
			String absoluteFieldPath = fieldModel.relativeFieldName;
			Object value1ToMatch = fieldModel.document1Value.indexedValue;
			Object value2ToMatch = fieldModel.document2Value.indexedValue;
			Object value3ToMatch = fieldModel.document3Value.indexedValue;

			// Default is including both bounds

			SearchQuery<DocumentReference> query = scope.query()
					.where( f -> f.range().field( absoluteFieldPath ).between( value1ToMatch, value2ToMatch ) )
					.toQuery();

			assertThat( query )
					.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_2 );

			// explicit exclusion for the lower bound

			query = scope.query()
					.where( f -> f.range().field( absoluteFieldPath )
							.between(
									value1ToMatch, RangeBoundInclusion.EXCLUDED,
									value2ToMatch, RangeBoundInclusion.INCLUDED
							)
					)
					.toQuery();

			assertThat( query )
					.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_2 );

			// explicit exclusion for the upper bound

			query = scope.query()
					.where( f -> f.range().field( absoluteFieldPath )
							.between(
									value1ToMatch, RangeBoundInclusion.INCLUDED,
									value2ToMatch, RangeBoundInclusion.EXCLUDED
							)
					)
					.toQuery();

			assertThat( query )
					.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1 );

			// explicit inclusion for both bounds

			query = scope.query()
					.where( f -> f.range().field( absoluteFieldPath )
							.between(
									value1ToMatch, RangeBoundInclusion.INCLUDED,
									value2ToMatch, RangeBoundInclusion.INCLUDED
							)
					)
					.toQuery();

			assertThat( query )
					.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_2 );

			// explicit exclusion for both bounds

			query = scope.query()
					.where( f -> f.range().field( absoluteFieldPath )
							.between(
									value1ToMatch, RangeBoundInclusion.EXCLUDED,
									value3ToMatch, RangeBoundInclusion.EXCLUDED
							)
					)
					.toQuery();

			assertThat( query )
					.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_2 );
		}
	}

	@Test
	public void between_nullBounds() {
		StubMappingScope scope = indexManager.createScope();

		for ( ByTypeFieldModel<?> fieldModel : indexMapping.supportedFieldModels ) {
			String absoluteFieldPath = fieldModel.relativeFieldName;
			Object lowerValueToMatch = fieldModel.predicateLowerBound;
			Object upperValueToMatch = fieldModel.predicateUpperBound;

			SearchQuery<DocumentReference> query;

			query = scope.query()
					.where( f -> f.range().field( absoluteFieldPath )
							.between( lowerValueToMatch, null ) )
					.toQuery();
			assertThat( query )
					.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_2, DOCUMENT_3 );

			query = scope.query()
					.where( f -> f.range().field( absoluteFieldPath )
							.between( null, upperValueToMatch ) )
					.toQuery();
			assertThat( query )
					.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_2 );
		}
	}

	@Test
	public void unsupported_field_types() {
		StubMappingScope scope = indexManager.createScope();

		for ( ByTypeFieldModel<?> fieldModel : indexMapping.unsupportedFieldModels ) {
			String absoluteFieldPath = fieldModel.relativeFieldName;

			Assertions.assertThatThrownBy(
					() -> scope.predicate().range().field( absoluteFieldPath ),
					"range() predicate with unsupported type on field " + absoluteFieldPath
			)
					.isInstanceOf( SearchException.class )
					.hasMessageContaining( "range predicates" )
					.hasMessageContaining( "are not supported by this field's type" )
					.satisfies( FailureReportUtils.hasContext(
							EventContexts.fromIndexFieldAbsolutePath( absoluteFieldPath )
					) );
		}
	}

	@Test
	public void fieldLevelBoost() {
		StubMappingScope scope = indexManager.createScope();

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.bool()
						.should( f.range().field( indexMapping.string1Field.relativeFieldName )
								.atLeast( indexMapping.string1Field.document3Value.indexedValue )
						)
						.should( f.range().field( indexMapping.string1Field.relativeFieldName ).boost( 42 )
								.atMost( indexMapping.string1Field.document1Value.indexedValue )
						)
				)
				.sort( f -> f.score() )
				.toQuery();

		assertThat( query )
				.hasDocRefHitsExactOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_3 );

		query = scope.query()
				.where( f -> f.bool()
						.should( f.range().field( indexMapping.string1Field.relativeFieldName ).boost( 42 )
								.atLeast( indexMapping.string1Field.document3Value.indexedValue )
						)
						.should( f.range().field( indexMapping.string1Field.relativeFieldName )
								.atMost( indexMapping.string1Field.document1Value.indexedValue )
						)
				)
				.sort( f -> f.score() )
				.toQuery();

		assertThat( query )
				.hasDocRefHitsExactOrder( INDEX_NAME, DOCUMENT_3, DOCUMENT_1 );
	}

	@Test
	public void predicateLevelBoost() {
		StubMappingScope scope = indexManager.createScope();

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.bool()
						.should( f.range().field( indexMapping.string1Field.relativeFieldName )
								.atLeast( indexMapping.string1Field.document3Value.indexedValue )
						)
						.should( f.range().field( indexMapping.string1Field.relativeFieldName )
								.atMost( indexMapping.string1Field.document1Value.indexedValue )
								.boost( 7 )
						)
				)
				.sort( f -> f.score() )
				.toQuery();

		assertThat( query )
				.hasDocRefHitsExactOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_3 );

		query = scope.query()
				.where( f -> f.bool()
						.should( f.range().field( indexMapping.string1Field.relativeFieldName )
								.atLeast( indexMapping.string1Field.document3Value.indexedValue )
								.boost( 39 )
						)
						.should( f.range().field( indexMapping.string1Field.relativeFieldName )
								.atMost( indexMapping.string1Field.document1Value.indexedValue )
						)
				)
				.sort( f -> f.score() )
				.toQuery();

		assertThat( query )
				.hasDocRefHitsExactOrder( INDEX_NAME, DOCUMENT_3, DOCUMENT_1 );
	}

	@Test
	public void predicateLevelBoost_andFieldLevelBoost() {
		StubMappingScope scope = indexManager.createScope();

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.bool()
						// 2 * 3 => boost x6
						.should( f.range().field( indexMapping.string1Field.relativeFieldName ).boost( 3 )
								.atLeast( indexMapping.string1Field.document3Value.indexedValue )
								.boost( 2 )
						)
						// 7 * 1 => boost x7
						.should( f.range().field( indexMapping.string1Field.relativeFieldName )
								.atMost( indexMapping.string1Field.document1Value.indexedValue )
								.boost( 7 )
						)
				)
				.sort( f -> f.score() )
				.toQuery();

		assertThat( query )
				.hasDocRefHitsExactOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_3 );

		query = scope.query()
				.where( f -> f.bool()
						// 39 * 0.5 => boost x19.5
						.should( f.range().field( indexMapping.string1Field.relativeFieldName ).boost( 0.5f )
								.atLeast( indexMapping.string1Field.document3Value.indexedValue )
								.boost( 39 )
						)
						// 3 * 3 => boost x9
						.should( f.range().field( indexMapping.string1Field.relativeFieldName ).boost( 3 )
								.atMost( indexMapping.string1Field.document1Value.indexedValue )
								.boost( 3 )
						)
				)
				.sort( f -> f.score() )
				.toQuery();

		assertThat( query )
				.hasDocRefHitsExactOrder( INDEX_NAME, DOCUMENT_3, DOCUMENT_1 );
	}

	@Test
	public void predicateLevelBoost_multiFields() {
		StubMappingScope scope = indexManager.createScope();

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.bool()
						.should( f.range().field( indexMapping.string1Field.relativeFieldName )
								.field( indexMapping.string2Field.relativeFieldName )
								.atLeast( indexMapping.string1Field.document3Value.indexedValue )
								.boost( 2 )
						)
						.should( f.range().field( indexMapping.string1Field.relativeFieldName )
								.field( indexMapping.string2Field.relativeFieldName )
								.atMost( indexMapping.string1Field.document1Value.indexedValue )
								.boost( 7 )
						)
				)
				.sort( f -> f.score() )
				.toQuery();

		assertThat( query )
				.hasDocRefHitsExactOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_3 );

		query = scope.query()
				.where( f -> f.bool()
						.should( f.range().field( indexMapping.string1Field.relativeFieldName )
								.field( indexMapping.string2Field.relativeFieldName )
								.atLeast( indexMapping.string1Field.document3Value.indexedValue )
								.boost( 39 )
						)
						.should( f.range().field( indexMapping.string1Field.relativeFieldName )
								.field( indexMapping.string2Field.relativeFieldName )
								.atMost( indexMapping.string1Field.document1Value.indexedValue )
								.boost( 3 )
						)
				)
				.sort( f -> f.score() )
				.toQuery();

		assertThat( query )
				.hasDocRefHitsExactOrder( INDEX_NAME, DOCUMENT_3, DOCUMENT_1 );
	}

	@Test
	public void multi_fields() {
		StubMappingScope scope = indexManager.createScope();

		// field(...).field(...)

		SearchQuery<DocumentReference> query = scope.query()
				.where( f -> f.range().field( indexMapping.string1Field.relativeFieldName )
						.field( indexMapping.string2Field.relativeFieldName )
						.atMost( indexMapping.string1Field.document1Value.indexedValue )
				)
				.toQuery();

		assertThat( query )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1 );

		query = scope.query()
				.where( f -> f.range().field( indexMapping.string1Field.relativeFieldName )
						.field( indexMapping.string2Field.relativeFieldName )
						.atLeast( indexMapping.string2Field.document3Value.indexedValue )
				)
				.toQuery();

		assertThat( query )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_3 );

		// field().fields(...)

		query = scope.query()
				.where( f -> f.range().field( indexMapping.string1Field.relativeFieldName )
						.fields( indexMapping.string2Field.relativeFieldName, indexMapping.string3Field.relativeFieldName )
						.atMost( indexMapping.string1Field.document1Value.indexedValue )
				)
				.toQuery();

		assertThat( query )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1 );

		query = scope.query()
				.where( f -> f.range().field( indexMapping.string1Field.relativeFieldName )
						.fields( indexMapping.string2Field.relativeFieldName, indexMapping.string3Field.relativeFieldName )
						.between( "d", "e" )
				)
				.toQuery();

		assertThat( query )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1 );

		query = scope.query()
				.where( f -> f.range().field( indexMapping.string1Field.relativeFieldName )
						.fields( indexMapping.string2Field.relativeFieldName, indexMapping.string3Field.relativeFieldName )
						.atLeast( indexMapping.string3Field.document3Value.indexedValue )
				)
				.toQuery();

		assertThat( query )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_3 );

		// fields(...)

		query = scope.query()
				.where( f -> f.range().fields( indexMapping.string1Field.relativeFieldName, indexMapping.string2Field.relativeFieldName )
						.atMost( indexMapping.string1Field.document1Value.indexedValue )
				)
				.toQuery();

		assertThat( query )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1 );

		query = scope.query()
				.where( f -> f.range().fields( indexMapping.string1Field.relativeFieldName, indexMapping.string2Field.relativeFieldName )
						.atLeast( indexMapping.string2Field.document3Value.indexedValue )
				)
				.toQuery();

		assertThat( query )
				.hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_3 );
	}

	@Test
	public void multiField_withDslConverter_dslConverterEnabled() {
		SearchQuery<DocumentReference> query = indexManager.createScope().query()
				.where( f -> f.range().field( indexMapping.string1FieldWithDslConverter.relativeFieldName )
						.field( indexMapping.string2FieldWithDslConverter.relativeFieldName )
						.atMost( new ValueWrapper<>( indexMapping.string1FieldWithDslConverter.document1Value.indexedValue ) )
				)
				.toQuery();

		assertThat( query ).hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1 );
	}

	@Test
	public void multiFields_withDslConverter_dslConverterDisabled() {
		SearchQuery<DocumentReference> query = indexManager.createScope().query()
				.where( f -> f.range().field( indexMapping.string1FieldWithDslConverter.relativeFieldName )
						.field( indexMapping.string2FieldWithDslConverter.relativeFieldName )
						.atMost( indexMapping.string1FieldWithDslConverter.document1Value.indexedValue, ValueConvert.NO )
				)
				.toQuery();

		assertThat( query ).hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1 );
	}

	@Test
	public void range_error_null() {
		StubMappingScope scope = indexManager.createScope();

		for ( ByTypeFieldModel<?> fieldModel : indexMapping.supportedFieldModels ) {
			String fieldPath = fieldModel.relativeFieldName;
			Assertions.assertThatThrownBy(
					() -> scope.predicate().range().field( fieldPath )
							.range( null ),
					"range() predicate with null bounds on field " + fieldPath
			)
					.isInstanceOf( IllegalArgumentException.class )
					.hasMessageContaining( "'range'" )
					.hasMessageContaining( "must not be null" );

			Assertions.assertThatThrownBy(
					() -> scope.predicate().range().field( fieldPath )
							.between( null, null ),
					"range() predicate with null bounds on field " + fieldPath
			)
					.isInstanceOf( SearchException.class )
					.hasMessageContaining( "Invalid value" )
					.hasMessageContaining( "at least one bound" )
					.hasMessageContaining( "must be non-null" )
					.hasMessageContaining( fieldPath );

			Assertions.assertThatThrownBy(
					() -> scope.predicate().range().field( fieldPath ).atLeast( null ),
					"range() predicate with null bounds on field " + fieldPath
			)
					.isInstanceOf( IllegalArgumentException.class )
					.hasMessageContaining( "'lowerBoundValue'" )
					.hasMessageContaining( "must not be null" );


			Assertions.assertThatThrownBy(
					() -> scope.predicate().range().field( fieldPath ).atMost( null ),
					"range() predicate with null bounds on field " + fieldPath
			)
					.isInstanceOf( IllegalArgumentException.class )
					.hasMessageContaining( "'upperBoundValue'" )
					.hasMessageContaining( "must not be null" );
		}
	}

	@Test
	public void unknown_field() {
		StubMappingScope scope = indexManager.createScope();

		Assertions.assertThatThrownBy(
				() -> scope.predicate().range().field( "unknown_field" ),
				"range() predicate with unknown field"
		)
				.isInstanceOf( SearchException.class )
				.hasMessageContaining( "Unknown field" )
				.hasMessageContaining( "'unknown_field'" );

		Assertions.assertThatThrownBy(
				() -> scope.predicate().range().fields( indexMapping.string1Field.relativeFieldName, "unknown_field" ),
				"range() predicate with unknown field"
		)
				.isInstanceOf( SearchException.class )
				.hasMessageContaining( "Unknown field" )
				.hasMessageContaining( "'unknown_field'" );

		Assertions.assertThatThrownBy(
				() -> scope.predicate().range().field( indexMapping.string1Field.relativeFieldName ).field( "unknown_field" ),
				"range() predicate with unknown field"
		)
				.isInstanceOf( SearchException.class )
				.hasMessageContaining( "Unknown field" )
				.hasMessageContaining( "'unknown_field'" );

		Assertions.assertThatThrownBy(
				() -> scope.predicate().range().field( indexMapping.string1Field.relativeFieldName ).fields( "unknown_field" ),
				"range() predicate with unknown field"
		)
				.isInstanceOf( SearchException.class )
				.hasMessageContaining( "Unknown field" )
				.hasMessageContaining( "'unknown_field'" );
	}

	@Test
	public void error_invalidType() {
		StubMappingScope scope = indexManager.createScope();

		List<ByTypeFieldModel<?>> fieldModels = new ArrayList<>();
		fieldModels.addAll( indexMapping.supportedFieldModels );
		fieldModels.addAll( indexMapping.supportedFieldWithDslConverterModels );

		for ( ByTypeFieldModel<?> fieldModel : fieldModels ) {
			String absoluteFieldPath = fieldModel.relativeFieldName;
			Object invalidValueToMatch = new InvalidType();

			Assertions.assertThatThrownBy(
					() -> scope.predicate().range().field( absoluteFieldPath ).atLeast( invalidValueToMatch ),
					"range().atLeast() predicate with invalid parameter type on field " + absoluteFieldPath
			)
					.isInstanceOf( SearchException.class )
					.hasMessageContaining( "Unable to convert DSL parameter: " )
					.hasMessageContaining( InvalidType.class.getName() )
					.hasCauseInstanceOf( ClassCastException.class )
					.satisfies( FailureReportUtils.hasContext(
							EventContexts.fromIndexFieldAbsolutePath( absoluteFieldPath )
					) );

			Assertions.assertThatThrownBy(
					() -> scope.predicate().range().field( absoluteFieldPath ).atMost( invalidValueToMatch ),
					"range().atMost() predicate with invalid parameter type on field " + absoluteFieldPath
			)
					.isInstanceOf( SearchException.class )
					.hasMessageContaining( "Unable to convert DSL parameter: " )
					.hasMessageContaining( InvalidType.class.getName() )
					.hasCauseInstanceOf( ClassCastException.class )
					.satisfies( FailureReportUtils.hasContext(
							EventContexts.fromIndexFieldAbsolutePath( absoluteFieldPath )
					) );

			Assertions.assertThatThrownBy(
					() -> scope.predicate().range().field( absoluteFieldPath )
							.between( invalidValueToMatch, null ),
					"range().from() predicate with invalid parameter type on field " + absoluteFieldPath
			)
					.isInstanceOf( SearchException.class )
					.hasMessageContaining( "Unable to convert DSL parameter: " )
					.hasMessageContaining( InvalidType.class.getName() )
					.hasCauseInstanceOf( ClassCastException.class )
					.satisfies( FailureReportUtils.hasContext(
							EventContexts.fromIndexFieldAbsolutePath( absoluteFieldPath )
					) );

			Assertions.assertThatThrownBy(
					() -> scope.predicate().range().field( absoluteFieldPath )
							.between( null, invalidValueToMatch ),
					"range().from().to() predicate with invalid parameter type on field " + absoluteFieldPath
			)
					.isInstanceOf( SearchException.class )
					.hasMessageContaining( "Unable to convert DSL parameter: " )
					.hasMessageContaining( InvalidType.class.getName() )
					.hasCauseInstanceOf( ClassCastException.class )
					.satisfies( FailureReportUtils.hasContext(
							EventContexts.fromIndexFieldAbsolutePath( absoluteFieldPath )
					) );
		}
	}

	@Test
	public void multiIndex_withCompatibleIndexManager_usingField() {
		StubMappingScope scope = indexManager.createScope( compatibleIndexManager );

		for ( ByTypeFieldModel<?> fieldModel : indexMapping.supportedFieldModels ) {
			String absoluteFieldPath = fieldModel.relativeFieldName;
			Object upperValueToMatch = fieldModel.predicateUpperBound;

			SearchQuery<DocumentReference> query = scope.query()
					.where( f -> f.range().field( absoluteFieldPath ).atMost( upperValueToMatch ) )
					.toQuery();

			assertThat( query ).hasDocRefHitsAnyOrder( b -> {
				b.doc( INDEX_NAME, DOCUMENT_1 );
				b.doc( INDEX_NAME, DOCUMENT_2 );
				b.doc( COMPATIBLE_INDEX_NAME, COMPATIBLE_INDEX_DOCUMENT_1 );
			} );
		}
	}

	@Test
	public void multiIndex_withRawFieldCompatibleIndexManager_dslConverterEnabled() {
		for ( ByTypeFieldModel<?> fieldModel : indexMapping.supportedFieldModels ) {
			String absoluteFieldPath = fieldModel.relativeFieldName;
			Object upperValueToMatch = fieldModel.predicateUpperBound;

			Assertions.assertThatThrownBy(
					() -> {
						indexManager.createScope( rawFieldCompatibleIndexManager )
								.predicate().range().field( absoluteFieldPath ).atMost( upperValueToMatch );
					}
			)
					.isInstanceOf( SearchException.class )
					.hasMessageContaining( "Multiple conflicting types to build a predicate" )
					.hasMessageContaining( "'" + fieldModel.relativeFieldName + "'" )
					.satisfies( FailureReportUtils.hasContext(
							EventContexts.fromIndexNames( INDEX_NAME, RAW_FIELD_COMPATIBLE_INDEX_NAME )
					) );
		}
	}

	@Test
	public void multiIndex_withRawFieldCompatibleIndexManager_dslConverterDisabled() {
		StubMappingScope scope = indexManager.createScope( rawFieldCompatibleIndexManager );

		for ( ByTypeFieldModel<?> fieldModel : indexMapping.supportedFieldModels ) {
			String absoluteFieldPath = fieldModel.relativeFieldName;
			Object upperValueToMatch = fieldModel.predicateUpperBound;

			SearchQuery<DocumentReference> query = scope.query()
					.where( f -> f.range().field( absoluteFieldPath ).atMost( upperValueToMatch, ValueConvert.NO ) )
					.toQuery();

			assertThat( query ).hasDocRefHitsAnyOrder( b -> {
				b.doc( INDEX_NAME, DOCUMENT_1 );
				b.doc( INDEX_NAME, DOCUMENT_2 );
				b.doc( RAW_FIELD_COMPATIBLE_INDEX_NAME, RAW_FIELD_COMPATIBLE_INDEX_DOCUMENT_1 );
			} );
		}
	}

	@Test
	public void multiIndex_withNoCompatibleIndexManager_dslConverterEnabled() {
		StubMappingScope scope = indexManager.createScope( incompatibleIndexManager );

		for ( ByTypeFieldModel<?> fieldModel : indexMapping.supportedFieldModels ) {
			String fieldPath = fieldModel.relativeFieldName;

			Assertions.assertThatThrownBy(
					() -> scope.predicate().range().field( fieldPath )
			)
					.isInstanceOf( SearchException.class )
					.hasMessageContaining( "Multiple conflicting types to build a predicate" )
					.hasMessageContaining( "'" + fieldPath + "'" )
					.satisfies( FailureReportUtils.hasContext(
							EventContexts.fromIndexNames( INDEX_NAME, INCOMPATIBLE_INDEX_NAME )
					) );
		}
	}

	@Test
	public void multiIndex_withNoCompatibleIndexManager_dslConverterDisabled() {
		StubMappingScope scope = indexManager.createScope( incompatibleIndexManager );

		for ( ByTypeFieldModel<?> fieldModel : indexMapping.supportedFieldModels ) {
			String fieldPath = fieldModel.relativeFieldName;

			Assertions.assertThatThrownBy(
					() -> scope.predicate().range().field( fieldPath )
			)
					.isInstanceOf( SearchException.class )
					.hasMessageContaining( "Multiple conflicting types to build a predicate" )
					.hasMessageContaining( "'" + fieldPath + "'" )
					.satisfies( FailureReportUtils.hasContext(
							EventContexts.fromIndexNames( INDEX_NAME, INCOMPATIBLE_INDEX_NAME )
					) );
		}
	}

	@Test
	public void multiIndex_incompatibleDecimalScale() {
		StubMappingScope scope = indexManager.createScope( incompatibleDecimalScaleIndexManager );
		String absoluteFieldPath = indexMapping.scaledBigDecimal.relativeFieldName;

		Assertions.assertThatThrownBy(
				() -> {
					scope.query().selectEntityReference()
							.where( f -> f.range().field( absoluteFieldPath ).atLeast( new BigDecimal( "739.333" ) ) )
							.toQuery();
				}
		)
				.isInstanceOf( SearchException.class )
				.hasMessageContaining( "Multiple conflicting types to build a predicate" )
				.hasMessageContaining( "'scaledBigDecimal'" )
				.satisfies( FailureReportUtils.hasContext(
						EventContexts.fromIndexNames( INDEX_NAME, INCOMPATIBLE_DECIMAL_SCALE_INDEX_NAME )
				) );
	}

	@Test
	public void multiIndex_incompatibleSearchable() {
		StubMappingScope scope = indexManager.createScope( unsearchableFieldsIndexManager );

		for ( ByTypeFieldModel<?> fieldModel : indexMapping.supportedFieldModels ) {
			String fieldPath = fieldModel.relativeFieldName;

			Assertions.assertThatThrownBy(
					() -> scope.predicate().range().field( fieldPath )
			)
					.isInstanceOf( SearchException.class )
					.hasMessageContaining( "Multiple conflicting types to build a predicate" )
					.hasMessageContaining( "'" + fieldPath + "'" )
					.satisfies( FailureReportUtils.hasContext(
							EventContexts.fromIndexNames( INDEX_NAME, UNSEARCHABLE_FIELDS_INDEX_NAME )
					) );
		}
	}

	private void initData() {
		IndexIndexingPlan<?> plan = indexManager.createIndexingPlan();
		plan.add( referenceProvider( DOCUMENT_1 ), document -> {
			indexMapping.supportedFieldModels.forEach( f -> f.document1Value.write( document ) );
			indexMapping.supportedFieldWithDslConverterModels.forEach( f -> f.document1Value.write( document ) );
			indexMapping.unsupportedFieldModels.forEach( f -> f.document1Value.write( document ) );
			indexMapping.string1Field.document1Value.write( document );
			indexMapping.string2Field.document1Value.write( document );
			indexMapping.string3Field.document1Value.write( document );
			indexMapping.string1FieldWithDslConverter.document1Value.write( document );
			indexMapping.string2FieldWithDslConverter.document1Value.write( document );
			indexMapping.scaledBigDecimal.document1Value.write( document );
		} );
		plan.add( referenceProvider( DOCUMENT_2 ), document -> {
			indexMapping.supportedFieldModels.forEach( f -> f.document2Value.write( document ) );
			indexMapping.supportedFieldWithDslConverterModels.forEach( f -> f.document2Value.write( document ) );
			indexMapping.unsupportedFieldModels.forEach( f -> f.document2Value.write( document ) );
			indexMapping.string1Field.document2Value.write( document );
			indexMapping.string2Field.document2Value.write( document );
			indexMapping.string3Field.document2Value.write( document );
			indexMapping.string1FieldWithDslConverter.document2Value.write( document );
			indexMapping.string2FieldWithDslConverter.document2Value.write( document );
			indexMapping.scaledBigDecimal.document2Value.write( document );
		} );
		plan.add( referenceProvider( DOCUMENT_3 ), document -> {
			indexMapping.supportedFieldModels.forEach( f -> f.document3Value.write( document ) );
			indexMapping.supportedFieldWithDslConverterModels.forEach( f -> f.document3Value.write( document ) );
			indexMapping.unsupportedFieldModels.forEach( f -> f.document3Value.write( document ) );
			indexMapping.string1Field.document3Value.write( document );
			indexMapping.string2Field.document3Value.write( document );
			indexMapping.string3Field.document3Value.write( document );
			indexMapping.string1FieldWithDslConverter.document3Value.write( document );
			indexMapping.string2FieldWithDslConverter.document3Value.write( document );
			indexMapping.scaledBigDecimal.document3Value.write( document );
		} );
		plan.add( referenceProvider( EMPTY_ID ), document -> { } );
		plan.execute().join();

		plan = compatibleIndexManager.createIndexingPlan();
		plan.add( referenceProvider( COMPATIBLE_INDEX_DOCUMENT_1 ), document -> {
			indexMapping.supportedFieldModels.forEach( f -> f.document1Value.write( document ) );
			indexMapping.supportedFieldWithDslConverterModels.forEach( f -> f.document1Value.write( document ) );
		} );
		plan.execute().join();

		plan = rawFieldCompatibleIndexManager.createIndexingPlan();
		plan.add( referenceProvider( RAW_FIELD_COMPATIBLE_INDEX_DOCUMENT_1 ), document -> {
			rawFieldCompatibleIndexMapping.supportedFieldModels.forEach( f -> f.document1Value.write( document ) );
		} );
		plan.execute().join();

		plan = incompatibleDecimalScaleIndexManager.createIndexingPlan();
		plan.add( referenceProvider( INCOMPATIBLE_DECIMAL_SCALE_INDEX_DOCUMENT_1 ), document -> {
			incompatibleDecimalScaleIndexMapping.scaledBigDecimal.document1Value.write( document );
		} );
		plan.execute().join();

		// Check that all documents are searchable
		SearchQuery<DocumentReference> query = indexManager.createScope().query()
				.where( f -> f.matchAll() )
				.toQuery();
		assertThat( query ).hasDocRefHitsAnyOrder( INDEX_NAME, DOCUMENT_1, DOCUMENT_2, DOCUMENT_3, EMPTY_ID );
		query = compatibleIndexManager.createScope().query()
				.where( f -> f.matchAll() )
				.toQuery();
		assertThat( query ).hasDocRefHitsAnyOrder( COMPATIBLE_INDEX_NAME, COMPATIBLE_INDEX_DOCUMENT_1 );
		query = rawFieldCompatibleIndexManager.createScope().query()
				.where( f -> f.matchAll() )
				.toQuery();
		assertThat( query ).hasDocRefHitsAnyOrder( RAW_FIELD_COMPATIBLE_INDEX_NAME, RAW_FIELD_COMPATIBLE_INDEX_DOCUMENT_1 );
		query = incompatibleDecimalScaleIndexManager.createScope().query()
				.selectEntityReference()
				.where( f -> f.matchAll() )
				.toQuery();
		assertThat( query ).hasDocRefHitsAnyOrder( INCOMPATIBLE_DECIMAL_SCALE_INDEX_NAME, INCOMPATIBLE_DECIMAL_SCALE_INDEX_DOCUMENT_1 );
	}

	private static void forEachTypeDescriptor(Consumer<FieldTypeDescriptor<?>> action) {
		FieldTypeDescriptor.getAll().stream()
				.filter( typeDescriptor -> typeDescriptor.getRangePredicateExpectations().isPresent() )
				.forEach( action );
	}

	private static void mapByTypeFields(IndexSchemaElement parent, String prefix,
			Consumer<StandardIndexFieldTypeOptionsStep<?, ?>> additionalConfiguration,
			FieldModelConsumer<RangePredicateExpectations<?>, ByTypeFieldModel<?>> consumer) {
		forEachTypeDescriptor( typeDescriptor -> {
			// Safe, see forEachTypeDescriptor
			RangePredicateExpectations<?> expectations = typeDescriptor.getRangePredicateExpectations().get();
			ByTypeFieldModel<?> fieldModel = ByTypeFieldModel.mapper( typeDescriptor )
					.map( parent, prefix + typeDescriptor.getUniqueName(), additionalConfiguration );
			consumer.accept( typeDescriptor, expectations, fieldModel );
		} );
	}

	private static class IndexMapping {
		final List<ByTypeFieldModel<?>> supportedFieldModels = new ArrayList<>();
		final List<ByTypeFieldModel<?>> supportedFieldWithDslConverterModels = new ArrayList<>();
		final List<ByTypeFieldModel<?>> unsupportedFieldModels = new ArrayList<>();

		final MainFieldModel<String> string1Field;
		final MainFieldModel<String> string2Field;
		final MainFieldModel<String> string3Field;

		final MainFieldModel<String> string1FieldWithDslConverter;
		final MainFieldModel<String> string2FieldWithDslConverter;

		final MainFieldModel<BigDecimal> scaledBigDecimal;

		IndexMapping(IndexSchemaElement root) {
			mapByTypeFields(
					root, "byType_", ignored -> { },
					(typeDescriptor, expectations, model) -> {
						if ( expectations.isRangePredicateSupported() ) {
							supportedFieldModels.add( model );
						}
						else {
							unsupportedFieldModels.add( model );
						}
					}
			);
			mapByTypeFields(
					root, "byType_converted_", c -> c.dslConverter( ValueWrapper.class, ValueWrapper.toIndexFieldConverter() ),
					(typeDescriptor, expectations, model) -> {
						if ( expectations.isRangePredicateSupported() ) {
							supportedFieldWithDslConverterModels.add( model );
						}
					}
			);
			string1Field = MainFieldModel.mapper( "ccc", "mmm", "xxx" )
					.map( root, "string1" );
			string2Field = MainFieldModel.mapper( "ddd", "nnn", "yyy" )
					.map( root, "string2" );
			string3Field = MainFieldModel.mapper( "eee", "ooo", "zzz" )
					.map( root, "string3" );
			string1FieldWithDslConverter = MainFieldModel.mapper(
					c -> c.asString().dslConverter( ValueWrapper.class, ValueWrapper.toIndexFieldConverter() ),
					"ccc", "mmm", "xxx"
			)
					.map( root, "string1FieldWithDslConverter" );
			string2FieldWithDslConverter = MainFieldModel.mapper(
					c -> c.asString().dslConverter( ValueWrapper.class, ValueWrapper.toIndexFieldConverter() ),
					"ddd", "nnn", "yyy"
			)
					.map( root, "string2FieldWithDslConverter" );
			scaledBigDecimal = MainFieldModel.mapper(
					c -> c.asBigDecimal().decimalScale( 3 ),
					new BigDecimal( "739.739" ), BigDecimal.ONE, BigDecimal.TEN
			)
					.map( root, "scaledBigDecimal" );
		}
	}

	private static class RawFieldCompatibleIndexMapping {
		final List<ByTypeFieldModel<?>> supportedFieldModels = new ArrayList<>();

		RawFieldCompatibleIndexMapping(IndexSchemaElement root) {
			/*
			 * Add fields with the same name as the supportedFieldModels from IndexMapping,
			 * but with an incompatible DSL converter.
			 */
			mapByTypeFields(
					root, "byType_", c -> c.dslConverter( ValueWrapper.class, ValueWrapper.toIndexFieldConverter() ),
					(typeDescriptor, expectations, model) -> {
						if ( expectations.isRangePredicateSupported() ) {
							supportedFieldModels.add( model );
						}
					}
			);
		}
	}

	private static class NotCompatibleIndexMapping {
		NotCompatibleIndexMapping(IndexSchemaElement root) {
			/*
			 * Add fields with the same name as the supportedFieldModels from IndexMapping,
			 * but with an incompatible type.
			 */
			forEachTypeDescriptor( typeDescriptor -> {
				StandardFieldMapper<?, IncompatibleFieldModel> mapper;
				if ( Integer.class.equals( typeDescriptor.getJavaType() ) ) {
					mapper = IncompatibleFieldModel.mapper( context -> context.asLong() );
				}
				else {
					mapper = IncompatibleFieldModel.mapper( context -> context.asInteger() );
				}
				mapper.map( root, "byType_" + typeDescriptor.getUniqueName() );
			} );
		}
	}

	private static class IncompatibleDecimalScaleIndexMapping {
		final MainFieldModel<BigDecimal> scaledBigDecimal;

		/*
		 * Unlike IndexMapping#scaledBigDecimal,
		 * we're using here a different decimal scale for the field.
		 */
		IncompatibleDecimalScaleIndexMapping(IndexSchemaElement root) {
			scaledBigDecimal = MainFieldModel.mapper(
					c -> c.asBigDecimal().decimalScale( 7 ),
					new BigDecimal( "739.739" ), BigDecimal.ONE, BigDecimal.TEN
			)
					.map( root, "scaledBigDecimal" );
		}
	}

	private static class UnsearchableFieldsIndexMapping {
		final List<ByTypeFieldModel<?>> supportedFieldModels = new ArrayList<>();

		@SuppressWarnings("unchecked")
		UnsearchableFieldsIndexMapping(IndexSchemaElement root) {
			mapByTypeFields(
					root, "byType_",
					// make the field not searchable
					c -> c.searchable( Searchable.NO ),
					(typeDescriptor, expectations, model) -> {
						if ( expectations.isRangePredicateSupported() ) {
							supportedFieldModels.add( model );
						}
					}
			);
		}
	}

	private static class ValueModel<F> {
		private final IndexFieldReference<F> reference;
		final F indexedValue;

		private ValueModel(IndexFieldReference<F> reference, F indexedValue) {
			this.reference = reference;
			this.indexedValue = indexedValue;
		}

		public void write(DocumentElement target) {
			target.addValue( reference, indexedValue );
		}
	}

	private static class MainFieldModel<T> {
		static StandardFieldMapper<String, MainFieldModel<String>> mapper(
				String document1Value, String document2Value, String document3Value) {
			return mapper( c -> c.asString(), document1Value, document2Value, document3Value );
		}

		static <LT> StandardFieldMapper<LT, MainFieldModel<LT>> mapper(
				Function<IndexFieldTypeFactory, StandardIndexFieldTypeOptionsStep<?, LT>> configuration,
				LT document1Value, LT document2Value, LT document3Value) {
			return StandardFieldMapper.of(
					configuration,
					(reference, name) -> new MainFieldModel<>( reference, name, document1Value, document2Value, document3Value )
			);
		}

		final String relativeFieldName;
		final ValueModel<T> document1Value;
		final ValueModel<T> document2Value;
		final ValueModel<T> document3Value;

		private MainFieldModel(IndexFieldReference<T> reference, String relativeFieldName,
				T document1Value, T document2Value, T document3Value) {
			this.relativeFieldName = relativeFieldName;
			this.document1Value = new ValueModel<>( reference, document1Value );
			this.document3Value = new ValueModel<>( reference, document3Value );
			this.document2Value = new ValueModel<>( reference, document2Value );
		}
	}

	private static class ByTypeFieldModel<F> {
		static <F> StandardFieldMapper<F, ByTypeFieldModel<F>> mapper(FieldTypeDescriptor<F> typeDescriptor) {
			// Safe, see caller
			RangePredicateExpectations<F> expectations = typeDescriptor.getRangePredicateExpectations().get();
			return StandardFieldMapper.of(
					typeDescriptor::configure,
					(reference, name) -> new ByTypeFieldModel<>( reference, name, expectations, typeDescriptor.getJavaType() )
			);
		}

		final String relativeFieldName;
		final ValueModel<F> document1Value;
		final ValueModel<F> document2Value;
		final ValueModel<F> document3Value;

		final F predicateLowerBound;
		final F predicateUpperBound;

		final Class<F> javaType;

		private ByTypeFieldModel(IndexFieldReference<F> reference, String relativeFieldName,
				RangePredicateExpectations<F> expectations, Class<F> javaType) {
			this.relativeFieldName = relativeFieldName;
			this.document1Value = new ValueModel<>( reference, expectations.getDocument1Value() );
			this.document2Value = new ValueModel<>( reference, expectations.getDocument2Value() );
			this.document3Value = new ValueModel<>( reference, expectations.getDocument3Value() );
			this.predicateLowerBound = expectations.getBetweenDocument1And2Value();
			this.predicateUpperBound = expectations.getBetweenDocument2And3Value();
			this.javaType = javaType;
		}
	}

	private static class IncompatibleFieldModel {
		static <F> StandardFieldMapper<F, IncompatibleFieldModel> mapper(
				Function<IndexFieldTypeFactory, StandardIndexFieldTypeOptionsStep<?, F>> configuration) {
			return StandardFieldMapper.of(
					configuration,
					(reference, name) -> new IncompatibleFieldModel( name )
			);
		}

		final String relativeFieldName;

		private IncompatibleFieldModel(String relativeFieldName) {
			this.relativeFieldName = relativeFieldName;
		}
	}
}
