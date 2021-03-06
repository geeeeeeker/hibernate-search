/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.integrationtest.backend.tck.search.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMapperUtils.referenceProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hibernate.search.engine.backend.document.DocumentElement;
import org.hibernate.search.engine.backend.document.IndexFieldReference;
import org.hibernate.search.engine.backend.document.IndexObjectFieldReference;
import org.hibernate.search.engine.backend.document.model.dsl.IndexSchemaElement;
import org.hibernate.search.engine.backend.document.model.dsl.IndexSchemaObjectField;
import org.hibernate.search.engine.backend.document.model.dsl.ObjectFieldStorage;
import org.hibernate.search.engine.backend.types.Aggregable;
import org.hibernate.search.engine.backend.work.execution.spi.IndexIndexer;
import org.hibernate.search.engine.search.aggregation.AggregationKey;
import org.hibernate.search.engine.search.aggregation.SearchAggregation;
import org.hibernate.search.engine.search.aggregation.dsl.AggregationFinalStep;
import org.hibernate.search.engine.search.aggregation.dsl.SearchAggregationFactory;
import org.hibernate.search.engine.search.predicate.dsl.PredicateFinalStep;
import org.hibernate.search.engine.search.predicate.dsl.SearchPredicateFactory;
import org.hibernate.search.integrationtest.backend.tck.testsupport.operations.AggregationDescriptor;
import org.hibernate.search.integrationtest.backend.tck.testsupport.operations.expectations.AggregationScenario;
import org.hibernate.search.integrationtest.backend.tck.testsupport.operations.expectations.SupportedSingleFieldAggregationExpectations;
import org.hibernate.search.integrationtest.backend.tck.testsupport.types.FieldTypeDescriptor;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.IndexFieldStructure;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.IndexFieldValueCardinality;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.SimpleFieldModelsByType;
import org.hibernate.search.integrationtest.backend.tck.testsupport.util.rule.SearchSetupHelper;
import org.hibernate.search.util.impl.integrationtest.common.assertion.SearchResultAssert;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.SimpleMappedIndex;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMappingScope;
import org.hibernate.search.util.impl.test.annotation.PortedFromSearch5;
import org.hibernate.search.util.impl.test.annotation.TestForIssue;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Tests basic behavior common to all single-field aggregations (range, terms, ...)
 * on supported types.
 */
@RunWith(Parameterized.class)
public class SingleFieldAggregationBaseIT<F> {

	private static final int INIT_BATCH_SIZE = 500;

	private static final String AGGREGATION_NAME = "aggregationName";

	private static Set<FieldTypeDescriptor<?>> supportedFieldTypes;
	private static List<DataSet<?>> dataSets;

	@Parameterized.Parameters(name = "{0} - {1} - {2}")
	public static Object[][] parameters() {
		supportedFieldTypes = new LinkedHashSet<>();
		dataSets = new ArrayList<>();
		List<Object[]> parameters = new ArrayList<>();
		for ( AggregationDescriptor aggregationDescriptor : AggregationDescriptor.getAll() ) {
			for ( FieldTypeDescriptor<?> fieldTypeDescriptor : FieldTypeDescriptor.getAll() ) {
				Optional<? extends SupportedSingleFieldAggregationExpectations<?>> expectations =
						aggregationDescriptor.getSingleFieldAggregationExpectations( fieldTypeDescriptor ).getSupported();
				if ( expectations.isPresent() ) {
					for ( IndexFieldStructure fieldStructure : IndexFieldStructure.values() ) {
						for ( IndexFieldValueCardinality valueCardinality : IndexFieldValueCardinality.values() ) {
							supportedFieldTypes.add( fieldTypeDescriptor );
							DataSet<?> dataSet = new DataSet<>( expectations.get(), fieldStructure, valueCardinality );
							dataSets.add( dataSet );
							parameters.add( new Object[] { expectations.get(), fieldStructure, valueCardinality, dataSet } );
						}
					}
				}
			}
		}
		return parameters.toArray( new Object[0][] );
	}

