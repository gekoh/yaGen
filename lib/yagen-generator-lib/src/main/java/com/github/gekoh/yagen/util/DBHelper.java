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

import com.github.gekoh.yagen.hibernate.DDLEnhancerAware;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import org.hibernate.Session;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.registry.internal.StandardServiceRegistryImpl;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.Size;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.jdbc.ReturningWork;
import org.hibernate.jdbc.Work;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.descriptor.sql.spi.DdlTypeRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.github.gekoh.yagen.api.Constants.DEFAULT_USER_NAME_LEN;

/**
 * @author Georg Kohlweiss
 */
public class DBHelper {
    enum DatabaseDialect {
        ORACLE,
        HSQLDB,
        POSTGRESQL
    }

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DBHelper.class);

    public static final String PROPERTY_GENERATE_BYPASS = "yagen.generator.bypass.implement";
    public static final String PROPERTY_BYPASS = "yagen.bypass";
    public static final String PROPERTY_BYPASS_REGEX = "yagen.bypass.regex";
    public static final String PROPERTY_SKIP_MODIFICATION = "yagen.skip-modification.regex";
    public static final String PROPERTY_POST_PROCESSOR_CLASS = "yagen.ddl.postprocessor.class";
    public static final String PROPERTY_AUDIT_USERCOL_LEN = "yagen.generator.audit.user.maxlen";

    public static final String PROPERTY_POSTGRES_USE_UUID_OSSP_EXTENSION = "yagen.generator.postgres.extension.uuid-ossp";

    private static Field FIELD_CONFIGURATION_VALUES;
    static {
        try {
            FIELD_CONFIGURATION_VALUES = StandardServiceRegistryImpl.class.getDeclaredField("configurationValues");
            FIELD_CONFIGURATION_VALUES.setAccessible(true);
        } catch (NoSuchFieldException e) {
            LOG.error("unable to get field via reflection", e);
        }
    }

    public static String createUUID() {
        return UUID.randomUUID().toString().replaceAll("-", "").toUpperCase(); // replace "-" 36 -> 32 char
    }

    public static String getOsUser() {
//        that's not necessarily the user logged in but one can change this value with env var USERNAME
//        which is absolutely sufficient in this case
        return System.getProperty("user.name");
    }

    public static boolean skipModificationOf(String objectName, Metadata metadata) {
        Map configurationValues = DBHelper.getConfigurationValues(metadata);
        if (objectName == null || configurationValues == null || !configurationValues.containsKey(PROPERTY_SKIP_MODIFICATION)) {
            return false;
        }

        return objectName.matches((String) configurationValues.get(PROPERTY_SKIP_MODIFICATION));
    }

    public static boolean implementBypassFunctionality(Metadata metadata) {
        Map configurationValues = metadata != null ? DBHelper.getConfigurationValues(metadata) : null;

        return configurationValues != null && configurationValues.containsKey(PROPERTY_GENERATE_BYPASS) &&
                Boolean.TRUE.equals(Boolean.valueOf((String) configurationValues.get(PROPERTY_GENERATE_BYPASS)));
    }

    public static void setBypass(String objectRegex, EntityManager em) {
        if (objectRegex == null) {
            objectRegex = "^.*$";
        }
        setSessionVariable(PROPERTY_BYPASS_REGEX, objectRegex, em);
    }

    public static void removeBypass(EntityManager em) {
        removeSessionVariable(PROPERTY_BYPASS_REGEX, em);
    }

    public static void removeSessionVariable(DatabaseDialect dialect, Connection connection, String name) throws SQLException {
        switch (dialect) {
            case ORACLE:
            case HSQLDB:
                try (PreparedStatement stmtUpdate = connection.prepareStatement("delete from SESSION_VARIABLES where name=?")) {
                    stmtUpdate.setString(1, name);
                    stmtUpdate.executeUpdate();
                }
                break;
            case POSTGRESQL:
                try (CallableStatement callableStatement = connection.prepareCall("{call remove_session_variable(?)}")) {
                    callableStatement.setString(1, name);
                    callableStatement.execute();
                }
                break;
            default:
                throw new IllegalArgumentException("unknown dialect: " + dialect);
        }
    }

    public static String getSessionVariable(DatabaseDialect dialect, Connection connection, String name) throws SQLException {
        switch (dialect) {
            case ORACLE:
            case HSQLDB:
                try (PreparedStatement stmtUpdate = connection.prepareStatement("select value from SESSION_VARIABLES where name=?")) {
                    stmtUpdate.setString(1, name);
                    try (ResultSet rs = stmtUpdate.executeQuery()) {
                        if (rs.next()) {
                            String ret = rs.getString(1);
                            return ret;
                        }
                        return null;
                    }
                }
            case POSTGRESQL:
                try (CallableStatement callableStatement = connection.prepareCall("{? = call get_session_variable(?)}")) {
                    callableStatement.setString(2, name);
                    callableStatement.registerOutParameter(1, Types.VARCHAR);
                    callableStatement.execute();

                    return callableStatement.getString(1);
                }
            default:
                throw new IllegalArgumentException("unknown dialect: " + dialect);
        }
    }

    public static void setSessionVariable(DatabaseDialect dialect, Connection connection, String name, String value) throws SQLException {
        switch (dialect) {
            case ORACLE:
            case HSQLDB:
                try (PreparedStatement stmtUpdate = connection.prepareStatement("update SESSION_VARIABLES set VALUE=? where NAME=?")) {
                    stmtUpdate.setString(1, value);
                    stmtUpdate.setString(2, name);
                    if (stmtUpdate.executeUpdate() < 1) {
                        try (PreparedStatement stmtInsert = connection.prepareStatement("insert into SESSION_VARIABLES (name, value) values (?, ?)")) {
                            stmtInsert.setString(1, name);
                            stmtInsert.setString(2, value);
                            stmtInsert.executeUpdate();
                        }
                    }
                }
                break;
            case POSTGRESQL:
                try (CallableStatement callableStatement = connection.prepareCall("{call set_session_variable(?, ?)}")) {
                    callableStatement.setString(1, name);
                    callableStatement.setString(2, value);
                    callableStatement.execute();
                }
                break;
            default:
                throw new IllegalArgumentException("unknown dialect: " + dialect);
        }
    }

    public static void setSessionVariable(String name, String value, EntityManager em) {

        // postgres drops the temporary table when the session closes, so probably we have to create it beforehand
        if (isPostgres(getDialect(em))) {
            em.unwrap(Session.class).doWork(new Work() {
                @Override
                public void execute(Connection connection) throws SQLException {
                    setSessionVariable(DatabaseDialect.POSTGRESQL, connection, name, value);
                }
            });
            return;
        }

        int affected = em.createNativeQuery("update SESSION_VARIABLES set VALUE=:value where NAME=:name")
                .setParameter("name", name)
                .setParameter("value", value)
                .executeUpdate();

        if (affected < 1) {
            em.createNativeQuery("insert into SESSION_VARIABLES (name, value) values (:name, :value)")
                    .setParameter("name", name)
                    .setParameter("value", value)
                    .executeUpdate();
        }
    }

    public static String getSessionVariable(String name, EntityManager em) {

        // postgres drops the temporary table when the session closes, so probably we have to create it beforehand
        if (isPostgres(getDialect(em))) {
            return em.unwrap(Session.class).doReturningWork(new ReturningWork<String>() {
                @Override
                public String execute(Connection connection) throws SQLException {
                    String ret = getSessionVariable(DatabaseDialect.POSTGRESQL, connection, name);
                    return ret;
                }
            });
        }

        String value = null;
        try {
            value = (String) em.createNativeQuery("select value from SESSION_VARIABLES where NAME=:name")
                    .setParameter("name", name)
                    .getSingleResult();
        } catch (NoResultException noResult) {
            // ignore
        }
        return value;
    }

    public static void removeSessionVariable(String name, EntityManager em) {
        // postgres drops the temporary table when the session closes, so probably it's not even existing
        if (isPostgres(getDialect(em))) {
            em.unwrap(Session.class).doWork(new Work() {
                @Override
                public void execute(Connection connection) throws SQLException {
                    removeSessionVariable(DatabaseDialect.POSTGRESQL, connection, name);
                }
            });
            return;
        }

        em.createNativeQuery("delete from SESSION_VARIABLES where name=:name")
                .setParameter("name", name)
                .executeUpdate();
    }

    public static boolean isStaticallyBypassed(String objectName) {
        final String bypass = System.getProperty(PROPERTY_BYPASS);
        if (bypass != null) {
            return true;
        }
        final String bypassPattern = System.getProperty(PROPERTY_BYPASS_REGEX);
        if (bypassPattern != null) {
            return objectName.matches(bypassPattern);
        }
        return false;
    }

    public static String getSysContext(String namespace, String parameter) {
        if ("USERENV".equals(namespace)) {
            if ("DB_NAME".equals(parameter)) {
                return "HSQLDB";
            }
            else if ("OS_USER".equals(parameter)) {
                return getOsUser();
            }
            else if ("CLIENT_IDENTIFIER".equals(parameter)) {
                return null;
            }
        }

        return null;
    }

    public static boolean regexpLike(String value, String regexp) {
        if (value == null) {
            return false;
        }
        return Pattern.compile(regexp).matcher(value).find();
    }

    public static boolean regexpLikeFlags(String value, String regexp, String flags) {
        if (value == null || flags == null) {
            return false;
        }
        String f = flags.toLowerCase();
        int opts = 0;
        if (f.contains("i")) {
            opts = opts | Pattern.CASE_INSENSITIVE;
        }
        if (f.contains("c")) {
            opts = opts | Pattern.CANON_EQ;
        }
        if (f.contains("n")) {
            opts = opts | Pattern.DOTALL;
        }
        if (f.contains("m")) {
            opts = opts | Pattern.MULTILINE;
        }
        return Pattern.compile(regexp, opts).matcher(value).find();
    }

    public static String injectSessionUser(String user, EntityManager em) {
        String prevUser;

        Dialect dialect = getDialect(em);
        if (isPostgres(dialect)
                || isHsqlDb(dialect)) {
            prevUser = getSessionVariable("CLIENT_IDENTIFIER", em);
            if (user == null) {
                removeSessionVariable("CLIENT_IDENTIFIER", em);
            } else {
                setSessionVariable("CLIENT_IDENTIFIER", user, em);
            }
        }
        else {
            prevUser = em.unwrap(Session.class).doReturningWork(new SetUserWorkOracle(user, dialect));
        }

        return prevUser;
    }

    public static boolean isHsqlDb(EntityManager em) {
        return isHsqlDb(getDialect(em));
    }
    public static boolean isHsqlDb(Dialect dialect) {
        return dialectMatches(dialect, "hsql");
    }

    public static boolean isPostgres(EntityManager em) {
        return isPostgres(getDialect(em));
    }
    public static boolean isPostgres(Dialect dialect) {
        return dialectMatches(dialect, "postgres");
    }

    public static boolean isOracle(EntityManager em) {
        return isOracle(getDialect(em));
    }
    public static boolean isOracle(Dialect dialect) {
        return dialectMatches(dialect, "oracle");
    }

    private static boolean dialectMatches(Dialect dialect, String subStr) {
        String driverClassName = getDriverClassName(dialect);
        return driverClassName != null ? driverClassName.toLowerCase().contains(subStr) : dialect.getClass().getName().toLowerCase().contains(subStr);
    }

    public static int getAuditUserMaxlength (Dialect dialect) {
        Metadata metadata = getMetadata(dialect);
        if (metadata != null) {
            Map values = getConfigurationValues(metadata);
            if (values != null && values.get(PROPERTY_AUDIT_USERCOL_LEN) != null) {
                return Integer.parseInt((String) values.get(PROPERTY_AUDIT_USERCOL_LEN));
            }
        }
        return DEFAULT_USER_NAME_LEN;
    }

    public static Metadata getMetadata(Dialect dialect) {
        Object metadataObj;
        if (!(dialect instanceof DDLEnhancerAware) || (metadataObj = ((DDLEnhancerAware) dialect).getMetadata()) == null) {
            return null;
        }
        return (Metadata) metadataObj;
    }

    public static String getDriverClassName(EntityManager em) {
        return em.unwrap(Session.class).doReturningWork(new ReturningWork<String>() {
            public String execute(Connection connection) throws SQLException {
                return connection.getMetaData().getDriverName();
            }
        });
    }

    public static Dialect getDialect(EntityManager em) {
        if (em.unwrap(Session.class) != null) {
            final SessionFactoryImpl sessionFactory = em.unwrap(Session.class).getSessionFactory().unwrap(SessionFactoryImpl.class);
            return sessionFactory != null ? sessionFactory.getJdbcServices().getDialect() : null;
        }
        return null;
    }

    public static String getDriverClassName(Dialect dialect) {

        Metadata metadata = getMetadata(dialect);
        if (metadata == null) {
            return null;
        }

        Map configurationValues = getConfigurationValues(metadata);
        return configurationValues != null ? tryGetDriverNameFromDataSource(configurationValues) : null;
    }

    private static Method basicDataSourceGetDriverClassNameMethod = null;
    private static boolean basicDataSourceGetDriverClassNameMethodInitDone = false;

    private static String tryGetDriverNameFromDataSource(Map properties) {
        String driverName = (String) properties.get("hibernate.connection.driver_class");
        if (driverName == null) {
            if (!basicDataSourceGetDriverClassNameMethodInitDone) {
                basicDataSourceGetDriverClassNameMethodInitDone = true;
                try {
                    basicDataSourceGetDriverClassNameMethod =
                            Class.forName("org.apache.commons.dbcp.BasicDataSource").getMethod("getDriverClassName");
                } catch (Exception ex) {
                    return null;
                }
            }
            if (basicDataSourceGetDriverClassNameMethod != null) {
                try {
                    return (String) basicDataSourceGetDriverClassNameMethod.invoke(properties.get("hibernate.connection.datasource"));
                } catch (Exception ex) {
                    return null;
                }
            }
        }
        return driverName;
    }


    public static Map getConfigurationValues(Metadata metadata) {
        ServiceRegistry serviceRegistry = metadata.getDatabase().getServiceRegistry();
        try {
            if (serviceRegistry instanceof StandardServiceRegistryImpl && FIELD_CONFIGURATION_VALUES != null) {
                return (Map) FIELD_CONFIGURATION_VALUES.get(serviceRegistry);
            }
        } catch (Exception ignore) {
        }
        LOG.warn("cannot get configuration values");
        return null;
    }

    public static String getTimestampDdlTypeDeclaration(Dialect dialect) {
        return getDdlTypeDeclaration(dialect, Types.TIMESTAMP, 0, dialect.getDefaultTimestampPrecision(), 0);
    }

    public static String getDdlTypeDeclaration(Dialect dialect, int type, int length, int intPrec, int intScale) {
        Metadata metadata = getMetadata(dialect);
        if (metadata != null) {
            DdlTypeRegistry ddlTypeRegistry = metadata.getDatabase().getTypeConfiguration().getDdlTypeRegistry();

            return ddlTypeRegistry.getTypeName(type, new Size(intPrec, intScale, length, null));
        } else {
            LOG.warn("unable to determine db data type");
            return "varchar(255)";
        }
    }

    public static Timestamp getCurrentTimestamp() {
        return NanoAwareTimestampUtil.getCurrentTimestamp();
    }

    public static class SetUserWorkOracle implements ReturningWork<String> {
        private Dialect dialect;
        private String userName;

        public SetUserWorkOracle(String userName, Dialect dialect) {
            this.userName = userName;
            this.dialect = dialect;
        }

        public String execute(Connection connection) throws SQLException {
            int auditUserMaxlength = getAuditUserMaxlength(dialect);
            CallableStatement statement = connection.prepareCall(
                    "declare newUserValue varchar2(" + auditUserMaxlength + ") := substr(?,1," + auditUserMaxlength + "); " +
                            "begin ? := sys_context('USERENV','CLIENT_IDENTIFIER'); " +
                            "DBMS_SESSION.set_identifier(newUserValue); " +
                            "end;"
            );
            statement.setString(1, userName);
            statement.registerOutParameter(2, Types.VARCHAR);
            statement.execute();
            String result = statement.getString(2);
            statement.close();
            LOG.info("set client_identifier in oracle session from '{}' to '{}'", result == null ? "" : result, userName == null ? "" : "<db_user> (" + userName + ")");
            return result;
        }
    }

    public static void executeProcedure(EntityManager em, String method, Object... args) {
        Session session = em.unwrap(Session.class);
        session.doWork(new Work() {
            @Override
            public void execute(Connection connection) throws SQLException {
                try (CallableStatement callableStatement = connection.prepareCall(String.format("{call %s}", method))) {
                    if (args != null) {
                        for (int i = 0; i<args.length; i++) {
                            callableStatement.setObject(i+1, args[i]);
                        }
                    }
                    callableStatement.execute();
                }
            }
        });
    }
}
