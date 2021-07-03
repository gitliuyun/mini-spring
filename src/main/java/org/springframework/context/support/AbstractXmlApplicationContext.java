package org.springframework.context.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;

/**
 * @author derekyi
 * @date 2020/11/28
 */
@Slf4j
public abstract class AbstractXmlApplicationContext extends AbstractRefreshableApplicationContext {

	protected void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) {
		log.info("加载bean定义。。。");
		XmlBeanDefinitionReader beanDefinitionReader = new XmlBeanDefinitionReader(beanFactory, this);
		String[] configLocations = getConfigLocations();
		if (configLocations != null) {
			beanDefinitionReader.loadBeanDefinitions(configLocations);
		}
	}

	protected abstract String[] getConfigLocations();
}
