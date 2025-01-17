package org.springframework.beans.factory.support;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.TypeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.*;
import org.springframework.core.convert.ConversionService;

import java.lang.reflect.Method;

/**
 * @author derekyi
 * @date 2020/11/22
 */
@Slf4j
public abstract class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory
		implements AutowireCapableBeanFactory {

	private InstantiationStrategy instantiationStrategy = new SimpleInstantiationStrategy();

	@Override
	protected Object createBean(String beanName, BeanDefinition beanDefinition) throws BeansException {
		//如果bean需要代理，则直接返回代理对象
		Object bean = resolveBeforeInstantiation(beanName, beanDefinition);
		if (bean != null) {
			return bean;
		}

		return doCreateBean(beanName, beanDefinition);
	}

	/**
	 * 执行InstantiationAwareBeanPostProcessor的方法，如果bean需要代理，直接返回代理对象
	 *
	 * @param beanName
	 * @param beanDefinition
	 * @return
	 */
	protected Object resolveBeforeInstantiation(String beanName, BeanDefinition beanDefinition) {
		Object bean = applyBeanPostProcessorsBeforeInstantiation(beanDefinition.getBeanClass(), beanName);
		if (bean != null) {
			log.info("创建【{}】之前判断是否需要代理", beanName);
			bean = applyBeanPostProcessorsAfterInitialization(bean, beanName);
		}
		return bean;
	}

	protected Object applyBeanPostProcessorsBeforeInstantiation(Class beanClass, String beanName) {
		for (BeanPostProcessor beanPostProcessor : getBeanPostProcessors()) {
			if (beanPostProcessor instanceof InstantiationAwareBeanPostProcessor) {
				//log.info("初始化之前执行后置处理");
				Object result = ((InstantiationAwareBeanPostProcessor) beanPostProcessor).postProcessBeforeInstantiation(beanClass, beanName);
				if (result != null) {
					return result;
				}
			}
		}

		return null;
	}

	protected Object doCreateBean(String beanName, BeanDefinition beanDefinition) {
		Object bean;
		try {
			log.info("【创建{}】【{}】开始", beanName, beanName);
			bean = createBeanInstance(beanDefinition);
			log.info("【实例化{}】【{}】完成,值为{}", beanName,beanName, bean.getClass());
			//为解决循环依赖问题，将实例化后的bean放进缓存中提前暴露
			if (beanDefinition.isSingleton()) {
				Object finalBean = bean;
				addSingletonFactory(beanName, new ObjectFactory<Object>() {
					@Override
					public Object getObject() throws BeansException {
						return getEarlyBeanReference(beanName, beanDefinition, finalBean);
					}
				});
			}

			//实例化bean之后执行
			boolean continueWithPropertyPopulation = applyBeanPostProcessorsAfterInstantiation(beanName, bean);
			if (!continueWithPropertyPopulation) {
				return bean;
			}
			//在设置bean属性之前，允许BeanPostProcessor修改属性值
			applyBeanPostProcessorsBeforeApplyingPropertyValues(beanName, bean, beanDefinition);
			//为bean填充属性
			applyPropertyValues(beanName, bean, beanDefinition);
			//执行bean的初始化方法和BeanPostProcessor的前置和后置处理方法
			bean = initializeBean(beanName, bean, beanDefinition);
		} catch (Exception e) {
			throw new BeansException("Instantiation of bean failed", e);
		}

		//注册有销毁方法的bean
		registerDisposableBeanIfNecessary(beanName, bean, beanDefinition);

		Object exposedObject = bean;
		if (beanDefinition.isSingleton()) {
			log.info("【初始化{}完成】【{}】，从缓存获取bean", beanName, beanName);
			//如果有代理对象，此处获取代理对象
			exposedObject = getSingleton(beanName);
			addSingleton(beanName, exposedObject);
		}
		log.info("【创建{}】【{}】结束,值为{}", beanName,beanName, exposedObject.getClass());
		return exposedObject;
	}

	protected Object getEarlyBeanReference(String beanName, BeanDefinition beanDefinition, Object bean) {
		log.info("获取bean：{}的早期引用", beanName);
		Object exposedObject = bean;
		for (BeanPostProcessor bp : getBeanPostProcessors()) {
			if (bp instanceof InstantiationAwareBeanPostProcessor) {
				exposedObject = ((InstantiationAwareBeanPostProcessor) bp).getEarlyBeanReference(exposedObject, beanName);
				if (exposedObject == null) {
					return exposedObject;
				}
			}
		}

		return exposedObject;
	}

	/**
	 * bean实例化后执行，如果返回false，不执行后续设置属性的逻辑
	 *
	 * @param beanName
	 * @param bean
	 * @return
	 */
	private boolean applyBeanPostProcessorsAfterInstantiation(String beanName, Object bean) {
		boolean continueWithPropertyPopulation = true;
		for (BeanPostProcessor beanPostProcessor : getBeanPostProcessors()) {
			if (beanPostProcessor instanceof InstantiationAwareBeanPostProcessor) {
				//log.info("{}实例化之后执行后置处理", beanName);
				if (!((InstantiationAwareBeanPostProcessor) beanPostProcessor).postProcessAfterInstantiation(bean, beanName)) {
					continueWithPropertyPopulation = false;
					break;
				}
			}
		}
		return continueWithPropertyPopulation;
	}

	/**
	 * 在设置bean属性之前，允许BeanPostProcessor修改属性值
	 *
	 * @param beanName
	 * @param bean
	 * @param beanDefinition
	 */
	protected void applyBeanPostProcessorsBeforeApplyingPropertyValues(String beanName, Object bean, BeanDefinition beanDefinition) {
		for (BeanPostProcessor beanPostProcessor : getBeanPostProcessors()) {
			if (beanPostProcessor instanceof InstantiationAwareBeanPostProcessor) {
				//log.info("{}实例化后，设置属性之前执行后置处理", beanName);
				PropertyValues pvs = ((InstantiationAwareBeanPostProcessor) beanPostProcessor).postProcessPropertyValues(beanDefinition.getPropertyValues(), bean, beanName);
				if (pvs != null) {
					for (PropertyValue propertyValue : pvs.getPropertyValues()) {
						beanDefinition.getPropertyValues().addPropertyValue(propertyValue);
					}
				}
			}
		}
	}

	/**
	 * 注册有销毁方法的bean，即bean继承自DisposableBean或有自定义的销毁方法
	 *
	 * @param beanName
	 * @param bean
	 * @param beanDefinition
	 */
	protected void registerDisposableBeanIfNecessary(String beanName, Object bean, BeanDefinition beanDefinition) {
		//只有singleton类型bean会执行销毁方法
		if (beanDefinition.isSingleton()) {
			if (bean instanceof DisposableBean || StrUtil.isNotEmpty(beanDefinition.getDestroyMethodName())) {
				registerDisposableBean(beanName, new DisposableBeanAdapter(bean, beanName, beanDefinition));
			}
		}
	}

	/**
	 * 实例化bean
	 *
	 * @param beanDefinition
	 * @return
	 */
	protected Object createBeanInstance(BeanDefinition beanDefinition) {
		return getInstantiationStrategy().instantiate(beanDefinition);
	}

	/**
	 * 为bean填充属性
	 *
	 * @param bean
	 * @param beanDefinition
	 */
	protected void applyPropertyValues(String beanName, Object bean, BeanDefinition beanDefinition) {
		try {
			log.info("【为{}填充属性】【{}】开始", beanName, beanName);
			for (PropertyValue propertyValue : beanDefinition.getPropertyValues().getPropertyValues()) {
				String name = propertyValue.getName();
				Object value = propertyValue.getValue();
				log.info("【为{}设置属性{}】开始，值为{}", beanName, name, value);
				if (value instanceof BeanReference) {
					// beanA依赖beanB，先实例化beanB
					BeanReference beanReference = (BeanReference) value;
					value = getBean(beanReference.getBeanName());
				} else {
					//类型转换
					Class<?> sourceType = value.getClass();
					Class<?> targetType = (Class<?>) TypeUtil.getFieldType(bean.getClass(), name);
					ConversionService conversionService = getConversionService();
					if (conversionService != null) {
						if (conversionService.canConvert(sourceType, targetType)) {
							value = conversionService.convert(value, targetType);
						}
					}
				}
				log.info("【为{}设置属性{}】结束，值为{}", beanName, name, value);
				//通过反射设置属性
				BeanUtil.setFieldValue(bean, name, value);
			}
			log.info("【为{}填充属性】【{}】结束", beanName, beanName);
		} catch (Exception ex) {
			throw new BeansException("Error setting property values for bean: " + beanName, ex);
		}
	}

	protected Object initializeBean(String beanName, Object bean, BeanDefinition beanDefinition) {
		log.info("【初始化{}】【{}】开始,值为{}", beanName, beanName, bean.getClass());
		if (bean instanceof BeanFactoryAware) {
			log.info("是aware接口，设置beanFactory");
			((BeanFactoryAware) bean).setBeanFactory(this);
		}

		//执行BeanPostProcessor的前置处理
		Object wrappedBean = applyBeanPostProcessorsBeforeInitialization(bean, beanName);

		try {
			log.info("调用【{}】初始化方法", beanName);
			invokeInitMethods(beanName, wrappedBean, beanDefinition);
		} catch (Throwable ex) {
			throw new BeansException("Invocation of init method of bean[" + beanName + "] failed", ex);
		}

		//执行BeanPostProcessor的后置处理
		wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
		log.info("【初始化{}】【{}】结束,值为{}", beanName, beanName, bean.getClass());
		return wrappedBean;
	}

	@Override
	public Object applyBeanPostProcessorsBeforeInitialization(Object existingBean, String beanName)
			throws BeansException {
		Object result = existingBean;
		for (BeanPostProcessor processor : getBeanPostProcessors()) {
			//log.info("{}执行初始化之前的后置处理", beanName);
			Object current = processor.postProcessBeforeInitialization(result, beanName);
			if (current == null) {
				return result;
			}
			result = current;
		}
		return result;
	}

	@Override
	public Object applyBeanPostProcessorsAfterInitialization(Object existingBean, String beanName)
			throws BeansException {
		Object result = existingBean;
		for (BeanPostProcessor processor : getBeanPostProcessors()) {
			//log.info("{}执行初始化之后的后置处理", beanName);
			Object current = processor.postProcessAfterInitialization(result, beanName);
			if (current == null) {
				return result;
			}
			result = current;
		}
		return result;
	}

	/**
	 * 执行bean的初始化方法
	 *
	 * @param beanName
	 * @param bean
	 * @param beanDefinition
	 * @throws Throwable
	 */
	protected void invokeInitMethods(String beanName, Object bean, BeanDefinition beanDefinition) throws Throwable {
		if (bean instanceof InitializingBean) {
			((InitializingBean) bean).afterPropertiesSet();
		}
		String initMethodName = beanDefinition.getInitMethodName();
		if (StrUtil.isNotEmpty(initMethodName)) {
			Method initMethod = ClassUtil.getPublicMethod(beanDefinition.getBeanClass(), initMethodName);
			if (initMethod == null) {
				throw new BeansException("Could not find an init method named '" + initMethodName + "' on bean with name '" + beanName + "'");
			}
			initMethod.invoke(bean);
		}
	}

	public InstantiationStrategy getInstantiationStrategy() {
		return instantiationStrategy;
	}

	public void setInstantiationStrategy(InstantiationStrategy instantiationStrategy) {
		this.instantiationStrategy = instantiationStrategy;
	}
}
