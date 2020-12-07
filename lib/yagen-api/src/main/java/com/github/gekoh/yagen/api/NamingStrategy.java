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
package com.github.gekoh.yagen.api;

import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.mapping.Constraint;

/**
 * @author Georg Kohlweiss 
 */
public interface NamingStrategy extends PhysicalNamingStrategy {

    String tableName(String tableName);

    String columnName(String columnName);

    String collectionTableName(String ownerEntity, String ownerEntityTable, String associatedEntity, String associatedEntityTable, String propertyName);

    String classToTableName(String className);

    /**
     * Return a table short name for an entity class
     * @param className the fully-qualified class name
     * @return a table short name
     */
    String classToTableShortName(String className);
    /**
     * Alter the table short name given by annotation {@link Table} or static String field named TABLE_NAME_SHORT
     * @param tableShortName a table short name
     * @return a table short name
     */
    String tableShortName(String tableShortName);
    /**
     * basically only generates a short name for a given table name if there is no other way to go
     * @param tableName a table name
     * @return a table short name
     */
    String tableShortNameFromTableName(String tableName);

    String constraintName(String constraintName);

    String constraintName(Constraint constraint, String entityClass);

    String constraintName(UniqueConstraint constraint);

    String constraintName(CheckConstraint constraint);

    String constraintName(String entityClass, String tableName, String colName, String suffix);

    String indexName(String indexName);

    String indexName(Index index);

    String indexName(String entityClass, String tableName, String colName);

    String triggerName(String triggerName);

    String triggerName(String entityClass, String tableName, String colName, String suffix);

    String sequenceName(String sequenceName);
}