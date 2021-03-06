/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.backend.lucene.types.sort.impl;

import java.lang.invoke.MethodHandles;

import org.hibernate.search.backend.lucene.logging.impl.Log;
import org.hibernate.search.backend.lucene.search.sort.impl.LuceneSearchSortBuilder;
import org.hibernate.search.backend.lucene.search.sort.impl.LuceneSearchSortCollector;
import org.hibernate.search.backend.lucene.types.sort.comparatorsource.impl.LuceneGeoPointDistanceComparatorSource;
import org.hibernate.search.engine.search.common.SortMode;
import org.hibernate.search.engine.search.sort.dsl.SortOrder;
import org.hibernate.search.engine.search.sort.spi.DistanceSortBuilder;
import org.hibernate.search.engine.spatial.GeoPoint;
import org.hibernate.search.util.common.logging.impl.LoggerFactory;

import org.apache.lucene.search.SortField;

public class LuceneGeoPointDistanceSortBuilder extends AbstractLuceneDocumentValueSortBuilder
		implements DistanceSortBuilder<LuceneSearchSortBuilder> {

	private static final Log log = LoggerFactory.make( Log.class, MethodHandles.lookup() );

	private final GeoPoint location;

	LuceneGeoPointDistanceSortBuilder(String absoluteFieldPath, String nestedDocumentPath, GeoPoint location) {
		super( absoluteFieldPath, nestedDocumentPath );
		this.location = location;
	}

	@Override
	public void mode(SortMode mode) {
		switch ( mode ) {
			case MIN:
			case MAX:
			case AVG:
			case MEDIAN:
				super.mode( mode );
				break;
			case SUM:
			default:
				throw log.cannotComputeSumForDistanceSort( getEventContext() );
		}
	}

	@Override
	public void buildAndContribute(LuceneSearchSortCollector collector) {
		LuceneGeoPointDistanceComparatorSource fieldComparatorSource = new LuceneGeoPointDistanceComparatorSource(
				nestedDocumentPath, location, getMultiValueMode(), getLuceneFilter() );
		SortField sortField = new SortField( absoluteFieldPath, fieldComparatorSource, order == SortOrder.DESC );
		collector.collectSortField( sortField, ( nestedDocumentPath == null ) ? null : fieldComparatorSource );
	}
}
