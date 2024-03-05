package com.github.gekoh.yagen.hibernate;

import com.github.gekoh.yagen.ddl.DDLGenerator;
import com.github.gekoh.yagen.hibernate.exporter.ForeignKeyExporterWrapper;
import com.github.gekoh.yagen.hibernate.exporter.IndexExporterWrapper;
import com.github.gekoh.yagen.hibernate.exporter.SequenceExporterWrapper;
import com.github.gekoh.yagen.hibernate.exporter.TableExporterWrapper;
import com.github.gekoh.yagen.hibernate.exporter.UniqueKeyExporterWrapper;
import com.github.gekoh.yagen.hibernate.schema.SchemaManagementToolWrapper;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;
import org.hibernate.HibernateException;
import org.hibernate.boot.Metadata;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.dialect.internal.DialectFactoryImpl;
import org.hibernate.engine.jdbc.dialect.spi.DialectFactory;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfoSource;
import org.hibernate.service.spi.ServiceRegistryAwareService;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.hibernate.tool.schema.spi.Exporter;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class YagenDialectFactory implements DialectFactory, ServiceRegistryAwareService {

    private final DialectFactory delegate = new DialectFactoryImpl();

    @Override
    public void injectServices(ServiceRegistryImplementor serviceRegistry) {
        ((ServiceRegistryAwareService) delegate).injectServices(serviceRegistry);
    }

    @Override
    public Dialect buildDialect(Map<String, Object> configValues, DialectResolutionInfoSource resolutionInfoSource) throws HibernateException {
        Dialect dialect = delegate.buildDialect(configValues, resolutionInfoSource);
        if (SchemaManagementToolWrapper.class.getName().equals(configValues.get("hibernate.schema_management_tool"))) {
            DynamicType.Loaded<? extends Dialect> dialectProxyType = new ByteBuddy().subclass(dialect.getClass())
                    .method(ElementMatchers.isDeclaredBy(DDLEnhancerAware.class).or(ElementMatchers.returns(Exporter.class)))
                    .intercept(InvocationHandlerAdapter.of(new DialectInvocationHandler(dialect)))
                    .implement(DDLEnhancerAware.class)
                    .make()
                    .load(dialect.getClass().getClassLoader());
            try {
                return dialectProxyType.getLoaded().getConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            return dialect;
        }
    }

    private static class DialectInvocationHandler implements InvocationHandler {
        private final AtomicReference ddlEnhancer = new AtomicReference(null);
        private final AtomicReference metadata = new AtomicReference(null);
        private final Dialect delegate;

        public DialectInvocationHandler(Dialect delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("initDDLEnhancer".equals(method.getName())) {
                metadata.set(args[1]);
                ddlEnhancer.set(DdlPatchHelper.newDDLEnhancer((DDLGenerator.Profile) args[0], (Metadata) args[1]));
                return null;
            } else if ("getDDLEnhancer".equals(method.getName())) {
                return ddlEnhancer.get();
            } else if ("getMetadata".equals(method.getName())) {
                return metadata.get();
            } else if ("getSequenceExporter".equals(method.getName())) {
                return new SequenceExporterWrapper(delegate.getSequenceExporter());
            } else if ("getForeignKeyExporter".equals(method.getName())) {
                return new ForeignKeyExporterWrapper(delegate.getForeignKeyExporter());
            } else if ("getUniqueKeyExporter".equals(method.getName())) {
                return new UniqueKeyExporterWrapper(delegate.getUniqueKeyExporter());
            } else if ("getTableExporter".equals(method.getName())) {
                return new TableExporterWrapper(delegate.getTableExporter());
            } else if ("getIndexExporter".equals(method.getName())) {
                return new IndexExporterWrapper(delegate.getIndexExporter());
            }
            return method.invoke(delegate, args);
        }
    }
}
