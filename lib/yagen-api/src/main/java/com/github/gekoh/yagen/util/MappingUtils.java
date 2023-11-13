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
package com.github.gekoh.yagen.util;

import com.github.gekoh.yagen.api.CascadeDelete;
import com.github.gekoh.yagen.api.CascadeNullable;
import jakarta.persistence.*;
import jakarta.persistence.metamodel.*;
import org.apache.commons.lang.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

/**
 * @author Georg Kohlweiss 
 */
public class MappingUtils {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(MappingUtils.class);

    public static String getJpaEntityName(Class entityClass) {
        Entity entity = (Entity) entityClass.getAnnotation(Entity.class);
        if (entity == null) {
            return null;
        }
        return StringUtils.isNotEmpty(entity.name()) ? entity.name() : entityClass.getSimpleName() ;
    }

    public static Class determineTargetEntity(AccessibleObject ao) {
        Class specifiedTargetEntity = null;
        if (ao.isAnnotationPresent(ManyToOne.class)) {
            specifiedTargetEntity = ao.getAnnotation(ManyToOne.class).targetEntity();
        }
        else if (ao.isAnnotationPresent(ManyToMany.class)) {
            specifiedTargetEntity = ao.getAnnotation(ManyToMany.class).targetEntity();
        }
        else if (ao.isAnnotationPresent(OneToOne.class)) {
            specifiedTargetEntity = ao.getAnnotation(OneToOne.class).targetEntity();
        }
        return determineTargetEntity(ao, specifiedTargetEntity);
    }

    public static Class determineTargetEntity(AccessibleObject ao, Class<?> specifiedTargetEntity) {
        String errorMessage = "targetEntity not present and not determinable (need generic declaration)";
        try {
            if (specifiedTargetEntity != null && specifiedTargetEntity != Void.TYPE) {
                return specifiedTargetEntity;
            } else {
                if (ao instanceof Field) {
                    Field f = (Field)ao;
                    if (Collection.class.isAssignableFrom(f.getType())) {
                        // this has to work, because otherwise the target entity must be valid
                        ParameterizedType type = (ParameterizedType) f.getGenericType();
                        return (Class<?>) type.getActualTypeArguments()[0];
                    }
                    return f.getType();
                } else if (ao instanceof Method) {
                    Method m = (Method)ao;
                    if (Collection.class.isAssignableFrom(m.getReturnType())) {
                        // this has to work, because otherwise the target entity must be valid
                        ParameterizedType type = (ParameterizedType) m.getGenericReturnType();
                        return (Class<?>) type.getActualTypeArguments()[0];
                    }
                    return m.getReturnType();
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(errorMessage, e);
        }
        throw new IllegalStateException(errorMessage);
    }

    public static String getVersionFieldName(EntityManagerFactory emf, Class entityClass) {
        EntityType entityType = emf.getMetamodel().entity(entityClass);
        SingularAttribute version;
        if (entityType != null && (version = entityType.getVersion(Long.class)) != null) {
            return version.getName();
        }
        return null;
    }

    public static Set<Class> getSubEntities(EntityManagerFactory emf, Class superClass) throws Exception {
        Set<Class> entities = new HashSet<Class>();
        for (Class aClass : getEntityClasses(emf)) {
            if (aClass.isAnnotationPresent(Entity.class) &&
                    aClass != superClass &&
                    superClass.isAssignableFrom(aClass)) {
                entities.add(aClass);
            }
        }
        return entities;
    }

    public static Class[] getEntityClasses(EntityManagerFactory emf)
            throws Exception {
        Set<EntityType<?>> entities = emf.getMetamodel().getEntities();
        Class[] classes = new Class[entities.size()];
        int arrIdx = 0;
        for (EntityType<?> entityType : entities) {
            classes[arrIdx++] = entityType.getJavaType();
        }
        return classes;
    }

    public static Set<Attribute> getCollectionDescriptors(EntityManagerFactory emf, Class entityClass) {
        Set<Attribute> attributes = new HashSet<Attribute>();
        EntityType entityType = emf.getMetamodel().entity(entityClass);
        for (Object o : entityType.getDeclaredAttributes()) {
            Attribute attribute = (Attribute) o;
            if (Collection.class.isAssignableFrom(attribute.getJavaType())) {
                attributes.add(attribute);
            }
        }
        return attributes;
    }

    /**
     *
     * @param emf
     * @return a map from the targetEntity of the OneToMany collection to the field of this collection descriptor
     * @throws Exception
     */
    public static Map<Class, Set<Field>> getCollectionDescriptorReferences(EntityManagerFactory emf)
            throws Exception {
        Map<Class, Set<Field>> detailClassToMasterFields = new HashMap<Class, Set<Field>>();

        for (Class aClass : getEntityClasses(emf)) {
            for (Field field : getFielsWith(OneToMany.class, aClass)) {
                OneToMany a = field.getAnnotation(OneToMany.class);
                Class targetEntity = determineTargetEntity(field, a.targetEntity());
                if (StringUtils.isEmpty(a.mappedBy())) {
                    Set<Field> fields = detailClassToMasterFields.get(targetEntity);
                    if (fields == null) {
                        detailClassToMasterFields.put(targetEntity, fields = new HashSet<Field>());
                    }
                    fields.add(field);
                }
            }
        }

        return detailClassToMasterFields;
    }

    public static Map<Class, Set<Field>> getEntityClassMapFieldsWith(Class<? extends Annotation> annotation, EntityManagerFactory emf)
            throws Exception {
        Map<Class, Set<Field>> fields = new HashMap<Class, Set<Field>>();

        for (Class aClass : getEntityClasses(emf)) {
            fields.put(aClass, getFielsWith(annotation, aClass));
        }

        return fields;
    }

    public static Set<Field> getManyToManyRelations(EntityManagerFactory emf)
            throws Exception {
        Set<Field> manyToManyFiels = new HashSet<Field>();

        for (Class aClass : getEntityClasses(emf)) {
            manyToManyFiels.addAll(getFielsWith(ManyToMany.class, aClass));
        }

        return manyToManyFiels;
    }

    public static Set<Field> getFielsWith(Class<? extends Annotation> a, Class clazz) {
        Set<Field> fields = new HashSet<Field>();

        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(a)) {
                fields.add(field);
            }
        }

        return fields;
    }

