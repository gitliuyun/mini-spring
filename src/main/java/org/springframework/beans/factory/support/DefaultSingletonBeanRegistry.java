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
		Object singletonObject = singletonObjects.get(beanName);
		if (singletonObject == null) {
			singletonObject = earlySingletonObjects.get(beanName);
			if (singletonObject == null) {
				ObjectFactory<?> singletonFactory = singletonFactories.get(beanName);
				if (singletonFactory != null) {
					singletonObject = singletonFactory.getObject();
					log.info("三级缓存取到【{}】", beanName);
					//从三级缓存放进二级缓存
					earlySingletonObjects.put(beanName, singletonObject);
					singletonFactories.remove(beanName);
					log.info("将【{}】从三级缓存取出放到二级缓存", beanName);
				} else {
					log.info("三级缓存未拿到【{}】", beanName);
				}
			} else {
				log.info("二级缓存拿到【{}】", beanName);
			}
		} else {
			log.info("一级缓存拿到【{}】", beanName);
		}
		return singletonObject;
	}

	@Override
	public void addSingleton(String beanName, Object singletonObject) {
		singletonObjects.put(beanName, singletonObject);
		log.info(" {} 放入一级缓存，并且从二三级缓存移除", beanName);
		earlySingletonObjects.remove(beanName);
		singletonFactories.remove(beanName);
	}

	protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
		log.info(" {} 放入三级缓存", beanName);
		singletonFactories.put(beanName, singletonFactory);
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
