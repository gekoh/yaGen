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

import com.github.gekoh.yagen.api.DefaultNamingStrategy;
import com.github.gekoh.yagen.util.DBHelper;
import org.apache.commons.lang.StringUtils;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.common.reflection.XClass;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.boot.model.relational.Sequence;
import org.hibernate.cfg.Configuration;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.internal.FormatStyle;
import org.hibernate.engine.jdbc.internal.Formatter;
import org.hibernate.internal.SessionFactoryImpl;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Constraint;
import org.hibernate.mapping.Index;
import org.hibernate.mapping.Table;
import org.hibernate.tool.schema.internal.Helper;
import org.hibernate.tool.schema.internal.SchemaCreatorImpl;
import org.hibernate.tool.schema.internal.exec.GenerationTarget;
import org.hibernate.tool.schema.internal.exec.GenerationTargetToDatabase;
import org.hibernate.tool.schema.internal.exec.GenerationTargetToScript;
import org.hibernate.tool.schema.internal.exec.GenerationTargetToStdout;
import org.hibernate.tool.schema.spi.ExecutionOptions;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Georg Kohlweiss 
 */
public class PatchGlue {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PatchGlue.class);

    public static final String STATEMENT_SEPARATOR = "\n------- CreateDDL statement separator -------\n";
    public static final Pattern SEPARATOR_PATTERN = Pattern.compile("\r?\n" + STATEMENT_SEPARATOR.trim() + "\r?\n");
    public static final Pattern PLSQL_END_PATTERN = Pattern.compile("[\\s]+end[\\s]*([a-z_]+)?;([\\s]*(\\r?\\n)?/?)$", Pattern.CASE_INSENSITIVE);
    public static final Pattern COMMENT_PATTERN = Pattern.compile(
            "(((--)[^\\n]*((\\r?\\n)|$))+)|" + // single line comment(s)
                    "(/\\*+(.*?)\\*+/)", // block comment
            Pattern.DOTALL);

    private static Method schemaExportPerform;
    private static Field generatorScriptDelimiter;
    private static Field generatorStdoutDelimiter;

    public static Object newDDLEnhancer(Object profile, Object metadataObj) {
        try {
            Metadata metadata = (Metadata) metadataObj;
            ReflectExecutor.m_registerMetadata.get().invoke(profile, metadata);
            return ReflectExecutor.i_createDdl.get().newInstance(profile, metadata.getDatabase().getDialect());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object createProfile(Metadata metadata) {
        try {
            return ReflectExecutor.m_createProfileFromMetadata.get().invoke(null, "runtime", metadata);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static void initDialect(Metadata metadata) {
        initDialect(createProfile(metadata), metadata);
    }

    public static void initDialect(SessionFactory sessionFactory) {
        if (!(sessionFactory instanceof SessionFactoryImpl)) {
            throw new IllegalStateException("expecting SessionFactoryImpl");
        }
        SessionFactoryImpl impl = (SessionFactoryImpl) sessionFactory;

        try {
            Metadata metadata = (Metadata) ReflectExecutor.m_getMetadata.get().invoke(impl);
            initDialect(createProfile(metadata), metadata);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static void initDialect(Object profile, Metadata metadata) {
        Dialect dialect = metadata.getDatabase().getDialect();

        if (dialect != null && ReflectExecutor.c_enhancer.get().isAssignableFrom(dialect.getClass())) {
            try {
                PhysicalNamingStrategy namingStrategy = metadata.getDatabase().getPhysicalNamingStrategy();

                if (namingStrategy instanceof DefaultNamingStrategy) {
                    ReflectExecutor.m_setNamingStrategy.get().invoke(profile, namingStrategy);
                }
                if (ReflectExecutor.m_getDDLEnhancer.get().invoke(dialect) == null) {
                    ReflectExecutor.m_initDDLEnhancer.get().invoke(dialect, profile, metadata);
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        else {
            LOG.warn("{} was not patched, generator enhancements not working", dialect != null ? dialect.getClass().getName() : "Dialect");
        }
    }

    public static void sortEntityClasses(List<XClass> classes) {
        classes.sort(Comparator.comparing(XClass::getName));
    }

    public static String[] afterTableSqlString(boolean createNotDrop, Table table, Metadata metadata, String[] returnValue) {
        String objectName = table.getName();
        if (DBHelper.skipModificationOf(objectName, metadata)) {
            return returnValue;
        }
        Dialect dialect = metadata.getDatabase().getDialect();
        StringBuffer buf = new StringBuffer(returnValue[0]);

        Map<String, Column> allColumns = new LinkedHashMap<String, Column>();
        Iterator<Column> colIt = table.getColumnIterator();
        while (colIt.hasNext()) {
            Column column = colIt.next();
            allColumns.put(column.getName().toLowerCase(), column);
        }

        Object ddlEnhancer = getDDLEnhancerFromDialect(dialect);
        if (ddlEnhancer != null) {
            try {
                returnValue[0] = (String) (createNotDrop ?
                        ReflectExecutor.m_updateCreateTable.get().invoke(ddlEnhancer, dialect, buf.append(dialect.getTableTypeString()), objectName, allColumns) :
                        ReflectExecutor.m_updateDropTable.get().invoke(ddlEnhancer, dialect, buf, objectName));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        return returnValue;
    }

    public static String[] afterConstraintSqlString(boolean createNotDrop, Constraint constraint, Metadata metadata, String[] returnValue) {
        if (!createNotDrop || returnValue == null || returnValue.length < 1) {
            return returnValue;
        }
        String objectName = constraint.getName();
        if (DBHelper.skipModificationOf(objectName, metadata)) {
            return returnValue;
        }
        Dialect dialect = metadata.getDatabase().getDialect();
        StringBuffer buf = new StringBuffer(returnValue[0]);

        Object ddlEnhancer = getDDLEnhancerFromDialect(dialect);
        if (ddlEnhancer != null) {
            try {
                returnValue[0] = (String) ReflectExecutor.m_updateCreateConstraint.get().invoke(ddlEnhancer, dialect, buf, objectName, constraint.getTable(), constraint);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        return returnValue;
    }

    public static String[] afterIndexSqlString(boolean createNotDrop, Index index, Metadata metadata, String[] returnValue) {
        if (!createNotDrop) {
            return returnValue;
        }
        String objectName = index.getName();
        if (DBHelper.skipModificationOf(objectName, metadata)) {
            return returnValue;
        }
        Dialect dialect = metadata.getDatabase().getDialect();
        StringBuffer buf = new StringBuffer(returnValue[0]);

        List<Column> columnList = new ArrayList<Column>();
        Iterator<Column> columns = index.getColumnIterator();
        while ( columns.hasNext() ) {
            Column column = columns.next();
            columnList.add(column);
        }

        Object ddlEnhancer = getDDLEnhancerFromDialect(dialect);
        if (ddlEnhancer != null) {
            try {
                returnValue[0] = (String) ReflectExecutor.m_updateCreateIndex.get().invoke(ddlEnhancer, dialect, buf, objectName, index.getTable(), columnList);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        return returnValue;
    }

    public static String[] afterSequenceSqlString(boolean createNotDrop, Sequence sequence, Metadata metadata, String[] returnValue) {
        if (!createNotDrop) {
            return returnValue;
        }
        String objectName = sequence.getName().getSequenceName().getText();
        if (DBHelper.skipModificationOf(objectName, metadata)) {
            return returnValue;
        }
        Dialect dialect = metadata.getDatabase().getDialect();

        Object ddlEnhancer = getDDLEnhancerFromDialect(dialect);
        if (ddlEnhancer != null) {
            try {
                returnValue[0] = (String) ReflectExecutor.m_updateCreateSequence.get().invoke(ddlEnhancer, dialect, returnValue[0]);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        return returnValue;
    }

    public static Object getDDLEnhancerFromDialect(Dialect dialect) {
        if (dialect != null && ReflectExecutor.c_enhancer.get().isAssignableFrom(dialect.getClass())) {
            Object ddlEnhancer;
            try {
                ddlEnhancer = ReflectExecutor.m_getDDLEnhancer.get().invoke(dialect);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            if (ddlEnhancer == null) {
                LOG.warn("cannot enhance DDL since dialect was not initialized");
            }
            return ddlEnhancer;
        }
        throw new IllegalArgumentException((dialect != null ? dialect.getClass().getName() : "Dialect") + " was not patched, generator enhancements inoperable");
    }

    public static void addConfigurationInterceptor(ConfigurationInterceptor interceptor) {
        ReflectExecutor.CONFIGURATION_INTERCEPTOR_INSTANCES.add(interceptor);
    }

    public static List<ConfigurationInterceptor> getConfigurationInterceptors() {
        return ReflectExecutor.CONFIGURATION_INTERCEPTOR_INSTANCES;
    }

    public interface ConfigurationInterceptor {
        void use(Configuration configuration);
    }

    public static void schemaExportPerform (String[] sqlCommands,
                                            Formatter formatter,
                                            ExecutionOptions options,
                                            GenerationTarget[] targets) {
        for (String sqlCommand : sqlCommands) {
            writePreparedStatements(sqlCommand, formatter, options, targets);
        }
    }

    private static void writePreparedStatements(String sqlCommand, Formatter formatter, ExecutionOptions options, GenerationTarget[] targets) {
        if (schemaExportPerform == null) {
            try {
                schemaExportPerform = SchemaCreatorImpl.class.getDeclaredMethod("applySqlStringsApi", String[].class, Formatter.class, ExecutionOptions.class, GenerationTarget[].class);
                generatorScriptDelimiter = GenerationTargetToScript.class.getDeclaredField("delimiter");
                generatorScriptDelimiter.setAccessible(true);
                generatorStdoutDelimiter = GenerationTargetToStdout.class.getDeclaredField("delimiter");
                generatorStdoutDelimiter.setAccessible(true);
            } catch (NoSuchMethodException | NoSuchFieldException e) {
                LOG.error(schemaExportPerform == null ? "cannot find api method inserted by patch" : "cannot find field delimiter of GenerationTarget impl class", e);
            }
        }
        final String[] wrapArr = new String[1];

        formatter = new FormatFixFormatter(formatter);

        for (String singleSql : splitSQL(sqlCommand)) {
            SqlStatement ddlStmt = prepareDDL(singleSql);
            wrapArr[0] = ddlStmt.getSql();
            try {
                GenerationTarget[] passedExporters = new GenerationTarget[1];
                for (GenerationTarget exporter : targets) {
                    passedExporters[0] = exporter;
                    if (exporter.getClass() == GenerationTargetToScript.class) {
                        generatorScriptDelimiter.set(exporter, ddlStmt.getDelimiter());
                    }
                    else if (exporter.getClass() == GenerationTargetToStdout.class) {
                        generatorStdoutDelimiter.set(exporter, ddlStmt.getDelimiter());
                    }
                    else if (exporter.getClass() == GenerationTargetToDatabase.class) {
                        if (isEmptyStatement(ddlStmt.getSql())) {
                            continue;
                        }
                        if (wrapArr[0].endsWith("/")) {
                            // remove pl/sql specific ending as postgresql does not like it
                            wrapArr[0] = wrapArr[0].substring(0, wrapArr[0].length()-1);
                        }
                    }
                    schemaExportPerform.invoke(null, new Object[]{wrapArr, formatter, options, passedExporters});
                }
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof SQLException) {
                    LOG.warn("failed executing sql: {}", singleSql);
                    LOG.warn("failure: {}", e.getCause().getMessage());
                }
            } catch (Exception e) {
                LOG.error("cannot call patched api method in SchemaExport", e);
            }
        }
    }

    public static boolean isScript(String sql) {
        int idx = indexOfSeparator(sql, 0);

        return idx > 0 || indexOfSeparator(sql, Math.min(STATEMENT_SEPARATOR.length(), sql.length())) > 0;
    }

    private static int indexOfSeparator(String sql, int startIdx) {
        Matcher matcher = SEPARATOR_PATTERN.matcher(sql);
        if (matcher.find(startIdx)) {
            return matcher.start();
        }
        return -1;
    }

    public static Collection<String> splitSQL(String sql) {
        Matcher matcher = SEPARATOR_PATTERN.matcher(sql);
        int endIdx, idx=0;
        ArrayList<String> statements = new ArrayList<String>();

        while(matcher.find(idx)) {
            endIdx=matcher.start();

            if (endIdx-idx > 0) {
                statements.add(sql.substring(idx, endIdx));
            }

            if (endIdx>=0) {
                idx = matcher.end();
            }
        }

        if (idx < sql.length()) {
            String singleSql = sql.substring(idx);
            if (StringUtils.isNotEmpty(singleSql.trim())) {
                statements.add(singleSql);
            }
        }

        for (int i=0; i<statements.size(); i++) {
            String stmt = statements.get(i);
            if (stmt == null || stmt.trim().length() < 1) {
                statements.remove(i);
                i--;
                continue;
            }
            matcher = COMMENT_PATTERN.matcher(stmt);
            if (matcher.find() && stmt.substring(0, matcher.start()).trim().length() < 1) {
                statements.remove(i);
                statements.add(i, stmt.substring(matcher.end()));
                if (stmt.substring(0, matcher.end()).trim().length() > 0) {
                    statements.add(i, stmt.substring(0, matcher.end()));
                }
            }
        }

        return statements;
    }

    public static boolean isEmptyStatement(String sqlStmt) {
        Matcher matcher = COMMENT_PATTERN.matcher(sqlStmt);

        while (matcher.find()) {
            sqlStmt = sqlStmt.substring(0, matcher.start()) + sqlStmt.substring(matcher.end());
            matcher = COMMENT_PATTERN.matcher(sqlStmt);
        }

        return sqlStmt.trim().length()<1;
    }

    public static SqlStatement prepareDDL(String sql){
        sql = sql.trim();
        String delimiter = "";

        Matcher matcher = PLSQL_END_PATTERN.matcher(sql);
        if (matcher.find()) {
            if (matcher.group(2) != null) {
                sql = sql.substring(0, matcher.start(2));
            }
            sql += "\n";
            delimiter = "/";
        }
        // remove trailing semicolon in case of non pl/sql type objects/statements
        else if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length()-1);
        }

        StringBuilder sqlWoComments = new StringBuilder(sql);
        while ((matcher = COMMENT_PATTERN.matcher(sqlWoComments.toString())).find()) {
            sqlWoComments.delete(matcher.start(), matcher.end());
        }

        if (delimiter.length() < 1 && sqlWoComments.toString().trim().length() > 0) {
            delimiter = ";";
        }

        return new SqlStatementImpl(sql, delimiter);
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

    public static void addHeader(Dialect dialect, ExecutionOptions options, GenerationTarget[] targets) {
        List<String> statements = getStatements(ReflectExecutor.m_getHeaderStatements.get(), dialect);
        writeStatement(options, targets, statements);
    }

    public static void addFooter(Dialect dialect, ExecutionOptions options, GenerationTarget[] targets) {
        List<String> statements = getStatements(ReflectExecutor.m_getFooterStatements.get(), dialect);
        writeStatement(options, targets, statements);
    }

    private static void writeStatement(ExecutionOptions options, GenerationTarget[] targets, List<String> statements) {
        if (statements != null) {
            final boolean format = Helper.interpretFormattingEnabled( options.getConfigurationValues() );
            final Formatter formatter = format ? FormatStyle.DDL.getFormatter() : FormatStyle.NONE.getFormatter();

            schemaExportPerform(statements.toArray(new String[statements.size()]), formatter, options, targets);
        }
    }

    public static List<String> getStatements(Method method, Dialect dialect){
        Object profile;
        Object enhancer;
        if (dialect != null && (enhancer = getDDLEnhancerFromDialect(dialect)) != null) {
            try {
                profile = ReflectExecutor.m_getProfile.get().invoke(enhancer);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        else {
            LOG.warn("{} was not patched, generator enhancements not working", dialect != null ? dialect.getClass().getName() : "Dialect");
            return null;
        }

        try {
            return (List<String>) method.invoke(profile, dialect);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static interface StringValueExtractor<T> {
        String getValue(T object);
    }

    public static class SqlStatementImpl implements SqlStatement {
        private String sql;
        private String delimiter;

        private SqlStatementImpl(String sql, String delimiter) {
            this.sql = sql;
            this.delimiter = delimiter;
        }

        public String getSql() {
            return sql;
        }

        public String getDelimiter() {
            return delimiter;
        }
    }

    public static final class FormatFixFormatter implements Formatter {

        // out of org.hibernate.engine.jdbc.internal.DDLFormatterImpl.INITIAL_LINE
        private static final String INITIAL_LINE = System.lineSeparator() + "    ";
        private static final Pattern CREATE_TABLE = Pattern.compile("(.*create table [^\\s(]+\\s?\\(\\s*)", Pattern.MULTILINE);

        protected Formatter delegateFormatter;

        public FormatFixFormatter(Formatter delegateFormatter) {
            this.delegateFormatter = delegateFormatter;
        }

        @Override
        public String format(String sql) {
            String format = delegateFormatter.format(sql);
            // first column is indented one char less due to added space char contained in source string before each subsequent column
            Matcher matcher = CREATE_TABLE.matcher(format);
            if (matcher.find()) {
                format = format.substring(0, matcher.end()) + " " + format.substring(matcher.end());
            }
            // for whatever reason create statements other than create table do not receive initial newline which would make generated DDL more readable
            return format.startsWith("create") ? INITIAL_LINE + format : format;
        }
    }
}
