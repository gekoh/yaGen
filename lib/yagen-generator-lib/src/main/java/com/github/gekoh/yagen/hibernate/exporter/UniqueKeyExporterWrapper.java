package com.github.gekoh.yagen.hibernate.exporter;

import com.github.gekoh.yagen.hibernate.DdlPatchHelper;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.mapping.Constraint;
import org.hibernate.tool.schema.spi.Exporter;

public class UniqueKeyExporterWrapper implements Exporter<Constraint>  {

    private final Exporter<Constraint> delegate;

    public UniqueKeyExporterWrapper(Exporter<Constraint> delegate) {
        this.delegate = delegate;
    }

    @Override
    public String[] getSqlCreateStrings(Constraint constraint, Metadata metadata, SqlStringGenerationContext context) {
        return DdlPatchHelper.afterConstraintSqlString(true, constraint, metadata, delegate.getSqlCreateStrings(constraint, metadata, context));
    }

    @Override
    public String[] getSqlDropStrings(Constraint constraint, Metadata metadata, SqlStringGenerationContext context) {
        return DdlPatchHelper.afterConstraintSqlString(false, constraint, metadata, delegate.getSqlCreateStrings(constraint, metadata, context));
    }
}