	@ClassRule
	public static final SearchSetupHelper setupHelper = new SearchSetupHelper();

	private static final SimpleMappedIndex<IndexBinding> mainIndex =
			SimpleMappedIndex.of( "Main", IndexBinding::new );
	private static final SimpleMappedIndex<IndexBinding> emptyIndex =
			SimpleMappedIndex.of( "Empty", IndexBinding::new );
	private static final SimpleMappedIndex<IndexBinding> nullOnlyIndex =
			SimpleMappedIndex.of( "NullOnly", IndexBinding::new );

	@BeforeClass
	public static void setup() {
		setupHelper.start().withIndexes( mainIndex, emptyIndex, nullOnlyIndex ).setup();

		List<CompletableFuture<?>> futures = new ArrayList<>();
		for ( DataSet<?> dataSet : dataSets ) {
			dataSet.init( futures::add );
			if ( futures.size() >= INIT_BATCH_SIZE ) {
				CompletableFuture.allOf( futures.toArray( new CompletableFuture[0] ) ).join();
				futures.clear();
			}
		}

		CompletableFuture.allOf( futures.toArray( new CompletableFuture[0] ) ).join();

		CompletableFuture.allOf(
				mainIndex.createWorkspace().flush(),
				mainIndex.createWorkspace().refresh(),
				nullOnlyIndex.createWorkspace().flush(),
				nullOnlyIndex.createWorkspace().refresh()
		).join();
	}

	private final SupportedSingleFieldAggregationExpectations<F> expectations;
	private final FieldTypeDescriptor<F> fieldType;
	private final IndexFieldStructure fieldStructure;
	private final IndexFieldValueCardinality valueCardinality;
	private final DataSet<F> dataSet;

	public SingleFieldAggregationBaseIT(SupportedSingleFieldAggregationExpectations<F> expectations,
			IndexFieldStructure fieldStructure, IndexFieldValueCardinality valueCardinality,
			DataSet<F> dataSet) {
		this.expectations = expectations;
		this.fieldType = expectations.fieldType();
		this.fieldStructure = fieldStructure;
		this.valueCardinality = valueCardinality;
		this.dataSet = dataSet;
	}

	@Test
	@TestForIssue(jiraKey = {
			"HSEARCH-726", "HSEARCH-900", "HSEARCH-809",
			"HSEARCH-2376", "HSEARCH-2472", "HSEARCH-2954", "HSEARCH-2535",
			"HSEARCH-1927", "HSEARCH-1929",
			"HSEARCH-3881"
	})
	@PortedFromSearch5(original = {
			"org.hibernate.search.test.query.facet.NumberFacetingTest",
			"org.hibernate.search.test.query.facet.RangeFacetingTest.testRangeQueryForInteger",
			"org.hibernate.search.test.query.facet.RangeFacetingTest.testDateRangeFaceting",
			"org.hibernate.search.test.query.facet.SimpleFacetingTest.testSimpleDiscretFaceting",
			"org.hibernate.search.test.query.facet.StringFacetingTest",
			"org.hibernate.search.test.query.facet.EmbeddedCollectionFacetingTest",
			"org.hibernate.search.test.query.facet.ManyToOneFacetingTest",
			"org.hibernate.search.test.query.facet.MultiValuedFacetingTest"
	})
	public void simple() {
		// Need a separate method to handle the scenario generics
		doTest_simple( getSimpleScenario() );
	}

	private <A> void doTest_simple(AggregationScenario<A> scenario) {
		StubMappingScope scope = mainIndex.createScope();
		String fieldPath = getFieldPath( mainIndex.binding() );
		AggregationKey<A> aggregationKey = AggregationKey.of( AGGREGATION_NAME );

		SearchResultAssert.assertThat(
				scope.query()
						.where( f -> f.matchAll() )
						.aggregation( aggregationKey, f -> scenario.setup( f, fieldPath, getFilterOrNull( mainIndex.binding() ) ) )
						.routing( dataSet.name )
						.toQuery()
		)
				.aggregation(
						aggregationKey,
						a -> assertThat( a ).isNotNull().satisfies( scenario::check )
				);
	}

