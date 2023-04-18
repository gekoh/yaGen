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

import com.github.gekoh.yagen.api.Constants;
import org.apache.commons.lang.StringUtils;
import org.hibernate.annotations.Type;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Basic;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.EmbeddedId;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Transient;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
* @author Georg Kohlweiss
*/
public class FieldInfo {

    private static Pattern NULLABLE_PATTERN = Pattern.compile("nullable=((true)|(false))");
    private static Pattern UNIQUE_PATTERN = Pattern.compile("unique=((true)|(false))");
    private static Pattern STRING_ATTR_PATTERN = Pattern.compile("[\\(|\\s*](name|columnDefinition|type)=([^\\s]*)[,\\)]");
    private static Pattern INSERTABLE_PATTERN = Pattern.compile("[\\(,]\\s*(insertable\\s*=\\s*(true|false))\\s*[,\\)]");
    private static Pattern UPDATABLE_PATTERN = Pattern.compile("[\\(,]\\s*(updatable\\s*=\\s*(true|false))\\s*[,\\)]");

    private Class type;
    private String name;
    private String columnName;
    private String columnAnnotation;
    private Field field;

    private boolean isEnum;
    private boolean isEmbedded;
    private boolean isLob;


    public FieldInfo(Class type, String name) {
        this.type = type;
        this.name = name;
    }

    public FieldInfo(Class type, String name, String columnAnnotation) {
        this(type, name);
        this.columnAnnotation = columnAnnotation;
        this.columnAnnotation = concatOverrides(this.columnAnnotation, getAttributeOverrides(type).values());
    }

    public FieldInfo(Class type, String name, AttributeOverrides overrides) {
        this(type, name, overrides != null ? formatAnnotation(overrides) : "");
        isEmbedded = true;
    }

    public FieldInfo(Class type, String name, AttributeOverride override) {
        this(type, name, override != null ? formatAnnotation(override) : "");
        isEmbedded = true;
    }

    public FieldInfo(Class type, String name, boolean anEnum, Column column, boolean anLob) {
        this(type, name, formatAnnotation(column));
        this.columnName = MappingUtils.deriveColumnName(column, name).toLowerCase();
        isEnum = anEnum;
        isLob = anLob;
    }

    public FieldInfo(Class type, String name, String columnName, int columnLength, JoinColumn joinColumn) {
        this(type, name, !isCollection(type) ? "@" + Column.class.getName() + "(name = \"" + escapeAttributeValue(columnName) + "\", length = " + columnLength + ", insertable = " + joinColumn.insertable() + ", updatable = " + joinColumn.updatable() + ")" : null);
        this.columnName = columnName.toLowerCase();
    }

    public FieldInfo(Class type, String name, String columnName, int columnLength) {
        this(type, name, !isCollection(type) ? "@" + Column.class.getName() + "(name = \"" + escapeAttributeValue(columnName) + "\", length = " + columnLength + ")" : null);
        this.columnName = columnName.toLowerCase();
    }

    public FieldInfo(Class type, String name, String columnName, boolean nullable, String typeAnnotation) {
        this(type, name, "@" + Column.class.getName() + "(name = \"" + escapeAttributeValue(columnName) + "\", nullable = " + nullable + ")" + (typeAnnotation != null ? " @" + Type.class.getName() + "(type = \"" + typeAnnotation + "\")" : ""));
        this.columnName = columnName.toLowerCase();
    }

    public Class getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getColumnName() {
        return columnName;
    }

    public String getColumnAnnotation() {
        return columnAnnotation;
    }

    public boolean isEnum() {
        return isEnum;
    }

    public boolean isEmbedded() {
        return isEmbedded;
    }

    public boolean isLob() {
        return isLob;
    }

    public boolean isBooleanType() {
        return type == Boolean.class || type == boolean.class;
    }

    public boolean isCollection() {
        return isCollection(type);
    }