    public static boolean fkIsPhysicallyNullable(Field field) {
        return (MappingUtils.fkIsNullable(field) || field.isAnnotationPresent(CascadeNullable.class))
                && !field.isAnnotationPresent(CascadeDelete.class);
    }

    public static boolean fkIsNullable(Field field) {
        if (field.isAnnotationPresent(ManyToOne.class) &&
                !field.getAnnotation(ManyToOne.class).optional()) {
            return false;
        }
        if (field.isAnnotationPresent(JoinColumn.class) &&
                !field.getAnnotation(JoinColumn.class).nullable()) {
            return false;
        }
        if (field.isAnnotationPresent(OneToMany.class)) {
            String mappedBy = field.getAnnotation(OneToMany.class).mappedBy();
            if (StringUtils.isNotEmpty(mappedBy)) {
                try {
                    Class targetEntity = MappingUtils.determineTargetEntity(field);
                    Field fkField = targetEntity.getDeclaredField(mappedBy);
                    if (fkField.isAnnotationPresent(JoinColumn.class) &&
                            !fkField.getAnnotation(JoinColumn.class).nullable()) {
                        return false;
                    }
                } catch (NoSuchFieldException e) {
                    LOG.error("unable to get mappedBy field", e);
                }
            }
        }
        return true;
    }

