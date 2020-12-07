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
package com.github.gekoh.yagen;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import javassist.Modifier;
import javassist.NotFoundException;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * @author Georg Kohlweiss
 */
public class PatchTransformer implements ClassFileTransformer {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PatchTransformer.class);

    public static final String YAGEN_INIT_MARKER_FIELD = "yagenInitDone";

    public static final List<String> PATCH_CLASS_LIST = Arrays.asList(
            "org.hibernate.internal.SessionFactoryImpl",
            "org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl",
            "org.hibernate.tool.schema.internal.StandardTableExporter",
            "org.hibernate.tool.schema.internal.StandardIndexExporter",
            "org.hibernate.tool.schema.internal.StandardUniqueKeyExporter",
            "org.hibernate.tool.schema.internal.StandardForeignKeyExporter",
            "org.hibernate.tool.schema.internal.StandardSequenceExporter",
            "org.hibernate.tool.schema.internal.SchemaCreatorImpl",
            "org.hibernate.dialect.Dialect",
            "org.hibernate.boot.model.source.internal.annotations.AnnotationMetadataSourceProcessorImpl"
    );

    private ClassPool classPool;

    public PatchTransformer() {
        classPool = new ClassPool();
        classPool.appendSystemPath();
        try {
            classPool.appendPathList(System.getProperty("java.class.path"));

            classPool.appendClassPath(new LoaderClassPath(ClassLoader.getSystemClassLoader()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (PATCH_CLASS_LIST.contains(className.replace("/", "."))) {
            logInfo("patching class: " + className);

            try {
                classPool.appendClassPath(new LoaderClassPath(loader));
                CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));

                if (patchClass(ctClass)) {
                    logInfo("... done");
                    return ctClass.toBytecode();
                }
                else {
                    logInfo("... no change (agent active?)");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return classfileBuffer;
    }

    public static boolean patchClass(CtClass clazz) throws Exception {

        try {
            if (clazz.getDeclaredField(YAGEN_INIT_MARKER_FIELD) != null) {
                return false;
            }
        } catch (NotFoundException ignore) {
            // exception means this class has not yet been initialized by yagen
        }

        clazz.addField(new CtField(clazz.getClassPool().get("boolean"), YAGEN_INIT_MARKER_FIELD, clazz));

        String className = clazz.getName().replace("/", ".");
        if("org.hibernate.internal.SessionFactoryImpl".equals(className)) {
            patchSessionFactory(clazz);
            return true;
        }
        if("org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl".equals(className)) {
            patchEmf(clazz);
            return true;
        }
        if("org.hibernate.tool.schema.internal.StandardTableExporter".equals(className)) {
            patchExporterClass(clazz, "afterTableSqlString");
            return true;
        }
        if("org.hibernate.tool.schema.internal.StandardIndexExporter".equals(className)) {
            patchExporterClass(clazz, "afterIndexSqlString");
            return true;
        }
        if("org.hibernate.tool.schema.internal.StandardUniqueKeyExporter".equals(className) || "org.hibernate.tool.schema.internal.StandardForeignKeyExporter".equals(className)) {
            patchExporterClass(clazz, "afterConstraintSqlString");
            return true;
        }
        if("org.hibernate.tool.schema.internal.StandardSequenceExporter".equals(className)) {
            patchExporterClass(clazz, "afterSequenceSqlString");
            return true;
        }
        if("org.hibernate.tool.schema.internal.SchemaCreatorImpl".equals(className)) {
            patchSchemaCreatorImpl(clazz);
            return true;
        }
        if("org.hibernate.dialect.Dialect".equals(className)) {
            patchDialect(clazz);
            return true;
        }
        if("org.hibernate.boot.model.source.internal.annotations.AnnotationMetadataSourceProcessorImpl".equals(className)) {
            clazz.getDeclaredMethod("processEntityHierarchies").insertBefore("com.github.gekoh.yagen.hibernate.PatchGlue.sortEntityClasses(this.xClasses);");
            return true;
        }
        return false;
    }

    private static void patchSessionFactory(CtClass clazz) throws NotFoundException, CannotCompileException {

        CtClass metadataClass = clazz.getClassPool().get("org.hibernate.boot.spi.MetadataImplementor");
        clazz.addField(new CtField(metadataClass, "metadata", clazz));

        clazz.getConstructors()[0].insertBefore("this.metadata = $1;");

        CtMethod getMetadata = new CtMethod(metadataClass, "getMetadata", null, clazz);
        getMetadata.setBody("return this.metadata;");
        clazz.addMethod(getMetadata);
    }

    private static void patchEmf(CtClass clazz) throws CannotCompileException, NotFoundException {

        String initDialectSrc = "com.github.gekoh.yagen.hibernate.PatchGlue.initDialect($_);\n" +
                "return $_;";

        clazz.getDeclaredMethod("metadata").insertAfter(initDialectSrc);
    }

    private static void patchExporterClass(CtClass clazz, String patchGlueMethodName) throws NotFoundException, CannotCompileException {
        CtMethod method = clazz.getDeclaredMethod("getSqlCreateStrings");

        method.insertAfter(
                "$_ = com.github.gekoh.yagen.hibernate.PatchGlue."+patchGlueMethodName+"(true, $1, $2, $_);"
        );

        method = clazz.getDeclaredMethod("getSqlDropStrings");

        method.insertAfter(
                "$_ = com.github.gekoh.yagen.hibernate.PatchGlue."+patchGlueMethodName+"(false, $1, $2, $_);"
        );
    }

    private static void patchSchemaCreatorImpl(CtClass clazz) throws CannotCompileException, NotFoundException {
        ClassPool cp = clazz.getClassPool();

        CtMethod method = clazz.getDeclaredMethod("applySqlStrings");
        method.setName("applySqlStringsApi");
        method.setModifiers(Modifier.setPublic(method.getModifiers()));

        CtMethod newMethod = CtMethod.make(
                "private static void applySqlStrings(java.lang.String[] sqlStrings,\n" +
                        "\t\t\torg.hibernate.engine.jdbc.internal.Formatter formatter,\n" +
                        "\t\t\torg.hibernate.tool.schema.spi.ExecutionOptions options,\n" +
                        "\t\t\torg.hibernate.tool.schema.internal.exec.GenerationTarget[] targets) {\n" +
                        "    com.github.gekoh.yagen.hibernate.PatchGlue.schemaExportPerform (sqlStrings, formatter, options, targets);\n" +
                        "}"
                ,
                clazz
        );
        clazz.addMethod(newMethod);

        method = clazz.getDeclaredMethod("performCreation");
        method.insertBefore("com.github.gekoh.yagen.hibernate.PatchGlue.addHeader($2, $3, $5);");
        method.insertAfter("com.github.gekoh.yagen.hibernate.PatchGlue.addFooter($2, $3, $5);");
    }

    private static void patchDialect(CtClass clazz) throws CannotCompileException, NotFoundException {
        clazz.addField(CtField.make("private Object ddlEnhancer;", clazz));
        clazz.addField(CtField.make("private Object metadata;", clazz));

        clazz.addMethod(CtMethod.make(
                "public void initDDLEnhancer(Object profile, Object metadata) {\n" +
                        "        this.metadata = metadata;\n" +
                        "        ddlEnhancer = com.github.gekoh.yagen.hibernate.PatchGlue.newDDLEnhancer(profile, metadata);\n" +
                        "    }",
                clazz
        ));

        clazz.addMethod(CtMethod.make(
                "public Object getDDLEnhancer() {\n" +
                        "        return ddlEnhancer;\n" +
                        "    }",
                clazz
        ));

        clazz.addMethod(CtMethod.make(
                "public Object getMetadata() {\n" +
                        "        return metadata;\n" +
                        "    }",
                clazz
        ));

        clazz.addInterface(clazz.getClassPool().get("com.github.gekoh.yagen.hibernate.DDLEnhancer"));
    }

    private static void logInfo(String msg) {
        System.out.println(new Date().toString() + " " + PatchTransformer.class.getName() + " " + msg);
    }
}