package com.lee.rokhan.container.context.impl;

import com.alibaba.fastjson.JSONArray;
import com.lee.rokhan.common.utils.ScanUtils;
import com.lee.rokhan.container.annotation.*;
import com.lee.rokhan.container.constants.ApplicationContextConstants;
import com.lee.rokhan.container.context.ApplicationContext;
import com.lee.rokhan.container.definition.BeanDefinition;
import com.lee.rokhan.container.definition.impl.IocBeanDefinition;
import com.lee.rokhan.container.factory.impl.IocBeanFactory;
import com.lee.rokhan.container.pojo.BeanReference;
import com.lee.rokhan.container.pojo.PropertyValue;
import com.lee.rokhan.container.resource.YamlResource;
import com.lee.rokhan.container.resource.impl.YamlResourceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Bean容器
 * @author lichujun
 * @date 2019/6/25 16:30
 */
@Slf4j
public class AnnotationApplicationContext extends IocBeanFactory implements ApplicationContext {

    /**
     * 扫描包扫出来的所有类
     */
    private final Set<Class<?>> classSet;

    /**
     * 接口类型所实现的Bean对象的Bean名称
     */
    private final Map<Class<?>, List<String>> typeToBeanNames = new HashMap<>();

    /**
     * 配置文件
     */
    private final YamlResource yamlResource;

    /**
     * 初始化classSet和yamlResource
     * @throws IOException 扫描class文件IO异常
     */
    @SuppressWarnings("unchecked")
    public AnnotationApplicationContext() throws IOException {
        yamlResource = new YamlResourceImpl();
        // 获取需要扫描的包
        JSONArray scanPackages = yamlResource.getYamlNodeArrayResource(ApplicationContextConstants.SCAN_PACKAGES);

        // 如果扫描的包为空，则classSet设为空集合
        if (scanPackages == null || scanPackages.isEmpty()) {
            classSet = Collections.EMPTY_SET;
        }
        // 如果扫描的包不为空，则扫描出所有class
        else {
            classSet = new HashSet<>();
            for (Object scanPackage : scanPackages) {
                Set<Class<?>> packageClassSet = ScanUtils.getClasses((String) scanPackage);
                if (CollectionUtils.isNotEmpty(packageClassSet)) {
                    classSet.addAll(packageClassSet);
                }
            }
        }
    }


    /**
     * 初始化Ioc容器
     */
    public void init() {
        if (CollectionUtils.isEmpty(classSet)) {
            return;
        }
        // 1.注册BeanDefinition
        for (Class<?> clazz : classSet) {
            registerBeanDefinitionWithoutDI(clazz);
            // 注册依赖关系
            registerDI(clazz);
        }
    }

    @Override
    public List<String> getBeanNamesByType(Class<?> type) {
        if (MapUtils.isEmpty(typeToBeanNames)) {
            for (Class<?> clazz : classSet) {
                String beanName = getComponentName(clazz);
                if (StringUtils.isBlank(beanName)) {
                    continue;
                }
                // 获取Bean对象实现的所有接口
                Class<?>[] typeInterfaces = Optional.ofNullable(clazz)
                        .map(Class::getInterfaces)
                        .orElse(null);
                if (typeInterfaces == null || ArrayUtils.isEmpty(typeInterfaces)) {
                    continue;
                }
                // 将接口和它的所有实现注册到容器中
                for (Class<?> typeInterface : typeInterfaces) {
                    List<String> beanNames = typeToBeanNames.get(typeInterface);
                    if (beanNames == null) {
                        beanNames = new ArrayList<>();
                        typeToBeanNames.put(type, beanNames);
                    }
                    beanNames.add(beanName);
                }
            }
        }
        return typeToBeanNames.get(type);
    }

