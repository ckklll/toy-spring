package com.spring;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ApplicationContext {
    private Class configClass;
    private final ConcurrentHashMap<String,Object> singletonObjects = new ConcurrentHashMap<>();//一级缓存
    private final ConcurrentHashMap<String, Object> earlySingletonObjects = new ConcurrentHashMap<>(); // 二级缓存
    private final ConcurrentHashMap<String, ObjectFactory<?>> singletonFactories = new ConcurrentHashMap<>(); // 三级缓存
    private final ConcurrentHashMap<String,BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>();
    private final List<BeanPostProcessor> beanPostProcessorList = new ArrayList<>();
    // 正在创建中的单例集合，用于判断是否需要走早期引用路径
    private final Set<String> creatingBean = ConcurrentHashMap.newKeySet();

    public ApplicationContext(Class configClass) {
        this.configClass = configClass;

        //解析@ComponentScan-->获取扫描路径->扫描->创建beanDefinition对象->加入到map中
        //完成扫描，生成BeanDefinition
        scan(configClass);

        //根据BeanDefinition创建单例bean，加入到单例池中（通过 getBean 触发，支持懒创建与三级缓存读取）
        for (String beanName : beanDefinitionMap.keySet()) {
            BeanDefinition beanDefinition = beanDefinitionMap.get(beanName);
            if (beanDefinition.getScope().equals("singleton")){
                // 使用 getBean 以确保按统一流程创建与缓存管理
                getBean(beanName);
            }
        }
    }

    private Object createBean(String beanName,BeanDefinition beanDefinition) {
        Class clazz = beanDefinition.getClazz();
        // 标记正在创建，确保循环依赖时能走早期引用
        creatingBean.add(beanName);
        try {
            final Object[] instance = new Object[1];
            // 优先构造器注入：若存在 @Autowired 构造器，则解析参数并实例化；否则使用无参构造
            Constructor<?> autowiredCtor = null;
            for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
                if (ctor.isAnnotationPresent(Autowired.class)) {
                    autowiredCtor = ctor;
                    break;
                }
            }
            if (autowiredCtor != null) {
                autowiredCtor.setAccessible(true);
                Class<?>[] paramTypes = autowiredCtor.getParameterTypes();
                Object[] args = new Object[paramTypes.length];
                for (int i = 0; i < paramTypes.length; i++) {
                    args[i] = resolveDependency(paramTypes[i], null);
                }
                instance[0] = autowiredCtor.newInstance(args);
            } else {
                instance[0] = clazz.getDeclaredConstructor().newInstance();
            }
            // 提前暴露，放入三级缓存中，解决循环依赖（调用提前代理机制）
            singletonFactories.put(beanName, () -> {
                Object earlyRef = instance[0];
                for (BeanPostProcessor beanPostProcessor : beanPostProcessorList) {
                    earlyRef = beanPostProcessor.getEarlyBeanReference(earlyRef, beanName);
                }
                return earlyRef;
            });

            // 依赖注入
            // 1. 对属性进行注入（类型优先，名称兜底）
            Field[] declaredFields = clazz.getDeclaredFields();
            for (Field declaredField : declaredFields) {
                if (declaredField.isAnnotationPresent(Autowired.class)) {
                    declaredField.setAccessible(true);
                    Object dependency = resolveDependency(declaredField.getType(), declaredField.getName());
                    declaredField.set(instance[0], dependency);
                }
            }
            // 2. 对方法进行注入（按照参数类型解析依赖）
            Method[] declaredMethods = clazz.getDeclaredMethods();
            for (Method declaredMethod : declaredMethods) {
                if (declaredMethod.isAnnotationPresent(Autowired.class)){
                    declaredMethod.setAccessible(true);
                    Class<?>[] paramTypes = declaredMethod.getParameterTypes();

                    Object[] args = new Object[paramTypes.length];
                    for (int i = 0; i < paramTypes.length; i++) {
                        args[i] = resolveDependency(paramTypes[i], null);
                    }
                    declaredMethod.invoke(instance[0], args);
                }
            }
            // aware回调
            if (instance[0] instanceof BeanNameAware) {
                ((BeanNameAware) instance[0]).setBeanName(beanName);
            }

            for (BeanPostProcessor beanPostProcessor : beanPostProcessorList) {
                instance[0] = beanPostProcessor.postProcessBeforeInitializing(instance[0], beanName);
            }

            //初始化
            if (instance[0] instanceof InitializingBean) {
                ((InitializingBean) instance[0]).afterPropertiesSet();
            }

            for (BeanPostProcessor beanPostProcessor : beanPostProcessorList) {
                instance[0] = beanPostProcessor.postProcessAfterInitializing(instance[0], beanName);
            }

            // 创建完成后，将最终对象放入一级缓存，并清理早期缓存与工厂
            singletonObjects.put(beanName, instance[0]);
            earlySingletonObjects.remove(beanName);
            singletonFactories.remove(beanName);
            return instance[0];
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // 移除正在创建标记，避免脏状态
            creatingBean.remove(beanName);
        }
    }

    //创建Bean的逻辑，如果一二三级缓存都没有命中，就去走创建逻辑
    public Object getBean(String beanName){
        BeanDefinition beanDefinition = beanDefinitionMap.get(beanName);
        if (beanDefinition == null) {
            //不存在该bean
            throw new NullPointerException();
        }
        String scope = beanDefinition.getScope();
        if (scope.equals("singleton")){
            // 1. 一级缓存命中
            Object singleton = singletonObjects.get(beanName);
            if (singleton != null) {
                return singleton;
            }
            // 2. 如果正在创建，则尝试从二级/三级缓存获取早期引用
            if (creatingBean.contains(beanName)) {
                Object early = earlySingletonObjects.get(beanName);
                if (early != null) return early;
                ObjectFactory<?> factory = singletonFactories.get(beanName);
                if (factory != null) {
                    Object earlyRef = factory.getObject();
                    //放入二级缓存
                    earlySingletonObjects.put(beanName, earlyRef);
                    return earlyRef;
                }
            }
            // 3. 未命中则创建
            return createBean(beanName, beanDefinition);
        } else {
            // prototype 直接创建
            return createBean(beanName,beanDefinition);
        }
    }

    //从BeanDefinition中查找依赖，找到之后，就走getBean的逻辑，如果找到多个候选，则尝试名称兜底
    private Object resolveDependency(Class<?> type, String candidateName) {
        // 优先按类型匹配唯一候选
        List<String> matches = new ArrayList<>();
        for (Map.Entry<String, BeanDefinition> entry : beanDefinitionMap.entrySet()) {
            Class<?> candidate = entry.getValue().getClazz();
            if (type.isAssignableFrom(candidate)) {
                matches.add(entry.getKey());
            }
        }
        if (matches.size() == 1) {
            return getBean(matches.get(0));
        }
        if (matches.size() > 1) {
            // 多候选情况下，尝试名称兜底
            if (candidateName != null && beanDefinitionMap.containsKey(candidateName)) {
                return getBean(candidateName);
            }
            throw new RuntimeException("Multiple bean candidates for type: " + type + ", please specify qualifier or unique name");
        }
        // 无类型候选，尝试名称
        if (candidateName != null && beanDefinitionMap.containsKey(candidateName)) {
            return getBean(candidateName);
        }
        throw new RuntimeException("No bean found for type: " + type + " and name: " + candidateName);
    }

    private void scan(Class configClass) {
        //如果配置类被ComponentScan修饰，解析@ComponentScan注解，获取包路径
        if (configClass.isAnnotationPresent(ComponentScan.class)){
            ComponentScan componentScanAnnotation = (ComponentScan) configClass.getDeclaredAnnotation(ComponentScan.class);
            String path = componentScanAnnotation.value();//com.zhouyu.service
            path = path.replace(".", "/");

            ClassLoader appClassLoader = ApplicationContext.class.getClassLoader();
            URL resource = appClassLoader.getResource(path);
            File file = new File(resource.getFile());
            if (file.isDirectory()){
                File[] files = file.listFiles();
                for (File f : files){
                    String fileName = f.getAbsolutePath();
                    if (fileName.endsWith(".class")){
                        //indexOf传递字符串时，返回的是str在该字符串中首次出现的索引位置，如果没有找到，则返回-1
                        String className = fileName.substring(fileName.indexOf("com"),fileName.indexOf(".class"));
                        className = className.replace("\\",".");

                        try {
                            Class<?> aClass = appClassLoader.loadClass(className);
                            //如果这个类被声明为一个Bean
                            if (aClass.isAnnotationPresent(Component.class)){
                                if (BeanPostProcessor.class.isAssignableFrom(aClass)){
                                    BeanPostProcessor beanPostProcessor = (BeanPostProcessor)aClass.getDeclaredConstructor().newInstance();
                                    beanPostProcessorList.add(beanPostProcessor);
                                }
                                //解析类，是单例bean还是prototypeBean
                                Component componentAnnotation = aClass.getDeclaredAnnotation(Component.class);
                                //获取beanName，默认为类名首字母小写
                                String beanName = componentAnnotation.value();
                                if (beanName.isEmpty()){
                                    beanName = Character.toLowerCase(aClass.getSimpleName().charAt(0))+aClass.getSimpleName().substring(1);
                                }

                                BeanDefinition beanDefinition = new BeanDefinition();
                                beanDefinition.setClazz(aClass);

                                if(aClass.isAnnotationPresent(Scope.class)){
                                    String scope = aClass.getDeclaredAnnotation(Scope.class).value();
                                    beanDefinition.setScope(scope);
                                }else {
                                    beanDefinition.setScope("singleton");
                                }
                                beanDefinitionMap.put(beanName,beanDefinition);
                            }
                        } catch (ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        } catch (NoSuchMethodException e) {
                            throw new RuntimeException(e);
                        } catch (InvocationTargetException e) {
                            throw new RuntimeException(e);
                        } catch (InstantiationException e) {
                            throw new RuntimeException(e);
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }
    }
}