    public static String deriveColumnName(AccessibleObject fieldOrMethod) {
        if (fieldOrMethod.isAnnotationPresent(JoinColumn.class)) {
            return fieldOrMethod.getAnnotation(JoinColumn.class).name();
        }
        else if (fieldOrMethod.isAnnotationPresent(PrimaryKeyJoinColumn.class)) {
            PrimaryKeyJoinColumn primaryKeyJoinColumn = fieldOrMethod.getAnnotation(PrimaryKeyJoinColumn.class);
            if (StringUtils.isNotEmpty(primaryKeyJoinColumn.name())) {
                return primaryKeyJoinColumn.name();
            }
            else if (fieldOrMethod instanceof Member) {
                return FieldInfo.getIdColumn(((Member) fieldOrMethod).getDeclaringClass()).name();
            }
        }
        return deriveColumnName(null, fieldOrMethod);
    }

    public static String deriveColumnName(Column column, AccessibleObject fieldOrMethod) {
        if (fieldOrMethod instanceof Field) {
            return deriveColumnName(column, ((Field) fieldOrMethod).getName());
        }
        String name = ((Method) fieldOrMethod).getName();
        return deriveColumnName(column, name.length()>3 ? name.substring(3) : name);
    }

    public static String deriveColumnName(Column column, String fieldName) {
        return column == null || StringUtils.isEmpty(column.name()) ? fieldName : column.name();
    }


    public static Iterator<Class> getClassesSequenceIterator(EntityManager oracleEm, boolean topDown, Class... classes) throws Exception {

        Map<String, Class> tableNameMap = getTableNameMap(classes);

        List<String> tableSequence = getTableSequence(oracleEm, topDown);
        List<Class> classSequence = new ArrayList<Class>(tableNameMap.size());

        for (String tableName : tableSequence) {
            if (tableNameMap.containsKey(tableName)) {
                classSequence.add(tableNameMap.get(tableName));
            }
        }

        return classSequence.iterator();
    }

    private static Map<String, Class> getTableNameMap(Class... classes) {
        Map<String, Class> tableNameMap = new HashMap<String, Class>(classes.length);

        for (Class aClass : classes) {
            if (aClass.isAnnotationPresent(Table.class)) {
                tableNameMap.put(((Table) aClass.getAnnotation(Table.class)).name().toUpperCase(), aClass);
            }
        }

        return tableNameMap;
    }

    public static List<String> getTableSequence (EntityManager oracleEm, boolean topDown) {
        return oracleEm.createNativeQuery(
                "SELECT table_name\n" +
                        "FROM (\n" +
                        "  SELECT table_name, max(lvl) lvl\n" +
                        "  FROM (\n" +
                        "    WITH \n" +
                        "    fk AS (\n" +
                        "      SELECT uc.table_name, urc.table_name ref_table_name\n" +
                        "      FROM\n" +
                        "        user_constraints uc\n" +
                        "        JOIN user_constraints urc ON uc.r_constraint_name=urc.constraint_name\n" +
                        "      WHERE\n" +
                        "        uc.table_name<>urc.table_name\n" +
                        "    ),\n" +
                        "    fks AS (\n" +
                        "      SELECT table_name, ref_table_name\n" +
                        "      FROM\n" +
                        "        fk\n" +
                        "      UNION ALL\n" +
                        "      SELECT table_name, null ref_table_name\n" +
                        "      FROM tabs\n" +
                        "      WHERE\n" +
                        "        table_name NOT IN (SELECT table_name FROM fk)\n" +
                        "        AND table_name NOT LIKE 'IMP\\_%' ESCAPE '\\'\n" +
                        "    )\n" +
                        "    SELECT\n" +
                        "      table_name, ref_table_name, level lvl\n" +
                        "    FROM\n" +
                        "      fks\n" +
                        "    CONNECT BY NOCYCLE PRIOR table_name=ref_table_name\n" +
                        "    START WITH ref_table_name IS null\n" +
                        "  )\n" +
                        "  GROUP BY table_name\n" +
                        ")\n" +
                        "ORDER BY lvl " + (topDown ? "asc" : "desc")
        ).getResultList();
    }

