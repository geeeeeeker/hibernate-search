/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.integrationtest.performance.backend.base.testsupport.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.search.engine.cfg.EngineSettings;
import org.hibernate.search.engine.cfg.spi.ConfigurationPropertyChecker;
import org.hibernate.search.engine.cfg.spi.ConfigurationPropertySource;
import org.hibernate.search.engine.common.spi.SearchIntegration;
import org.hibernate.search.engine.common.spi.SearchIntegrationBuilder;
import org.hibernate.search.engine.common.spi.SearchIntegrationFinalizer;
import org.hibernate.search.engine.common.spi.SearchIntegrationPartialBuildState;
import org.hibernate.search.integrationtest.performance.backend.base.testsupport.filesystem.TemporaryFileHolder;
import org.hibernate.search.util.common.impl.SuppressingCloser;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMapping;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMappingInitiator;
import org.hibernate.search.util.impl.integrationtest.mapper.stub.StubMappingKey;

import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Thread)
public abstract class AbstractBackendHolder {

	public static final int INDEX_COUNT = 3;

	private static final String BACKEND_NAME = "testedBackend";

	private SearchIntegration integration;
	private List<MappedIndex> indexes;

	@Setup(Level.Trial)
	public void startHibernateSearch(TemporaryFileHolder temporaryFileHolder) throws IOException {
		Map<String, Object> baseProperties = new LinkedHashMap<>();
		baseProperties.put( EngineSettings.DEFAULT_BACKEND, BACKEND_NAME );

		ConfigurationPropertySource propertySource = ConfigurationPropertySource.fromMap( baseProperties )
				.withOverride(
						getDefaultBackendProperties( temporaryFileHolder )
								// Allow overrides at the backend level using system properties
								.withOverride( ConfigurationPropertySource.system() )
								.withPrefix( EngineSettings.BACKENDS + "." + BACKEND_NAME )
				);

		ConfigurationPropertyChecker unusedPropertyChecker = ConfigurationPropertyChecker.create();

		SearchIntegrationBuilder integrationBuilder =
				SearchIntegration.builder( propertySource, unusedPropertyChecker );

		StubMappingInitiator initiator = new StubMappingInitiator( false );
		StubMappingKey mappingKey = new StubMappingKey();
		integrationBuilder.addMappingInitiator( mappingKey, initiator );

		indexes = new ArrayList<>();
		for ( int i = 0; i < INDEX_COUNT; ++i ) {
			MappedIndex index = new MappedIndex();
			indexes.add( index );
			initiator.add(
					"type_" + i, BACKEND_NAME, "index_" + i,
					index::bind
			);
		}

		SearchIntegrationPartialBuildState integrationPartialBuildState = integrationBuilder.prepareBuild();
		try {
			SearchIntegrationFinalizer finalizer =
					integrationPartialBuildState.finalizer( propertySource, unusedPropertyChecker );
			StubMapping mapping = finalizer.finalizeMapping(
					mappingKey, (context, partialMapping) -> partialMapping.finalizeMapping()
			);
			for ( int i = 0; i < INDEX_COUNT; ++i ) {
				MappedIndex index = indexes.get( i );
				String typeId = "type_" + i;
				index.setIndexManager( mapping.getIndexMappingByTypeIdentifier( typeId ) );
			}
			integration = finalizer.finalizeIntegration();
		}
		catch (RuntimeException e) {
			new SuppressingCloser( e )
					.push( SearchIntegrationPartialBuildState::closeOnFailure, integrationPartialBuildState );
			throw e;
		}
	}

	@Setup(Level.Iteration)
	public void initializeIndexes(IndexInitializer indexInitializer) {
		indexInitializer.intializeIndexes( indexes );
	}

	@TearDown(Level.Trial)
	public void stopHibernateSearch() {
		if ( integration != null ) {
			integration.close();
		}
	}

	public List<MappedIndex> getIndexes() {
		return indexes;
	}

	protected abstract ConfigurationPropertySource getDefaultBackendProperties(TemporaryFileHolder temporaryFileHolder)
			throws IOException;

}