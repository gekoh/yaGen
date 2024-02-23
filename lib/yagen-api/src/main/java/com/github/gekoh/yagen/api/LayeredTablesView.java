package com.github.gekoh.yagen.api;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author Georg Kohlweiss
 */
@Target({TYPE, FIELD})
@Retention(RUNTIME)
public @interface LayeredTablesView {

    String[] keyColumns();

    String[] tableNamesInOrder();

    /**
     * Optional where conditions to allow excluding layer table records e.g. when primary key consists
     * of multiple records and only a part of the PK defines record exclude from layer table.
     * @return when used must provide a value for each entry in {@link #tableNamesInOrder()}
     */
    String[] wheresInOrder() default {};
}