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

import com.github.gekoh.yagen.PatchTransformer;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;

/**
 * @author Georg Kohlweiss 
 */
public class PatchHibernateMappingClasses {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PatchHibernateMappingClasses.class);

    public static void applyPatch() throws Exception {
        if (!isAlreadyPatched()) {
            if (alreadyLoaded(PatchTransformer.PATCH_CLASS_LIST.get(0))) {
                throw new HibernateClassesAlreadyLoadedException();
            }
            ClassPool cp = ClassPool.getDefault();
            cp.insertClassPath(new ClassClassPath(PatchHibernateMappingClasses.class));

            for (String class2Patch : PatchTransformer.PATCH_CLASS_LIST) {
                CtClass ctClass = cp.getCtClass(class2Patch);
                PatchTransformer.patchClass(ctClass);
                LOG.info("patched class {}", ctClass.toClass().getName());
            }
        }
    }

    public static boolean alreadyLoaded(String className) throws Exception {
        java.lang.reflect.Method m = ClassLoader.class.getDeclaredMethod("findLoadedClass", new Class[]{String.class});
        m.setAccessible(true);
        ClassLoader cl = PatchGlue.class.getClassLoader();
        Object test1 = m.invoke(cl, className);
        return test1 != null;
    }

    public static boolean isAlreadyPatched() throws Exception {
        String dialectClassName = "org.hibernate.dialect.Dialect";
        if (alreadyLoaded(dialectClassName)) {
            Class ddlEnhClass = Class.forName("com.github.gekoh.yagen.hibernate.DDLEnhancer");
            for (Class interf : Class.forName(dialectClassName).getInterfaces()) {
                if (ddlEnhClass.equals(interf)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void patchCollectionsAlwaysLazy() throws Exception {
        ClassPool cp = ClassPool.getDefault();
        cp.insertClassPath(new ClassClassPath(PatchHibernateMappingClasses.class));

        {
            CtClass clazz = cp.get("org.hibernate.mapping.Collection");
            CtMethod method = clazz.getDeclaredMethod("setLazy");

            method.insertAfter(
                            "this.lazy = true;\n"
            );

            method = clazz.getDeclaredMethod("setFetchMode");

            method.insertAfter(
                            "this.fetchMode = org.hibernate.FetchMode.SELECT;\n"
            );

            method = clazz.getDeclaredMethod("setOrphanDelete");

            method.insertAfter(
                    "this.orphanDelete = false;\n"
            );

            LOG.info("patched class {}", clazz.toClass().toString());
        }

    }

    public static void patchIgnoreVersion() throws Exception {
        ClassPool cp = ClassPool.getDefault();
        cp.insertClassPath(new ClassClassPath(PatchHibernateMappingClasses.class));

        {
            CtClass clazz = cp.get("org.hibernate.event.def.DefaultMergeEventListener");
            CtMethod method = clazz.getDeclaredMethod("isVersionChanged");

            method.insertBefore("return false;");

            LOG.info("patched class {}", clazz.toClass().toString());
        }
    }

    public static void patchNumericBooleanTypeForOracle() throws Exception {
        ClassPool cp = ClassPool.getDefault();
        cp.insertClassPath(new ClassClassPath(PatchHibernateMappingClasses.class));

        {
            CtClass clazz = cp.get("org.hibernate.type.NumericBooleanType");
            CtConstructor constructor = clazz.getConstructors()[0];

            constructor.setBody("super(org.hibernate.type.descriptor.sql.BitTypeDescriptor.INSTANCE, org.hibernate.type.descriptor.java.BooleanTypeDescriptor.INSTANCE);");

            LOG.info("patched class {}", clazz.toClass().toString());
        }
    }
}
