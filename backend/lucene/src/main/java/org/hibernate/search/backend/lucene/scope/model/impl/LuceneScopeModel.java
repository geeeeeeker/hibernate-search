/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.backend.lucene.scope.model.impl;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.search.backend.lucene.document.model.impl.LuceneIndexModel;
import org.hibernate.search.backend.lucene.document.model.impl.LuceneIndexSchemaFieldNode;
import org.hibernate.search.backend.lucene.document.model.impl.LuceneIndexSchemaObjectNode;
import org.hibernate.search.backend.lucene.logging.impl.Log;
import org.hibernate.search.backend.lucene.types.predicate.impl.LuceneObjectPredicateBuilderFactory;
import org.hibernate.search.backend.lucene.types.predicate.impl.LuceneObjectPredicateBuilderFactoryImpl;
import org.hibernate.search.engine.backend.document.model.dsl.ObjectFieldStorage;
import org.hibernate.search.engine.backend.types.converter.spi.ToDocumentIdentifierValueConverter;
import org.hibernate.search.engine.reporting.spi.EventContexts;
import org.hibernate.search.util.common.logging.impl.LoggerFactory;
import org.hibernate.search.util.common.reporting.EventContext;

public class LuceneScopeModel {

	private static final Log log = LoggerFactory.make( Log.class, MethodHandles.lookup() );

	private final Set<LuceneIndexModel> indexModels;
	private final Set<String> indexNames;
	private final Set<LuceneScopeIndexManagerContext> indexManagerContexts;

	public LuceneScopeModel(Set<LuceneIndexModel> indexModels,
			Set<LuceneScopeIndexManagerContext> indexManagerContexts) {
		this.indexModels = indexModels;
		this.indexNames = indexModels.stream()
				.map( LuceneIndexModel::getIndexName )
				.collect( Collectors.toSet() );
		this.indexManagerContexts = indexManagerContexts;
	}

	public Set<String> getIndexNames() {
		return indexNames;
	}

	public EventContext getIndexesEventContext() {
		return EventContexts.fromIndexNames( indexNames );
	}

	public Set<LuceneScopeIndexManagerContext> getIndexManagerContexts() {
		return indexManagerContexts;
	}

	public LuceneScopedIndexRootComponent<ToDocumentIdentifierValueConverter<?>> getIdDslConverter() {
		Iterator<LuceneIndexModel> iterator = indexModels.iterator();
		LuceneIndexModel indexModelForSelectedIdConverter = null;
		ToDocumentIdentifierValueConverter<?> selectedIdConverter = null;
		LuceneScopedIndexRootComponent<ToDocumentIdentifierValueConverter<?>> scopedIndexFieldComponent =
				new LuceneScopedIndexRootComponent<>();

		while ( iterator.hasNext() ) {
			LuceneIndexModel indexModel = iterator.next();
			ToDocumentIdentifierValueConverter<?> idConverter = indexModel.getIdDslConverter();

			if ( selectedIdConverter == null ) {
				indexModelForSelectedIdConverter = indexModel;
				selectedIdConverter = idConverter;
				scopedIndexFieldComponent.setComponent( selectedIdConverter );
				continue;
			}

			if ( !selectedIdConverter.isCompatibleWith( idConverter ) ) {
				LuceneFailingIdCompatibilityChecker failingCompatibilityChecker =
						new LuceneFailingIdCompatibilityChecker(
								selectedIdConverter, idConverter,
								EventContexts.fromIndexNames(
										indexModelForSelectedIdConverter.getIndexName(),
										indexModel.getIndexName()
								)
						);
				scopedIndexFieldComponent.setIdConverterCompatibilityChecker( failingCompatibilityChecker );
			}
		}

		return scopedIndexFieldComponent;
	}

