package org.springframework.beans.factory.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.SingletonBeanRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author derekyi
 * @date 2020/11/22
 */
@Slf4j
public class DefaultSingletonBeanRegistry implements SingletonBeanRegistry {

	//一级缓存
	private Map<String, Object> singletonObjects = new HashMap<>();

	//二级缓存
	private Map<String, Object> earlySingletonObjects = new HashMap<>();

	//三级缓存
	private Map<String, ObjectFactory<?>> singletonFactories = new HashMap<String, ObjectFactory<?>>();

	private final Map<String, DisposableBean> disposableBeans = new HashMap<>();

	@Override
	public Object getSingleton(String beanName) {
		log.info("开始从一级缓存取bean ==={} ", beanName);
		Object singletonObject = singletonObjects.get(beanName);
		if (singletonObject == null) {
			log.info("====一级缓存bean ==={} 未拿到，从二级缓存取 ", beanName);
			singletonObject = earlySingletonObjects.get(beanName);
			if (singletonObject == null) {
				log.info("二级缓存bean ==={} 未拿到，从三级缓存取 ", beanName);
				ObjectFactory<?> singletonFactory = singletonFactories.get(beanName);
				if (singletonFactory != null) {
					log.info("三级缓存取到bean ===={}", beanName);
					singletonObject = singletonFactory.getObject();
					//从三级缓存放进二级缓存
					earlySingletonObjects.put(beanName, singletonObject);
					singletonFactories.remove(beanName);
					log.info("将bean==={}从三级缓存取出放到二级缓存", beanName);
				}
			}
		}
		return singletonObject;
	}

	@Override
	public void addSingleton(String beanName, Object singletonObject) {
		singletonObjects.put(beanName, singletonObject);
		log.info("bean === {} 放入一级缓存", beanName);
		earlySingletonObjects.remove(beanName);
		log.info("bean === {} 从二级缓存移除", beanName);
		singletonFactories.remove(beanName);
		log.info("bean === {} 从三级缓存移除", beanName);
	}

	protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
		singletonFactories.put(beanName, singletonFactory);
		log.info("bean=== {} 放入三级缓存", beanName);
	}

	public void registerDisposableBean(String beanName, DisposableBean bean) {
		disposableBeans.put(beanName, bean);
	}

	public void destroySingletons() {
		Set<String> beanNames = disposableBeans.keySet();
		for (String beanName : beanNames) {
			DisposableBean disposableBean = disposableBeans.remove(beanName);
			try {
				disposableBean.destroy();
			} catch (Exception e) {
				throw new BeansException("Destroy method on bean with name '" + beanName + "' threw an exception", e);
			}
		}
	}
}