    public static Iterator<Class> getClassesSequenceIterator(EntityManagerFactory emf, boolean topDown) throws Exception{
        Map<Class, TreeEntity> treeEntities = new HashMap<Class, TreeEntity>();

        for (EntityType entityType : emf.getMetamodel().getEntities()) {
            Class entityClass = entityType.getJavaType();
            if (!treeEntities.containsKey(entityClass)) {
                treeEntities.put(entityClass, new TreeEntity(entityClass));
            }

            fillTreeEntityMap(treeEntities, entityType.getAttributes(), entityClass);
        }

        for (TreeEntity treeEntity : treeEntities.values()) {
            if (topDown) {
                treeEntity.calculateMaxLevel(treeEntities);
            }
            else {
                treeEntity.calculateMinLevel(treeEntities);
            }
        }

        List<TreeEntity> sorted = new ArrayList<TreeEntity>(treeEntities.values());
        Collections.sort(sorted);

        if (!topDown) {
            Collections.reverse(sorted);
        }

        List<Class> sortedClasses = new ArrayList<Class>(sorted.size());
        for (TreeEntity treeEntity : sorted) {
            sortedClasses.add(treeEntity.getEntityClass());
        }

        return sortedClasses.iterator();
    }

    public static Map<Class, Set<Class>> getMasterToDetailClassesMap(EntityManagerFactory emf) throws Exception {
        Map<Class, TreeEntity> treeEntities = new HashMap<Class, TreeEntity>();

        for (EntityType entityType : emf.getMetamodel().getEntities()) {
            Class entityClass = entityType.getJavaType();
            if (!treeEntities.containsKey(entityClass)) {
                treeEntities.put(entityClass, new TreeEntity(entityClass));
            }

            fillTreeEntityMap(treeEntities, entityType.getAttributes(), entityClass);
        }

        Map<Class, Set<Class>> m2dMap = new HashMap<Class, Set<Class>>();

        for (Map.Entry<Class, TreeEntity> entry : treeEntities.entrySet()) {
            Class detail = entry.getKey();
            for (Class master : entry.getValue().getMasterEntities()) {
                Set<Class> details = m2dMap.get(master);
                if (details == null) {
                    m2dMap.put(master, details = new HashSet<Class>());
                }
                details.add(detail);
            }
        }

        return m2dMap;
    }

    private static void fillTreeEntityMap(Map<Class, TreeEntity> treeEntities, Set<Attribute> attributes, Class entityClass) {
        for (Attribute attribute : attributes) {
            if (attribute instanceof SingularAttribute && ((SingularAttribute)attribute).getType() instanceof EmbeddableType) {
                fillTreeEntityMap(treeEntities, ((EmbeddableType) ((SingularAttribute) attribute).getType()).getAttributes(), entityClass);
            }
            else if (!attribute.isCollection() &&
                    attribute.getPersistentAttributeType() != Attribute.PersistentAttributeType.BASIC &&
                    attribute.getDeclaringType() instanceof EntityType) {
                Class targetEntity = attribute.getJavaType();
                addMasterEntity(treeEntities, entityClass, targetEntity);
            }
            else if (attribute.isCollection() &&
                    attribute.getPersistentAttributeType() != Attribute.PersistentAttributeType.MANY_TO_MANY &&
                    attribute instanceof PluralAttribute) {
                addMasterEntity(treeEntities, ((PluralAttribute) attribute).getElementType().getJavaType(), entityClass);
            }
        }
        if (!entityClass.isAnnotationPresent(Table.class)) {
            Class lastEntity = entityClass;
            Class parent = lastEntity;
            do {
                parent = parent.getSuperclass();
                if (parent.isAnnotationPresent(Entity.class)) {
                    addMasterEntity(treeEntities, parent, lastEntity);
                    lastEntity = parent;
                }
                if (parent.isAnnotationPresent(Table.class)) {
                    break;
                }
            } while(parent.getSuperclass() != null);
        }
    }

