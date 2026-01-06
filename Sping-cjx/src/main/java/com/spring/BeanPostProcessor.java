package com.spring;

public interface BeanPostProcessor {
    Object postProcessBeforeInitializing(Object bean,String beanName);
    Object postProcessAfterInitializing(Object bean,String beanName);
    // 提前代理：在实例化后、初始化前，为循环依赖场景提供“早期代理引用”
    Object getEarlyBeanReference(Object bean, String beanName);
}
