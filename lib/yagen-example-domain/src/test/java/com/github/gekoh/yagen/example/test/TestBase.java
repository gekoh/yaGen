/*
 * TestBase
 * Copyright (c) 2012 CREDIT SUISSE Technology and Operations. All Rights Reserved.
 * This software is the proprietary information of CREDIT SUISSE Technology and Operations.
 * Use is subject to license and non-disclosure terms.
 */
package com.github.gekoh.yagen.example.test;

import com.github.gekoh.yagen.ddl.CreateDDL;
import com.github.gekoh.yagen.ddl.ObjectType;
import com.github.gekoh.yagen.example.ddl.ExampleProfileProvider;
import com.github.gekoh.yagen.hibernate.DDLEnhancerAware;
import com.github.gekoh.yagen.util.DBHelper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.After;
import org.junit.Before;

import java.util.Map;

/**
 * @author Georg Kohlweiss
 */
public abstract class TestBase {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(TestBase.class);
    protected  Map<ObjectType, Map<String, String>> ddlMap;

    protected static EntityManagerFactory emf;

    protected EntityManager em;

    public EntityManagerFactory getEntityManagerFactory() {
        if (emf == null) {
            setupDatabase();
            emf = Persistence.createEntityManagerFactory(getPersistenceUnitName(), null);
        }
        return emf;
    }

    protected abstract String getPersistenceUnitName();

    @Before
    public void setup() {
        em = getEntityManagerFactory().createEntityManager();
        ddlMap = ((ExampleProfileProvider.Profile) ((CreateDDL) ((DDLEnhancerAware) DBHelper.getDialect(em)).getDDLEnhancer()).getProfile()).getRecordedDdl();
    }

    @After
    public void shutdown() {
        if (em != null) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            em.close();
        }
        shutdownDatabase();
    }

    protected void setupDatabase() { }
    protected void shutdownDatabase() {}
    protected abstract String getDbUserName();
}
