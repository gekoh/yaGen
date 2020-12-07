/*
 Copyright 2014 Georg Kohlweiss

 Licensed under the Apache License, Version 2.0 (the License);
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an AS IS BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/
package com.github.gekoh.yagen.hibernate;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.Dialect;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.AbstractSingleColumnStandardBasicType;
import org.hibernate.type.BooleanType;
import org.hibernate.type.DiscriminatorType;
import org.hibernate.type.NumericBooleanType;
import org.hibernate.type.PrimitiveType;
import org.hibernate.type.descriptor.java.BooleanTypeDescriptor;
import org.hibernate.type.descriptor.sql.BitTypeDescriptor;

import java.io.Serializable;

/**
 * Usage: create custom {@link Dialect} subclass where you
 * override {@link Dialect#contributeTypes(TypeContributions, ServiceRegistry)} and add this type, e.g.:
 * <pre>
 * public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
 *     super.contributeTypes(typeContributions, serviceRegistry);
 *     typeContributions.contributeType(new BitBooleanType());
 * }
 * </pre>
 * No type-annotations in entities needed - this type will be automatically used for {@code boolean} and {@code Boolean} fields.
 *
 * @author Georg Kohlweiss
 */
public class BitBooleanType extends AbstractSingleColumnStandardBasicType<Boolean>
        implements PrimitiveType<Boolean>, DiscriminatorType<Boolean> {

    public BitBooleanType() {
        super(BitTypeDescriptor.INSTANCE, BooleanTypeDescriptor.INSTANCE);
    }

    @Override
    public Boolean stringToObject(String xml) throws Exception {
        return NumericBooleanType.INSTANCE.stringToObject(xml);
    }

    @Override
    public Class getPrimitiveClass() {
        return NumericBooleanType.INSTANCE.getPrimitiveClass();
    }

    @Override
    public Serializable getDefaultValue() {
        return NumericBooleanType.INSTANCE.getDefaultValue();
    }

    @Override
    public String objectToSQLString(Boolean value, Dialect dialect) throws Exception {
        return NumericBooleanType.INSTANCE.objectToSQLString(value, dialect);
    }

    @Override
    public String getName() {
        return NumericBooleanType.INSTANCE.getName();
    }

    @Override
    public String[] getRegistrationKeys() {
        return new String[] {
                NumericBooleanType.class.getName(),
                NumericBooleanType.INSTANCE.getName(),
                BooleanType.INSTANCE.getName(),
                NumericBooleanType.INSTANCE.getJavaTypeDescriptor().getJavaType().getName()
        };
    }
}