	private Function<? super SearchPredicateFactory, ? extends PredicateFinalStep> getFilterOrNull(IndexBinding binding) {
		if ( fieldStructure == IndexFieldStructure.IN_NESTED_REQUIRING_FILTER ) {
			return pf -> pf.match()
					.field( binding.nestedObjectRequiringFilter.relativeFieldName + ".discriminator" )
					.matching( "included" );
		}
		else {
			return null;
		}
	}

	@Test
	public void aggregationObject() {
		// Need a separate method to handle the scenario generics
		doTest_aggregationObject( getSimpleScenario() );
	}

	private <A> void doTest_aggregationObject(AggregationScenario<A> scenario) {
		StubMappingScope scope = mainIndex.createScope();
		String fieldPath = getFieldPath( mainIndex.binding() );
		AggregationKey<A> aggregationKey = AggregationKey.of( AGGREGATION_NAME );

		SearchAggregation<A> aggregation = scenario.setup( scope.aggregation(), fieldPath, getFilterOrNull( mainIndex.binding() ) )
				.toAggregation();

		SearchResultAssert.assertThat(
				mainIndex.createScope().query()
						.where( f -> f.matchAll() )
						.aggregation( aggregationKey, aggregation )
						.routing( dataSet.name )
						.toQuery()
		)
				.aggregation(
						aggregationKey,
						a -> assertThat( a ).isNotNull().satisfies( scenario::check )
				);
	}

	@Test
	@TestForIssue(jiraKey = { "HSEARCH-1968" })
	@PortedFromSearch5(original = "org.hibernate.search.test.query.facet.NoQueryResultsFacetingTest")
	public void noMatch() {
		String fieldPath = getFieldPath( mainIndex.binding() );

		AggregationScenario<?> scenario = expectations.withoutMatch();
		testValidAggregation(
				scenario, mainIndex.createScope(),
				f -> f.id().matching( "none" ), // Don't match any document
				(f, e) -> e.setup( f, fieldPath, getFilterOrNull( mainIndex.binding() ) )
		);
	}

	/**
	 * Test behavior when aggregating on an index with no value for the targeted field
	 * because there is no document in the mainIndex.
	 */
	@Test
	@TestForIssue(jiraKey = { "HSEARCH-2955", "HSEARCH-745" })
	@PortedFromSearch5(original = {
			"org.hibernate.search.test.facet.NoIndexedValueFacetingTest",
			"org.hibernate.search.test.query.facet.EdgeCaseFacetTest"
	})
	public void emptyIndex() {
		String fieldPath = getFieldPath( emptyIndex.binding() );

		AggregationScenario<?> scenario = expectations.withoutMatch();
		testValidAggregation(
				scenario, emptyIndex.createScope(), fieldPath, null
		);
	}

	/**
	 * Test behavior when aggregating on an index with no value for the targeted field
	 * because no document has a non-null value for this field.
	 */
	@Test
	@TestForIssue(jiraKey = "HSEARCH-2955")
	@PortedFromSearch5(original = "org.hibernate.search.test.facet.NoIndexedValueFacetingTest")
	public void nullOnlyIndex() {
		String fieldPath = getFieldPath( nullOnlyIndex.binding() );

		AggregationScenario<?> scenario = expectations.withoutMatch();
		testValidAggregation(
				scenario, nullOnlyIndex.createScope(), fieldPath, null
		);
	}