    /**
     * 注册Bean的注册信息，不包含依赖关系
     * @param clazz 类对象
     */
    private void registerBeanDefinitionWithoutDI(Class<?> clazz) {
        if (clazz == null) {
            return;
        }
        String beanName = getComponentName(clazz);
        if (StringUtils.isBlank(beanName)) {
            return;
        }
        // 通过构造函数实例化Bean对象注册Bean信息
        BeanDefinition beanDefinition = new IocBeanDefinition();
        beanDefinition.setBeanClass(clazz);
        Constructor[] constructors = clazz.getDeclaredConstructors();
        if (ArrayUtils.isNotEmpty(constructors)) {
            if (constructors.length > 1) {
                throw new RuntimeException(clazz.getSimpleName() + "存在多个构造函数");
            }
            Constructor constructor = constructors[0];
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (ArrayUtils.isNotEmpty(parameterTypes)) {
                List<Object> parameters = getParameterDIValues(parameterTypes);
                beanDefinition.setArgumentValues(parameters);
                beanDefinition.setConstructor(constructor);
            }
        }
        // 注册Bean的信息
        registerBeanDefinition(beanName, beanDefinition);

        // 通过工厂Bean的方法或者静态工厂方法注册Bean信息
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            Bean bean = method.getDeclaredAnnotation(Bean.class);
            if (bean == null) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            String beanValue = bean.value();
            if (StringUtils.isBlank(beanValue)) {
                beanValue = StringUtils.uncapitalize(returnType.getSimpleName());
            }
            // 静态工厂方法注册Bean信息
            if (Modifier.isStatic(method.getModifiers())) {
                BeanDefinition factoryMethodBeanDefinition = new IocBeanDefinition();
                factoryMethodBeanDefinition.setBeanClass(clazz);
                factoryMethodBeanDefinition.setFactoryMethodName(method.getName());
                registerBeanDefinition(beanValue, factoryMethodBeanDefinition);
            }
            // 工厂Bean的方法注册Bean信息
            else {
                BeanDefinition methodBeanDefinition = new IocBeanDefinition();
                methodBeanDefinition.setFactoryBeanName(beanName);
                methodBeanDefinition.setFactoryMethodName(method.getName());
                registerBeanDefinition(beanValue, methodBeanDefinition);
            }
        }
    }

    /**
     * 注册Bean的依赖关系
     * @param clazz 类对象
     */
    private void registerDI(Class<?> clazz) {
        if (clazz == null) {
            return;
        }
        String beanName = getComponentName(clazz);
        if (StringUtils.isBlank(beanName)) {
            return;
        }
        // 获取Bean名称的注册信息
        BeanDefinition iocBeanDefinition = getBeanDefinition(beanName);
        if (iocBeanDefinition == null) {
            return;
        }
        Field[] fields = clazz.getDeclaredFields();
        if (ArrayUtils.isNotEmpty(fields)) {
            for (Field field : fields) {
                // 如果是接口，则通过接口注册依赖信息
                if (field.getType().isInterface()) {
                    registerInterfaceDI(field, iocBeanDefinition);
                }
                // 如果是类，则通过类注册依赖信息
                else {
                    registerClassDI(field, iocBeanDefinition);
                }
            }
        }
        Method[] methods = clazz.getDeclaredMethods();
        if (ArrayUtils.isNotEmpty(methods)) {
            for (Method method : methods) {
                Bean bean = method.getDeclaredAnnotation(Bean.class);
                if (bean == null) {
                    continue;
                }
                String beanValue = bean.value();
                if (StringUtils.isBlank(beanValue)) {
                    beanValue = StringUtils.uncapitalize(method.getReturnType().getSimpleName());
                }
                BeanDefinition beanDefinition = getBeanDefinition(beanValue);
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (ArrayUtils.isNotEmpty(parameterTypes)) {
                    List<Object> parameters = getParameterDIValues(parameterTypes);
                    beanDefinition.setArgumentValues(parameters);
                }
            }
        }
    }

    /**
     * 通过参数列表类型获取参数的注入的对象
     * @param parameterTypes 参数列表类型
     * @return 参数列表对象
     */
    private List<Object> getParameterDIValues(Class<?>[] parameterTypes) {
        List<Object> parameters = new ArrayList<>();
        for (Class<?> parameterType : parameterTypes) {
            String parameterBeanName;
            Autowired autowired = parameterType.getDeclaredAnnotation(Autowired.class);
            if (autowired == null || StringUtils.isBlank(autowired.value())) {
                parameterBeanName = getDIValueByType(parameterType);
            }
            else {
                parameterBeanName = autowired.value();
            }
            BeanReference beanReference = new BeanReference(parameterBeanName);
            parameters.add(beanReference);
        }
        return parameters;
    }


    /**
     * 通过接口注册依赖信息
     * @param field field字段
     * @param beanDefinition Bean的注册信息
     */
    private void registerInterfaceDI(Field field, BeanDefinition beanDefinition) {
        Autowired autowired = field.getDeclaredAnnotation(Autowired.class);
        if (autowired == null) {
            return;
        }
        String propertyBeanName = autowired.value();
        if (StringUtils.isBlank(propertyBeanName)) {
            propertyBeanName = getDIValueByType(field.getType());
        }
        BeanReference beanReference = new BeanReference(propertyBeanName);
        PropertyValue propertyValue = new PropertyValue(field.getName(), beanReference);
        beanDefinition.addPropertyValue(propertyValue);
    }

    /**
     * 通过类注册依赖信息
     * @param field field字段
     * @param beanDefinition Bean的注册信息
     */
    private void registerClassDI(Field field, BeanDefinition beanDefinition) {
        Autowired autowired = field.getDeclaredAnnotation(Autowired.class);
        if (autowired == null) {
            return;
        }
        String propertyBeanName = autowired.value();
        if (StringUtils.isBlank(propertyBeanName)) {
            propertyBeanName = StringUtils.uncapitalize(field.getType().getSimpleName());
        }
        BeanReference beanReference = new BeanReference(propertyBeanName);
        PropertyValue propertyValue = new PropertyValue(field.getName(), beanReference);
        beanDefinition.addPropertyValue(propertyValue);
    }

    /**
     * 通过类型获取依赖注入的Bean名称
     * @param type 类型
     * @return Bean名称
     */
    private String getDIValueByType(Class<?> type) {
        // 获取接口实现的所有Bean对象的bean名称
        List<String> beanNames = getBeanNamesByType(type);
        // 没有实现的Bean对象，则抛出异常，停止运行
        if (CollectionUtils.isEmpty(beanNames)) {
            throw new RuntimeException("找不到"+ type.getSimpleName() + "的Bean");
        }
        // 如果有多个实现，但是没有指定Bean名称，则抛出异常，停止运行
        else if (beanNames.size() > 1){
            throw new RuntimeException("该接口"+ type.getSimpleName() + "有多个实现，请指定Bean名称");
        }
        return beanNames.get(0);
    }

    /**
     * 获取该类的Bean名称
     * @param clazz 类对象
     * @return Bean名称
     */
    private String getComponentName(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }
        String beanName = null;
        if (clazz.isAnnotationPresent(Component.class)) {
            Component component = clazz.getDeclaredAnnotation(Component.class);
            beanName = component.value();
        }
        if (clazz.isAnnotationPresent(Controller.class)) {
            Controller controller = clazz.getDeclaredAnnotation(Controller.class);
            beanName = controller.value();
        }
        if (clazz.isAnnotationPresent(Service.class)) {
            Service service = clazz.getDeclaredAnnotation(Service.class);
            beanName = service.value();
        }
        if (clazz.isAnnotationPresent(Repository.class)) {
            Repository repository = clazz.getDeclaredAnnotation(Repository.class);
            beanName = repository.value();
        }
        if (beanName == null) {
            return null;
        }
        if (StringUtils.isBlank(beanName)) {
            return StringUtils.uncapitalize(clazz.getSimpleName());
        }
        return beanName;
    }

}
