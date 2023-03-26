package com.cookie.lkernel.spi;

import java.lang.annotation.*;

/**
 * Spi注解
 *
 * @author cookie
 * @since 2023-03-26 17:34
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Spi {

    String value() default "";
}