	public LuceneObjectPredicateBuilderFactory getObjectPredicateBuilderFactory(String absoluteFieldPath) {
		LuceneObjectPredicateBuilderFactory result = null;

		LuceneIndexSchemaObjectNode objectNode = null;
		String objectNodeIndexName = null;
		LuceneIndexSchemaFieldNode<?> fieldNode = null;
		String fieldNodeIndexName = null;

		for ( LuceneIndexModel indexModel : indexModels ) {
			String indexName = indexModel.getIndexName();

			LuceneIndexSchemaFieldNode<?> currentFieldNode = indexModel.getFieldNode( absoluteFieldPath );
			if ( currentFieldNode != null ) {
				fieldNode = currentFieldNode;
				fieldNodeIndexName = indexName;
				if ( objectNode != null ) {
					throw log.conflictingFieldModel( absoluteFieldPath, objectNode, fieldNode,
							EventContexts.fromIndexNames( objectNodeIndexName, indexName )
					);
				}
				continue;
			}

			LuceneIndexSchemaObjectNode currentObjectNode = indexModel.getObjectNode( absoluteFieldPath );
			if ( currentObjectNode == null ) {
				continue;
			}

			if ( fieldNode != null ) {
				throw log.conflictingFieldModel( absoluteFieldPath, currentObjectNode, fieldNode,
						EventContexts.fromIndexNames( fieldNodeIndexName, indexName )
				);
			}

			LuceneObjectPredicateBuilderFactoryImpl predicateBuilderFactory = new LuceneObjectPredicateBuilderFactoryImpl( indexModel, currentObjectNode );
			if ( result == null ) {
				result = predicateBuilderFactory;
				objectNode = currentObjectNode;
				objectNodeIndexName = indexName;
				continue;
			}

			if ( !result.isCompatibleWith( predicateBuilderFactory ) ) {
				throw log.conflictingObjectFieldModel( absoluteFieldPath, objectNode, currentObjectNode,
						EventContexts.fromIndexNames( objectNodeIndexName, indexName )
				);
			}
		}
		return result;
	}

	public <T> LuceneScopedIndexFieldComponent<T> getSchemaNodeComponent(String absoluteFieldPath,
			IndexSchemaFieldNodeComponentRetrievalStrategy<T> componentRetrievalStrategy) {
		LuceneIndexModel indexModelForSelectedSchemaNode = null;
		LuceneIndexSchemaFieldNode<?> selectedSchemaNode = null;
		LuceneScopedIndexFieldComponent<T> scopedIndexFieldComponent = new LuceneScopedIndexFieldComponent<>();

		for ( LuceneIndexModel indexModel : indexModels ) {
			LuceneIndexSchemaFieldNode<?> schemaNode = indexModel.getFieldNode( absoluteFieldPath );
			if ( schemaNode == null ) {
				continue;
			}

			T component = componentRetrievalStrategy.extractComponent( schemaNode );
			if ( selectedSchemaNode == null ) {
				selectedSchemaNode = schemaNode;
				indexModelForSelectedSchemaNode = indexModel;
				scopedIndexFieldComponent.setComponent( component );
				continue;
			}

			if ( !componentRetrievalStrategy.hasCompatibleCodec( scopedIndexFieldComponent.getComponent(), component ) ) {
				throw componentRetrievalStrategy.createCompatibilityException(
						absoluteFieldPath,
						scopedIndexFieldComponent.getComponent(),
						component,
						EventContexts.fromIndexNames(
								indexModelForSelectedSchemaNode.getIndexName(),
								indexModel.getIndexName()
						)
				);
			}

			LuceneFailingFieldCompatibilityChecker<T> failingCompatibilityChecker = new LuceneFailingFieldCompatibilityChecker<>(
					absoluteFieldPath, scopedIndexFieldComponent.getComponent(), component, EventContexts.fromIndexNames(
					indexModelForSelectedSchemaNode.getIndexName(), indexModel.getIndexName()
			), componentRetrievalStrategy );

			if ( !componentRetrievalStrategy.hasCompatibleConverter( scopedIndexFieldComponent.getComponent(), component ) ) {
				scopedIndexFieldComponent.setConverterCompatibilityChecker( failingCompatibilityChecker );
			}
			if ( !componentRetrievalStrategy.hasCompatibleAnalyzer( scopedIndexFieldComponent.getComponent(), component ) ) {
				scopedIndexFieldComponent.setAnalyzerCompatibilityChecker( failingCompatibilityChecker );
			}
		}
		if ( selectedSchemaNode == null ) {
			throw log.unknownFieldForSearch( absoluteFieldPath, getIndexesEventContext() );
		}
		return scopedIndexFieldComponent;
	}

