package creeper.core.internal.hibernate;

import creeper.core.models.CreeperModuleDef;
import creeper.core.services.jpa.BaseEntityTestCase;
import creeper.test.dao.EntityBDao;
import creeper.test.entities.EntityB;
import junit.framework.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;

/**
 *
 * @author jcai
 */
public class DateUserTypeTest extends BaseEntityTestCase {
    private Logger logger = LoggerFactory.getLogger(DateUserTypeTest.class);
    @Override
    protected CreeperModuleDef[] getCreeperModules() {
        return new CreeperModuleDef[]{CreeperModuleDef.create("测试","creeper.test"),CreeperModuleDef.create("用户","creeper.user")};
    }
    @Test
    public void testIntDate(){
        EntityBDao dao = registry.getObject(EntityBDao.class,null);
        EntityB b = new EntityB();
        Calendar c = Calendar.getInstance();
        c.set(2011,12,12);
        b.setDate(c.getTime());
        logger.debug("date time:{}",b.getDate());
        dao.save(b);

        EntityB b2 = dao.findOne(b.getId());
        logger.debug("b2 date time:{}",b2.getDate());
        Assert.assertEquals(c.getTimeInMillis(),b2.getDate().getTime());
    }
}
