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
package com.github.gekoh.yagen.ddl;

import com.github.gekoh.yagen.api.Auditable;
import com.github.gekoh.yagen.api.CascadeDelete;
import com.github.gekoh.yagen.api.CascadeNullable;
import com.github.gekoh.yagen.api.Changelog;
import com.github.gekoh.yagen.api.CheckConstraint;
import com.github.gekoh.yagen.api.Constants;
import com.github.gekoh.yagen.api.Default;
import com.github.gekoh.yagen.api.DefaultNamingStrategy;
import com.github.gekoh.yagen.api.Deferrable;
import com.github.gekoh.yagen.api.I18NDetailEntityRelation;
import com.github.gekoh.yagen.api.Index;
import com.github.gekoh.yagen.api.IntervalPartitioning;
import com.github.gekoh.yagen.api.LayeredTablesView;
import com.github.gekoh.yagen.api.NamingStrategy;
import com.github.gekoh.yagen.api.NoForeignKeyConstraint;
import com.github.gekoh.yagen.api.Profile;
import com.github.gekoh.yagen.api.Sequence;
import com.github.gekoh.yagen.api.TemporalEntity;
import com.github.gekoh.yagen.api.UniqueConstraint;
import com.github.gekoh.yagen.util.MappingUtils;
import org.apache.commons.lang.StringUtils;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Georg Kohlweiss 
 */
@SuppressWarnings({"unchecked"})
public class TableConfig {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(TableConfig.class);

    private static final Set<Class<? extends Annotation>> COLLECT_ANNOTATIONS = new HashSet(Arrays.asList(
            Profile.class,
            TemporalEntity.class,
            Changelog.class,
            com.github.gekoh.yagen.api.Table.class,
            IntervalPartitioning.class,
            Auditable.class,
            JoinTable.class,
            CollectionTable.class,
            LayeredTablesView.class
    ));


    private String tableName;
    private String baseClassName;
    private AccessibleObject definedAtFieldOrMethod;
    private CreateDDL ddlEnhancer;
    private boolean tableToBeRendered = true;
    private TableConfig superClassConfig;

    private List<String> pkColnames = new ArrayList<String>();
    private Map<Annotation, Class> annotations2annClassMap = new HashMap<Annotation, Class>();
    private List<Sequence> sequences = new ArrayList<Sequence>();
    private List<Index> indexes = new ArrayList<Index>();

    private Map<String, AccessibleObject> columnNameToAccessibleObject = new HashMap<String, AccessibleObject>();
    private Map<String, String> columnNameToEnumCheckConstraints = new HashMap<String, String>();
    private Map<String, Deferrable> columnNameToDeferrable = new HashMap<String, Deferrable>();
    private Set<String> columnNamesIsCascadeDelete = new HashSet<String>();
    private Set<String> columnNamesIsCascadeNullable = new HashSet<String>();
    private Set<String> columnNamesIsNoFK = new HashSet<String>();
    private Map<String, String> colNameToDefault = new HashMap<String, String>();
    private String i18nBaseEntityFkCol;
    private String i18nBaseEntityTblName;

    public static TableConfig add(CreateDDL ddlEnhancer, String tableName) {
        TableConfig tableConfig = new TableConfig(ddlEnhancer, (Class)null, tableName);
        ddlEnhancer.addTableConfig(tableConfig);
        NamingStrategy namingStrategy = ddlEnhancer.getProfile().getNamingStrategy();
        if (!(namingStrategy instanceof FakeClassNamingStrategy)) {
            ddlEnhancer.getProfile().setNamingStrategy(namingStrategy = new FakeClassNamingStrategy());
        }
        tableConfig.baseClassName = ((FakeClassNamingStrategy) namingStrategy).forTableConfig(tableConfig);
        return tableConfig;
    }

    public TableConfig(CreateDDL ddlEnhancer, Class baseClass, String tableName) {
        this.ddlEnhancer = ddlEnhancer;
        this.baseClassName = baseClass != null ? baseClass.getName() : null;
        this.tableName = getIdentifierForReference(tableName);
        Class superclass = baseClass != null ? baseClass.getSuperclass() : null;
        if (superclass != null && superclass.isAnnotationPresent(MappedSuperclass.class)) {
            this.superClassConfig = new TableConfig(ddlEnhancer, superclass, ddlEnhancer.getProfile().getNamingStrategy().classToTableName(superclass.getName()));
        }
    }

    public TableConfig(CreateDDL ddlEnhancer, AccessibleObject definedAtFieldOrMethod, String tableName) {
        this.ddlEnhancer = ddlEnhancer;
        this.definedAtFieldOrMethod = definedAtFieldOrMethod;
        this.tableName = getIdentifierForReference(tableName);
        for (Annotation annotation : definedAtFieldOrMethod.getAnnotations()) {
            if (COLLECT_ANNOTATIONS.contains(annotation.annotationType()) && !annotations2annClassMap.containsKey(annotation)) {
                putTableAnnotation(definedAtFieldOrMethod, annotation);
            }
        }
    }

