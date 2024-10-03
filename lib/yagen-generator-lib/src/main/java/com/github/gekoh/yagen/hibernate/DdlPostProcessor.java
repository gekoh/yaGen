package com.github.gekoh.yagen.hibernate;

import org.hibernate.dialect.Dialect;

public interface DdlPostProcessor {

    String postProcessDDL(String ddl, Dialect dialect);
}