	private <A> void testValidAggregation(AggregationScenario<A> scenario, StubMappingScope scope,
			String fieldPath, Function<? super SearchPredicateFactory, ? extends PredicateFinalStep> filterOrNull) {
		testValidAggregation(
				scenario, scope,
				f -> f.matchAll(),
				(f, e) -> e.setup( f, fieldPath, filterOrNull )
		);
	}

	private <A> void testValidAggregation(AggregationScenario<A> scenario, StubMappingScope scope,
			Function<SearchPredicateFactory, ? extends PredicateFinalStep> predicateContributor,
			BiFunction<SearchAggregationFactory, AggregationScenario<A>, AggregationFinalStep<A>> aggregationContributor) {
		AggregationKey<A> aggregationKey = AggregationKey.of( AGGREGATION_NAME );
		SearchResultAssert.assertThat(
				scope.query()
						.where( predicateContributor )
						.aggregation( aggregationKey, f -> aggregationContributor.apply( f, scenario ) )
						.routing( dataSet.name )
						.toQuery()
		)
				.aggregation(
						aggregationKey,
						a -> assertThat( a ).isNotNull().satisfies( scenario::check )
				);
	}

	private AggregationScenario<?> getSimpleScenario() {
		AggregationScenario<?> scenario;
		if ( IndexFieldValueCardinality.SINGLE_VALUED.equals( valueCardinality ) ) {
			scenario = expectations.simple();
		}
		else {
			scenario = expectations.onMultiValuedIndex();
		}
		return scenario;
	}

	private String getFieldPath(IndexBinding indexBinding) {
		switch ( fieldStructure ) {
			case ROOT:
				return getRelativeFieldName( indexBinding );
			case IN_FLATTENED:
				return indexBinding.flattenedObject.relativeFieldName
						+ "." + getRelativeFieldName( indexBinding.flattenedObject );
			case IN_NESTED:
				return indexBinding.nestedObject.relativeFieldName
						+ "." + getRelativeFieldName( indexBinding.nestedObject );
			case IN_NESTED_TWICE:
				return indexBinding.nestedObject.relativeFieldName
						+ "." + indexBinding.nestedObject.nestedObject.relativeFieldName
						+ "." + getRelativeFieldName( indexBinding.nestedObject.nestedObject );
			case IN_NESTED_REQUIRING_FILTER:
				return indexBinding.nestedObjectRequiringFilter.relativeFieldName
						+ "." + getRelativeFieldName( indexBinding.nestedObjectRequiringFilter );
			default:
				throw new IllegalStateException( "Unexpected value: " + fieldStructure );
		}
	}

	private String getRelativeFieldName(AbstractObjectBinding binding) {
		return getFieldModelsByType( binding ).get( fieldType ).relativeFieldName;
	}

	private SimpleFieldModelsByType getFieldModelsByType(AbstractObjectBinding binding) {
		switch ( valueCardinality ) {
			case SINGLE_VALUED:
				return binding.fieldWithSingleValueModels;
			case MULTI_VALUED:
				return binding.fieldWithMultipleValuesModels;
			default:
				throw new IllegalStateException( "Unexpected field value cardinality: " + valueCardinality );
		}
	}

	private static class DataSet<F> {
		final SupportedSingleFieldAggregationExpectations<F> expectations;
		final FieldTypeDescriptor<F> fieldType;
		final String name;
		private final IndexFieldStructure fieldStructure;
		private final IndexFieldValueCardinality valueCardinality;

		private DataSet(SupportedSingleFieldAggregationExpectations<F> expectations,
				IndexFieldStructure fieldStructure, IndexFieldValueCardinality valueCardinality) {
			this.expectations = expectations;
			this.fieldType = expectations.fieldType();
			this.name = expectations.aggregationName() + "_" + expectations.fieldType().getUniqueName()
					+ "_" + fieldStructure.name() + "_" + valueCardinality.name();
			this.fieldStructure = fieldStructure;
			this.valueCardinality = valueCardinality;
		}

