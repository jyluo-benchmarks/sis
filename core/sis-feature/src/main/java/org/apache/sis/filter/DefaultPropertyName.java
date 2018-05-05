/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sis.filter;

import java.util.Map;
import java.util.Objects;
import java.io.Serializable;
import org.apache.sis.util.ArgumentChecks;
import org.apache.sis.util.collection.BackingStoreException;
import org.apache.sis.feature.builder.FeatureTypeBuilder;

// Branch-dependent imports
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureType;
import org.opengis.feature.IdentifiedType;
import org.opengis.feature.Operation;
import org.opengis.feature.PropertyType;
import org.opengis.feature.PropertyNotFoundException;
import org.opengis.filter.expression.ExpressionVisitor;
import org.opengis.filter.expression.PropertyName;


/**
 * Immutable property name expression.
 * A property name does not store any value, it acts as an indirection to a
 * property value of the evaluated feature.
 *
 * @author  Johann Sorel (Geomatys)
 * @version 1.0
 * @since   1.0
 * @module
 */
final class DefaultPropertyName extends AbstractExpression implements PropertyName, Serializable {
    /**
     * For cross-version compatibility.
     */
    private static final long serialVersionUID = -8474562134021521300L;

    private final String property;

    /**
     *
     * @param property attribute name
     */
    DefaultPropertyName(final String property) {
        ArgumentChecks.ensureNonNull("property", property);
        this.property = property;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public String getPropertyName() {
        return property;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public Object evaluate(final Object candidate) {
        if (candidate instanceof Feature) {
            try {
                return ((Feature) candidate).getPropertyValue(property);
            } catch (PropertyNotFoundException ex) {
                return null;
            }
        } else if (candidate instanceof Map<?,?>) {
            return ((Map<?,?>) candidate).get(property);
        }
        return null;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public PropertyType expectedType(FeatureType type) {
        PropertyType propertyType = type.getProperty(property);
        while (propertyType instanceof Operation) {
            IdentifiedType it = ((Operation) propertyType).getResult();
            if (it instanceof FeatureType) {
                propertyType = new FeatureTypeBuilder().addAssociation(type).setName(property).build();
            } else if (it instanceof PropertyType) {
                propertyType = (PropertyType) it;
            } else {
                throw new BackingStoreException("Unexpected operation result type "+it);
            }
        }
        return propertyType;
    }

    /**
     * Accepts a visitor.
     */
    @Override
    public Object accept(final ExpressionVisitor visitor, final Object extraData) {
        return visitor.visit(this, extraData);
    }

    /**
     * Returns a hash-code value for this property.
     */
    @Override
    public boolean equals(final Object obj) {
        return (obj instanceof DefaultPropertyName) && property.equals(((DefaultPropertyName) obj).property);
    }

    /**
     * Returns a hash-code value for this property.
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(property) ^ (int) serialVersionUID;
    }

    /**
     * Returns a string representation of this property.
     */
    @Override
    public String toString() {
        return '{' + property + '}';
    }
}
