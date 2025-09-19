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

import com.github.gekoh.yagen.ddl.CreateDDL;
import com.github.gekoh.yagen.ddl.DDLGenerator;
import com.github.gekoh.yagen.util.DBHelper;
import org.apache.commons.lang.StringUtils;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.model.relational.Sequence;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.internal.Formatter;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Constraint;
import org.hibernate.mapping.Index;
import org.hibernate.mapping.Table;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Georg Kohlweiss 
 */
public class DdlPatchHelper {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DdlPatchHelper.class);

    public static final String STATEMENT_SEPARATOR = "\n------- CreateDDL statement separator -------\n";
    public static final Pattern SEPARATOR_PATTERN = Pattern.compile("\r?\n" + STATEMENT_SEPARATOR.trim() + "\r?\n");
    public static final Pattern PLSQL_END_PATTERN = Pattern.compile("[\\s]+end[\\s]*([a-z_]+)?;([\\s]*(\\r?\\n)?/?)$", Pattern.CASE_INSENSITIVE);
    public static final Pattern COMMENT_PATTERN = Pattern.compile(
            "(((--)[^\\n]*((\\r?\\n)|$))+)|" + // single line comment(s)
                    "(/\\*+(.*?)\\*+/)", // block comment
            Pattern.DOTALL);

    public static Object newDDLEnhancer(DDLGenerator.Profile profile, Metadata metadata) {
        return new CreateDDL(profile, metadata.getDatabase().getDialect());
    }

    public static void initDialect(Metadata metadata) {
        Dialect dialect = metadata.getDatabase().getDialect();
        DDLEnhancerAware ea = unwrap(dialect);

        DDLGenerator.Profile profile;

        if (ea == null || ea.getDDLEnhancer() == null || ea.getDDLEnhancer().getProfile() == null) {
            profile = DDLGenerator.createProfileFromMetadata("runtime", metadata);
        }
        else {
            profile = ea.getDDLEnhancer().getProfile();
        }
        initDialect(profile, metadata);
    }

    public static void initDialect(DDLGenerator.Profile profile, Metadata metadata) {
        Dialect dialect = metadata.getDatabase().getDialect();

        DDLEnhancerAware ea = unwrap(dialect);

        if (ea != null) {
            if (ea.getDDLEnhancer() != null) {
                LOG.info("replacing current DDL enhancer");
            }
            profile.registerMetadata(metadata);
            ea.initDDLEnhancer(profile, metadata);
        }
        else {
            throw new IllegalStateException();
        }
    }

    public static String[] afterTableSqlString(boolean createNotDrop, Table table, Metadata metadata, String[] returnValue) {
        String objectName = table.getName();
        if (DBHelper.skipModificationOf(objectName, metadata)) {
            return returnValue;
        }
        Dialect dialect = metadata.getDatabase().getDialect();
        CreateDDL ddlEnhancer = getDDLEnhancerFromDialect(dialect);

        if (ddlEnhancer == null) {
            return returnValue;
        }

        StringBuffer buf = new StringBuffer(returnValue[0]);

        Map<String, Column> allColumns = new LinkedHashMap<String, Column>();
        table.getColumns().forEach(c -> allColumns.put(c.getName().toLowerCase(), c));

        try {
            return spoilSqlStrings(returnValue, (createNotDrop ?
                            ddlEnhancer.updateCreateTable(dialect, buf.append(dialect.getTableTypeString()), objectName, allColumns) :
                            ddlEnhancer.updateDropTable(dialect, buf, objectName)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
        CreateDDL ddlEnhancer = getDDLEnhancerFromDialect(dialect);

        if (ddlEnhancer == null) {
            return returnValue;
        }

        StringBuffer buf = new StringBuffer(returnValue[0]);

        try {
            return spoilSqlStrings(returnValue, ddlEnhancer.updateCreateConstraint(dialect, buf, objectName, constraint.getTable(), constraint));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
        CreateDDL ddlEnhancer = getDDLEnhancerFromDialect(dialect);

        if (ddlEnhancer == null) {
            return returnValue;
        }

        StringBuffer buf = new StringBuffer(returnValue[0]);

        List<Column> columnList = new ArrayList<>(index.getColumns());

        try {
            return spoilSqlStrings(returnValue, ddlEnhancer.updateCreateIndex(dialect, buf, objectName, index.getTable(), columnList));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
        CreateDDL ddlEnhancer = getDDLEnhancerFromDialect(dialect);

        if (ddlEnhancer == null) {
            return returnValue;
        }

        try {
            return spoilSqlStrings(returnValue, ddlEnhancer.updateCreateSequence(dialect, returnValue[0]));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String[] spoilSqlStrings(String[] sqlStrings, String modifiedMainSql) {
        ArrayList<String> sqlList = new ArrayList<>(Arrays.asList(sqlStrings));
        sqlList.remove(0);

        sqlList.addAll(0, splitSQL(modifiedMainSql));

        return sqlList.toArray(new String[0]);
    }

    private static DDLEnhancerAware unwrap(Dialect dialect) {
        if (dialect instanceof DDLEnhancerAware) {
            return (DDLEnhancerAware) dialect;
        }
        LOG.warn("used dialect needs to implement {}, generator enhancements not working", DDLEnhancerAware.class.getName());
        return null;
    }

    public static CreateDDL getDDLEnhancerFromDialect(Dialect dialect) {
        DDLEnhancerAware ea = unwrap(dialect);
        if (ea != null) {
            return ea.getDDLEnhancer();
        }
        throw new IllegalArgumentException((dialect != null ? dialect.getClass().getName() : "Dialect") + " must implement the DDLEnhancerAware interface");
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

    public static SqlStatement prepareDDL(String sql, Dialect dialect, DdlPostProcessor postProcessor){
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

        if (postProcessor != null) {
            sql = postProcessor.postProcessDDL(sql, dialect);
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

    public static List<String> getHeaderStatements(Dialect dialect) {
        CreateDDL enhancer = getDDLEnhancerFromDialect(dialect);
        return enhancer != null ? enhancer.getProfile().getHeaderStatements(dialect) : Collections.emptyList();
    }

    public static List<String> getFooterStatements(Dialect dialect) {
        CreateDDL enhancer = getDDLEnhancerFromDialect(dialect);
        return enhancer != null ? enhancer.getProfile().getFooterStatements(dialect) : Collections.emptyList();
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