		private void init(Consumer<CompletableFuture<?>> futureCollector) {
			IndexIndexer indexer = mainIndex.createIndexer();
			if ( IndexFieldValueCardinality.SINGLE_VALUED.equals( valueCardinality ) ) {
				List<F> values = expectations.getMainIndexDocumentFieldValues();
				for ( int i = 0; i < values.size(); i++ ) {
					F valueForDocument = values.get( i );
					F garbageValueForDocument = values.get( i == 0 ? 1 : i - 1 );
					futureCollector.accept(
							indexer.add( referenceProvider( name + "_document_" + i, name ), document -> {
								initSingleValued( mainIndex.binding(), document,
										valueForDocument, garbageValueForDocument );
							} )
					);
				}
			}
			else {
				List<List<F>> values = expectations.getMultiValuedIndexDocumentFieldValues();
				for ( int i = 0; i < values.size(); i++ ) {
					List<F> valuesForDocument = values.get( i );
					List<F> garbageValuesForDocument = values.get( i == 0 ? 1 : i - 1 );
					futureCollector.accept(
							indexer.add( referenceProvider( name + "_document_" + i, name ), document -> {
								initMultiValued( mainIndex.binding(), document,
										valuesForDocument, garbageValuesForDocument );
							} )
					);
				}
			}
			futureCollector.accept(
					indexer.add( referenceProvider( name + "_document_empty", name ), document -> { } )
			);

			indexer = nullOnlyIndex.createIndexer();
			futureCollector.accept(
				indexer.add( referenceProvider( name + "_nullOnlyIndex_document_0", name ), document -> {
					if ( IndexFieldValueCardinality.SINGLE_VALUED.equals( valueCardinality ) ) {
						initSingleValued( nullOnlyIndex.binding(), document, null, null );
					}
					else {
						initMultiValued( nullOnlyIndex.binding(), document, Arrays.asList( null, null ), Arrays.asList( null, null ) );
					}
				} )
			);
		}

		private void initSingleValued(IndexBinding binding, DocumentElement document, F value, F garbageValue) {
			switch ( fieldStructure ) {
				case ROOT:
					document.addValue( binding.fieldWithSingleValueModels.get( fieldType ).reference, value );
					break;
				case IN_FLATTENED:
					DocumentElement flattenedObject = document.addObject( binding.flattenedObject.self );
					flattenedObject.addValue( binding.flattenedObject.fieldWithSingleValueModels.get( fieldType ).reference,
							value
					);
					break;
				case IN_NESTED:
					DocumentElement nestedObject = document.addObject( binding.nestedObject.self );
					nestedObject.addValue( binding.nestedObject.fieldWithSingleValueModels.get( fieldType ).reference,
							value
					);
					break;
				case IN_NESTED_TWICE:
					DocumentElement nestedObjectFirstLevel = document.addObject( binding.nestedObject.self );
					DocumentElement nestedObjectSecondLevel =
							nestedObjectFirstLevel.addObject( binding.nestedObject.nestedObject.self );
					nestedObjectSecondLevel.addValue(
							binding.nestedObject.nestedObject.fieldWithSingleValueModels.get( fieldType ).reference,
							value
					);
					break;
				case IN_NESTED_REQUIRING_FILTER:
					// The nested object requiring filters is split into two objects:
					// the first one is included by the filter and holds the value that will be aggregated,
					// and the second one is excluded by the filter and holds a garbage value that, if they were taken into account,
					// would mess with the sort order and eventually fail at least *some* tests.
					DocumentElement nestedObjectRequiringFilter_1 = document.addObject( binding.nestedObjectRequiringFilter.self );
					nestedObjectRequiringFilter_1.addValue( binding.nestedObjectRequiringFilter.discriminator, "included" );
					nestedObjectRequiringFilter_1.addValue(
							binding.nestedObjectRequiringFilter.fieldWithSingleValueModels.get( fieldType ).reference,
							value
					);
					DocumentElement nestedObjectRequiringFilter_2 = document.addObject( binding.nestedObjectRequiringFilter.self );
					nestedObjectRequiringFilter_2.addValue( binding.nestedObjectRequiringFilter.discriminator, "excluded" );
					nestedObjectRequiringFilter_2.addValue(
							binding.nestedObjectRequiringFilter.fieldWithSingleValueModels.get( fieldType ).reference,
							garbageValue
					);
			}
		}

