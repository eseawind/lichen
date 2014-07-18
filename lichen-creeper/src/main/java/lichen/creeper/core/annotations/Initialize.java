package lichen.creeper.core.annotations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;


/**
 * 初始化对象
 * @author shen
 *
 */
@Target({ FIELD })
@Retention(RUNTIME)
@Documented
public @interface Initialize {

}
