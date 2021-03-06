/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.backend.lucene.search.query.impl;

import org.hibernate.search.backend.lucene.search.extraction.impl.LuceneCollectors;
import org.hibernate.search.engine.backend.session.spi.BackendSessionContext;
import org.hibernate.search.engine.search.loading.context.spi.LoadingContext;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;

/**
 * The context holding all the useful information pertaining to the Lucene search query,
 * to be used when extracting data from the response,
 * to get an "extract" context linked to the session/loading context
 * ({@link #createExtractContext(IndexSearcher, LuceneCollectors)}.
 */
class LuceneSearchQueryRequestContext {

	private final BackendSessionContext sessionContext;
	private final LoadingContext<?, ?> loadingContext;
	private final Query luceneQuery;
	private final Sort luceneSort;

	LuceneSearchQueryRequestContext(
			BackendSessionContext sessionContext,
			LoadingContext<?, ?> loadingContext,
			Query luceneQuery,
			Sort luceneSort) {
		this.sessionContext = sessionContext;
		this.loadingContext = loadingContext;
		this.luceneQuery = luceneQuery;
		this.luceneSort = luceneSort;
	}

	Query getLuceneQuery() {
		return luceneQuery;
	}

	Sort getLuceneSort() {
		return luceneSort;
	}

	LuceneSearchQueryExtractContext createExtractContext(IndexSearcher indexSearcher,
			LuceneCollectors luceneCollectors) {
		return new LuceneSearchQueryExtractContext(
				sessionContext,
				loadingContext.getProjectionHitMapper(),
				indexSearcher,
				luceneQuery,
				luceneCollectors
		);
	}

}