    public static Map<String, Field> getNonCollectionReferences(EntityManagerFactory emf, Class entityClass) {
        Map<String, Field> fields = new HashMap<String, Field>();
        appendNonCollectionReferences(emf.getMetamodel().entity(entityClass).getAttributes(), fields, "");
        return fields;
    }

    private static void appendNonCollectionReferences(Set<Attribute> attributes, Map<String, Field> fields, String context) {
        for (Attribute attribute : attributes) {
            String attributeName = context + attribute.getName();

            if (attribute instanceof SingularAttribute && ((SingularAttribute)attribute).getType() instanceof EmbeddableType) {
                appendNonCollectionReferences(((EmbeddableType)((SingularAttribute)attribute).getType()).getAttributes(), fields, attributeName+".");
            }
            else if (!attribute.isCollection() &&
                    attribute.getPersistentAttributeType() != Attribute.PersistentAttributeType.BASIC &&
                    attribute.getDeclaringType() instanceof EntityType) {
                fields.put(attributeName, (Field)attribute.getJavaMember());
            }
        }
    }

    private static void addMasterEntity(Map<Class, TreeEntity> treeEntityMap, Class detailClass, Class masterClass) {
        TreeEntity treeEntity = treeEntityMap.get(detailClass);
        if (treeEntity == null) {
            treeEntityMap.put(detailClass, treeEntity = new TreeEntity(detailClass));
        }
        treeEntity.addMasterEntity(masterClass);
    }

    private static class TreeEntity implements Comparable<TreeEntity> {
        private Class entityClass;
        private Set<Class> masterEntities = new HashSet<Class>();

        private Integer calculatedLevel;

        private TreeEntity(Class entityClass) {
            this.entityClass = entityClass;
        }

        private Class getEntityClass() {
            return entityClass;
        }

        private Integer getCalculatedLevel() {
            return calculatedLevel;
        }

        public void addMasterEntity(Class entity) {
            masterEntities.add(entity);
        }

        private Set<Class> getMasterEntities() {
            return masterEntities;
        }

        public void calculateMaxLevel(Map<Class, TreeEntity> map) {
            // break circular loop
            if (calculatedLevel != null) {
                return;
            }

            calculatedLevel=1;

            for (Class masterEntity : masterEntities) {
                TreeEntity treeEntity = map.get(masterEntity);
                Integer calculatedMasterLevel = treeEntity.getCalculatedLevel();
                if (calculatedMasterLevel == null) {
                    treeEntity.calculateMaxLevel(map);
                    calculatedMasterLevel = treeEntity.getCalculatedLevel();
                }
                if (!entityClass.isAssignableFrom(masterEntity)) {
                    calculatedMasterLevel++;
                }
                calculatedLevel = Math.max(calculatedLevel, calculatedMasterLevel);
            }

        }

        public void calculateMinLevel(Map<Class, TreeEntity> map) {
            // break circular loop
            if (calculatedLevel != null) {
                return;
            }

            calculatedLevel=1;

            for (Class masterEntity : masterEntities) {
                TreeEntity treeEntity = map.get(masterEntity);
                Integer calculatedMasterLevel = treeEntity.getCalculatedLevel();
                if (calculatedMasterLevel == null) {
                    treeEntity.calculateMinLevel(map);
                    calculatedMasterLevel = treeEntity.getCalculatedLevel();
                }
                if (!entityClass.isAssignableFrom(masterEntity)) {
                    calculatedMasterLevel++;
                }
                calculatedLevel = Math.max(calculatedLevel, calculatedMasterLevel);
            }
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof TreeEntity && entityClass.equals(((TreeEntity)obj).entityClass);
        }

        @Override
        public int hashCode() {
            return entityClass.hashCode();
        }

        @Override
        public int compareTo(TreeEntity o) {
            int lvlCmp = getCalculatedLevel().compareTo(o.getCalculatedLevel());
            return lvlCmp != 0 ? lvlCmp : getEntityClass().getSimpleName().compareTo(o.getEntityClass().getSimpleName());
        }
    }
}