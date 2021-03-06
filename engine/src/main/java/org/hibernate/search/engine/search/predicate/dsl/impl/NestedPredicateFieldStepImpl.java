/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.engine.search.predicate.dsl.impl;

import java.lang.invoke.MethodHandles;
import java.util.function.Function;

import org.hibernate.search.engine.logging.impl.Log;
import org.hibernate.search.engine.search.predicate.SearchPredicate;
import org.hibernate.search.engine.search.predicate.dsl.NestedPredicateOptionsStep;
import org.hibernate.search.engine.search.predicate.dsl.PredicateFinalStep;
import org.hibernate.search.engine.search.predicate.dsl.SearchPredicateFactory;
import org.hibernate.search.engine.search.predicate.dsl.NestedPredicateFieldStep;
import org.hibernate.search.engine.search.predicate.dsl.NestedPredicateNestStep;
import org.hibernate.search.engine.search.predicate.dsl.spi.AbstractPredicateFinalStep;
import org.hibernate.search.engine.search.predicate.spi.NestedPredicateBuilder;
import org.hibernate.search.engine.search.predicate.spi.SearchPredicateBuilderFactory;
import org.hibernate.search.util.common.logging.impl.LoggerFactory;


class NestedPredicateFieldStepImpl<B>
		extends AbstractPredicateFinalStep<B>
		implements NestedPredicateFieldStep<NestedPredicateNestStep<?>>,
				NestedPredicateNestStep<NestedPredicateOptionsStep<?>>,
				NestedPredicateOptionsStep<NestedPredicateOptionsStep<?>> {

	private static final Log log = LoggerFactory.make( Log.class, MethodHandles.lookup() );

	private final SearchPredicateFactory factory;
	private NestedPredicateBuilder<B> builder;
	private B childPredicateBuilder;

	NestedPredicateFieldStepImpl(SearchPredicateBuilderFactory<?, B> builderFactory, SearchPredicateFactory factory) {
		super( builderFactory );
		this.factory = factory;
	}

	@Override
	public NestedPredicateNestStep<?> objectField(String absoluteFieldPath) {
		this.builder = builderFactory.nested( absoluteFieldPath );
		return this;
	}

	@Override
	public NestedPredicateOptionsStep<?> nest(SearchPredicate searchPredicate) {
		if ( this.childPredicateBuilder != null ) {
			throw log.cannotAddMultiplePredicatesToNestedPredicate();
		}
		this.childPredicateBuilder = builderFactory.toImplementation( searchPredicate );
		builder.nested( childPredicateBuilder );
		return this;
	}

	@Override
	public NestedPredicateOptionsStep<?> nest(
			Function<? super SearchPredicateFactory, ? extends PredicateFinalStep> predicateContributor) {
		return nest( predicateContributor.apply( factory ) );
	}

	@Override
	protected B toImplementation() {
		return builder.toImplementation();
	}

}
