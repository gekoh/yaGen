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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Georg Kohlweiss
 */
public class ReflectExecutor {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ReflectExecutor.class);

    public static List CONFIGURATION_INTERCEPTOR_INSTANCES = new ArrayList();

    public static final String PROFILE_CLASS_NAME = "com.github.gekoh.yagen.ddl.DDLGenerator$Profile";
    public static final String PATCH_HIBERNATE_CLASS_NAME = "com.github.gekoh.yagen.hibernate.PatchHibernateMappingClasses";
    public static final String DDLENHANCER_CLASS_NAME = "com.github.gekoh.yagen.hibernate.DDLEnhancer";
    public static final String CREATEDDL_CLASS_NAME = "com.github.gekoh.yagen.ddl.CreateDDL";
    public static final String DDLGEN_CLASS_NAME = "com.github.gekoh.yagen.ddl.DDLGenerator";
    public static final String YAGENINIT_CLASS_NAME = "com.github.gekoh.yagen.hibernate.YagenInit";
    public static final String SESSIONFACTORY_CLASS_NAME = "org.hibernate.internal.SessionFactoryImpl";

    static final Initializer<Constructor> i_createDdl = getConstructor(CREATEDDL_CLASS_NAME);

    static final Initializer<Class> c_enhancer      = getClass(DDLENHANCER_CLASS_NAME);
    static final Initializer<Constructor> i_profile = getConstructor(PROFILE_CLASS_NAME);

    static final Initializer<Method> m_registerMetadata = getMethod(PROFILE_CLASS_NAME + ".registerMetadata");
    static final Initializer<Method> m_setNamingStrategy   = getMethod(PROFILE_CLASS_NAME + ".setNamingStrategy");
    static final Initializer<Method> m_clone               = getMethod(PROFILE_CLASS_NAME + ".clone");
    static final Initializer<Method> m_getHeaderStatements = getMethod(PROFILE_CLASS_NAME + ".getHeaderStatements");
    static final Initializer<Method> m_getFooterStatements = getMethod(PROFILE_CLASS_NAME + ".getFooterStatements");

    static final Initializer<Method> m_getDDLEnhancer  = getMethod(DDLENHANCER_CLASS_NAME + ".getDDLEnhancer");
    static final Initializer<Method> m_initDDLEnhancer = getMethod(DDLENHANCER_CLASS_NAME + ".initDDLEnhancer");

    static final Initializer<Method> m_patchCollectionsAlwaysLazy = getMethod(PATCH_HIBERNATE_CLASS_NAME + ".patchCollectionsAlwaysLazy");
    static final Initializer<Method> m_patchIgnoreVersion   = getMethod(PATCH_HIBERNATE_CLASS_NAME + ".patchIgnoreVersion");

    static final Initializer<Method> m_updateCreateTable    = getMethod(CREATEDDL_CLASS_NAME + ".updateCreateTable");
    static final Initializer<Method> m_updateDropTable      = getMethod(CREATEDDL_CLASS_NAME + ".updateDropTable");
    static final Initializer<Method> m_updateCreateConstraint = getMethod(CREATEDDL_CLASS_NAME + ".updateCreateConstraint");
    static final Initializer<Method> m_updateCreateIndex    = getMethod(CREATEDDL_CLASS_NAME + ".updateCreateIndex");
    static final Initializer<Method> m_updateCreateSequence = getMethod(CREATEDDL_CLASS_NAME + ".updateCreateSequence");
    static final Initializer<Method> m_getProfile           = getMethod(CREATEDDL_CLASS_NAME + ".getProfile");

    public static final Initializer<Method> m_getConfigurationValues = getMethod(DDLGEN_CLASS_NAME + ".getConfigurationValues");
    public static final Initializer<Method> m_createProfile = getMethod(DDLGEN_CLASS_NAME + ".createProfile");
    public static final Initializer<Method> m_createProfileFromMetadata = getMethod(DDLGEN_CLASS_NAME + ".createProfileFromMetadata");

    static final Initializer<Method> m_newProfileIfNull     = getMethod(YAGENINIT_CLASS_NAME + ".newProfileIfNull");

    static final Initializer<Method> m_getMetadata          = getMethod(SESSIONFACTORY_CLASS_NAME + ".getMetadata");

    private static Initializer<Method> getMethod(final String fqMethodName) {
        final String className = fqMethodName.substring(0, fqMethodName.lastIndexOf("."));
        final String methodName = fqMethodName.substring(className.length() + 1);

        return new Initializer<Method>() {
            @Override
            public Method init() {
                try {
                    Method[] methods = ReflectExecutor.getClass(className).get().getDeclaredMethods();
                    for (Method method : methods) {
                        if (methodName.equals(method.getName())) {
                            return method;
                        }
                    }
                } catch (Exception e) {
                    LOG.error("cannot find method " + fqMethodName, e);
                }
                return null;
            }
        };
    }

    private static Initializer<Class> getClass(final String className) {
        return new Initializer<Class>() {
            @Override
            public Class init() {
                try {
                    return Class.forName(className);
                } catch (ClassNotFoundException e) {
                    LOG.error("cannot find class " + className, e);
                }
                return null;
            }
        };
    }

    private static Initializer<Constructor> getConstructor(final String className) {
        return new Initializer<Constructor>() {
            @Override
            public Constructor init() {
                try {
                    return ReflectExecutor.getClass(className).get().getConstructor();
                } catch (Exception e) {
                    return ReflectExecutor.getClass(className).get().getConstructors()[0];
                }
            }
        };
    }

    public static void newProfileIfNull() {
        try {
            m_newProfileIfNull.get().invoke(null);
        } catch (Exception e) {
            LOG.error("error calling newProfileIfNull", e);
        }
    }

    public abstract static class Initializer<T> {

        T object;

        public abstract T init();
        public T get() {
            if (object == null) {
                object = init();
            }
            return object;
        }

    }
}