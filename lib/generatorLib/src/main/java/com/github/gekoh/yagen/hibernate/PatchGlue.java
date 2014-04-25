package com.github.gekoh.yagen.hibernate;

import com.github.gekoh.yagen.api.DefaultNamingStrategy;
import com.github.gekoh.yagen.ddl.CreateDDL;
import com.github.gekoh.yagen.ddl.DDLGenerator;
import org.hibernate.dialect.Dialect;
import org.hibernate.ejb.Ejb3Configuration;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Constraint;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;
import org.hibernate.type.Type;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author Georg Kohlweiss 
 */
public class PatchGlue {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PatchGlue.class);

    private static Map<String, DDLGenerator.Profile> PROFILES = new HashMap<String, DDLGenerator.Profile>();

    private static final List<String> ADDITIONAL_DDL_REGISTRY = Arrays.asList(
            "/com/github/gekohl/yagen/ddl/rightApplication.ddl.sql",
            "/com/github/gekohl/yagen/ddl/additionalObjects.ddl.sql",
            "/com/github/gekohl/yagen/ddl/hstIndexes.ddl.sql",
            "/com/github/gekohl/yagen/ddl/timelineViews.ddl.sql",
            "/ddl/rom-additionalObjects.ddl.sql",
            "/ddl/flipflop-views.ddl.sql",
            "/ddl/rim-additionalObjects.ddl",
            "/ddl/rim-additionalProd.sql",
            "/ddl/rct-views.ddl.sql"
    );

    public static final DDLGenerator.Profile INIT_DATA = new DDLGenerator.Profile("init_data");
    static {
        DDLGenerator.Profile profile = INIT_DATA;
        PROFILES.put(profile.getName(), profile);

        profile.addHeaderDdl(new DDLGenerator.AddTemplateDDLEntry(PatchGlue.class.getResource("/com/github/gekohl/yagen/ddl/header.ddl.sql")));

        profile.addDdl(new DDLGenerator.AddTemplateDDLEntry(PatchGlue.class.getResource("/com/github/gekohl/yagen/ddl/initialData.ddl.sql")));

        URL resource = PatchGlue.class.getResource("/ddl/ocm-settings.sql");
        if (resource != null) {
            profile.addDdl(new DDLGenerator.AddTemplateDDLEntry(resource));
        }

        addAvailableDDLs(profile, ADDITIONAL_DDL_REGISTRY);
    }

    public static final DDLGenerator.Profile START_EMPTY = new DDLGenerator.Profile("start_empty");
    static {
        DDLGenerator.Profile profile = START_EMPTY;
        PROFILES.put(profile.getName(), profile);

        profile.addHeaderDdl(new DDLGenerator.AddTemplateDDLEntry(PatchGlue.class.getResource("/com/github/gekohl/yagen/ddl/header.ddl.sql")));

        profile.addDdl(new DDLGenerator.AddTemplateDDLEntry(PatchGlue.class.getResource("/com/github/gekohl/yagen/ddl/CascadeNullableInitiallyEnabled.sql")));

        addAvailableDDLs(profile, ADDITIONAL_DDL_REGISTRY);
    }

    private static void addAvailableDDLs(DDLGenerator.Profile profile, List<String> ddlResources) {
        for (String ddlResource : ddlResources) {
            URL resource = PatchGlue.class.getResource(ddlResource);
            if (resource != null) {
                profile.addDdl(new DDLGenerator.AddTemplateDDLEntry(resource));
            }
        }
    }

    public static DDLGenerator.Profile cloneProfile(String name) {
        if (PROFILES.containsKey(name)) {
            try {
                return PROFILES.get(name).clone();
            } catch (CloneNotSupportedException e) {
                LOG.error("error cloning profile");
            }
        }
        return null;
    }

    private static DDLGenerator.Profile profile;

    public PatchGlue() throws Exception {
        init();
    }

    public static void init() throws Exception {
        init(null);
    }

    public static void init(DDLGenerator.Profile profile) throws Exception {
        if (profile == null) {
            profile = new DDLGenerator.Profile("runtime");
        }
        if (PatchGlue.profile == null) {
            try {
                PatchHibernateMappingClasses.applyPatch();
            } catch (HibernateClassesAlreadyLoadedException e) {
                throw new IllegalStateException("hibernate classes already loaded, cannot patch", e);
            }
        }
        PatchGlue.profile = profile;
    }

    public static void patch4Transfer() {
        patch4Transfer(null);
    }

    public static void patch4Transfer(DDLGenerator.Profile profile) {
        try {
            init(profile);
            PatchHibernateMappingClasses.patchCollectionsAlwaysLazy();
            PatchHibernateMappingClasses.patchIgnoreVersion();
        } catch (Exception e) {
            LOG.error("unable to patch for transfer", e);
        }
    }

    public static void initDialect(Dialect dialect, org.hibernate.cfg.NamingStrategy namingStrategy, Properties cfgProperties) {
        if (dialect instanceof DDLEnhancer) {
            if (profile == null) {
                profile = new DDLGenerator.Profile("runtime");
            }
            DDLEnhancer ddlEnhancer = (DDLEnhancer) dialect;
            if (namingStrategy instanceof DefaultNamingStrategy) {
                profile.setNamingStrategy((DefaultNamingStrategy) namingStrategy);
            }
            if (ddlEnhancer.getDDLEnhancer() == null) {
                ddlEnhancer.initDDLEnhancer(profile, dialect);
            }
            if (cfgProperties.containsKey("AURA.CORE.VERSION")) {
                ddlEnhancer.getDDLEnhancer().setVersionCore(cfgProperties.getProperty("AURA.CORE.VERSION"));
            }
            if (cfgProperties.containsKey("AURA.SHARED.VERSION")) {
                ddlEnhancer.getDDLEnhancer().setVersionShared(cfgProperties.getProperty("AURA.SHARED.VERSION"));
            }
        }
        else {
            LOG.warn("dialect does not implement {}", DDLEnhancer.class.getName());
        }
    }

    public static void addClass (PersistentClass clazz) {
        if (profile != null) {
            profile.addPersistenceClass(clazz.getMappedClass());
        }
    }

    public static String afterTableSqlCreateString(Table table, Dialect dialect, String returnValue) {
        StringBuffer buf = new StringBuffer(returnValue);

        Map<String, Column> allColumns = new LinkedHashMap<String, Column>();
        Iterator<Column> colIt = table.getColumnIterator();
        while (colIt.hasNext()) {
            Column column = colIt.next();
            allColumns.put(column.getName().toLowerCase(), column);
        }

        return getDDLEnhancerFromDialect(dialect).updateCreateTable(dialect, buf.append(dialect.getTableTypeString()), table.getName(), allColumns);
    }

    public static String afterTableSqlDropString(Table table, Dialect dialect, String returnValue) {
        StringBuffer buf = new StringBuffer(returnValue);

        return getDDLEnhancerFromDialect(dialect).updateDropTable(dialect, buf, table.getName());
    }

    public static String afterConstraintSqlCreateString(Table table, Dialect dialect, String returnValue, Constraint constraint) {
        if (returnValue == null) {
            return null;
        }

        StringBuffer buf = new StringBuffer(returnValue);

        return getDDLEnhancerFromDialect(dialect).updateCreateConstraint(dialect, buf, constraint.getName(), table, constraint);
    }

    public static String afterIndexSqlCreateString(Table table, Dialect dialect, String returnValue, String name, Iterator columns) {
        StringBuffer buf = new StringBuffer(returnValue);

        List<Column> columnList = new ArrayList<Column>();
        while ( columns.hasNext() ) {
            Column column = (Column) columns.next();
            columnList.add(column);
        }

        return getDDLEnhancerFromDialect(dialect).updateCreateIndex(dialect, buf, name, table, columnList);
    }

    public static String[] afterSequenceSqlCreateStrings(Dialect dialect, String[] ddl, Type type) {
        String returnValue = join(Arrays.asList(ddl), "\n", new StringValueExtractor<String>() {
            @Override
            public String getValue(String object) {
                return object != null ? object : "";
            }
        });

        return new String[]{getDDLEnhancerFromDialect(dialect).updateCreateSequence(dialect, returnValue, type)};
    }

    public static CreateDDL getDDLEnhancerFromDialect(Dialect dialect) {
        if (dialect instanceof DDLEnhancer) {
            DDLEnhancer ddlEnhancer = (DDLEnhancer) dialect;
            return ddlEnhancer.getDDLEnhancer();
        }
        throw new IllegalArgumentException("dialect must implement " + DDLEnhancer.class.getName());
    }

    public static void addConfigurationInterceptor(ConfigurationInterceptor interceptor) {
        PatchHibernateMappingClasses.CONFIGURATION_INTERCEPTOR_INSTANCES.add(interceptor);
    }

    public static List<ConfigurationInterceptor> getConfigurationInterceptors() {
        return PatchHibernateMappingClasses.CONFIGURATION_INTERCEPTOR_INSTANCES;
    }

    public static interface ConfigurationInterceptor {
        void use(Ejb3Configuration configuration);
    }

    public static <T> String join(List<T> list, String separator, StringValueExtractor<T> valueExtractor) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            T object = list.get(i);
            if (i > 0) {
                sb.append(separator);
            }
            sb.append(valueExtractor.getValue(object));
        }
        return sb.toString();
    }

    public static interface StringValueExtractor<T> {
        String getValue(T object);
    }
}
