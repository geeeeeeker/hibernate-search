/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.mapper.javabean.mapping.impl;

import org.hibernate.search.mapper.pojo.mapping.building.spi.PojoContainedTypeExtendedMappingCollector;

/*
 * TODO HSEARCH-1800 There's nothing here at the moment, just a placeholder.
 *  We may want to use an actual implementation once we allow users to plug in their own loading.
 */
class JavaBeanContainedTypeContext {

	private JavaBeanContainedTypeContext() {
	}

	static class Builder<E> implements PojoContainedTypeExtendedMappingCollector {
		Builder() {
		}
	}
}