    public static boolean isCollection(Class type) {
        return Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type);
    }

    public void addAnnotation(Annotation annotation) {
        columnAnnotation = (columnAnnotation != null ? columnAnnotation+"\n    " : "" ) + formatAnnotation(annotation);
    }

    public Field getField() {
        return field;
    }

    public void setField(Field field) {
        this.field = field;
    }

    public void setReadOnly(boolean readOnly) {
        if (columnAnnotation == null) {
            return;
        }
        Matcher matcher = INSERTABLE_PATTERN.matcher(columnAnnotation);
        if (matcher.find()) {
            columnAnnotation = columnAnnotation.substring(0, matcher.start(2)) + !readOnly + columnAnnotation.substring(matcher.end(2));
        }
        else {
            int index = columnAnnotation.lastIndexOf(')');
            columnAnnotation = columnAnnotation.substring(0, index) + ", insertable = " + !readOnly + columnAnnotation.substring(index);
        }
        matcher = UPDATABLE_PATTERN.matcher(columnAnnotation);
        if (matcher.find()) {
            columnAnnotation = columnAnnotation.substring(0, matcher.start(2)) + !readOnly + columnAnnotation.substring(matcher.end(2));
        }
        else {
            int index = columnAnnotation.lastIndexOf(')');
            columnAnnotation = columnAnnotation.substring(0, index) + ", updatable = " + !readOnly + columnAnnotation.substring(index);
        }
    }

    public boolean isReadOnly() {
        if (columnAnnotation == null) {
            return false;
        }
        Matcher matcher = UPDATABLE_PATTERN.matcher(columnAnnotation);
        return matcher.find() && matcher.group(2).equals("" + false);
    }

    private static String formatAnnotation(Annotation annotation) {
        String a = annotation.toString();
        StringBuilder result = new StringBuilder();

        if (a.endsWith("=)")) {
            a = a.substring(0, a.length() - 2) + "= )";
        }

        // wrap string value of attribute "name" into double quotes as needed for java code
        Matcher m = STRING_ATTR_PATTERN.matcher(a);
        int idx = 0;
        while (m.find(idx)) {
            result.append(a.substring(idx, m.start(2)));
            result.append("\"").append(escapeAttributeValue(m.group(2))).append("\"");
            result.append(a.substring(m.end(2), m.end()));
            idx = m.end();
        }
        result.append(a.substring(idx));

        a = result.toString();

        if (a.endsWith(" )")) {
            a = a.substring(0, a.length() - 2) + ")";
        }

        result = new StringBuilder();

        // remove empty attributes like (columnDefinition=)
        m = Pattern.compile("\\(?(,?\\s*[A-Za-z]*=)[,|\\)]").matcher(a);
        idx = 0;
        while (m.find(idx)) {
            result.append(a.substring(idx, m.start(1)));
            idx = m.end(1);
        }
        result.append(a.substring(idx));

        // set nullable=true
        m = NULLABLE_PATTERN.matcher(result);
        idx = 0;
        while (m.find(idx)) {
            if (m.group(1).equals("false")) {
                result.replace(m.start(1), m.end(1), "true");
            }
            idx = m.start(1)+1;
            m = NULLABLE_PATTERN.matcher(result);
        }

        // set unique=false
        m = UNIQUE_PATTERN.matcher(result);
        idx = 0;
        while (m.find(idx)) {
            if (m.group(1).equals("true")) {
                result.replace(m.start(1), m.end(1), "false");
            }
            idx = m.start(1)+1;
            m = UNIQUE_PATTERN.matcher(result);
        }

        return result.toString().replaceAll("=\\[([^\\]]*)\\]", "={$1}");
    }

    private static String escapeAttributeValue(String value) {
        return value.replace("\"", "\\\"");
    }

    private static final Pattern ATTR_OVERR_NAME = Pattern.compile(AttributeOverride.class.getName() + "\\s*\\(\\s*name\\s*=\\s*\"([^\"]+)\"");

    private static int findAttributeOverride(String annotations, String name) {
        Matcher matcher = ATTR_OVERR_NAME.matcher(annotations);

        int idx = 0;
        while (matcher.find(idx)) {
            if (name.equals(matcher.group(1))) {
                return matcher.start();
            }
            idx = matcher.end();
        }
        return -1;
    }

    private static String addNamePrefixToAttributeOverride (String annotation, String prefix) {
        Matcher matcher = ATTR_OVERR_NAME.matcher(annotation);
        if (matcher.find()) {
            return annotation.substring(0, matcher.start(1)) + prefix + annotation.substring(matcher.start(1));
        }
        throw new IllegalArgumentException("no AttributeOverride found in '"+annotation+"'");
    }

    private static String getNameFromAttributeOverride (String attributeOverride) {
        Matcher matcher = ATTR_OVERR_NAME.matcher(attributeOverride);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("no name of AttributeOverride found in '"+attributeOverride+"'");
    }

    /**
     * generates a collection of javax.persistence.AttributeOverride annotations needed to override nullable=false
     * columns since this is not allowed in *Hst-Entities
     *
     * @param type embeddable class with non-nullable fields
     * @return collection of javax.persistence.AttributeOverride annotations needed to set columns to nullable=true
     */
    private Map<String, String> getAttributeOverrides(Class type) {
        Map<String, String> overrides = new LinkedHashMap<String, String>();
        addAttributeOverrides(overrides, "", type);
        return overrides;
    }

    private void addAttributeOverrides(Map<String, String> overrides, String path, Class type) {
        for (Field field : type.getDeclaredFields()) {
            String fieldPath = path + field.getName();
            String curPath = fieldPath + ".";

            if (findAttributeOverride(columnAnnotation, fieldPath) < 0) {

                Column column;
                if (field.isAnnotationPresent(AttributeOverride.class)) {
                    addAttributeOverride(overrides, addNamePrefixToAttributeOverride(
                            formatAnnotation(field.getAnnotation(AttributeOverride.class)),
                            curPath));
                } else if (field.isAnnotationPresent(AttributeOverrides.class)) {
                    for (AttributeOverride attributeOverride : field.getAnnotation(AttributeOverrides.class).value()) {
                        addAttributeOverride(overrides, addNamePrefixToAttributeOverride(
                                formatAnnotation(attributeOverride),
                                curPath));
                    }
                } else if (((column = field.getAnnotation(Column.class)) != null && (!column.nullable() || column.unique())) ||
                        (field.isAnnotationPresent(Basic.class) && !field.getAnnotation(Basic.class).optional())) {
                    String columnName = column != null ? column.name() : field.getName();
                    int length = column != null ? column.length() : 255;

                    String override = "@javax.persistence.AttributeOverride(name=\"" + fieldPath + "\", column=" +
                            "@javax.persistence.Column(name=\"" + columnName + "\", length=" + length + ", nullable=true, unique=false))";

                    addAttributeOverride(overrides, override);
                }
            }

            if (field.isAnnotationPresent(Embedded.class)) {
                addAttributeOverrides(overrides, curPath, field.getType());
            }
        }
    }

    private void addAttributeOverride(Map<String, String> overrides, String attributeOverride) {
        String name = getNameFromAttributeOverride(attributeOverride);
        if (!overrides.containsKey(name) && findAttributeOverride(columnAnnotation, name) < 0) {
            overrides.put(name, attributeOverride);
        }
    }

    private static final Pattern PATTERN_ATTR_OVERRIDES = Pattern.compile("@" + AttributeOverrides.class.getName() + "\\((value=)?\\{([^}]*)(\\})\\)");
    private static final Pattern PATTERN_ATTR_OVERRIDE =  Pattern.compile("(@" + AttributeOverride.class.getName() + "\\([^)]*@" + Column.class.getName() + "\\([^)]*\\)[^)]*\\))(, )?");

    /**
     * merges given collection of javax.persistence.AttributeOverride elements into an optionally existing
     * javax.persistence.AttributeOverrides annotation with optionally pre-existing javax.persistence.AttributeOverride elements.
     *
     * @param annotation existing javax.persistence.AttributeOverrides annotation, if any, otherwise it will be created
     * @param attributeOverrides collection of javax.persistence.AttributeOverride annotation to be appended
     * @return merged AttributeOverrides annotation
     */
    private static String concatOverrides(String annotation, Collection<String> attributeOverrides) {
        if (attributeOverrides == null || attributeOverrides.size() < 1) {
            return annotation;
        }
        if (annotation == null) {
            annotation = "";
        }
        else {
            Matcher overrideMatcher;
            while ((overrideMatcher = PATTERN_ATTR_OVERRIDE.matcher(annotation)).find()) {
                if (!(attributeOverrides instanceof ArrayList)) {
                    attributeOverrides = new ArrayList<String>(attributeOverrides);
                }
                ((ArrayList) attributeOverrides).add(0, overrideMatcher.group(1));
                annotation = annotation.substring(0, overrideMatcher.start()) + annotation.substring(overrideMatcher.end());
            }
        }

        Matcher overridesMatcher = PATTERN_ATTR_OVERRIDES.matcher(annotation);

        if (!overridesMatcher.find()) {
            annotation = (annotation.length() < 1 ? "" : annotation + "\n    ") + "@" + AttributeOverrides.class.getName() + "({})";
        }

        for (String addOverride : attributeOverrides) {
            overridesMatcher = PATTERN_ATTR_OVERRIDES.matcher(annotation);
            if (!overridesMatcher.find()) {
                throw new IllegalStateException();
            }
            if (StringUtils.isNotEmpty(overridesMatcher.group(2))) {
                addOverride = ", " + addOverride;
            }
            annotation = annotation.substring(0, overridesMatcher.start(3)) + addOverride +
                    annotation.substring(overridesMatcher.start(3));
        }

        return annotation;
    }


    public static List<FieldInfo> convertDeclaredAndInheritedFields(Class baseEntity) {
        List<FieldInfo> fields = new ArrayList<FieldInfo>();
        Class clazz = baseEntity;
        while (clazz != null) {
            convertFields(fields, clazz);
            clazz = clazz.getSuperclass();
        }
        return fields;
    }

    public static List<FieldInfo> convertFields(Class baseEntity) {
        return convertFields(new ArrayList<FieldInfo>(), baseEntity);
    }

    private static List<FieldInfo> convertFields(List<FieldInfo> fields, Class baseEntity) {

        for (Field field : baseEntity.getDeclaredFields()) {
            FieldInfo fi;
            Class type = field.getType();
            String name = field.getName();
            Column column = field.getAnnotation(Column.class);
            if (field.isAnnotationPresent(Embedded.class)) {
                if (field.isAnnotationPresent(AttributeOverride.class)) {
                    fi = new FieldInfo(type, name, field.getAnnotation(AttributeOverride.class));
                } else {
                    fi = new FieldInfo(type, name, field.getAnnotation(AttributeOverrides.class));
                }
            } else if (field.isAnnotationPresent(Enumerated.class)) {
                fi = new FieldInfo(type, name, true, column, false);
            } else if (column != null && !field.isAnnotationPresent(CollectionTable.class)) {
                if (type.isPrimitive()) {
                    if (type.equals(Boolean.TYPE)) {
                        type = Boolean.class;
                    } else if (type.equals(Long.TYPE)) {
                        type = Long.class;
                    } else if (type.equals(Integer.TYPE)) {
                        type = Integer.class;
                    } else if (type.equals(Short.TYPE)) {
                        type = Short.class;
                    } else if (type.equals(Byte.TYPE)) {
                        type = Byte.class;
                    } else if (type.equals(Double.TYPE)) {
                        type = Double.class;
                    } else if (type.equals(Float.TYPE)) {
                        type = Float.class;
                    } else if (type.equals(Character.TYPE)) {
                        type = Character.class;
                    }
                }
                fi = new FieldInfo(type, name, false, column, field.isAnnotationPresent(Lob.class));
            } else if ((field.isAnnotationPresent(ManyToOne.class) && !field.isAnnotationPresent(JoinTable.class)) ||
                    (field.isAnnotationPresent(OneToOne.class) && StringUtils.isEmpty(field.getAnnotation(OneToOne.class).mappedBy()))) {
                fi = getIdFieldInfo(type, name, MappingUtils.deriveColumnName(field));
                if (field.isAnnotationPresent(PrimaryKeyJoinColumn.class)) {
                    fi.setReadOnly(true);
                }
            } else if (!field.isAnnotationPresent(Transient.class) &&
                    (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) &&
                    (field.isAnnotationPresent(JoinColumn.class) || field.isAnnotationPresent(JoinTable.class) || field.isAnnotationPresent(CollectionTable.class) ||
                            (field.isAnnotationPresent(OneToMany.class) && StringUtils.isNotEmpty(field.getAnnotation(OneToMany.class).mappedBy())))) {
                fi = new FieldInfo(type, name);
            } else {
                continue;
            }
            if (field.isAnnotationPresent(Type.class)) {
                fi.addAnnotation(field.getAnnotation(Type.class));
            }
            fi.setField(field);
            fields.add(fi);
        }

        return fields;
    }

    public static Column getIdColumn (Class classType) {
        AccessibleObject id = getIdFieldOrMethod(classType);
        if (id == null) {
            return null;
        }
        Column column = id.getAnnotation(Column.class);
        Class type;
        if (id instanceof Field) {
            type = ((Field) id).getType();
        } else {
            type = ((Method) id).getReturnType();
        }
        if (column == null && id.isAnnotationPresent(EmbeddedId.class)) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(Column.class)) {
                    return field.getAnnotation(Column.class);
                }
            }
        }
        return column;
    }

    public static FieldInfo getIdFieldInfo (Class classType, String namePrefix, String columnName) {
        AccessibleObject id = getIdFieldOrMethod(classType);
        String suffix;
        Class type;
        if (id instanceof Field) {
            type = ((Field) id).getType();
            suffix = ((Field) id).getName().substring(0, 1).toUpperCase() + ((Field) id).getName().substring(1);
        } else {
            type = ((Method) id).getReturnType();
            suffix = ((Method) id).getName().replace("get", "").replace("set", "");
        }
        Column column = getIdColumn(classType);
        if (column == null) {
            throw new IllegalStateException("cannot find @Column on Id field for type " + classType);
        }
        String name = namePrefix + suffix;
        if (id.isAnnotationPresent(EmbeddedId.class)) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(Column.class)) {
                    FieldInfo fieldInfo = new FieldInfo(type, name, "@" + AttributeOverride.class.getName() + "(name=\"" + field.getName() + "\", column=" +
                            "@" + Column.class.getName() + "(name = \"" + escapeAttributeValue(columnName) + "\", length = " + column.length() + "))");
                    fieldInfo.isEmbedded = true;
                    return fieldInfo;
                }
            }
        }
        return new FieldInfo(type, name, columnName, column.length());
    }

    public static List<FieldInfo> convertInverseFKs (List<AccessibleObject> inverseFKfieldsOrMethods) {
        List<FieldInfo> fields = new ArrayList<FieldInfo>();
        for (AccessibleObject inverseFK : inverseFKfieldsOrMethods) {
            JoinColumn joinColumn = inverseFK.getAnnotation(JoinColumn.class);
            String joinColName = joinColumn.name();
            fields.add(new FieldInfo(String.class, toCamelCase("INV_FK_"+joinColName), joinColName, Constants.UUID_LEN, joinColumn));
        }
        return fields;
    }

    public static AccessibleObject getIdFieldOrMethod(Class entityClass) {
        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class) || field.isAnnotationPresent(EmbeddedId.class)) {
                return field;
            }
        }
        for (Method method : entityClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Id.class) || method.isAnnotationPresent(EmbeddedId.class)) {
                return method;
            }
        }
        return entityClass.getSuperclass() != null ? getIdFieldOrMethod(entityClass.getSuperclass()) : null;
    }

    public static String toCamelCase(String columnName) {
        StringBuilder s = new StringBuilder();
        int idx=-1, lastIdx=0;

        while ((idx = columnName.indexOf('_', idx+1)) >= 0) {
            if (lastIdx > 0) {
                s.append(columnName.substring(lastIdx+1, lastIdx+2).toUpperCase());
                s.append(columnName.substring(lastIdx+2, idx).toLowerCase());
            }
            else {
                s.append(columnName.substring(lastIdx, idx).toLowerCase());
            }
            lastIdx = idx;
        }

        if (lastIdx > 0) {
            s.append(columnName.substring(lastIdx+1, lastIdx+2).toUpperCase());
            s.append(columnName.substring(lastIdx+2).toLowerCase());
        }
        else {
            s.append(columnName.substring(lastIdx).toLowerCase());
        }

        return s.toString();
    }
}