    public void scanEntityClass(Class entityClass, boolean selectiveRendering) {
        Class annClass = entityClass;
        while (annClass != null) {
            for (Annotation annotation : annClass.getAnnotations()) {
                if (COLLECT_ANNOTATIONS.contains(annotation.annotationType()) && !annotations2annClassMap.containsKey(annotation)) {
                    putTableAnnotation(annClass, annotation);
                }
            }
            annClass = annClass.getSuperclass();
        }

        com.github.gekoh.yagen.api.Table addTblInfo = (com.github.gekoh.yagen.api.Table) entityClass.getAnnotation(com.github.gekoh.yagen.api.Table.class);
        if (addTblInfo != null && addTblInfo.additionalSequences().length > 0) {
            sequences.addAll(Arrays.asList(addTblInfo.additionalSequences()));
        }

        processTypeAnnotations(entityClass, selectiveRendering);

        addI18NInfo(entityClass.getDeclaredFields());
        addI18NInfo(entityClass.getDeclaredMethods());

        gatherPkColumn(entityClass);
        gatherEnumCheckConstraints(entityClass);
        gatherCascade(entityClass);
        gatherDeferrable(entityClass);
        gatherIndexes(entityClass);
        gatherAccessibleObjects(entityClass);

        if (superClassConfig != null) {
            superClassConfig.scanEntityClass(superClassConfig.getEntityBaseClass(), selectiveRendering);
        }
    }

    public boolean isTableToBeRendered() {
        return tableToBeRendered;
    }

    public void setTableToBeRendered(boolean tableToBeRendered) {
        this.tableToBeRendered = tableToBeRendered;
    }

    public String getTableName() {
        return tableName;
    }