	public void checkNestedField(String absoluteFieldPath) {
		boolean found = false;

		for ( LuceneIndexModel indexModel : indexModels ) {
			LuceneIndexSchemaObjectNode schemaNode = indexModel.getObjectNode( absoluteFieldPath );
			if ( schemaNode != null ) {
				found = true;
				if ( !ObjectFieldStorage.NESTED.equals( schemaNode.getStorage() ) ) {
					throw log.nonNestedFieldForNestedQuery(
							absoluteFieldPath, indexModel.getEventContext()
					);
				}
			}
		}
		if ( !found ) {
			for ( LuceneIndexModel indexModel : indexModels ) {
				LuceneIndexSchemaFieldNode<?> schemaNode = indexModel.getFieldNode( absoluteFieldPath );
				if ( schemaNode != null ) {
					throw log.nonObjectFieldForNestedQuery(
							absoluteFieldPath, indexModel.getEventContext()
					);
				}
			}
			throw log.unknownFieldForSearch( absoluteFieldPath, getIndexesEventContext() );
		}
	}

	public String getNestedDocumentPath(String absoluteFieldPath) {
		Optional<String> nestedDocumentPath = indexModels.stream()
				.map( indexModel -> indexModel.getFieldNode( absoluteFieldPath ) )
				.filter( Objects::nonNull )
				.map( fieldNode -> Optional.ofNullable( fieldNode.getNestedDocumentPath() ) )
				.reduce( (nestedDocumentPath1, nestedDocumentPath2) -> {
					if ( Objects.equals( nestedDocumentPath1, nestedDocumentPath2 ) ) {
						return nestedDocumentPath1;
					}

					throw log.conflictingNestedDocumentPaths(
							absoluteFieldPath, nestedDocumentPath1.orElse( null ), nestedDocumentPath2.orElse( null ), getIndexesEventContext() );
				} )
				.orElse( Optional.empty() );

		return nestedDocumentPath.orElse( null );
	}

	public List<String> getNestedPathHierarchyForField(String absoluteFieldPath) {
		Optional<List<String>> nestedDocumentPath = indexModels.stream()
				.map( indexModel -> indexModel.getFieldNode( absoluteFieldPath ) )
				.filter( Objects::nonNull )
				.map( node -> Optional.ofNullable( node.getNestedPathHierarchy() ) )
				.reduce( (nestedDocumentPath1, nestedDocumentPath2) -> {
					if ( Objects.equals( nestedDocumentPath1, nestedDocumentPath2 ) ) {
						return nestedDocumentPath1;
					}

					throw log.conflictingNestedDocumentPathHierarchy(
							absoluteFieldPath, nestedDocumentPath1.orElse( null ), nestedDocumentPath2.orElse( null ), getIndexesEventContext() );
				} )
				.orElse( Optional.empty() );

		return nestedDocumentPath.orElse( Collections.emptyList() );
	}

	public List<String> getNestedPathHierarchyForObject(String absoluteFieldPath) {
		Optional<List<String>> nestedDocumentPath = indexModels.stream()
				.map( indexModel -> indexModel.getObjectNode( absoluteFieldPath ) )
				.filter( Objects::nonNull )
				.map( node -> Optional.ofNullable( node.getNestedPathHierarchy() ) )
				.reduce( (nestedDocumentPath1, nestedDocumentPath2) -> {
					if ( Objects.equals( nestedDocumentPath1, nestedDocumentPath2 ) ) {
						return nestedDocumentPath1;
					}

					throw log.conflictingNestedDocumentPathHierarchy(
							absoluteFieldPath, nestedDocumentPath1.orElse( null ), nestedDocumentPath2.orElse( null ), getIndexesEventContext() );
				} )
				.orElse( Optional.empty() );

		return nestedDocumentPath.orElse( Collections.emptyList() );
	}
}
