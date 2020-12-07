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
package com.github.gekoh.yagen.ddl.comment;

import com.github.gekoh.yagen.ddl.CoreDDLGenerator;
import com.github.gekoh.yagen.ddl.DDLGenerator;
import com.github.gekoh.yagen.hibernate.YagenInit;
import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.AnnotationValue;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.Doclet;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.LanguageVersion;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.Type;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang.StringUtils;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Embeddable;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.Version;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CommentsDDLGenerator extends Doclet {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CommentsDDLGenerator.class);

    private static final Class<?>[] jpaAnnotations = new Class<?>[]{Entity.class};
    private static final Class<?>[] mappedSuperclassAnnotations = new Class<?>[]{MappedSuperclass.class};
    public static final Class<?>[] tableAnnotations = new Class<?>[]{Table.class};
    public static final Class<?>[] columnAnnotations = new Class<?>[]{Column.class};
    public static final Class<?>[] allColumnAnnotations = new Class<?>[]{Id.class, Column.class, Version.class, Basic.class};
    public static final Class<?>[] transientAnnotations = new Class<?>[]{Transient.class};
    public static final Class<?>[] embededColumnAnnotations = new Class<?>[]{Embedded.class};
    public static final Class<?>[] embeddableClassAnnotations = new Class<?>[]{Embeddable.class};
    public static final Class<?>[] relationAnnotations = new Class<?>[]{ManyToMany.class, OneToMany.class, OneToOne.class, ManyToOne.class};
    public static final Class<?>[] oneToManyAnnotaions = new Class<?>[]{OneToMany.class};
    public static final Class<?>[] oneToOneAnnotations = new Class<?>[]{OneToOne.class};
    public static final Class<?>[] manyToOneAnnotations = new Class<?>[]{ManyToOne.class};
    public static final Class<?>[] manyToManyAnnotations = new Class<?>[]{ManyToMany.class};
    public static final Class<?>[] joinColumnAnnotation = new Class<?>[]{JoinColumn.class};
    public static final Class<?>[] joinTableAnnotations = new Class<?>[]{JoinTable.class};
    public static final Class<?>[] attributeOverrideAnnotation = new Class<?>[]{AttributeOverride.class, AttributeOverrides.class};
    public static final Class<?>[] discriminatorAnnotation = new Class<?>[]{DiscriminatorValue.class};
    public static final Class<?>[] inheritanceAnnotation = new Class<?>[]{Inheritance.class};
    public static final Class<?>[] tableAnnotation = new Class<?>[]{com.github.gekoh.yagen.api.Table.class};

    // maps entity class name to map of column name to comment
    // table comment specified as comment of a "null" column name
    private static Map<String, Map<String, String>> OUTPUT_COMMENTS;

    private static Set<String> ENTITY_CLASS_NAMES_ONLY;
    private static Set<String> RENDERED_OBJECTS;

    // need to drop unused arguments specified by gradle javadoc task
    private static final List<String> UNUSED_OPTIONS = Arrays.asList("-d", "-doctitle", "-windowtitle");

    @SuppressWarnings("UnusedDeclaration")
    public static int optionLength(String option) {
        String longOpt = option.substring(2);

        if (UNUSED_OPTIONS.contains(option)) {
            LOG.warn("unused option {} recognized", option);
            return 2;
        }

        if (CoreDDLGenerator.OPTIONS.hasOption(longOpt)) {
            if (CoreDDLGenerator.OPTIONS.getOption(longOpt).hasArg()) {
                return 2;
            }
            return 1;
        }

        return 0;
    }

    @SuppressWarnings("UnusedDeclaration")
    public static boolean validOptions(String options[][],
                                       DocErrorReporter reporter) {
        try {
            parseOptions(options);
            return true;
        } catch (Exception e) {
            reporter.printError(e.getMessage());
        }
        return false;
    }

    private static DDLGenerator.Profile parseOptions(String[][] options) {
        try {
            YagenInit.init();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        List<String> args = new ArrayList<String>();
        for (String[] opt : options) {
            if (opt[0].startsWith("--") && CoreDDLGenerator.OPTIONS.hasOption(opt[0].substring(2))) {
                args.add(opt[0]);
                if (opt.length > 1) {
                    args.add(opt[1]);
                }
            }
        }
        try {
            return CoreDDLGenerator.createProfileFrom(args.toArray(new String[args.size()]));
        } catch (ParseException e) {
            LOG.error("error parsing arguments", e);
        }
        return null;
    }

    @SuppressWarnings("UnusedDeclaration")
    public static boolean start(RootDoc root) {
        DDLGenerator.Profile profile = parseOptions(root.options());

        OUTPUT_COMMENTS = new LinkedHashMap<String, Map<String, String>>();

        final ClassDoc[] classes = root.classes();
        // sorting classes to get deterministic results, otherwise reference columns get added first occasionally
        Arrays.sort(classes, new Comparator<ClassDoc>() {
            public int compare(ClassDoc o1, ClassDoc o2) {
                return o1.name().compareTo(o2.name());
            }
        });
        
        for (final ClassDoc klass : classes) {
            handleClass(root, klass, null, null);
        }

        profile.setComments(OUTPUT_COMMENTS);

        CoreDDLGenerator.generateFrom(profile);

        return true;
    }

    @SuppressWarnings("UnusedDeclaration")
    public static LanguageVersion languageVersion() {
        return LanguageVersion.JAVA_1_5;
    }

    public static Map<String, String> getColumnComments (String entityClassName) {
        Map<String, String> columnComments = OUTPUT_COMMENTS.get(entityClassName);
        if (columnComments == null) {
            OUTPUT_COMMENTS.put(entityClassName, columnComments = new LinkedHashMap<String, String>());
        }
        return columnComments;
    }

    private static void handleClass(RootDoc root, ClassDoc klass, String tableName, String shortTableName) {
        if (ENTITY_CLASS_NAMES_ONLY != null && !ENTITY_CLASS_NAMES_ONLY.contains(klass.qualifiedName())) {
            return;
        }

        if ((tableName == null && DocletUtils.findAnnotatedClass(klass, jpaAnnotations) != null) ||
                (tableName != null && DocletUtils.findAnnotatedClass(klass, mappedSuperclassAnnotations) != null)) {

            if (tableName == null) {
                //do not write table comments for mapped superclasses
                tableName = getTableName(klass);
                shortTableName = getShortTableName(klass);

                if (RENDERED_OBJECTS != null &&
                        !RENDERED_OBJECTS.contains(tableName.toLowerCase())) {
                    return;
                }

                Map<String, String> columnComments = getColumnComments(klass.qualifiedName());
                if (DocletUtils.findAnnotatedClass(klass, discriminatorAnnotation) == null) {
                    // no table comment for derived classes of a single table inheritance super class
                    putTableComment(columnComments, klass, tableName, shortTableName);
                }
            }

            Map<String, String> columnComments = getColumnComments(klass.qualifiedName());
            writeColumns(columnComments, root, klass, tableName);

            ClassDoc superClass = klass.superclass();
            String value = getInheritanceStrategyValue(superClass);
            if (DocletUtils.hasAnnotation(superClass, mappedSuperclassAnnotations) ||
                    (DocletUtils.hasAnnotation(superClass, jpaAnnotations) && "javax.persistence.InheritanceType.TABLE_PER_CLASS".equals(value))) {
                //only hande mappedSuperclass when 'direct' superclass
                handleClass(root, superClass, tableName, shortTableName);
            }

            if (DocletUtils.hasAnnotation(superClass, tableAnnotations) &&
                    DocletUtils.hasAnnotation(superClass, inheritanceAnnotation)) {
                if ("javax.persistence.InheritanceType.SINGLE_TABLE".equals(value) &&
                        DocletUtils.hasAnnotation(klass, tableAnnotations)) {
                    handleClass(root, superClass, tableName, shortTableName);
                }
            }
        }
    }

    private static String getTableComment(String tableName, String tableShortName, ClassDoc classDoc) {
        String comment = classDoc.commentText();
        TableMetadata metadata = new TableMetadata(tableShortName, classDoc.qualifiedName(), comment);

        if (StringUtils.isEmpty(comment)) {
            LOG.info("missing comment on table {}", tableName);
        }
        return MetadataSerializationSupport.toXML(metadata);
    }

    private static void writeColumns(Map<String, String> comments, RootDoc root, ClassDoc klass, String tableName) {
        handleClassFields(comments, root, klass, tableName);
        handleClassMethods(comments, klass, tableName);
    }

    private static void handleClassFields(Map<String, String> comments,
                                          RootDoc root,
                                          ClassDoc klass, String tableName) {
        for (final FieldDoc fieldDoc : klass.fields(false)) {
            if (isTransient(fieldDoc)) {
                continue;
            }

            String comment = fieldDoc.commentText();

            if (isEmbeddedColumn(fieldDoc)) {
                writeEmbeddedClass(comments, root, fieldDoc, tableName);

            } else if (isColumn(fieldDoc)) {
                String columnName = getFullFieldNameFromAnnotation(fieldDoc, tableName, false, columnAnnotations);
                putColumnComment(comments, columnName, comment);

            } else if (isRelationColumn(fieldDoc)) {
                handleRelationshipColumn(comments, tableName, fieldDoc, comment);
            }
        }
    }

    private static void putTableComment(Map<String, String> comments, ClassDoc klass, String tableName, String shortTableName) {
        String comment = getTableComment(tableName, shortTableName, klass);
        if (comment == null) {
            return;
        }
        // table comment always should be first one added (we have a LinkedHashMap here)
        if (!comments.isEmpty()) {
            Map<String, String> existing = new LinkedHashMap<String, String>(comments);
            comments.clear();
            comments.put(null, comment);
            comments.putAll(existing);
        }
        else {
            comments.put(null, comment);
        }
    }

    private static void putColumnComment(Map<String, String> comments, String columnName, String comment) {
        if (StringUtils.isEmpty(comment)) {
            return;
        }
        columnName = columnName.toLowerCase();
        comments.put(columnName, comment);
    }

    private static void handleClassMethods(Map<String, String> comments,
                                           ClassDoc klass, String tableName) {
        for (final MethodDoc methodDoc : klass.methods(false)) {
            if (isTransient(methodDoc)) {
                continue;
            }

            String comment = methodDoc.commentText();

            if (isColumn(methodDoc)) {
                String columnName = getFullFieldNameFromAnnotation(methodDoc, tableName, false, columnAnnotations);
                putColumnComment(comments, columnName, comment);
            }
        }
    }


    private static void handleRelationshipColumn(Map<String, String> comments,
                                                 String tableName,
                                                 FieldDoc fieldDoc,
                                                 String comment) {
//      this relation has a JoinTable, so there is no column on this side of the relation
        if (isManyToOneColumn(fieldDoc) && hasJoinTable(fieldDoc)) {
            return;
        }

        String columnName;
        if (isOneToManyColumn(fieldDoc)) {
            //handle inverse relation
            putOneToManyColumnComment(fieldDoc, comment);
            return;
        } else if (isManyToOneColumn(fieldDoc) || (isOneToOneColumn(fieldDoc))) {
            AnnotationDesc ann = DocletUtils.findAnnotation(fieldDoc, oneToOneAnnotations);

            if (ann != null && getParameterValue("mappedBy", ann.elementValues()) != null) {
//              there is no column on this side of the relation
                return;
            }

            columnName = getFullFieldNameFromAnnotation(fieldDoc, tableName, true, joinColumnAnnotation);
        } else if (isManyToManyColumn(fieldDoc)) {
            System.out.println("WARNING: ManyToMany mapping not yet supported on: " + tableName + "." + fieldDoc.name());
            return;
        } else {
            throw new IllegalStateException("Relationship found which is not specified in relationAnnotations!");
        }

        putColumnComment(comments, columnName, comment);
    }

    private static void putOneToManyColumnComment(FieldDoc fieldDoc, String comment) {

        ClassDoc targetEntity = getAnnotationTargetEntity(fieldDoc, oneToManyAnnotaions);

        if (targetEntity == null) {
            throw new IllegalArgumentException("no target entity specified for " + fieldDoc.qualifiedName());
        } else {
            String targetTableName = getTableName(targetEntity).toLowerCase();
            FieldDoc mappedByField = getMappedBy(fieldDoc);
            String columnName = getFullFieldNameFromAnnotation(mappedByField != null ? mappedByField : fieldDoc, targetTableName, true, joinColumnAnnotation);

            putColumnComment(getColumnComments(getAnnotationTargetEntity(fieldDoc, oneToManyAnnotaions).qualifiedName()), columnName, comment);
        }
    }

    private static void writeEmbeddedColumns(Map<String, String> comments,
                                             ClassDoc klass,
                                             String inheritedComment,
                                             Map<String, String> columnNameOverrides,
                                             String targetAttribute) {
        for (final FieldDoc fieldDoc : klass.fields(false)) {
            String comment = null;
            String columnName = null;

            if (fieldDoc.name().equals(targetAttribute)) {
                comment = StringUtils.isEmpty(inheritedComment) ? fieldDoc.commentText() : inheritedComment + fieldDoc.commentText();

                if (columnNameOverrides == null || columnNameOverrides.get(fieldDoc.name()) == null) {
                    columnName = targetAttribute;
                } else {
                    columnName = columnNameOverrides.get(fieldDoc.name());
                }
            } else if (isColumn(fieldDoc)) {
                comment = StringUtils.isEmpty(inheritedComment) ? fieldDoc.commentText() : inheritedComment + fieldDoc.commentText();

                if (columnNameOverrides == null || columnNameOverrides.get(fieldDoc.name()) == null) {
                    columnName = getFieldNameFromAnnotation(fieldDoc, columnAnnotations);
                } else {
                    columnName = columnNameOverrides.get(fieldDoc.name());
                }

            }

            if (columnName != null && comment != null) {
                putColumnComment(comments, columnName, comment);
            }
        }
    }

    private static void writeEmbeddedClass(Map<String, String> comments, RootDoc root, FieldDoc fieldDoc, String tableName) {

        Map<String, String> columnNameOverrides = null;
        if (isAttributeOverride(fieldDoc)) {
            columnNameOverrides = getAttributeOverrideColumns(fieldDoc, attributeOverrideAnnotation);
        }

        String targetAttribute = getAnnotationNameValue(fieldDoc, attributeOverrideAnnotation);

        for (ClassDoc klass : root.classes()) {
            if (DocletUtils.hasAnnotation(klass, embeddableClassAnnotations)) {
                if (fieldDoc.type().toString().equals((klass.qualifiedName()))) {
                    String commentString;
                    if (StringUtils.isEmpty(fieldDoc.commentText())) {
                        commentString = fieldDoc.commentText();
                    } else {
                        commentString = fieldDoc.commentText().endsWith(".") ? fieldDoc.commentText() + " " : fieldDoc.commentText() + ". ";
                    }
                    writeEmbeddedColumns(comments, klass, commentString, columnNameOverrides, targetAttribute);

                }
            }
        }
    }

    public static boolean isTransient(ProgramElementDoc field) {
        return DocletUtils.hasAnnotation(field, transientAnnotations);
    }

    public static boolean isColumn(ProgramElementDoc field) {
        return DocletUtils.hasAnnotation(field, allColumnAnnotations);
    }

    public static boolean isAttributeOverride(FieldDoc field) {
        return DocletUtils.hasAnnotation(field, attributeOverrideAnnotation);
    }

    public static boolean isEmbeddedColumn(FieldDoc field) {
        return DocletUtils.hasAnnotation(field, embededColumnAnnotations);
    }

    public static boolean isOneToManyColumn(FieldDoc field) {
        return DocletUtils.hasAnnotation(field, oneToManyAnnotaions);
    }

    public static boolean isManyToOneColumn(FieldDoc field) {
        return DocletUtils.hasAnnotation(field, manyToOneAnnotations);
    }

    private static boolean hasJoinTable(FieldDoc fieldDoc) {
        return DocletUtils.hasAnnotation(fieldDoc, joinTableAnnotations);
    }

    public static boolean isOneToOneColumn(FieldDoc field) {
        return DocletUtils.hasAnnotation(field, oneToOneAnnotations);
    }

    public static boolean isManyToManyColumn(FieldDoc field) {
        return DocletUtils.hasAnnotation(field, manyToManyAnnotations);
    }

    public static boolean isRelationColumn(FieldDoc field) {
        return DocletUtils.hasAnnotation(field, relationAnnotations);
    }

    public static String getTableName(ClassDoc klass) {
        String tableName = getAnnotationNameValue(klass, tableAnnotations);

        if (StringUtils.isEmpty(tableName)) {
            if (klass.superclass() != null && DocletUtils.hasAnnotation(klass.superclass(), jpaAnnotations)) {
                tableName = getTableName(klass.superclass());
            }

            if (StringUtils.isEmpty(tableName)) {
                tableName = klass.name();
            }
        }

        return tableName;
    }

    public static String getShortTableName(ClassDoc klass) {
        String shortTableName = null;
        if (DocletUtils.hasAnnotation(klass, tableAnnotation)) {
            String shortName = getParameterStringValue("shortName", DocletUtils.findAnnotation(klass, tableAnnotation).elementValues());
            if (StringUtils.isNotEmpty(shortName)) {
                return shortName;
            }
        }
        for (FieldDoc field : klass.fields(true)) {
            if ("TABLE_NAME_SHORT".equals(field.name())) {
                return field.constantValue().toString();
            }
        }
        return shortTableName;
    }

    public static Map<String, String> getAttributeOverrideColumns(FieldDoc field, Class<?>... annotations) {
        Map<String, String> columnNameMap = new HashMap<String, String>();

        AnnotationDesc attOverride = DocletUtils.findAnnotation(field, AttributeOverride.class);

        if (attOverride != null) {
            columnNameMap.put(getNameAttribute(attOverride), getColumnNameValue(attOverride));
        } else if ((attOverride = DocletUtils.findAnnotation(field, AttributeOverrides.class)) != null) {
            for (AnnotationDesc annotationDesc : getSubAnnotations(attOverride)) {
                if (annotationDesc.annotationType().qualifiedTypeName().equals(AttributeOverride.class.getName())) {
                    columnNameMap.put(getNameAttribute(annotationDesc), getColumnNameValue(annotationDesc));
                }
            }
        }

        return columnNameMap;
    }

    private static List<AnnotationDesc> getSubAnnotations(AnnotationDesc attributeOverrides) {
        List<AnnotationDesc> att = new ArrayList<AnnotationDesc>();

        for (AnnotationDesc.ElementValuePair elementValuePair : attributeOverrides.elementValues()) {
            if (!(elementValuePair.value().value() instanceof AnnotationValue[])) {
                continue;
            }
            for (AnnotationValue attributeValue : ((AnnotationValue[]) elementValuePair.value().value())) {
                if (attributeValue.value() instanceof AnnotationDesc) {
                    att.add((AnnotationDesc) attributeValue.value());
                }
            }
        }

        return att;
    }

    private static FieldDoc getMappedBy(FieldDoc field) {
        String mappedFieldName = getParameterStringValue("mappedBy", DocletUtils.findAnnotation(field, relationAnnotations)
                .elementValues());

        for (FieldDoc fieldDoc : getAnnotationTargetEntity(field, oneToManyAnnotaions).fields(false)) {
            if (fieldDoc.name().equals(mappedFieldName)) {
                return fieldDoc;
            }
        }

        return null;
    }

    public static String getFullFieldNameFromAnnotation(ProgramElementDoc field,
                                                        String tableName,
                                                        boolean isJoinColumn,
                                                        Class<?>... annotations) {

        String columnName = getAnnotationNameValue(field, annotations);

        if (StringUtils.isEmpty(columnName) && isJoinColumn) {
            LOG.warn("No name specified for @JoinColumn " + tableName + "." + field.name().toLowerCase());
        }

        if (StringUtils.isEmpty(columnName)) {
            columnName = field.name().toLowerCase();
        }

        return columnName;
    }

    public static String getParameterStringValue (String name, AnnotationDesc.ElementValuePair[] values) {
        Object obj = getParameterValue(name, values);
        return obj != null ? obj.toString() : null;
    }

    public static Object getParameterValue (String name, AnnotationDesc.ElementValuePair[] values) {
        for (AnnotationDesc.ElementValuePair value : values) {
            if (name.equals(value.element().name())) {
                return value.value().value();
            }
        }

        return null;
    }

    public static String getFieldNameFromAnnotation(FieldDoc field, Class<?>... annotations) {
        String columnName = getAnnotationNameValue(field, annotations);

        if (StringUtils.isEmpty(columnName)) {
            columnName = field.name().toLowerCase();
        }

        return columnName;
    }

    private static String getAnnotationNameValue(ProgramElementDoc field, Class<?>[] annotations) {
        AnnotationDesc annotationDesc = DocletUtils.findAnnotation(field, annotations);
        if (annotationDesc != null) {
            return getNameAttribute(annotationDesc);
        }
        return null;
    }

    private static String getNameAttribute(AnnotationDesc annotationDesc) {
        return getParameterStringValue("name", annotationDesc.elementValues());
    }

    private static String getInheritanceStrategyValue(ProgramElementDoc field) {
        AnnotationDesc annotationDesc = DocletUtils.findAnnotation(field, inheritanceAnnotation);
        if (annotationDesc != null) {
            return getParameterStringValue("strategy", annotationDesc.elementValues());
        }
        return null;
    }

    private static ClassDoc getAnnotationTargetEntity(FieldDoc field, Class<?>[] annotations) {
        AnnotationDesc annotationDesc = DocletUtils.findAnnotation(field, annotations);
        Type res = null;
        if (annotationDesc != null) {
            res = (Type) getParameterValue("targetEntity", annotationDesc.elementValues());
        }
        if (res == null) {
            try {
                res = field.type().asParameterizedType().typeArguments()[0];
            } catch (Exception e) {
                throw new IllegalStateException("cannot determine target entity, provide targetEntity attribute within relation annotation on field " + field.containingClass().name() + "." + field.name(), e);
            }
        }
        return res.asClassDoc();
    }

    private static String getAnnotationColumnNameValue(ProgramElementDoc field, Class<?>[] annotations) {
        AnnotationDesc annotationDesc = DocletUtils.findAnnotation(field, annotations);
        if (annotationDesc != null) {
            return getColumnNameValue(annotationDesc);
        }
        return null;
    }

    private static String getColumnNameValue(AnnotationDesc annotationDesc) {
        AnnotationDesc column = (AnnotationDesc) getParameterValue("column", annotationDesc.elementValues());

        return column != null ? getNameAttribute(column) : null;
    }

    public static void setOnlyProcessEntityClasses(Set<Class> entityClasses) {
        ENTITY_CLASS_NAMES_ONLY = new HashSet<String>();
        for (Class entityClass : entityClasses) {
            ENTITY_CLASS_NAMES_ONLY.add(entityClass.getName());

            Class superClass = entityClass;
            while ((superClass = superClass.getSuperclass()) != null) {
                ENTITY_CLASS_NAMES_ONLY.add(superClass.getName());
            }
        }
    }

    public static void setRenderedObjects(Set<String> objectNames) {
        RENDERED_OBJECTS = objectNames;
    }
}