		private void initMultiValued(IndexBinding binding, DocumentElement document, List<F> values, List<F> garbageValues) {
			switch ( fieldStructure ) {
				case ROOT:
					for ( F value : values ) {
						document.addValue( binding.fieldWithMultipleValuesModels.get( fieldType ).reference, value );
					}
					break;
				case IN_FLATTENED:
					DocumentElement flattenedObject = document.addObject( binding.flattenedObject.self );
					for ( F value : values ) {
						flattenedObject.addValue(
								binding.flattenedObject.fieldWithMultipleValuesModels.get( fieldType ).reference,
								value
						);
					}
					break;
				case IN_NESTED:
					// Make sure to create multiple nested documents here, to test all the scenarios.
					DocumentElement nestedObject1 = document.addObject( binding.nestedObject.self );
					nestedObject1.addValue(
							binding.nestedObject.fieldWithMultipleValuesModels.get( fieldType ).reference,
							values.get( 0 )
					);
					DocumentElement nestedObject2 = document.addObject( binding.nestedObject.self );
					for ( F value : values.subList( 1, values.size() ) ) {
						nestedObject2.addValue(
								binding.nestedObject.fieldWithMultipleValuesModels.get( fieldType ).reference,
								value
						);
					}
					break;
				case IN_NESTED_TWICE:
					DocumentElement nestedObjectFirstLevel = document.addObject( binding.nestedObject.self );
					DocumentElement nestedObjectSecondLevel =
							nestedObjectFirstLevel.addObject( binding.nestedObject.nestedObject.self );
					for ( F value : values ) {
						nestedObjectSecondLevel.addValue(
								binding.nestedObject.nestedObject.fieldWithMultipleValuesModels.get( fieldType ).reference,
								value
						);
					}
					break;
				case IN_NESTED_REQUIRING_FILTER:
					// The nested object requiring filters is split into four objects:
					// the first two are included by the filter and each hold part of the values that will be sorted on,
					// and the last two are excluded by the filter and hold garbage values that, if they were taken into account,
					// would mess with the sort order and eventually fail at least *some* tests.
					DocumentElement nestedObjectRequiringFilter_1 = document.addObject( binding.nestedObjectRequiringFilter.self );
					nestedObjectRequiringFilter_1.addValue( binding.nestedObjectRequiringFilter.discriminator, "included" );
					nestedObjectRequiringFilter_1.addValue(
							binding.nestedObjectRequiringFilter.fieldWithMultipleValuesModels.get( fieldType ).reference,
							values.get( 0 )
					);
					DocumentElement nestedObjectRequiringFilter_2 = document.addObject( binding.nestedObjectRequiringFilter.self );
					nestedObjectRequiringFilter_2.addValue( binding.nestedObjectRequiringFilter.discriminator, "included" );
					for ( F value : values.subList( 1, values.size() ) ) {
						nestedObjectRequiringFilter_2.addValue(
								binding.nestedObjectRequiringFilter.fieldWithMultipleValuesModels.get( fieldType ).reference,
								value
						);
					}
					DocumentElement nestedObjectRequiringFilter_3 = document.addObject( binding.nestedObjectRequiringFilter.self );
					nestedObjectRequiringFilter_3.addValue( binding.nestedObjectRequiringFilter.discriminator, "excluded" );
					for ( F value : garbageValues ) {
						nestedObjectRequiringFilter_3.addValue(
								binding.nestedObjectRequiringFilter.fieldWithMultipleValuesModels.get( fieldType ).reference,
								value
						);
					}
					DocumentElement nestedObjectRequiringFilter_4 = document.addObject( binding.nestedObjectRequiringFilter.self );
					nestedObjectRequiringFilter_4.addValue( binding.nestedObjectRequiringFilter.discriminator, "excluded" );
					for ( F value : garbageValues ) {
						nestedObjectRequiringFilter_4.addValue(
								binding.nestedObjectRequiringFilter.fieldWithMultipleValuesModels.get( fieldType ).reference,
								value
						);
					}
			}
		}
	}