    public Class getEntityBaseClass() {
        try {
            return baseClassName != null ? Class.forName(baseClassName) : null;
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public String getEntityBaseClassName() {
        return baseClassName;
    }

    public TableConfig getSuperClassConfig() {
        return superClassConfig;
    }

    private void putTableAnnotation(AccessibleObject fieldOrMethod, Annotation annotation) {
        putTableAnnotation(fieldOrMethod instanceof Member ? ((Member)fieldOrMethod).getDeclaringClass() : null, annotation);
    }

    private void putTableAnnotation(Class annClass, Annotation annotation) {
        if (!COLLECT_ANNOTATIONS.contains(annotation.annotationType())) {
            throw new IllegalArgumentException("not chosen to collect annotations of type " + annotation.getClass());
        }

        annotations2annClassMap.put(annotation, annClass);
    }

    public <T extends Annotation> T getTableAnnotationOfType (Class<T> annotationType) {
        for (Annotation annotation: annotations2annClassMap.keySet()) {
            if (annotationType.isAssignableFrom(annotation.getClass())) {
                return (T) annotation;
            }
        }

        return null;
    }

    public String getI18nBaseEntityFkCol() {
        return i18nBaseEntityFkCol;
    }

    public String getI18nBaseEntityTblName() {
        return i18nBaseEntityTblName;
    }

    public List<String> getPkColnames() {
        return pkColnames;
    }

    public Set<Annotation> getAnnotations() {
        return annotations2annClassMap.keySet();
    }

    public List<Sequence> getSequences() {
        return sequences;
    }

    public List<Index> getIndexes() {
        return indexes;
    }

    public Map<String, AccessibleObject> getColumnNameToAccessibleObject() {
        return columnNameToAccessibleObject;
    }

    public Map<String, String> getColumnNameToEnumCheckConstraints() {
        return columnNameToEnumCheckConstraints;
    }

    public Map<String, Deferrable> getColumnNameToDeferrable() {
        return columnNameToDeferrable;
    }

    public Set<String> getColumnNamesIsCascadeDelete() {
        return columnNamesIsCascadeDelete;
    }

    public Set<String> getColumnNamesIsCascadeNullable() {
        return columnNamesIsCascadeNullable;
    }

    public Set<String> getColumnNamesIsNoFK() {
        return columnNamesIsNoFK;
    }

    public Map<String, String> getColNameToDefault() {
        return colNameToDefault;
    }

    private void gatherEnumCheckConstraints(Class entityClass) {
        gatherEnumCheckConstraints(new HashMap<String, String>(), "", entityClass);
    }

    private void gatherEnumCheckConstraints(final Map<String, String> attr2colName, final String attrPath, Class type) {
//        HACK: we are using field annotations, need to skip methods otherwise we would have wrong constraints
        traverseFieldsAndMethods(type, true, false, new GatherFieldOrMethodInfoAction() {
            @Override
            public void gatherInfo(AccessibleObject fOm) {
                String attributeName = getAttributeName(fOm);
                String attrPathField = attrPath + "." + attributeName;
                Class attributeType = getAttributeType(fOm);

                if (fOm.isAnnotationPresent(Embedded.class)) {
                    addAttributeOverrides(attr2colName, attrPathField, fOm);
                    gatherEnumCheckConstraints(attr2colName, attrPathField, attributeType);
                }
                else if (attributeType.isEnum()) {
                    String colName = attr2colName.get(attrPathField);
                    if (colName == null) {
                        if (fOm.isAnnotationPresent(Column.class)) {
                            colName = getIdentifierForReference(fOm.getAnnotation(Column.class).name());
                        }

                        if (StringUtils.isEmpty(colName)) {
                            colName = getIdentifierForReference(attributeName);
                        }
                    }
                    boolean useName = fOm.isAnnotationPresent(Enumerated.class) && fOm.getAnnotation(Enumerated.class).value() == EnumType.STRING;
                    StringBuilder cons = new StringBuilder();
                    for (Object e : attributeType.getEnumConstants()) {
                        if (cons.length() > 0) {
                            cons.append(", ");
                        }
                        if (useName) {
                            cons.append("'").append(((Enum)e).name()).append("'");
                        }
                        else {
                            cons.append(((Enum) e).ordinal());
                        }
                    }
                    columnNameToEnumCheckConstraints.put(colName, cons.toString());
                }
            }
        });

        Class superClass = getEntitySuperclass(type);
        if (superClass != null) {
            gatherEnumCheckConstraints(attr2colName, attrPath, superClass);
        }
    }

    private static Class getEntitySuperclass(Class entity) {
        // in case of joined table inheritance the superclass has its own table and we do not need
        // to care about attributes from superclasses
        if (entity.isAnnotationPresent(DiscriminatorValue.class)) {
            return null;
        }

        // in all other cases we also need to consider attributes/columns of superclasses as they
        // were found in this entity's table
        Class superClass = entity.getSuperclass();

        if (superClass != null &&
                (superClass.isAnnotationPresent(MappedSuperclass.class) || superClass.isAnnotationPresent(Entity.class))) {
            return superClass;
        }

        return null;
    }

    private void gatherCascade(Class entityClass) {
        do {
            gatherCascade(new HashMap<String, String>(), "", entityClass);
        } while ((entityClass = getEntitySuperclass(entityClass)) != null);
    }

    private void gatherCascade(final Map<String, String> attr2colName, final String attrPath, Class type) {
//        HACK: we are using field annotations, need to skip methods otherwise we would have wrong constraints
        traverseFieldsAndMethods(type, true, false, new GatherFieldOrMethodInfoAction() {
            @Override
            public void gatherInfo(AccessibleObject fOm) {
                String attributeName = getAttributeName(fOm);
                String attrPathField = attrPath + "." + attributeName;
                Class attributeType = getAttributeType(fOm);
                boolean onDeleteCascade = fOm.isAnnotationPresent(OnDelete.class) ? fOm.getAnnotation(OnDelete.class).action() == OnDeleteAction.CASCADE : false;

                if (fOm.isAnnotationPresent(Embedded.class)) {
                    addAttributeOverrides(attr2colName, attrPathField, fOm);
                    gatherCascade(attr2colName, attrPathField, attributeType);
                }

                if (fOm.isAnnotationPresent(NoForeignKeyConstraint.class)) {
                    String colName = attr2colName.get(attrPathField);
                    if (colName == null) {
                        if (fOm.isAnnotationPresent(JoinColumn.class)) {
                            colName = getIdentifierForReference(fOm.getAnnotation(JoinColumn.class).name());
                        }

                        if (StringUtils.isEmpty(colName)) {
                            colName = getIdentifierForReference(attributeName);
                        }
                        columnNamesIsNoFK.add(colName);
                    }
                }

                Set<String> fkCols = new HashSet<String>();
                String fkTableName = null;
                if (fOm.isAnnotationPresent(JoinTable.class)) {
                    JoinTable joinTable = fOm.getAnnotation(JoinTable.class);
                    fkCols.add(getIdentifierForReference(joinTable.joinColumns()[0].name()));
                    if (joinTable.inverseJoinColumns().length > 0) {
                        fkCols.add(getIdentifierForReference(joinTable.inverseJoinColumns()[0].name()));
                    }
                    fkTableName = joinTable.name();
                } else if (fOm.isAnnotationPresent(CollectionTable.class)) {
                    CollectionTable annotation = fOm.getAnnotation(CollectionTable.class);
                    fkTableName = annotation.name();
                    fkCols.add(getIdentifierForReference(annotation.joinColumns()[0].name()));
                } else if (fOm.isAnnotationPresent(OneToMany.class)) {
                    JoinColumn joinColumn = getJoinColumn(fOm);
                    if (joinColumn != null) {
                        Class<?> targetEntityClass = MappingUtils.determineTargetEntity(fOm, fOm.getAnnotation(OneToMany.class).targetEntity());
                        fkTableName = getTableAnnotation(targetEntityClass).name();
                        fkCols.add(getIdentifierForReference(joinColumn.name()));
                    }
                } else if (fOm.isAnnotationPresent(ManyToOne.class) || fOm.isAnnotationPresent(OneToOne.class)) {
                    JoinColumn joinColumn = getJoinColumn(fOm);
                    if (joinColumn != null) {
                        fkTableName = tableName;
                        fkCols.add(getIdentifierForReference(joinColumn.name()));
                    }
                }

                if (fkTableName != null) {
                    fkTableName = ddlEnhancer.getProfile().getNamingStrategy().tableName(fkTableName);
                    TableConfig fkConfig = ddlEnhancer.getConfigForTableName(getIdentifierForReference(fkTableName));
                    if (fkConfig != null) {
                        if (onDeleteCascade || fOm.isAnnotationPresent(CascadeDelete.class)) {
                            fkConfig.columnNamesIsCascadeDelete.addAll(fkCols);
                        }
                        else if (fOm.isAnnotationPresent(CascadeNullable.class)) {
                            fkConfig.columnNamesIsCascadeNullable.addAll(fkCols);
                        }
                    }
                }
            }
        });
    }

    private void gatherDeferrable(Class entityClass) {
        gatherDeferrable(new HashMap<String, String>(), "", entityClass);
    }

    private void gatherDeferrable(final Map<String, String> attr2colName, final String attrPath, Class type) {
        traverseFieldsAndMethods(type, true, true, new GatherFieldOrMethodInfoAction() {
            @Override
            public void gatherInfo(AccessibleObject fOm) {
                String attributeName = getAttributeName(fOm);
                String attrPathField = attrPath + "." + attributeName;
                Class attributeType = getAttributeType(fOm);

                if (fOm.isAnnotationPresent(Embedded.class)) {
                    addAttributeOverrides(attr2colName, attrPathField, fOm);
                    gatherDeferrable(attr2colName, attrPathField, attributeType);
                } else if (fOm.isAnnotationPresent(Deferrable.class)) {
                    String colName = attr2colName.get(attrPathField);
                    if (colName == null) {
                        if (fOm.isAnnotationPresent(JoinColumn.class)) {
                            colName = getIdentifierForReference(fOm.getAnnotation(JoinColumn.class).name());
                        }

                        if (StringUtils.isEmpty(colName)) {
                            colName = getIdentifierForReference(attributeName);
                        }
                        columnNameToDeferrable.put(colName, fOm.getAnnotation(Deferrable.class));
                    }
                }
            }
        });
    }

    private void gatherAccessibleObjects(Class entityClass) {
        gatherAccessibleObjects(new HashMap<String, String>(), "", entityClass);
    }

    private void gatherAccessibleObjects(final Map<String, String> attr2colName, final String attrPath, Class type) {
        traverseFieldsAndMethods(type, true, true, new GatherFieldOrMethodInfoAction() {
            @Override
            public void gatherInfo(AccessibleObject fOm) {
                String attributeName = getAttributeName(fOm);
                String attrPathField = attrPath + "." + attributeName;
                Class attributeType = getAttributeType(fOm);

                if (fOm.isAnnotationPresent(Embedded.class)) {
                    addAttributeOverrides(attr2colName, attrPathField, fOm);
                    gatherAccessibleObjects(attr2colName, attrPathField, attributeType);
                } else {
                    String colName = attr2colName.get(attrPathField);
                    if (colName == null) {
                        if (fOm.isAnnotationPresent(Column.class)) {
                            colName = getIdentifierForReference(fOm.getAnnotation(Column.class).name());
                        }
                        else if (fOm.isAnnotationPresent(JoinColumn.class)) {
                            colName = getIdentifierForReference(fOm.getAnnotation(JoinColumn.class).name());
                        }

                        if (StringUtils.isEmpty(colName)) {
                            colName = getIdentifierForReference(attributeName);
                        }
                    }
                    columnNameToAccessibleObject.put(colName, fOm);
                }
            }
        });
    }

    private void gatherIndexes(Class type) {
        while (type != null) {
            traverseFieldsAndMethods(type, true, true, new GatherFieldOrMethodInfoAction() {
                public void gatherInfo(AccessibleObject fOm) {

                    if (fOm.isAnnotationPresent(Index.class)) {
                        indexes.add(fOm.getAnnotation(Index.class));
                    }
                }
            });

            if (type.isAnnotationPresent(Table.class)) {
                break;
            }
            type = type.getSuperclass();
        }
    }

    private static void traverseFieldsAndMethods (Class type, boolean fields, boolean methods, GatherFieldOrMethodInfoAction action) {
        List<AccessibleObject> fOms = new ArrayList<AccessibleObject>();
        if (fields) {
            fOms.addAll(Arrays.asList(type.getDeclaredFields()));
        }
        if (methods) {
            fOms.addAll(Arrays.asList(type.getDeclaredMethods()));
        }

        for (AccessibleObject fOm : fOms) {
            if (fOm.isAnnotationPresent(Transient.class)) {
                continue;
            }

            action.gatherInfo(fOm);
        }
    }

    public TableConfig withTemporalEntityAnnotation() {
        return withTemporalEntityAnnotation(null, null);
    }

    public TableConfig withTemporalEntityAnnotation(final String historyTableName, final String historyTimestampColumnName, final String... ignoreChangeOfColumns) {
        putTableAnnotation((Class)null, new TemporalEntity() {
            public String historyTableName() {
                return historyTableName != null ? historyTableName : "";
            }

            public String historyTimestampColumnName() {
                return historyTimestampColumnName != null ? historyTimestampColumnName : "TRANSACTION_TIMESTAMP";
            }

            public String[] ignoreChangeOfColumns() {
                return ignoreChangeOfColumns != null ? ignoreChangeOfColumns : new String[0];
            }

            public Class<? extends Annotation> annotationType() {
                return TemporalEntity.class;
            }
        });
        TableConfig hstTableConfig = TableConfig.add(ddlEnhancer, historyTableName != null ? historyTableName : tableName + Constants._HST);
        ddlEnhancer.addTableConfig(hstTableConfig);
        ((FakeClassNamingStrategy)ddlEnhancer.getProfile().getNamingStrategy()).registerHistoryTableMapping(hstTableConfig, this);
        return this;
    }

    public TableConfig withAuditableAnnotation() {
        return withAuditableAnnotation(null, null);
    }

    public TableConfig withAuditableAnnotation(final Boolean createNonExistingColumns, final Integer userNameLength) {
        putTableAnnotation((Class)null, new Auditable() {
            public boolean createNonExistingColumns() {
                return createNonExistingColumns != null ? createNonExistingColumns : true;
            }

            public int userNameLength() {
                return userNameLength != null ? userNameLength : 35;
            }

            public Class<? extends Annotation> annotationType() {
                return Auditable.class;
            }
        });
        return this;
    }

    public TableConfig withIntervalPartitioningAnnotation(String useExistingDateColumnNameAndPartitionMonthly) {
        return withIntervalPartitioningAnnotation(useExistingDateColumnNameAndPartitionMonthly, null, null, null, null);
    }

    public TableConfig withIntervalPartitioningAnnotation(final String columnName, final String interval, final String startPartitionLessThanValue, final Boolean enableRowMovement, final Boolean useLocalPK) {
        putTableAnnotation((Class)null, new IntervalPartitioning() {
            public String columnName() {
                return columnName != null ? columnName : "partition_date";
            }

            public String interval() {
                return interval != null ? interval : "numtoyminterval(1, 'MONTH')";
            }

            public String startPartitionLessThanValue() {
                return startPartitionLessThanValue != null ? startPartitionLessThanValue : "";
            }

            public boolean enableRowMovement() {
                return enableRowMovement != null ? enableRowMovement : false;
            }

            public boolean useLocalPK() {
                return useLocalPK != null ? useLocalPK : false;
            }

            public Class<? extends Annotation> annotationType() {
                return IntervalPartitioning.class;
            }
        });
        return this;
    }

    public TableConfig withTableAnnotation(final String shortName) {
        return withTableAnnotation(shortName, null, null);
    }

    public TableConfig withTableAnnotation(final String shortName,
                                           final Boolean isGlobalTemporary,
                                           final String globalTemporaryOnCommit) {
        putTableAnnotation((Class)null, new com.github.gekoh.yagen.api.Table() {
            public String shortName() {
                return shortName != null ? shortName : "";
            }

            public boolean isGlobalTemporary() {
                return isGlobalTemporary != null ? isGlobalTemporary : false;
            }

            public String globalTemporaryOnCommit() {
                return globalTemporaryOnCommit != null ? globalTemporaryOnCommit : "DELETE ROWS";
            }

            public Sequence[] additionalSequences() {
                return new Sequence[0];
            }

            public CheckConstraint[] checkConstraints() {
                return new CheckConstraint[0];
            }

            public UniqueConstraint[] uniqueConstraints() {
                return new UniqueConstraint[0];
            }

            public Index[] indexes() {
                return new Index[0];
            }

            public Class<? extends Annotation> annotationType() {
                return com.github.gekoh.yagen.api.Table.class;
            }
        });
        return this;
    }

    private static interface GatherFieldOrMethodInfoAction {
        void gatherInfo(AccessibleObject fieldOrMethod);
    }

    private static String getAttributeName(AccessibleObject fom) {
        if (fom instanceof Field) {
            return ((Field)fom).getName();
        }
        else if (fom instanceof Method) {
            String name = ((Method)fom).getName().replaceAll("(set|get)", "");
            return name.substring(0, 1).toLowerCase() + name.substring(1);
        }
        return null;
    }

    private static Class getAttributeType(AccessibleObject fom) {
        if (fom instanceof Field) {
            return ((Field)fom).getType();
        }
        else if (fom instanceof Method) {
            return ((Method)fom).getReturnType();
        }
        return null;
    }

    private static void addAttributeOverrides(Map<String, String> attr2colName, String attrPath, AccessibleObject fom) {
        List<AttributeOverride> overrides = new ArrayList<AttributeOverride>();
        if (fom.isAnnotationPresent(AttributeOverride.class)) {
            overrides.add(fom.getAnnotation(AttributeOverride.class));
        }

        if (fom.isAnnotationPresent(AttributeOverrides.class)) {
            overrides.addAll(Arrays.asList(fom.getAnnotation(AttributeOverrides.class).value()));
        }

        for (AttributeOverride override : overrides) {
            String attr = attrPath+"."+override.name();

            if (!attr2colName.containsKey(attr)) {
                attr2colName.put(attr, getIdentifierForReference(override.column().name()));
            }
        }
    }

    private void gatherPkColumn(Class entityClass) {
        Set<AccessibleObject> fieldsOrMethods = getAnnotatedFieldOrMethod(new HashSet<AccessibleObject>(), Id.class, entityClass, true);

        if (entityClass.isAnnotationPresent(PrimaryKeyJoinColumn.class)) {
            pkColnames = Arrays.asList(((PrimaryKeyJoinColumn) entityClass.getAnnotation(PrimaryKeyJoinColumn.class)).name());
        }
        else if (fieldsOrMethods.size() > 0) {
            pkColnames = new ArrayList<String>();
            for (AccessibleObject fieldOrMethod : fieldsOrMethods) {
                String colName = null;
                if (fieldOrMethod.isAnnotationPresent(Column.class)) {
                    colName = fieldOrMethod.getAnnotation(Column.class).name();
                }
                if (colName == null && fieldOrMethod instanceof Field) {
                    colName = ((Field) fieldOrMethod).getName();
                }
                if (colName != null) {
                    pkColnames.add(colName);
                }
            }
        }
    }

    private static <T extends Annotation> Set<AccessibleObject> getAnnotatedFieldOrMethod(Set<AccessibleObject> fieldsOrMethods,
                                                                                          Class<T> annotationClass,
                                                                                          Class entityClass,
                                                                                          boolean withInheritance) {
        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(annotationClass)) {
                fieldsOrMethods.add(field);
            }
        }
        for (Method method : entityClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(annotationClass)) {
                fieldsOrMethods.add(method);
            }
        }
        if (entityClass.getSuperclass() != null && withInheritance) {
            return getAnnotatedFieldOrMethod(fieldsOrMethods, annotationClass, entityClass.getSuperclass(), withInheritance);
        }
        return fieldsOrMethods;
    }

    private void processTypeAnnotations(Class type, boolean selectiveRendering) {
        do {
            processAnnotations(type.getDeclaredFields(), selectiveRendering);
            processAnnotations(type.getDeclaredMethods(), selectiveRendering);
        } while ((type = getEntitySuperclass(type)) != null);
    }

    private void processAnnotations (AccessibleObject[] fieldsOrMethods, boolean selectiveRendering) {
        for (AccessibleObject fieldOrMethod: fieldsOrMethods) {
            JoinTable joinTable = fieldOrMethod.getAnnotation(JoinTable.class);
            CollectionTable collectionTable = fieldOrMethod.getAnnotation(CollectionTable.class);
            String joinTableName = joinTable != null ? joinTable.name() : collectionTable != null ? collectionTable.name() : null;
            TableConfig joinTableConfig = joinTableName != null ? ddlEnhancer.getConfigForTableName(getIdentifierForReference(joinTableName)) : null;

            if (joinTableName != null && joinTableConfig == null) {
                joinTableConfig = new TableConfig(ddlEnhancer, fieldOrMethod, ddlEnhancer.getProfile().getNamingStrategy().tableName(joinTableName));
                ddlEnhancer.addTableConfig(joinTableConfig);
            }

            if (joinTableConfig != null) {
                joinTableConfig.putTableAnnotation(fieldOrMethod, joinTable != null ? joinTable : collectionTable);

                if (fieldOrMethod.isAnnotationPresent(IntervalPartitioning.class)) {
                    joinTableConfig.putTableAnnotation(fieldOrMethod, fieldOrMethod.getAnnotation(IntervalPartitioning.class));
                }
                if (fieldOrMethod.isAnnotationPresent(Auditable.class)) {
                    joinTableConfig.putTableAnnotation(fieldOrMethod, fieldOrMethod.getAnnotation(Auditable.class));
                }
            }

            if (fieldOrMethod.getAnnotation(Profile.class) != null) {
                if (joinTableConfig == null) {
                    throw new IllegalArgumentException("need @"+JoinTable.class.getSimpleName()+" or @"+CollectionTable.class.getSimpleName()+" for @"+Profile.class.getSimpleName()+" on a field");
                }
                Profile annotation = fieldOrMethod.getAnnotation(Profile.class);

                if (selectiveRendering && !Arrays.asList(annotation.value()).contains(ddlEnhancer.getProfile().getName())) {
                    joinTableConfig.setTableToBeRendered(false);
                }
            }
            if (fieldOrMethod.getAnnotation(TemporalEntity.class) != null) {
                if (joinTableConfig == null) {
                    throw new IllegalArgumentException("need @"+JoinTable.class.getSimpleName()+" or @"+CollectionTable.class.getSimpleName()+" for @"+TemporalEntity.class.getSimpleName()+" on a field");
                }
                TemporalEntity annotation = fieldOrMethod.getAnnotation(TemporalEntity.class);

                if (annotation != null) {
                    joinTableConfig.putTableAnnotation(fieldOrMethod, annotation);
                }
            }
            if (fieldOrMethod.getAnnotation(Sequence.class) != null) {
                sequences.add(fieldOrMethod.getAnnotation(Sequence.class));
            }
            else if (fieldOrMethod.getAnnotation(Embedded.class) != null) {
                Class embed;
                if (fieldOrMethod instanceof Field) {
                    Field field = (Field) fieldOrMethod;
                    embed = field.getType();
                }
                else if (fieldOrMethod instanceof Method) {
                    Method method = (Method) fieldOrMethod;
                    embed = method.getReturnType();
                }
                else {
                    throw new IllegalStateException("AccessibleObject not type of Field or Method");
                }
                processTypeAnnotations(embed, selectiveRendering);
            }
//            if we have a ManyToMany JoinTable and current entity is part of selective rendering,
//            we also need to render the JoinTable
            else if (fieldOrMethod.isAnnotationPresent(ManyToMany.class)) {
                if (joinTable == null) {
                    String propName = fieldOrMethod instanceof Field ? ((Field) fieldOrMethod).getName() : ((Method) fieldOrMethod).getName().substring(3);
                    joinTableConfig = new TableConfig(ddlEnhancer, fieldOrMethod, ddlEnhancer.getProfile().getNamingStrategy().collectionTableName(null, getTableName(), null, null, propName));
                    ddlEnhancer.addTableConfig(joinTableConfig);
                }
                joinTableConfig.setTableToBeRendered(true);
            }

            if (fieldOrMethod.isAnnotationPresent(Default.class)) {
                String defaultValue = fieldOrMethod.getAnnotation(Default.class).sqlExpression();
                if (fieldOrMethod.isAnnotationPresent(Column.class)) {
                    colNameToDefault.put(getIdentifierForReference(fieldOrMethod.getAnnotation(Column.class).name()), defaultValue);
                }
                else if (fieldOrMethod instanceof Field) {
                    colNameToDefault.put(getIdentifierForReference(ddlEnhancer.getProfile().getNamingStrategy().columnName(((Field) fieldOrMethod).getName())), defaultValue);
                }
                else {
                    LOG.warn(Default.class+" only supported on fields or @{} annotated methods", Column.class.toString());
                }
            }
        }
    }

    private static JoinColumn getJoinColumn(AccessibleObject fieldOrMethod) {
        if (fieldOrMethod.isAnnotationPresent(JoinColumn.class)) {
            return fieldOrMethod.getAnnotation(JoinColumn.class);
        }
        if (fieldOrMethod.isAnnotationPresent(OneToMany.class)) {
            OneToMany o2m = fieldOrMethod.getAnnotation(OneToMany.class);
            try {
                if (fieldOrMethod.getAnnotation(JoinColumn.class) != null) {
                    return fieldOrMethod.getAnnotation(JoinColumn.class);
                }
                else if (fieldOrMethod.isAnnotationPresent(JoinColumns.class)) {
                    return null; // TODO: implement compound FK
                }
                else {
                    Class<?> targetEntityClass = MappingUtils.determineTargetEntity(fieldOrMethod, o2m.targetEntity());
                    return targetEntityClass.getDeclaredField(o2m.mappedBy()).getAnnotation(JoinColumn.class);
                }
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException(e);
            }
        }
        return null;
    }

    private void addI18NInfo (AccessibleObject[] fieldsOrMethods) {
        for (AccessibleObject fieldOrMethod: fieldsOrMethods) {
            if (fieldOrMethod.getAnnotation(I18NDetailEntityRelation.class) != null) {
                JoinColumn joinColumn = getJoinColumn(fieldOrMethod);
                String entityTableName, detailTableName;
                if (fieldOrMethod.getAnnotation(OneToMany.class) != null) {
                    OneToMany o2m = fieldOrMethod.getAnnotation(OneToMany.class);
                    Class<?> targetEntityClass = MappingUtils.determineTargetEntity(fieldOrMethod, o2m.targetEntity());
                    detailTableName = getTableAnnotation(targetEntityClass).name();
                    entityTableName = getTableName();
                }
                else {
                    ManyToOne m2o = fieldOrMethod.getAnnotation(ManyToOne.class);
                    Class<?> targetEntityClass = MappingUtils.determineTargetEntity(fieldOrMethod, m2o.targetEntity());
                    entityTableName = getTableAnnotation(targetEntityClass).name();
                    detailTableName = getTableName();
                }

                detailTableName = ddlEnhancer.getProfile().getNamingStrategy().tableName(detailTableName);
                TableConfig detailTableConfig = ddlEnhancer.getConfigForTableName(getIdentifierForReference(detailTableName));

                if (joinColumn != null && detailTableConfig != null) {
                    detailTableConfig.i18nBaseEntityFkCol = joinColumn.name();
                    detailTableConfig.i18nBaseEntityTblName = entityTableName;
                }
            }
        }
    }

    private static javax.persistence.Table getTableAnnotation(Class type) {
        Class entityClass = getClassOfTableAnnotation(type);
        if (entityClass != null) {
            return (javax.persistence.Table) entityClass.getAnnotation(javax.persistence.Table.class);
        }
        return null;
    }

    public static Class getClassOfTableAnnotation(Class type) {
        do {
            if (type.isAnnotationPresent(javax.persistence.Table.class)) {
                return type;
            }
        } while ((type = type.getSuperclass()) != null);

        return null;
    }


    public static String getIdentifierForReference(String identifier) {
        return identifier.replaceAll("[\"'`]", "").toLowerCase();
    }

    private static class FakeClassNamingStrategy extends DefaultNamingStrategy {

        private static final String PREFIX = FakeClassNamingStrategy.class.getSimpleName() + ".";

        private Map<String, TableConfig> fakeClassNames2ConfigMap = new HashMap<String, TableConfig>();
        private Map<TableConfig, TableConfig> history2liveMap = new HashMap<TableConfig, TableConfig>();

        @Override
        public String classToTableName(String className) {
            return fakeClassNames2ConfigMap.containsKey(className) ? className.substring(PREFIX.length()) : super.classToTableName(className);
        }

        @Override
        public String classToTableShortName(String className) {
            if (!fakeClassNames2ConfigMap.containsKey(className)) {
                super.classToTableShortName(className);
            }

            TableConfig tableConfig = fakeClassNames2ConfigMap.get(className);
            com.github.gekoh.yagen.api.Table annotation = tableConfig.getTableAnnotationOfType(com.github.gekoh.yagen.api.Table.class);

            if (annotation == null && history2liveMap.containsKey(tableConfig)) {
                annotation = history2liveMap.get(tableConfig).getTableAnnotationOfType(com.github.gekoh.yagen.api.Table.class);

                if (annotation != null && StringUtils.isNotEmpty(annotation.shortName())) {
                    return CreateDDL.getHistTableShortNameFromLiveTableShortName(annotation.shortName());
                }
            }

            return annotation != null && StringUtils.isNotEmpty(annotation.shortName()) ? annotation.shortName() : tableShortNameFromTableName(classToTableName(className));
        }

        public String forTableConfig(TableConfig tableConfig) {
            String fakeClassName = PREFIX + tableConfig.getTableName();
            fakeClassNames2ConfigMap.put(fakeClassName, tableConfig);
            return fakeClassName;
        }

        public void registerHistoryTableMapping(TableConfig hstTableConfig, TableConfig tableConfig) {
            history2liveMap.put(hstTableConfig, tableConfig);
        }
    }
}
