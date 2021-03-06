package lichen.creeper.core.services.jpa;

import lichen.creeper.core.annotations.CreeperCore;
import lichen.creeper.core.config.CreeperCoreConfig;
import lichen.creeper.core.internal.CreeperModuleManagerImpl;
import lichen.creeper.core.models.CreeperModuleDef;
import lichen.creeper.core.services.CreeperModuleManager;
import lichen.creeper.core.services.db.DatabaseMigrationModule;
import org.apache.shiro.util.ThreadContext;
import org.apache.tapestry5.ioc.Configuration;
import org.apache.tapestry5.ioc.Registry;
import org.apache.tapestry5.ioc.RegistryBuilder;
import org.apache.tapestry5.ioc.ServiceBinder;
import org.apache.tapestry5.ioc.annotations.Contribute;
import org.apache.tapestry5.ioc.annotations.Marker;
import org.hibernate.cfg.Environment;
import org.junit.After;
import org.junit.Before;
import org.logicalcobwebs.proxool.ProxoolException;
import org.logicalcobwebs.proxool.ProxoolFacade;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 基础类，用于对实体和dao的测试
 * @author jcai
 */
public abstract class BaseEntityTestCase {
    protected Registry registry;
    protected Class<?>[] getIocModules(){ return null;}
    protected CreeperModuleDef[] getCreeperModules(){return null;}
    @Before
    public void setup(){
        ThreadContext.put("lichen.creeper.modules", getCreeperModules());
        List<Class<?>> all = new ArrayList<Class<?>>();
        if(getIocModules()!= null){
            all.addAll(Arrays.asList(getIocModules()));
        }
        all.add(DatabaseMigrationModule.class);
        all.add(CreeperJpaModule.class);
        all.add(TestDatabaseModule.class);
        registry = RegistryBuilder.buildAndStartupRegistry(all.toArray(new Class<?>[all.size()]));
        //OpenSession In Thread
        EntityManagerFactory entityManagerFactory = registry.getService(EntityManagerFactory.class);
        EntityManager em = registry.getService(EntityManager.class);
        EntityManagerHolder emHolder = new EntityManagerHolder(em);
        TransactionSynchronizationManager.bindResource(entityManagerFactory, emHolder);
    }
    @After
    public void teardown() throws ProxoolException {
        EntityManagerFactory emf = registry.getService(EntityManagerFactory.class);
        EntityManagerHolder emHolder = (EntityManagerHolder)
                TransactionSynchronizationManager.unbindResource(emf);
        EntityManagerFactoryUtils.closeEntityManager(emHolder.getEntityManager());

        registry.shutdown();
        ProxoolFacade.removeConnectionPool("creeper");
        ProxoolFacade.shutdown();
        ThreadContext.remove("lichen.creeper.modules");
    }
    public static class TestDatabaseModule{
        public static void bind(ServiceBinder binder){
            binder.bind(CreeperModuleManager.class, CreeperModuleManagerImpl.class);
        }
        @Contribute(CreeperModuleManager.class)
        public static void provideTestModule(Configuration<CreeperModuleDef> modules){
            CreeperModuleDef[] testModules = (CreeperModuleDef[]) ThreadContext.get("lichen.creeper.modules");
            if(testModules !=null)
                for(CreeperModuleDef m:testModules){
                    modules.add(m);
                }
        }
        @Marker(CreeperCore.class)
        public static CreeperCoreConfig buildConfig(){
            CreeperCoreConfig config = new CreeperCoreConfig();
            config.db.driverClassName ="org.h2.Driver";
            config.db.url ="jdbc:h2:mem:testdb";
            //config.db.url="jdbc:h2:file:target/test";
            config.db.username = "sa";
           
            CreeperCoreConfig.JpaProperty property = new CreeperCoreConfig.JpaProperty();
            /*property.name = Environment.HBM2DDL_AUTO;
            property.value = "create";
            config.jpaProperties.add(property);
            */

            property = new CreeperCoreConfig.JpaProperty();
            property.name = Environment.SHOW_SQL;
            property.value = "true";
            config.jpaProperties.add(property);

            return config;
        }
    }
}
