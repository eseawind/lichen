package creeper.core.internal;

import java.io.Serializable;

import javax.persistence.EntityManager;

import org.apache.tapestry5.ValueEncoder;
import org.apache.tapestry5.ioc.services.PropertyAccess;
import org.apache.tapestry5.ioc.services.PropertyAdapter;
import org.apache.tapestry5.ioc.services.TypeCoercer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * hibernate映射实体ValueEncoder
 * @author shen
 *
 * @param <T>
 */
public class EntityValueEncoder<T> implements ValueEncoder<T> {
	
	@SuppressWarnings("unused")
	private static Logger logger = LoggerFactory.getLogger(EntityValueEncoder.class);
	
	//主键字段
	private final String primaryKey;
	
	//实体类
	private final Class<T> entityClass;
	
	//tapestry ioc 服务：存放所有对于类的封装的ClassPropertyAdapter的服务，提供从Map<Class, ClassPropertyAdapter>,中获取ClassPropertyAdapter等
	//方法,如 ClassPropertyAdapter getAdapter(Class forClass);如果map中没有这个class的话，就调用ClassPropertyAdapter buildAdapter(Class forClass)
	//创建并加入到map，供以后使用。
	//BeanInfo info = Introspector.getBeanInfo(forClass);通过jdk对bean的封装！！！
	private final PropertyAccess propertyAccess;
	
	//tapestry ioc 服务：Tapestry 常常必需强制转换对象的类型。通过强制，我们能转换某些类型的对象为有相似内容的不同类型的新对象：
	//一个常用的例子是强制转换一个string "1"为 integer 1 或 double 1。
	private final TypeCoercer typeCoercer;
	
	//hibernate session
	private final EntityManager entityManager;
	
	//tapestry ioc 服务：对于一个类中某个属性的封装。
	private final PropertyAdapter propertyAdapter;
	
	
	/**
	 * 构造方法注入
	 */
	public EntityValueEncoder(Class<T> entityClass,String primaryKey,
			PropertyAccess propertyAccess,
			TypeCoercer typeCoercer, EntityManager entityManager) {
		this.entityClass = entityClass;
		this.primaryKey = primaryKey;
		this.propertyAccess = propertyAccess;
		this.typeCoercer = typeCoercer;
		this.entityManager = entityManager;
		this.propertyAdapter = this.propertyAccess.getAdapter(this.entityClass).getPropertyAdapter(this.primaryKey);
	}

	/*
	 * (non-Javadoc)
	 * @see org.apache.tapestry5.ValueEncoder#toClient(java.lang.Object)
	 */
	public String toClient(T value) {
//		logger.debug("--"+value);
		if (value == null)
            return null;

        Object id = propertyAdapter.get(value);

        if (null == id){
            return null;
        }
        
        return typeCoercer.coerce(id, String.class);
	}

	/*
	 * (non-Javadoc)
	 * @see org.apache.tapestry5.ValueEncoder#toValue(java.lang.String)
	 */
	@SuppressWarnings("unchecked")
	public T toValue(String clientValue) {
//		logger.debug("=="+clientValue);
		if(null == clientValue)
			return null;
		//根据propertyAdapter.getType()获得的原始字段类型，调用typeCoercer服务将id转换，id可为string，int等类型，所以要转换。
		Object id = typeCoercer.coerce(clientValue, propertyAdapter.getType());
		Serializable ser_id = (Serializable) id;
		T result = (T) entityManager.find(entityClass, ser_id);
		return result;
	}

}
