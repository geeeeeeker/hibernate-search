/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.backend.lucene.search.aggregation.dsl.impl;

import org.hibernate.search.backend.lucene.search.aggregation.impl.LuceneSearchAggregationBuilderFactory;
import org.hibernate.search.backend.lucene.search.aggregation.dsl.LuceneSearchAggregationFactory;
import org.hibernate.search.backend.lucene.search.predicate.dsl.LuceneSearchPredicateFactory;
import org.hibernate.search.engine.search.aggregation.dsl.SearchAggregationFactory;
import org.hibernate.search.engine.search.aggregation.dsl.spi.DelegatingSearchAggregationFactory;
import org.hibernate.search.engine.search.aggregation.dsl.spi.SearchAggregationDslContext;

public class LuceneSearchAggregationFactoryImpl
		extends DelegatingSearchAggregationFactory<LuceneSearchPredicateFactory>
		implements LuceneSearchAggregationFactory {

	public LuceneSearchAggregationFactoryImpl(SearchAggregationFactory delegate,
			SearchAggregationDslContext<LuceneSearchAggregationBuilderFactory, LuceneSearchPredicateFactory> dslContext) {
		super( delegate, dslContext );
	}

	// Empty: no extension at the moment.

}
