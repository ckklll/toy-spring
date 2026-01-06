package com.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要 AOP 环绕增强的方法。
 * 使用方式：在目标方法上添加 @Loggable，即可被 AOP 代理拦截并执行前后逻辑。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Loggable {
}