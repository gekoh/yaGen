package com.github.gekoh.yagen.hibernate.schema;

import com.github.gekoh.yagen.hibernate.DdlPatchHelper;
import org.hibernate.boot.Metadata;
import org.hibernate.dialect.Dialect;
import org.hibernate.resource.transaction.spi.DdlTransactionIsolator;
import org.hibernate.tool.schema.TargetType;
import org.hibernate.tool.schema.internal.HibernateSchemaManagementTool;
import org.hibernate.tool.schema.internal.SchemaCreatorImpl;
import org.hibernate.tool.schema.internal.exec.GenerationTarget;
import org.hibernate.tool.schema.internal.exec.GenerationTargetToDatabase;
import org.hibernate.tool.schema.internal.exec.GenerationTargetToScript;
import org.hibernate.tool.schema.internal.exec.GenerationTargetToStdout;
import org.hibernate.tool.schema.internal.exec.JdbcContext;
import org.hibernate.tool.schema.spi.CommandAcceptanceException;
import org.hibernate.tool.schema.spi.ContributableMatcher;
import org.hibernate.tool.schema.spi.ExecutionOptions;
import org.hibernate.tool.schema.spi.SchemaCreator;
import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.hibernate.tool.schema.spi.ScriptTargetOutput;
import org.hibernate.tool.schema.spi.SourceDescriptor;
import org.hibernate.tool.schema.spi.TargetDescriptor;

import java.util.Collection;

public class SchemaCreatorWrapper implements SchemaCreator {

    private final SchemaCreatorImpl delegate;
    private final HibernateSchemaManagementTool tool;

    public SchemaCreatorWrapper(HibernateSchemaManagementTool tool, SchemaCreatorImpl delegate) {
        this.delegate = delegate;
        this.tool = tool;
    }

    @Override
    public void doCreation(Metadata metadata, ExecutionOptions options, ContributableMatcher contributableInclusionFilter, SourceDescriptor sourceDescriptor, TargetDescriptor targetDescriptor) {
        if ( targetDescriptor.getTargetTypes().isEmpty() ) {
            return;
        }
        final JdbcContext jdbcContext = tool.resolveJdbcContext( options.getConfigurationValues() );
        final GenerationTarget[] targets = buildGenerationTargets(
                targetDescriptor,
                jdbcContext,
                options
        );

        delegate.doCreation( metadata, jdbcContext.getDialect(), options, contributableInclusionFilter, sourceDescriptor, targets );
    }

    private GenerationTarget[] buildGenerationTargets(
            TargetDescriptor targetDescriptor,
            JdbcContext jdbcContext,
            ExecutionOptions options) {

        final GenerationTarget[] targets = new GenerationTarget[ targetDescriptor.getTargetTypes().size() ];

        int index = 0;

        if ( targetDescriptor.getTargetTypes().contains( TargetType.STDOUT ) ) {
            targets[index] = new GenerationTargetToStdoutWrapper(options, jdbcContext.getDialect());
            index++;
        }

        if ( targetDescriptor.getTargetTypes().contains( TargetType.SCRIPT ) ) {
            if ( targetDescriptor.getScriptTargetOutput() == null ) {
                throw new SchemaManagementException( "Writing to script was requested, but no script file was specified" );
            }
            targets[index] = new GenerationTargetToScriptWrapper( targetDescriptor.getScriptTargetOutput(), options, jdbcContext.getDialect() );
            index++;
        }

        if ( targetDescriptor.getTargetTypes().contains( TargetType.DATABASE ) ) {
            targets[index] = new GenerationTargetToDatabaseWrapper( tool.getDdlTransactionIsolator( jdbcContext ), options, jdbcContext.getDialect() );
            index++;
        }

        return targets;
    }

    private static abstract class GenerationTargetWrapper implements GenerationTarget {
        protected final GenerationTarget delegate;
        private final ExecutionOptions options;
        private final Dialect dialect;

        public GenerationTargetWrapper(GenerationTarget delegate, ExecutionOptions options, Dialect dialect) {
            this.delegate = delegate;
            this.options = options;
            this.dialect = dialect;
        }

        @Override
        public void prepare() {
            delegate.prepare();
            acceptExtraStatements(DdlPatchHelper.getHeaderStatements(dialect));
        }

        @Override
        public void accept(String sqlCommand) {
            DdlPatchHelper.splitSQL(sqlCommand).stream()
                    .map(DdlPatchHelper::prepareDDL)
                    .forEach(ddlStmt -> doAccept(ddlStmt.getSql(), ddlStmt.getDelimiter()));
        }

        abstract void doAccept(String command, String delimiter);

        @Override
        public void release() {
            acceptExtraStatements(DdlPatchHelper.getFooterStatements(dialect));
            delegate.release();
        }

        private void acceptExtraStatements(Collection<String> statements) {
            statements.stream()
                    .flatMap(s -> DdlPatchHelper.splitSQL(s).stream())
                    .map(DdlPatchHelper::prepareDDL)
                    .forEach(ddlStmt -> {
                        try {
                            doAccept(ddlStmt.getSql(), ddlStmt.getDelimiter());
                        } catch (CommandAcceptanceException e) {
                            options.getExceptionHandler().handleException(e);
                        }
                    });
        }
    }

    private static class GenerationTargetToDatabaseWrapper extends GenerationTargetWrapper {
        public GenerationTargetToDatabaseWrapper(DdlTransactionIsolator ddlTransactionIsolator, ExecutionOptions options, Dialect dialect) {
            super(new GenerationTargetToDatabase(ddlTransactionIsolator), options, dialect);
        }

        @Override
        void doAccept(String command, String delimiter) {
            if (!DdlPatchHelper.isEmptyStatement(command)) {
                if (command.endsWith("/")) {
                    // remove pl/sql specific ending as postgresql does not like it
                    command = command.substring(0, command.length()-1);
                }
                delegate.accept(command);
            }
        }
    }

    private static class GenerationTargetToStdoutWrapper extends GenerationTargetWrapper {

        public GenerationTargetToStdoutWrapper(ExecutionOptions options, Dialect dialect) {
            super(new GenerationTargetToStdout(null), options, dialect);
        }

        @Override
        void doAccept(String command, String delimiter) {
            delegate.accept(command + (delimiter != null ? delimiter : ""));
        }
    }
    private static class GenerationTargetToScriptWrapper extends GenerationTargetWrapper {

        public GenerationTargetToScriptWrapper(ScriptTargetOutput scriptTarget, ExecutionOptions options, Dialect dialect) {
            super(new GenerationTargetToScript(scriptTarget, null), options, dialect);
        }

        @Override
        void doAccept(String command, String delimiter) {
            delegate.accept(command + (delimiter != null ? delimiter : ""));
        }
    }
}
