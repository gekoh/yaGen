package com.github.gekoh.yagen.hibernate.schema;

import com.github.gekoh.yagen.hibernate.DdlPatchHelper;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.registry.selector.spi.StrategySelector;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.spi.ServiceRegistryAwareService;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.hibernate.tool.schema.internal.DefaultSchemaFilterProvider;
import org.hibernate.tool.schema.internal.HibernateSchemaManagementTool;
import org.hibernate.tool.schema.internal.SchemaCreatorImpl;
import org.hibernate.tool.schema.internal.exec.GenerationTarget;
import org.hibernate.tool.schema.internal.exec.JdbcContext;
import org.hibernate.tool.schema.spi.ContributableMatcher;
import org.hibernate.tool.schema.spi.DelayedDropAction;
import org.hibernate.tool.schema.spi.ExecutionOptions;
import org.hibernate.tool.schema.spi.ExtractionTool;
import org.hibernate.tool.schema.spi.SchemaCreator;
import org.hibernate.tool.schema.spi.SchemaDropper;
import org.hibernate.tool.schema.spi.SchemaFilterProvider;
import org.hibernate.tool.schema.spi.SchemaManagementTool;
import org.hibernate.tool.schema.spi.SchemaMigrator;
import org.hibernate.tool.schema.spi.SchemaTruncator;
import org.hibernate.tool.schema.spi.SchemaValidator;
import org.hibernate.tool.schema.spi.SourceDescriptor;
import org.hibernate.tool.schema.spi.TargetDescriptor;

import java.util.Map;

public class SchemaManagementToolWrapper implements SchemaManagementTool, ServiceRegistryAwareService {

    private final HibernateSchemaManagementTool delegate = new HibernateSchemaManagementTool();

    private ServiceRegistry serviceRegistry;

    @Override
    public void injectServices(ServiceRegistryImplementor serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
        delegate.injectServices(serviceRegistry);
    }

    @Override
    public SchemaCreator getSchemaCreator(Map options) {
        SchemaCreatorWrapper schemaCreatorWrapper = new SchemaCreatorWrapper(delegate, new SchemaCreatorImpl(delegate, getSchemaFilterProvider(options).getCreateFilter()));
        return new SchemaCreator() {
            @Override
            public void doCreation(Metadata metadata, ExecutionOptions options, ContributableMatcher contributableInclusionFilter, SourceDescriptor sourceDescriptor, TargetDescriptor targetDescriptor) {
                DdlPatchHelper.initDialect(metadata);
                schemaCreatorWrapper.doCreation(metadata, options, contributableInclusionFilter, sourceDescriptor, targetDescriptor);
            }
        };
    }

    private SchemaFilterProvider getSchemaFilterProvider(Map options) {
        final Object configuredOption = (options == null)
                ? null
                : options.get( AvailableSettings.HBM2DDL_FILTER_PROVIDER );
        return serviceRegistry.getService( StrategySelector.class ).resolveDefaultableStrategy(
                SchemaFilterProvider.class,
                configuredOption,
                DefaultSchemaFilterProvider.INSTANCE
        );
    }

    @Override
    public ExtractionTool getExtractionTool() {
        return delegate.getExtractionTool();
    }

    @Override
    public SchemaTruncator getSchemaTruncator(Map<String, Object> options) {
        SchemaTruncator schemaTruncator = delegate.getSchemaTruncator(options);
        return new SchemaTruncator() {
            @Override
            public void doTruncate(Metadata metadata, ExecutionOptions options, ContributableMatcher contributableInclusionFilter, TargetDescriptor targetDescriptor) {
                DdlPatchHelper.initDialect(metadata);
                schemaTruncator.doTruncate(metadata, options, contributableInclusionFilter, targetDescriptor);
            }
        };
    }

    @Override
    public GenerationTarget[] buildGenerationTargets(TargetDescriptor targetDescriptor, JdbcContext jdbcContext, Map<String, Object> options, boolean needsAutoCommit) {
        return delegate.buildGenerationTargets(targetDescriptor, jdbcContext, options, needsAutoCommit);
    }

    @Override
    public SchemaDropper getSchemaDropper(Map options) {
        SchemaDropper schemaDropper = delegate.getSchemaDropper(options);
        return new SchemaDropper() {
            @Override
            public void doDrop(Metadata metadata, ExecutionOptions options, ContributableMatcher contributableInclusionFilter, SourceDescriptor sourceDescriptor, TargetDescriptor targetDescriptor) {
                DdlPatchHelper.initDialect(metadata);
                schemaDropper.doDrop(metadata, options, contributableInclusionFilter, sourceDescriptor, targetDescriptor);
            }

            @Override
            public DelayedDropAction buildDelayedAction(Metadata metadata, ExecutionOptions options, ContributableMatcher contributableInclusionFilter, SourceDescriptor sourceDescriptor) {
                return schemaDropper.buildDelayedAction(metadata, options, contributableInclusionFilter, sourceDescriptor);
            }
        };
    }

    @Override
    public SchemaMigrator getSchemaMigrator(Map options) {
        SchemaMigrator schemaMigrator = delegate.getSchemaMigrator(options);
        return new SchemaMigrator() {
            @Override
            public void doMigration(Metadata metadata, ExecutionOptions options, ContributableMatcher contributableInclusionFilter, TargetDescriptor targetDescriptor) {
                DdlPatchHelper.initDialect(metadata);
                schemaMigrator.doMigration(metadata, options, contributableInclusionFilter, targetDescriptor);
            }
        };
    }

    @Override
    public SchemaValidator getSchemaValidator(Map options) {
        SchemaValidator schemaValidator = delegate.getSchemaValidator(options);
        return new SchemaValidator() {
            @Override
            public void doValidation(Metadata metadata, ExecutionOptions options, ContributableMatcher contributableInclusionFilter) {
                DdlPatchHelper.initDialect(metadata);
                schemaValidator.doValidation(metadata, options, contributableInclusionFilter);
            }
        };
    }

    @Override
    public void setCustomDatabaseGenerationTarget(GenerationTarget generationTarget) {
        delegate.setCustomDatabaseGenerationTarget(generationTarget);
    }
}
