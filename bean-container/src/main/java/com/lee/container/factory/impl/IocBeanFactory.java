package com.lee.container.factory.impl;

import com.lee.container.definition.BeanDefinition;
import com.lee.container.factory.BeanFactory;
import com.lee.container.instance.BeanInstance;
import com.lee.container.instance.impl.BeanInstances;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ioc实例工厂
 * 注：屏蔽构造函数，只能通过工厂方法生成对象
 * @author lichujun
 * @date 2019/6/17 11:38
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class IocBeanFactory implements BeanFactory, Cloneable {

    // 考虑并发情况，默认256，防止扩容
    private static final int DEFAULT_SIZE = 256;

    private Map<String, BeanDefinition>  beanDefinitionMap = new ConcurrentHashMap<>(DEFAULT_SIZE);

    private Map<String, Object> beanMap = new ConcurrentHashMap<>(DEFAULT_SIZE);

    @Override
    public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition) throws Exception {
        //参数检查
        Objects.requireNonNull(beanName,"beanName不能为空");
        Objects.requireNonNull(beanDefinition,"beanDefinition不能为空");

        // 校验Bean注册信息是否合法
        if (!beanDefinition.validate()) {
            log.error("Bean名称为[{}]的注册信息不合法");
            throw new Exception("Bean名称为[" + beanName + "]的注册信息不合法");
        }
        // 判断是否已经存在了Bean名称的注册信息，如果有，就停止运行
        else if (containsBeanDefinition(beanName)) {
            log.error("已经存在了Bean名称为[{}]的注册信息", beanName);
            throw new Exception("已经存在了Bean名称为[" + beanName + "]的注册信息");
        }
        else {
            beanDefinitionMap.put(beanName, beanDefinition);
        }
    }

    @Override
    public BeanDefinition getBeanDefinition(String beanName) {
        return beanDefinitionMap.get(beanName);
    }

    @Override
    public boolean containsBeanDefinition(String beanName) {
        return beanDefinitionMap.keySet().contains(beanName);
    }

    @Override
    public Object getBeanByName(String beanName) throws Exception {
        Objects.requireNonNull(beanName, "注册Bean需要输入beanName");
        Object beanObject = beanMap.get(beanName);
        if (beanObject != null) {
            return beanObject;
        }
        BeanDefinition beanDefinition = beanDefinitionMap.get(beanName);
        Objects.requireNonNull(beanDefinition, "beanDefinition不能为空");

        Class<?> beanClass = beanDefinition.getBeanClass();
        // 获取实例生成器
        BeanInstance beanInstance;
        if (beanClass != null) {
               if (StringUtils.isBlank(beanDefinition.getFactoryMethodName())) {
                   // 使用构造函数的实例生成器
                   beanInstance = BeanInstances.getConstructorInstance();
               } else {
                   // 使用工厂方法的实例生成器
                   beanInstance = BeanInstances.getFactoryMethodInstance();
               }
        } else {
            // 使用工厂Bean的实例生成器
            beanInstance = BeanInstances.getFactoryBeanInstance();
        }
        // 实例化对象
        beanObject = beanInstance.instance(beanDefinition);
        // 对象初始化
        doInit(beanObject, beanDefinition);
        // 如果是单例模式，则缓存到Map容器
        if (beanDefinition.isSingleton()) {
            beanMap.put(beanName, beanObject);
        }
        return beanObject;
    }

    /**
     * 做对象初始化工作
     * @param beanObject bean实例
     * @param beanDefinition bean注册信息
     */
    private void doInit(Object beanObject, BeanDefinition beanDefinition) throws Exception {
        if (StringUtils.isNotBlank(beanDefinition.getInitMethodName())) {
            Method method = beanObject.getClass().getDeclaredMethod(beanDefinition.getInitMethodName());
            if (method != null) {
                method.invoke(beanObject);
            }
        }
    }

    @Override
    public Object getBeanByClass(Class<?> classObject) throws Exception {
        Objects.requireNonNull(classObject, "类对象不能为空");
        String className = classObject.getSimpleName();
        String beanName = StringUtils.uncapitalize(className);
        return getBeanByName(beanName);
    }

}
