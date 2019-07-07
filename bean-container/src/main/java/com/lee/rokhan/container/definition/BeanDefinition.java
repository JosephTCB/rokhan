package com.lee.rokhan.container.definition;

import com.lee.rokhan.container.factory.BeanFactory;
import com.lee.rokhan.container.pojo.PropertyValue;
import org.apache.commons.lang3.StringUtils;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Bean注册信息
 *
 * @author lichujun
 * @date 2019/6/17 10:58
 */

public interface BeanDefinition {

    /**
     * 单例
     */
    String SCOPE_SINGLETON = "singleton";

    /**
     * 原型
     */
    String SCOPE_PROTOTYPE = "prototype";

    Class<?> getBeanClass();

    void setBeanClass(Class<?> beanClass);

    Class<?> getReturnType();

    void setReturnType(Class<?> returnType);

    String getScope();

    void setScope(String scope);

    boolean isSingleton();

    boolean isPrototype();

    String getFactoryBeanName();

    void setFactoryBeanName(String factoryBeanName);

    String getFactoryMethodName();

    void setFactoryMethodName(String factoryMethodName);

    String getInitMethodName();

    void setInitMethodName(String initMethodName);

    String getDestroyMethodName();

    void setDestroyMethodName(String destroyMethodName);

    List<Object> getArgumentValues();

    void setArgumentValues(List<Object> parameters);

    List<PropertyValue> getPropertyValues();

    //以下4个方法仅供BeanFactory使用，对于prototype,缓存构造方法或工厂方法
    Constructor<?> getConstructor();

    void setConstructor(Constructor<?> constructor);

    Method getFactoryMethod();

    void setFactoryMethod(Method method);

    void addPropertyValue(PropertyValue propertyValue);

    /**
     * 获取方法的参数对象列表
     * @param beanFactory Bean工厂
     * @return 方法的参数对象列表
     * @throws Throwable 异常
     */
    Object[] getArgumentRealValues(BeanFactory beanFactory) throws Throwable;

    default boolean validate() {
        // 验证返回类型不为空
        if (getReturnType() == null) {
            return false;
        }
        // class没指定,工厂bean或工厂method不指定皆为不合法情况
        if (getBeanClass() == null) {
            if (StringUtils.isBlank(getFactoryBeanName()) || StringUtils.isBlank(getFactoryMethodName())) {
                return false;
            }
        }

        // class和工厂bean同时存在不合法
        return getBeanClass() == null || StringUtils.isBlank(getFactoryBeanName());
    }
}
