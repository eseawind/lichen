package lichen.migration.internal;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.logicalcobwebs.proxool.ProxoolDataSource;
import org.logicalcobwebs.proxool.ProxoolException;
import org.logicalcobwebs.proxool.ProxoolFacade;
import org.logicalcobwebs.proxool.configuration.PropertyConfigurator;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * migrator test
 * @author jcai
 */
public class MigratorTest {
    private static Migrator migrator;

    @Test
    public void test_migrate() throws Throwable {
        migrator.migrate(MigratorOperation.InstallAllMigrations,"com.imageworks.vnp.dao.migrations", false);
    }
    @BeforeClass
    public static void setup() throws ProxoolException {
        String driver_class_name = "org.h2.Driver";
        DatabaseVendor vendor = DatabaseVendor.forDriver(driver_class_name);
        DatabaseAdapter databaseAdapter = DatabaseAdapter.forVendor(vendor, Option.<String>None());
        Properties info = new Properties();
        info.setProperty("jdbc-x.proxool.alias", "test");
        info.setProperty("jdbc-x.proxool.maximum-connection-count", "50");
        info.setProperty("jdbc-x.user", "sa");
        info.setProperty("jdbc-x.proxool.driver-class", "org.h2.Driver");
        info.setProperty("jdbc-x.proxool.driver-url", "jdbc:h2:mem:testdb");

        info.setProperty("jdbc-x.proxool.maximum-connection-lifetime", "18000000000");
        info.setProperty("jdbc-x.proxool.maximum-active-time", "60000000000");

        //configuration proxool database source
        PropertyConfigurator.configure(info);
        //new datasource
        DataSource dataSource = new ProxoolDataSource("test");

        migrator = new Migrator(dataSource, databaseAdapter);
    }
    @AfterClass
    public static void shutdown(){
        ProxoolFacade.shutdown();
    }
}
