/*
 * Copyright (c) 2020 CREDIT SUISSE Technology and Operations. All Rights Reserved.
 *
 * This software is the proprietary information of CREDIT SUISSE Technology and Operations.
 * Use is subject to license and non-disclosure terms.
 */
package com.github.gekoh.yagen.hibernate;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.Oracle10gDialect;
import org.hibernate.service.ServiceRegistry;

/**
 * {@code DefaultOracleDialect} enhances Oracle10gDialect with BitBooleanType contribution.
 *
 * @author Hanspeter D&uuml;nnenberger
 * @since 28.04.2020
 */
public class DefaultOracle10gDialect extends Oracle10gDialect {

    @Override
    public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        super.contributeTypes(typeContributions, serviceRegistry);
        typeContributions.contributeType(new BitBooleanType());
    }
}
