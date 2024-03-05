package com.github.gekoh.yagen.hibernate.exporter;

import com.github.gekoh.yagen.hibernate.DdlPatchHelper;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.model.relational.Sequence;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.tool.schema.spi.Exporter;

public class SequenceExporterWrapper implements Exporter<Sequence> {

    private final Exporter<Sequence> delegate;

    public SequenceExporterWrapper(Exporter<Sequence> delegate) {
        this.delegate = delegate;
    }

    @Override
    public String[] getSqlCreateStrings(Sequence sequence, Metadata metadata, SqlStringGenerationContext context) {
        return DdlPatchHelper.afterSequenceSqlString(true, sequence, metadata, delegate.getSqlCreateStrings(sequence, metadata, context));
    }

    @Override
    public String[] getSqlDropStrings(Sequence sequence, Metadata metadata, SqlStringGenerationContext context) {
        return DdlPatchHelper.afterSequenceSqlString(false, sequence, metadata, delegate.getSqlDropStrings(sequence, metadata, context));
    }
}
