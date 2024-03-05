package com.github.gekoh.yagen.hibernate.exporter;

import com.github.gekoh.yagen.hibernate.DdlPatchHelper;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.mapping.Index;
import org.hibernate.tool.schema.spi.Exporter;

public class IndexExporterWrapper implements Exporter<Index> {

    private final Exporter<Index> delegate;

    public IndexExporterWrapper(Exporter<Index> delegate) {
        this.delegate = delegate;
    }

    @Override
    public String[] getSqlCreateStrings(Index index, Metadata metadata, SqlStringGenerationContext context) {
        return DdlPatchHelper.afterIndexSqlString(true, index, metadata, delegate.getSqlCreateStrings(index, metadata, context));
    }

    @Override
    public String[] getSqlDropStrings(Index index, Metadata metadata, SqlStringGenerationContext context) {
        return DdlPatchHelper.afterIndexSqlString(false, index, metadata, delegate.getSqlDropStrings(index, metadata, context));
    }
}
