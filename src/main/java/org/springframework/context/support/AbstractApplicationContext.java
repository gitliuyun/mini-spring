package org.springframework.context.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Collection;
import java.util.Map;

/**
 * 抽象应用上下文
 *
 * @author derekyi
 * @date 2020/11/28
 */
@Slf4j
public abstract class AbstractApplicationContext extends DefaultResourceLoader implements ConfigurableApplicationContext {

	public static final String APPLICATION_EVENT_MULTICASTER_BEAN_NAME = "applicationEventMulticaster";

	public static final String CONVERSION_SERVICE_BEAN_NAME = "conversionService";

	private ApplicationEventMulticaster applicationEventMulticaster;

	@Override
	public void refresh() throws BeansException {
		log.info("开始刷新容器");
		//创建BeanFactory，并加载BeanDefinition
		refreshBeanFactory();
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();

		//添加ApplicationContextAwareProcessor，让继承自ApplicationContextAware的bean能感知bean
		log.info("【1】开始注册ApplicationContextAwareProcessor<");
		beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));
		log.info("【1】结束注册ApplicationContextAwareProcessor>");

		//在bean实例化之前，执行BeanFactoryPostProcessor
		log.info("【2】开始执行BeanFactoryPostProcessor<<");
		invokeBeanFactoryPostProcessors(beanFactory);
		log.info("【2】结束执行BeanFactoryPostProcessor>>");

		//BeanPostProcessor需要提前与其他bean实例化之前注册
		log.info("【3】开始注册BeanPostProcessors<<<");
		registerBeanPostProcessors(beanFactory);
		log.info("【3】结束注册BeanPostProcessors>>>");

		//初始化事件发布者
		log.info("【4】开始初始化事件发布者<<<<");
		initApplicationEventMulticaster();
		log.info("【4】结束开始初始化事件发布者>>>>");

		//注册事件监听器
		log.info("【5】开始注册事件监听器<<<<<");
		registerListeners();
		log.info("【5】结束注册事件监听器>>>>>");

		//注册类型转换器和提前实例化单例bean
		log.info("【7】开始注册类型转换器和实例化单例bean<<<<<");
		finishBeanFactoryInitialization(beanFactory);
		log.info("【7】结束注册类型转换器和实例化单例bean>>>>>");

		log.info("容器刷新完成");
		//发布容器刷新完成事件
		finishRefresh();
	}

	protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
		//设置类型转换器
		if (beanFactory.containsBean(CONVERSION_SERVICE_BEAN_NAME)) {
			log.info("获取类型转换器{}", CONVERSION_SERVICE_BEAN_NAME);
			Object conversionService = beanFactory.getBean(CONVERSION_SERVICE_BEAN_NAME);
			if (conversionService instanceof ConversionService) {
				beanFactory.setConversionService((ConversionService) conversionService);
			}
		}

		//提前实例化单例bean
		beanFactory.preInstantiateSingletons();
	}

	/**
	 * 创建BeanFactory，并加载BeanDefinition
	 *
	 * @throws BeansException
	 */
	protected abstract void refreshBeanFactory() throws BeansException;

	/**
	 * 在bean实例化之前，执行BeanFactoryPostProcessor
	 *
	 * @param beanFactory
	 */
	protected void invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory) {
		log.info("调用BeanFactoryPostProcessors");
		Map<String, BeanFactoryPostProcessor> beanFactoryPostProcessorMap = beanFactory.getBeansOfType(BeanFactoryPostProcessor.class);
		for (BeanFactoryPostProcessor beanFactoryPostProcessor : beanFactoryPostProcessorMap.values()) {
			log.info("调用BeanFactoryPostProcessors的postProcessBeanFactory方法,processor是{}", beanFactoryPostProcessorMap.values());
			beanFactoryPostProcessor.postProcessBeanFactory(beanFactory);
		}
	}

	/**
	 * 注册BeanPostProcessor
	 *
	 * @param beanFactory
	 */
	protected void registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory) {
		log.info("注册BeanPostProcessors");
		Map<String, BeanPostProcessor> beanPostProcessorMap = beanFactory.getBeansOfType(BeanPostProcessor.class);
		for (BeanPostProcessor beanPostProcessor : beanPostProcessorMap.values()) {
			beanFactory.addBeanPostProcessor(beanPostProcessor);
		}
	}

	/**
	 * 初始化事件发布者
	 */
	protected void initApplicationEventMulticaster() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		applicationEventMulticaster = new SimpleApplicationEventMulticaster(beanFactory);
		beanFactory.addSingleton(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, applicationEventMulticaster);
	}

	/**
	 * 注册事件监听器
	 */
	protected void registerListeners() {
		Collection<ApplicationListener> applicationListeners = getBeansOfType(ApplicationListener.class).values();
		for (ApplicationListener applicationListener : applicationListeners) {
			applicationEventMulticaster.addApplicationListener(applicationListener);
		}
	}

	/**
	 * 发布容器刷新完成事件
	 */
	protected void finishRefresh() {
		publishEvent(new ContextRefreshedEvent(this));
	}

	@Override
	public void publishEvent(ApplicationEvent event) {
		applicationEventMulticaster.multicastEvent(event);
	}

	@Override
	public boolean containsBean(String name) {
		return getBeanFactory().containsBean(name);
	}

	@Override
	public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
		return getBeanFactory().getBean(name, requiredType);
	}

	@Override
	public <T> Map<String, T> getBeansOfType(Class<T> type) throws BeansException {
		return getBeanFactory().getBeansOfType(type);
	}

	public <T> T getBean(Class<T> requiredType) throws BeansException {
		return getBeanFactory().getBean(requiredType);
	}

	@Override
	public Object getBean(String name) throws BeansException {
		return getBeanFactory().getBean(name);
	}

	public String[] getBeanDefinitionNames() {
		return getBeanFactory().getBeanDefinitionNames();
	}

	public abstract ConfigurableListableBeanFactory getBeanFactory();

	public void close() {
		doClose();
	}

	public void registerShutdownHook() {
		Thread shutdownHook = new Thread() {
			public void run() {
				doClose();
			}
		};
		Runtime.getRuntime().addShutdownHook(shutdownHook);

	}

	protected void doClose() {
		//发布容器关闭事件
		publishEvent(new ContextClosedEvent(this));

		//执行单例bean的销毁方法
		destroyBeans();
	}

	protected void destroyBeans() {
		getBeanFactory().destroySingletons();
	}
}

