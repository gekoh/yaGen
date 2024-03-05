package com.github.gekoh.yagen.hibernate.exporter;

import com.github.gekoh.yagen.hibernate.DdlPatchHelper;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.mapping.ForeignKey;
import org.hibernate.tool.schema.spi.Exporter;

public class ForeignKeyExporterWrapper implements Exporter<ForeignKey> {

    private final Exporter<ForeignKey> delegate;

    public ForeignKeyExporterWrapper(Exporter<ForeignKey> delegate) {
        this.delegate = delegate;
    }

    @Override
    public String[] getSqlCreateStrings(ForeignKey foreignKey, Metadata metadata, SqlStringGenerationContext context) {
        return DdlPatchHelper.afterConstraintSqlString(true, foreignKey, metadata, delegate.getSqlCreateStrings(foreignKey, metadata, context));
    }

    @Override
    public String[] getSqlDropStrings(ForeignKey foreignKey, Metadata metadata, SqlStringGenerationContext context) {
        return DdlPatchHelper.afterConstraintSqlString(false, foreignKey, metadata, delegate.getSqlCreateStrings(foreignKey, metadata, context));
    }

}
