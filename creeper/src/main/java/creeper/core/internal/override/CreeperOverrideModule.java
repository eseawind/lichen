package creeper.core.internal.override;

import creeper.core.annotations.CreeperCore;
import org.apache.tapestry5.internal.services.TapestrySessionFactory;
import org.apache.tapestry5.ioc.MappedConfiguration;
import org.apache.tapestry5.ioc.ServiceBinder;
import org.apache.tapestry5.ioc.annotations.Contribute;
import org.apache.tapestry5.ioc.annotations.Local;
import org.apache.tapestry5.ioc.services.ServiceOverride;
import org.apache.tapestry5.services.ComponentEventLinkEncoder;
import org.apache.tapestry5.services.Request;
import org.apache.tapestry5.services.ValidationDecoratorFactory;

/**
 * 覆盖tapestry内置服务的模块
 * @author jcai
 */
public class CreeperOverrideModule {
    public static void bind(ServiceBinder binder){
        binder.bind(ValidationDecoratorFactory.class,CreeperValidationDecoratorFactory.class).withId("CreeperValidationDecoratorFactory").withMarker(CreeperCore.class);
        binder.bind(ComponentEventLinkEncoder.class,CreeperLinkEncoder.class).withId("CreeperLinkEncoder").withMarker(CreeperCore.class);
        //集成session共用
        binder.bind(TapestrySessionFactory.class, TapestrySessionFactoryWithShiroSession.class).withId("CreeperTapestrySessionFactory");
    }
    @Contribute(ServiceOverride.class)
    public static void overrideDecorator(MappedConfiguration<Class,Object> configuration,
                                         @Local ValidationDecoratorFactory factory,
                                         @Local ComponentEventLinkEncoder encoder,
                                         @Local TapestrySessionFactory sessionFactory
                                         ){
        configuration.add(ValidationDecoratorFactory.class, factory);
        configuration.add(ComponentEventLinkEncoder.class, encoder);
        configuration.add(TapestrySessionFactory.class, sessionFactory);
    }
}

