/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.backend.lucene.search.predicate.impl;

import java.util.List;

import org.hibernate.search.backend.lucene.lowlevel.query.impl.Queries;
import org.hibernate.search.engine.search.predicate.spi.NestedPredicateBuilder;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.join.BitSetProducer;
import org.apache.lucene.search.join.ScoreMode;
import org.apache.lucene.search.join.ToParentBlockJoinQuery;

class LuceneNestedPredicateBuilder extends AbstractLuceneSingleFieldPredicateBuilder
		implements NestedPredicateBuilder<LuceneSearchPredicateBuilder> {

	private LuceneSearchPredicateBuilder nestedBuilder;

	LuceneNestedPredicateBuilder(String absoluteFieldPath, List<String> nestedPathHierarchy) {
		super(
				absoluteFieldPath,
				// The given list includes absoluteFieldPath at the end, but here we don't want it to be included.
				nestedPathHierarchy.subList( 0, nestedPathHierarchy.size() - 1 )
		);
	}

	@Override
	public void nested(LuceneSearchPredicateBuilder nestedBuilder) {
		nestedBuilder.checkNestableWithin( absoluteFieldPath );
		this.nestedBuilder = nestedBuilder;
	}

	@Override
	protected Query doBuild(LuceneSearchPredicateContext context) {
		LuceneSearchPredicateContext childContext = new LuceneSearchPredicateContext( absoluteFieldPath );
		return doBuild( context.getNestedPath(), absoluteFieldPath, nestedBuilder.build( childContext ) );
	}

	public static Query doBuild(String parentNestedDocumentPath, String nestedDocumentPath, Query nestedQuery) {
		if ( nestedDocumentPath.equals( parentNestedDocumentPath ) ) {
			return nestedQuery;
		}

		BooleanQuery.Builder childQueryBuilder = new BooleanQuery.Builder();
		childQueryBuilder.add( Queries.childDocumentQuery(), Occur.FILTER );
		childQueryBuilder.add( Queries.nestedDocumentPathQuery( nestedDocumentPath ), Occur.FILTER );
		childQueryBuilder.add( nestedQuery, Occur.MUST );

		// Note: this filter should include *all* parents, not just the matched ones.
		// Otherwise we will not "see" non-matched parents,
		// and we will consider its matching children as children of the next matching parent.
		BitSetProducer parentFilter = Queries.parentFilter( parentNestedDocumentPath );

		// TODO HSEARCH-3090 at some point we should have a parameter for the score mode
		return new ToParentBlockJoinQuery( childQueryBuilder.build(), parentFilter, ScoreMode.Avg );
	}
}
