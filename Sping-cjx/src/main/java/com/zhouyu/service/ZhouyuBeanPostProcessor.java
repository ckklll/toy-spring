package com.zhouyu.service;

import com.spring.BeanPostProcessor;
import com.spring.Component;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

@Component
public class ZhouyuBeanPostProcessor implements BeanPostProcessor {
    @Override
    public Object postProcessBeforeInitializing(Object bean, String beanName) {
        if (beanName.equals("userService")){
            System.out.println("初始化前");
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitializing(Object bean, String beanName) {
        if (beanName.equals("userService")){
            System.out.println("userService初始化后");
            Class<?>[] interfaces = bean.getClass().getInterfaces();
            if (interfaces == null || interfaces.length == 0) {
                return bean;
            }
            Object proxyInstance = Proxy.newProxyInstance(BeanPostProcessor.class.getClassLoader(), interfaces, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    System.out.println("代理逻辑");
                    return method.invoke(bean, args);
                }
            });
            return proxyInstance;
        }
        return bean;
    }

    @Override
    public Object getEarlyBeanReference(Object bean, String beanName) {
        // 提前代理：与初始化后一致的代理逻辑（若需要）
        if (beanName.equals("userService")){
            Class<?>[] interfaces = bean.getClass().getInterfaces();
            if (interfaces == null || interfaces.length == 0) {
                return bean;
            }
            return Proxy.newProxyInstance(BeanPostProcessor.class.getClassLoader(), interfaces, (proxy, method, args) -> {
                System.out.println("提前代理逻辑");
                return method.invoke(bean, args);
            });
        }
        return bean;
    }
}
