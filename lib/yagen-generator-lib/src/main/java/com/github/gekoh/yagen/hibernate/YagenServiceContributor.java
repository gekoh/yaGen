package com.github.gekoh.yagen.hibernate;

import org.hibernate.boot.registry.StandardServiceInitiator;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.config.internal.ConfigurationServiceImpl;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.engine.jdbc.dialect.spi.DialectFactory;
import org.hibernate.service.spi.ServiceContributor;
import org.hibernate.service.spi.ServiceRegistryImplementor;

import java.util.Map;

import static org.hibernate.cfg.MappingSettings.COLUMN_ORDERING_STRATEGY;

public class YagenServiceContributor implements ServiceContributor {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(YagenServiceContributor.class);

    private static final String COLUMN_ORDERING_STRATEGY_REQUIRED_VALUE = "legacy";

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

        serviceRegistryBuilder.addInitiator(new StandardServiceInitiator<ConfigurationService>() {
            @Override
            public ConfigurationService initiateService(Map<String, Object> map, ServiceRegistryImplementor serviceRegistryImplementor) {

                Object cos = map.get(COLUMN_ORDERING_STRATEGY);

                if (cos != null && !cos.equals(COLUMN_ORDERING_STRATEGY_REQUIRED_VALUE)) {
                    LOG.warn("{} set to {}, timeline views (@Changelog) may not work correctly due to primary key disorder", COLUMN_ORDERING_STRATEGY, cos);
                }
                else if (cos == null) {
                    map.put(COLUMN_ORDERING_STRATEGY, COLUMN_ORDERING_STRATEGY_REQUIRED_VALUE);
                    LOG.info("setting property {}={}, this may be required by timeline views (@Changelog) which rely on primary key order", COLUMN_ORDERING_STRATEGY, COLUMN_ORDERING_STRATEGY_REQUIRED_VALUE);
                }

                return new ConfigurationServiceImpl(map);
            }

            @Override
            public Class<ConfigurationService> getServiceInitiated() {
                return ConfigurationService.class;
            }
        });
    }
}
