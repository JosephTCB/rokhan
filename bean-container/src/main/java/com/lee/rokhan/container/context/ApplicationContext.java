package com.lee.rokhan.container.context;

import java.util.List;

/**
 * Bean容器
 * 应用上下文
 * @author lichujun
 * @date 2019/6/26 10:25
 */
public interface ApplicationContext {

    /**
     * 通过接口类型获取实现的Bean对象的Bean名称
     * @param type 接口类型
     * @return Bean名称数组
     */
    List<String> getBeanNamesByType(Class<?> type);
}
