package com.lee.rokhan.container.context;

import com.lee.rokhan.common.utils.throwable.ThrowConsumer;
import com.lee.rokhan.container.factory.BeanFactory;
import com.lee.rokhan.container.pojo.ComponentProperty;

/**
 * Bean容器
 * 应用上下文
 * @author lichujun
 * @date 2019/6/26 10:25
 */
public interface ApplicationContext extends BeanFactory {

    /**
     * 处理扫描出的所有Class对象
     * @param consumer 处理
     * @throws Throwable 异常
     */
    void processScanClass(ThrowConsumer<Class<?>, Throwable> consumer) throws Throwable;

    /**
     * 处理所有组件的Class对象
     * @param consumer 处理
     * @throws Throwable 异常
     */
    void processAllComponentProperty(ThrowConsumer<ComponentProperty, Throwable> consumer) throws Throwable;

    /**
     * 处理指定组件的Class对象
     * @param consumer 处理
     * @param componentClass 组件的类对象
     * @throws Throwable 异常
     */
    void processComponentProperty(ThrowConsumer<ComponentProperty, Throwable> consumer, Class<?> componentClass) throws Throwable;
}
