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

import org.apache.commons.lang.StringUtils;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.mapping.Constraint;
import org.hibernate.mapping.ForeignKey;
import org.hibernate.mapping.UniqueKey;

import java.lang.reflect.Field;
import java.util.regex.Pattern;

/**
 * @author Georg Kohlweiss 
 */
public class DefaultNamingStrategy implements NamingStrategy {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DefaultNamingStrategy.class);

    private static final Pattern NONAME_KEY_PATTERN = Pattern.compile("key[0-9]*");

    @Override
    public Identifier toPhysicalCatalogName(Identifier name, JdbcEnvironment jdbcEnvironment) {
        return name;
    }

    @Override
    public Identifier toPhysicalSchemaName(Identifier name, JdbcEnvironment jdbcEnvironment) {
        return name;
    }

    @Override
    public Identifier toPhysicalTableName(Identifier name, JdbcEnvironment jdbcEnvironment) {
        return name != null ? Identifier.toIdentifier(tableName(name.getText())) : null;
    }

    @Override
    public Identifier toPhysicalSequenceName(Identifier name, JdbcEnvironment jdbcEnvironment) {
        return name != null ? Identifier.toIdentifier(sequenceName(name.getText())) : null;
    }

    @Override
    public Identifier toPhysicalColumnName(Identifier name, JdbcEnvironment jdbcEnvironment) {
        return name;
    }

    public String tableName(String tableName) {
        return tableName;
    }

    @Override
    public String classToTableName(String className) {
        try {
            Class<?> aClass = Class.forName(className);
            if (aClass.isAnnotationPresent(javax.persistence.Table.class)) {
                String tableName = aClass.getAnnotation(javax.persistence.Table.class).name();
                if (StringUtils.isNotEmpty(tableName)) {
                    return tableName(tableName);
                }
            }
        } catch (ClassNotFoundException ignore) {
        }
        return StringHelper.unqualify( className );
    }

    @Override
    public String classToTableShortName(String className) {
        try {
            Class<?> aClass = Class.forName(className);
            String tableShortName = null;
            if (aClass.isAnnotationPresent(Table.class)) {
                tableShortName = aClass.getAnnotation(Table.class).shortName();
            }
            if (StringUtils.isEmpty(tableShortName)) {
                try {
                    Field tblShortNameField = aClass.getDeclaredField("TABLE_NAME_SHORT");
                    if (tblShortNameField != null) {
                        if (!tblShortNameField.isAccessible()) {
                            tblShortNameField.setAccessible(true);
                        }
                        tableShortName = tblShortNameField.get(null).toString();
                    }
                } catch (Exception ignore) {
                }
            }
            if (StringUtils.isEmpty(tableShortName)) {
                tableShortName = tableShortNameFromTableName(classToTableName(className));
            }
            return tableShortName(tableShortName);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("cannot get table short name from class " + className, e);
        }
    }

    public String columnName(String columnName) {
        return columnName;
    }

    public String collectionTableName(String ownerEntity, String ownerEntityTable, String associatedEntity, String associatedEntityTable, String propertyName) {
        return tableName(
                new StringBuilder( ownerEntityTable ).append( "_" )
                        .append(
                                associatedEntityTable != null ?
                                        associatedEntityTable :
                                        StringHelper.unqualify( propertyName )
                        ).toString()
        );
    }

    @Override
    public String tableShortName(String tableShortName) {
        return tableShortName.toLowerCase();
    }

    @Override
    public String tableShortNameFromTableName(String tableName) {
        return tableShortName(generateShortName(tableName, null, null));
    }

    @Override
    public String constraintName(String constraintName) {
        return constraintName;
    }

    @Override
    public String constraintName(Constraint constraint, String entityClass) {
        String name = constraint.getName();

        if (constraint instanceof ForeignKey || constraint instanceof UniqueKey) {
            StringBuilder colList = new StringBuilder();

            for (org.hibernate.mapping.Column column : (Iterable<? extends org.hibernate.mapping.Column>) constraint.getColumns()) {
                if (colList.length() > 0) {
                    colList.append(", ");
                }
                colList.append(column.getName().toLowerCase());
            }

            name = beautifyConstraintName(name, entityClass, tableName(constraint.getTable().getName()), concatColumnNames(colList.toString()));
        }

        return constraintName(name);
    }

    @Override
    public String constraintName(UniqueConstraint constraint) {
        return constraintName(constraint.name());
    }

    @Override
    public String constraintName(CheckConstraint constraint) {
        return constraintName(constraint.name());
    }

    @Override
    public String indexName(String indexName) {
        return indexName;
    }

    @Override
    public String indexName(Index index) {
        return indexName(index.name());
    }

    @Override
    public String constraintName(String entityClass, String tableName, String colName, String suffix) {
        return constraintName(findName(entityClass, tableName, colName, suffix));
    }

    @Override
    public String indexName(String entityClass, String tableName, String colName) {
        return indexName(findName(entityClass, tableName, colName, "_IX"));
    }

    @Override
    public String triggerName(String triggerName) {
        return triggerName;
    }

    @Override
    public String triggerName(String entityClass, String tableName, String colName, String suffix) {
        return triggerName(findName(entityClass, tableName, colName, suffix));
    }

    @Override
    public String sequenceName(String sequenceName) {
        return sequenceName;
    }

    protected String beautifyConstraintName(String name, String entityClass, String tableName, String colList) {
        if (name.startsWith("FK") || name.startsWith("UK") || NONAME_KEY_PATTERN.matcher(name).matches()) {
            String newName = findName(entityClass, tableName, colList, "_" + name.substring(0, 2));
            LOG.debug("no constraint name specified for {}({}), using {}", new Object[]{tableName, colList, newName});
            return newName;
        }
        return name;
    }

    public String findName(String entityClass, String tableName, String colName, String suffix) {
        String name = entityClass != null ? classToTableShortName(entityClass) : tableShortNameFromTableName(tableName);

        if (StringUtils.isEmpty(colName)) {
            if (tableName.length() + suffix.length() <= 30) {
                name = tableName;
            }
        }
        else {
            int remainingLength = 30 - name.length() - suffix.length();

            if (remainingLength < 1) {
                throw new IllegalArgumentException("cannot find object name, prefix "+name+" and suffix "+suffix+" too long");
            }

            if (colName.length() > remainingLength-1) {
                name +=  "_" + generateShortName(colName, remainingLength-1, 5);
            }
            else {
                name +=  "_" + colName;
            }
        }

        return name + suffix;
    }

    public static String concatColumnNames(String columnNameList) {
        StringBuilder colName = new StringBuilder();
        for (String s : columnNameList.split("[, ]")) {
            if (s.trim().length() < 1) {
                continue;
            }

            if (colName.length() > 0) {
                colName.append("_");
            }
            colName.append(s.replace("_", ""));
        }
        return colName.toString();
    }

    public static String generateShortName(String name, Integer maxLength, Integer charsPerGroup) {
        StringBuilder b = new StringBuilder();
        int charsPg = charsPerGroup != null ? charsPerGroup : 1;

        int idx = 0, prevIdx = 0;
        while ((idx = name.indexOf('_', idx+1)) > 0 && name.length() > idx) {
            b.append(name.substring(prevIdx, Math.min(Math.min(prevIdx + charsPg, name.length()), idx)));
            prevIdx = idx + 1;
            if (charsPg > 1) {
                b.append("_");
            }
        }

        if (prevIdx > 0 && name.length()>=prevIdx) {
            b.append(name.substring(prevIdx, Math.min(prevIdx+charsPg, name.length())));
        }

        if (b.length() < 1) {
            b.append(name);
        }

        if (maxLength != null && b.length() > maxLength) {
            b.delete(maxLength.intValue(), b.length());
        }

        return b.toString();
    }
}