package com.github.gekoh.yagen.hibernate.exporter;

import com.github.gekoh.yagen.hibernate.DdlPatchHelper;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.mapping.Table;
import org.hibernate.tool.schema.spi.Exporter;

public class TableExporterWrapper implements Exporter<Table> {

    private final Exporter<Table> delegate;

    public TableExporterWrapper(Exporter<Table> delegate) {
        this.delegate = delegate;
    }

    @Override
    public String[] getSqlCreateStrings(Table table, Metadata metadata, SqlStringGenerationContext context) {
        return DdlPatchHelper.afterTableSqlString(true, table, metadata, delegate.getSqlCreateStrings(table, metadata, context));
    }

    @Override
    public String[] getSqlDropStrings(Table table, Metadata metadata, SqlStringGenerationContext context) {
        return DdlPatchHelper.afterTableSqlString(false, table, metadata, delegate.getSqlDropStrings(table, metadata, context));
    }
}
