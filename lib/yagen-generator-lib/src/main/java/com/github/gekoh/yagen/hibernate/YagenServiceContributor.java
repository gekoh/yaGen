package com.github.gekoh.yagen.hibernate;

import org.hibernate.boot.registry.StandardServiceInitiator;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.jdbc.dialect.spi.DialectFactory;
import org.hibernate.service.spi.ServiceContributor;
import org.hibernate.service.spi.ServiceRegistryImplementor;

import java.util.Map;

public class YagenServiceContributor implements ServiceContributor {

    @Override
    public void contribute(StandardServiceRegistryBuilder serviceRegistryBuilder) {
        serviceRegistryBuilder.addInitiator(new StandardServiceInitiator<DialectFactory>() {
            @Override
            public DialectFactory initiateService(Map<String, Object> configurationValues, ServiceRegistryImplementor registry) {
                return new YagenDialectFactory();
            }

            @Override
            public Class<DialectFactory> getServiceInitiated() {
                return DialectFactory.class;
            }
        });
    }
}