	private static class AbstractObjectBinding {
		final SimpleFieldModelsByType fieldWithSingleValueModels;
		final SimpleFieldModelsByType fieldWithMultipleValuesModels;

		AbstractObjectBinding(IndexSchemaElement self) {
			fieldWithSingleValueModels = SimpleFieldModelsByType.mapAll( supportedFieldTypes, self,
					"", c -> c.aggregable( Aggregable.YES ) );
			fieldWithMultipleValuesModels = SimpleFieldModelsByType.mapAllMultiValued( supportedFieldTypes, self,
					"multiValued_", c -> c.aggregable( Aggregable.YES ) );
		}
	}

	private static class IndexBinding extends AbstractObjectBinding {
		final FirstLevelObjectBinding flattenedObject;
		final FirstLevelObjectBinding nestedObject;
		final FirstLevelObjectBinding nestedObjectRequiringFilter;

		IndexBinding(IndexSchemaElement root) {
			super( root );
			flattenedObject = FirstLevelObjectBinding.create( root, "flattenedObject", ObjectFieldStorage.FLATTENED );
			nestedObject = FirstLevelObjectBinding.create( root, "nestedObject", ObjectFieldStorage.NESTED, true );
			nestedObjectRequiringFilter = FirstLevelObjectBinding.create( root, "nestedObjectRequiringFilter",
					ObjectFieldStorage.NESTED, true );
		}
	}

	private static class FirstLevelObjectBinding extends AbstractObjectBinding {
		final String relativeFieldName;
		final IndexObjectFieldReference self;

		final IndexFieldReference<String> discriminator;

		final SecondLevelObjectBinding nestedObject;


		public static FirstLevelObjectBinding create(IndexSchemaElement parent, String relativeFieldName,
				ObjectFieldStorage storage) {
			return create( parent, relativeFieldName, storage, false );
		}

		public static FirstLevelObjectBinding create(IndexSchemaElement parent, String relativeFieldName,
				ObjectFieldStorage storage,
				boolean multiValued) {
			IndexSchemaObjectField objectField = parent.objectField( relativeFieldName, storage );
			if ( multiValued ) {
				objectField.multiValued();
			}
			return new FirstLevelObjectBinding( relativeFieldName, objectField );
		}

		FirstLevelObjectBinding(String relativeFieldName, IndexSchemaObjectField objectField) {
			super( objectField );
			this.relativeFieldName = relativeFieldName;
			self = objectField.toReference();
			discriminator = objectField.field( "discriminator", f -> f.asString() ).toReference();
			nestedObject = SecondLevelObjectBinding.create(
					objectField, "nestedObject", ObjectFieldStorage.NESTED
			);
		}
	}

	private static class SecondLevelObjectBinding extends AbstractObjectBinding {
		final String relativeFieldName;
		final IndexObjectFieldReference self;

		public static SecondLevelObjectBinding create(IndexSchemaElement parent, String relativeFieldName,
				ObjectFieldStorage storage) {
			IndexSchemaObjectField objectField = parent.objectField( relativeFieldName, storage );
			return new SecondLevelObjectBinding( relativeFieldName, objectField );
		}

		SecondLevelObjectBinding(String relativeFieldName, IndexSchemaObjectField objectField) {
			super( objectField );
			this.relativeFieldName = relativeFieldName;
			self = objectField.toReference();
		}
	}

}
