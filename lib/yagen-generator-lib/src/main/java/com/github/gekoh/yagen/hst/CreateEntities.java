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
package com.github.gekoh.yagen.hst;

import com.github.gekoh.yagen.api.Constants;
import com.github.gekoh.yagen.api.DefaultNamingStrategy;
import com.github.gekoh.yagen.api.NamingStrategy;
import com.github.gekoh.yagen.api.TemporalEntity;
import com.github.gekoh.yagen.ddl.CreateDDL;
import com.github.gekoh.yagen.ddl.DDLGenerator;
import com.github.gekoh.yagen.util.FieldInfo;
import com.github.gekoh.yagen.util.MappingUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

import javax.persistence.Column;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.OneToMany;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Georg Kohlweiss
 */
public class CreateEntities {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CreateEntities.class);

    public static final String HISTORY_ENTITY_SUFFIX = "Hst";

    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    public static void main (String[] args) {
        if (args == null || args.length<1) {
            LOG.error("parameters: <java-src-output-dir> <base-classes-package-name> <[persistence-unit-name]|[persistence-xml-list]> <orm2.0-file-out-path> [<orm1.0-file-out-path>] [override-DateTimeType.class.name] [customFile-BaseEntity.java.vm] [customFile-HstTemplate.java.vm]");
            return;
        }
        Collection<Class> entityClasses;
        String filesOrPuName = args[2];
        
        if (filesOrPuName.contains(";") || new File(filesOrPuName).exists() || CreateEntities.class.getClassLoader().getResource(filesOrPuName) != null) {
            entityClasses = extractFromPersistenceXml(filesOrPuName.split(";[\\s]*"));
        }
        else {
            LOG.info("{} seems not to be a file since it is not found in classpath, trying as persistence unit name", filesOrPuName);
            entityClasses = scanEntityClasses(filesOrPuName);
        }
        CreateEntities createEntities = new CreateEntities(new File(args[0]), entityClasses);

        if (args.length > 5) {
            createEntities.additionalProperties.put("dateTimeType", StringUtils.isNotEmpty(args[5]) ? args[5] : "");
        }
        Reader customBaseTemplate = null;
        if (args.length > 6 && StringUtils.isNotEmpty(args[6])) {
            try {
                customBaseTemplate = new FileReader(args[6]);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return;
            }
        }
        if (args.length > 7 && StringUtils.isNotEmpty(args[7])) {
            try {
                createEntities.template = readContents(new FileInputStream(args[7]));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return;
            }
        }

        createEntities.writeBaseClasses(args[1], customBaseTemplate);

        createEntities.processBaseEntityClasses(args[1]);

        File orm20OutFile = new File(args[3]);
        createEntities.writeOrmFile(orm20OutFile, args[1], "2.0");

        if (args.length > 4 && StringUtils.isNotEmpty(args[4])) {
            File orm10OutFile = new File(args[4]);
            createEntities.writeOrmFile(orm10OutFile, args[1], "1.0");
        }
    }

    private File outputDirectory;

    private String template = readClasspathResource("HstTemplate.java.vm");

    private Collection<Class> entityClasses;
    private List<String> createdMappedSuperClasses = new ArrayList<String>();
    private List<String> createdEntityClasses = new ArrayList<String>();

    private NamingStrategy namingStrategy = new DefaultNamingStrategy();

    public CreateEntities(File outputDirectory, Collection<Class> entityClasses) {
        this.outputDirectory = outputDirectory;
        this.entityClasses = entityClasses;
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw new IllegalArgumentException("cannot create directory '" + outputDirectory.getAbsolutePath() + "'");
        }
        Velocity.init();
    }

    public void processBaseEntityClasses (String baseClassPackageName) {
        Map<Class, List<AccessibleObject>> inverseFKs = getInverseFKs (entityClasses);

        for (Class baseEntity : entityClasses) {
            Class tableEntity = getTableEntityClass(baseEntity);
            if (baseEntity.isAnnotationPresent(MappedSuperclass.class) ||
                    (tableEntity != null && tableEntity.isAnnotationPresent(TemporalEntity.class))) {

                String className = createHistoryEntity(baseClassPackageName, baseEntity, new StringReader(template), inverseFKs.get(baseEntity));

                if (baseEntity.isAnnotationPresent(MappedSuperclass.class)) {
                    createdMappedSuperClasses.add(className);
                }
                else {
                    createdEntityClasses.add(className);
                }
            }

            for (AccessibleObject accessibleObject : getFieldsAndMethods(baseEntity)) {
                if (accessibleObject.isAnnotationPresent(TemporalEntity.class) && accessibleObject.isAnnotationPresent(JoinTable.class)) {
                    String className = createHistoryEntity(baseClassPackageName, accessibleObject, new StringReader(template));
                    createdEntityClasses.add(className);
                }
            }
        }
    }

    private Class getTableEntityClass(Class baseClass) {
        while (baseClass != null && !baseClass.isAnnotationPresent(Table.class)) {
            baseClass = baseClass.getSuperclass();
        }
        return baseClass;
    }

    private Map<Class, List<AccessibleObject>> getInverseFKs(Collection<Class> baseEntities) {
        Map<Class, List<AccessibleObject>> inverseFKs = new HashMap<Class, List<AccessibleObject>>();

        for (Class baseEntity : baseEntities) {
            Set<AccessibleObject> fieldOrMethods = getFieldsAndMethods(baseEntity);

            for (AccessibleObject fieldOrMethod : fieldOrMethods) {
                if (fieldOrMethod.isAnnotationPresent(OneToMany.class)) {
                    OneToMany o2m = fieldOrMethod.getAnnotation(OneToMany.class);
                    Class<?> mappedClass = MappingUtils.determineTargetEntity(fieldOrMethod, o2m.targetEntity());

                    if (fieldOrMethod.isAnnotationPresent(JoinColumn.class) &&
                            !hasColumnDeclared(mappedClass, fieldOrMethod.getAnnotation(JoinColumn.class).name())) {
                        List<AccessibleObject> fks = inverseFKs.get(mappedClass);

                        if (fks == null) {
                            inverseFKs.put(mappedClass, fks = new ArrayList<AccessibleObject>());
                        }

                        fks.add(fieldOrMethod);
                    }
                }
            }
        }

        return inverseFKs;
    }

    private Set<AccessibleObject> getFieldsAndMethods(Class clazz) {
        Set<AccessibleObject> fieldOrMethods = new HashSet<AccessibleObject>(Arrays.asList(clazz.getDeclaredFields()));
        fieldOrMethods.addAll(Arrays.asList(clazz.getDeclaredMethods()));

        return fieldOrMethods;
    }

    private boolean hasColumnDeclared(Class clazz, String columnName) {
        for (AccessibleObject fieldOrMethod : getFieldsAndMethods(clazz)) {
            if (fieldOrMethod.isAnnotationPresent(Column.class) &&
                    fieldOrMethod.getAnnotation(Column.class).name().compareToIgnoreCase(columnName) == 0) {
                return true;
            }
            if (fieldOrMethod.isAnnotationPresent(JoinColumn.class) &&
                    fieldOrMethod.getAnnotation(JoinColumn.class).name().compareToIgnoreCase(columnName) == 0) {
                return true;
            }
        }
        return false;
    }

    private String createHistoryEntity(String baseClassPackageName,
                                       AccessibleObject fieldOrMethod,
                                       Reader template) {
        TemporalEntity temporalEntity = fieldOrMethod.getAnnotation(TemporalEntity.class);
        JoinTable joinTable = fieldOrMethod.getAnnotation(JoinTable.class);
        Class targetEntity = null;
        Class declaringClass;
        String packageName;
        String hstEntityClassSimpleName = FieldInfo.toCamelCase(joinTable.name()) + HISTORY_ENTITY_SUFFIX;
        hstEntityClassSimpleName = hstEntityClassSimpleName.substring(0, 1).toUpperCase() + hstEntityClassSimpleName.substring(1);

        if (fieldOrMethod.isAnnotationPresent(ManyToMany.class)) {
            targetEntity = MappingUtils.determineTargetEntity(fieldOrMethod, fieldOrMethod.getAnnotation(ManyToMany.class).targetEntity());
        }
        else if (fieldOrMethod.isAnnotationPresent(ManyToOne.class)) {
            targetEntity = MappingUtils.determineTargetEntity(fieldOrMethod, fieldOrMethod.getAnnotation(ManyToOne.class).targetEntity());
        }
        else {
            throw new UnsupportedOperationException("when generating history entity for relation on " + fieldOrMethod);
        }

        if (fieldOrMethod instanceof Field) {
            declaringClass = ((Field) fieldOrMethod).getDeclaringClass();
        }
        else {
            declaringClass = ((Method) fieldOrMethod).getDeclaringClass();
        }
        packageName = declaringClass.getPackage().getName();

        List<FieldInfo> fieldInfos = new ArrayList<FieldInfo>();

//      add join columns to both sides of the relation (assumes we have only one each)
        fieldInfos.add(FieldInfo.getIdFieldInfo(declaringClass, getFieldNameFromReferencingClassName(declaringClass.getSimpleName()), joinTable.joinColumns()[0].name()));
        fieldInfos.add(FieldInfo.getIdFieldInfo(targetEntity, getFieldNameFromReferencingClassName(targetEntity.getSimpleName()), joinTable.inverseJoinColumns()[0].name()));

        return createHistoryEntity(
                baseClassPackageName,
                packageName,
                hstEntityClassSimpleName,
                temporalEntity.historyTableName(),
                null,
                null,
                template,
                fieldInfos);
    }

    private String getFieldNameFromReferencingClassName(String classSimpleName) {
        return classSimpleName.substring(0, 1).toLowerCase() + classSimpleName.substring(1);
    }

    private String createHistoryEntity (String baseClassPackageName,
                                        Class baseEntity,
                                        Reader template,
                                        List<AccessibleObject> inverseFKs) {
        List<FieldInfo> fields = new ArrayList<FieldInfo>(FieldInfo.convertFields(baseEntity));
        if (inverseFKs != null) {
            fields.addAll(FieldInfo.convertInverseFKs(inverseFKs));
        }

        TemporalEntity temporalEntity = (TemporalEntity) baseEntity.getAnnotation(TemporalEntity.class);

        return createHistoryEntity(
                baseClassPackageName,
                baseEntity.getPackage().getName(),
                baseEntity.getSimpleName() + HISTORY_ENTITY_SUFFIX,
                temporalEntity != null ? temporalEntity.historyTableName() : null,
                temporalEntity != null ? namingStrategy.classToTableShortName(baseEntity.getName()) : null,
                baseEntity,
                template,
                fields);
    }

    private String createHistoryEntity (String baseClassPackageName,
                                        String hstEntityClassPackageName,
                                        String hstEntityClassSimpleName,
                                        String historyTableName,
                                        String historyTableShortName,
                                        Class baseEntity,
                                        Reader template,
                                        List<FieldInfo> fields) {
        String hstEntityClassName = hstEntityClassPackageName + "." + hstEntityClassSimpleName;
        Class baseEntitySuperClass = baseEntity != null && !baseEntity.getSuperclass().equals(Object.class) ? baseEntity.getSuperclass() : null;
        String classAnnotations = "";

        VelocityContext context = new VelocityContext();
        context.put("baseClassPackageName", baseClassPackageName);
        context.put("entityClassPackageName", hstEntityClassPackageName);
        context.put("entityClassSimpleName", hstEntityClassSimpleName);

        if (historyTableName != null) {
            context.put("tableName", historyTableName);
            if (historyTableShortName != null) {
                context.put("tableShortName", CreateDDL.getHistTableShortNameFromLiveTableShortName(historyTableShortName));
                classAnnotations = String.format("@com.github.gekoh.yagen.api.Table(shortName=%s.%s)\n", hstEntityClassSimpleName, CreateDDL.STATIC_FIELD_TABLE_NAME_SHORT);
            }
        }

        context.put("fieldInfoList", fields);

        if (baseEntity != null && baseEntity.isAnnotationPresent(MappedSuperclass.class)) {
            context.put("classAnnotation", classAnnotations + "@javax.persistence.MappedSuperclass");
        }
        else if (baseEntity != null && baseEntity.isAnnotationPresent(Entity.class)) {
            String value = classAnnotations + "@javax.persistence.Entity";
            String entityName = ((Entity) baseEntity.getAnnotation(Entity.class)).name();
            if (StringUtils.isNotEmpty(entityName)) {
                value += "(name = \"" + entityName + HISTORY_ENTITY_SUFFIX + "\")";
            }
            context.put("classAnnotation", value);
        }

//        Inheritance used, baseEntity is superclass
        if (baseEntity != null && baseEntity.isAnnotationPresent(Inheritance.class)) {
            DiscriminatorColumn discriminatorColumn = (DiscriminatorColumn) baseEntity.getAnnotation(DiscriminatorColumn.class);
            String classAnnotation = context.get("classAnnotation") + "\n" +
                    "@javax.persistence.Inheritance(strategy=javax.persistence.InheritanceType." + ((Inheritance) baseEntity.getAnnotation(Inheritance.class)).strategy().name() + ")";
            if (discriminatorColumn != null) {
                classAnnotation += "\n@javax.persistence.DiscriminatorColumn(name=\"" + discriminatorColumn.name() + "\", length=" + discriminatorColumn.length() + ")";
            }
            context.put("classAnnotation", classAnnotation);
        }
//        Inheritance used, baseEntity is subclass
        else if (baseEntity != null && baseEntity.isAnnotationPresent(DiscriminatorValue.class)) {
            context.put("classAnnotation", context.get("classAnnotation") + "\n" +
                    "@javax.persistence.DiscriminatorValue(\""+((DiscriminatorValue) baseEntity.getAnnotation(DiscriminatorValue.class)).value()+"\")");
        }

        if (baseEntitySuperClass != null) {
            context.put("entitySuperClassName", baseEntitySuperClass.getName() + "Hst");
        }
        else if (baseEntity != null) {
            String name = null;
            int length = -1;
            PrimaryKeyJoinColumn pkJC = (PrimaryKeyJoinColumn) baseEntity.getAnnotation(PrimaryKeyJoinColumn.class);
            if (pkJC != null) {
                name = pkJC.name();
                length = Constants.UUID_LEN;
            }
            else {
                Column column = FieldInfo.getIdColumn(baseEntity);
                AccessibleObject idFieldOrMethod = FieldInfo.getIdFieldOrMethod(baseEntity);
                if (idFieldOrMethod != null) {
                    name = MappingUtils.deriveColumnName(column, idFieldOrMethod);
                    length = column != null ? column.length() : 256;
                }
            }
            if (name != null) {
                context.put("baseEntityUuidColumnName", name.toLowerCase());
                context.put("baseEntityUuidColumnLength", length);
            }
        }

        evaluate2JavaFile(hstEntityClassName, template, context);

        return hstEntityClassName;
    }

    private void writeBaseClasses(String baseClassPackageName, Reader customBaseTemplate) {
        VelocityContext context = new VelocityContext(additionalProperties);
        context.put("baseClassPackageName", baseClassPackageName);

        evaluate2JavaFile(baseClassPackageName+".BaseEntity",
                customBaseTemplate == null ? new StringReader(readClasspathResource("BaseEntity.java.vm")) : customBaseTemplate,
                context);
    }

    private void evaluate2JavaFile(String entityClassName, Reader template, VelocityContext context) {
        try {
            File outFile = new File(outputDirectory, entityClassName.replace('.', File.separatorChar) + ".java");
            if (!outFile.getParentFile().exists() && !outFile.getParentFile().mkdirs()) {
                throw new IllegalArgumentException("error creating output directory for generated sources");
            }
            FileWriter wr = new FileWriter(outFile);
            Velocity.evaluate(context, wr, CreateEntities.class.getSimpleName()+"#createHistoryEntity", template);
            wr.close();
        } catch (IOException e) {
            throw new IllegalStateException("cannot create output file", e);
        }
    }


    public void writeOrmFile (File ormOutFile, String baseClassPackageName, String ormVersion) {
        if (!ormOutFile.getParentFile().exists() && !ormOutFile.getParentFile().mkdirs()) {
            throw new IllegalArgumentException(
                    "Could not create the output directory for the ORM file '" + ormOutFile.getAbsolutePath() + "'.");
        }

        VelocityContext context = new VelocityContext();
        context.put("baseClassPackageName", baseClassPackageName);
        context.put("mappedSuperClassNames", createdMappedSuperClasses);
        context.put("entityClassNames", createdEntityClasses);
        context.put("ormVersion", ormVersion);

        try {
            FileWriter wr = new FileWriter(ormOutFile);
            Velocity.evaluate(context, wr,
                    CreateEntities.class + "#writeOrmFile", readClasspathResource("entities.orm.xml.vm"));
            wr.close();
        } catch (IOException e) {
            LOG.error("error writing to orm file", e);
        }
    }

    /**
     * <p>Extracts all entity classes in the given set of persistence.xml files an returns a collection thereof.</p>
     * @param persistenceUnitName persistence unit managing source entity classes
     * @return extracted entity classes
     */
    private static Collection<Class> scanEntityClasses(String persistenceUnitName) {

        return new DDLGenerator.SchemaExportHelper(persistenceUnitName).getEntityAndMappedSuperClasses();
    }

    /**
     * <p>Extracts all entity classes in the given set of persistence.xml files an returns a collection thereof.</p>
     * @param persistenceXmlFiles set of persistence.xml files that you want to scan
     * @return extracted entity classes
     */
    private static Collection<Class> extractFromPersistenceXml(String... persistenceXmlFiles) {
        List<Class> entityClasses = new ArrayList<>();
        for (String file : persistenceXmlFiles) {
            addPersistenceFile(entityClasses, getPersistenceDocument(file));
        }
        return entityClasses;
    }

    private static void addPersistenceFile (Collection<Class> entityClasses, Document persistenceXml) {
        if (persistenceXml==null) {
            return;
        }
        try {
            Element pu = persistenceXml.getRootElement().element("persistence-unit");
            if(pu != null) {
                for (Object classNode: pu.elements("class")) {
                    entityClasses.add(Class.forName(((Node) classNode).getText()));
                }
                for (Object fileNode: pu.elements("mapping-file")) {
                    addPersistenceFile(entityClasses, getPersistenceDocument(((Node) fileNode).getText()));
                }
            } else {
                for(Object entityNode : persistenceXml.getRootElement().elements("mapped-superclass")) {
                    entityClasses.add(Class.forName((String) ((Element)entityNode).attribute("class").getData()));
                }
                for(Object entityNode : persistenceXml.getRootElement().elements("entity")) {
                    entityClasses.add(Class.forName((String) ((Element)entityNode).attribute("class").getData()));
                }
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static Document getPersistenceDocument (String persistenceXml) {
        if (StringUtils.isEmpty(persistenceXml)) {
            LOG.warn("empty persistence.xml or orm file specified");
            return null;
        }
        try {
            InputStream resource = CreateEntities.class.getResourceAsStream("/" + persistenceXml);
            if (resource == null) {
                resource = new FileInputStream(persistenceXml);
            }
            return new SAXReader().read(resource);
        } catch (Exception e) {
            throw new IllegalArgumentException("unable to find resource "+persistenceXml+" in classpath or filesystem", e);
        }
    }

    /**
     * <p>Reads an entire file from the classpath using UTF-8 encoding.</p>
     * @param filename the name of the file in the classpath
     * @return the contents of the file
     */
    private static String readClasspathResource(String filename) {
        InputStream is = CreateEntities.class.getResourceAsStream(filename);
        if (is == null) {
            throw new IllegalArgumentException(
                    "Could not find the given file '" + filename + "' in the classpath.");
        }
        
        return readContents(is);
    }

    private static String readContents(InputStream is) {
        StringWriter wr = new StringWriter();
        
        try {
            Reader rd = new InputStreamReader(is, "UTF-8");
            
            char[] buf = new char[1024];
            int read;
            while ((read = rd.read(buf)) > 0) {
                wr.write(buf, 0, read);
            }
            
        } catch (Exception e) {
            LOG.error("An error occurred while reading the template file.", e);
        }
        
        return wr.toString();
    }

}
