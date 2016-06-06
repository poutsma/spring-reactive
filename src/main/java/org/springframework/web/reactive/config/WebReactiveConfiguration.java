/*
 * Copyright 2002-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.web.reactive.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import reactor.core.converter.DependencyUtils;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.codec.Decoder;
import org.springframework.core.codec.Encoder;
import org.springframework.core.codec.support.ByteBufferDecoder;
import org.springframework.core.codec.support.ByteBufferEncoder;
import org.springframework.core.codec.support.JacksonJsonDecoder;
import org.springframework.core.codec.support.JacksonJsonEncoder;
import org.springframework.core.codec.support.Jaxb2Decoder;
import org.springframework.core.codec.support.Jaxb2Encoder;
import org.springframework.core.codec.support.JsonObjectDecoder;
import org.springframework.core.codec.support.StringDecoder;
import org.springframework.core.codec.support.StringEncoder;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.convert.support.ReactiveStreamsToCompletableFutureConverter;
import org.springframework.core.convert.support.ReactiveStreamsToRxJava1Converter;
import org.springframework.format.Formatter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.reactive.CodecHttpMessageConverter;
import org.springframework.http.converter.reactive.HttpMessageConverter;
import org.springframework.http.converter.reactive.ResourceHttpMessageConverter;
import org.springframework.util.ClassUtils;
import org.springframework.web.reactive.accept.RequestedContentTypeResolver;
import org.springframework.web.reactive.accept.RequestedContentTypeResolverBuilder;
import org.springframework.web.reactive.result.SimpleHandlerAdapter;
import org.springframework.web.reactive.result.SimpleResultHandler;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.reactive.result.method.annotation.ResponseBodyResultHandler;
import org.springframework.web.reactive.result.view.ViewResolutionResultHandler;
import org.springframework.web.reactive.result.view.ViewResolver;

/**
 * The main class for Spring Web Reactive configuration.
 *
 * <p>Import directly or extend and override protected methods to customize.
 *
 * @author Rossen Stoyanchev
 */
@Configuration @SuppressWarnings("unused")
public class WebReactiveConfiguration implements ApplicationContextAware {

	private static final ClassLoader classLoader = WebReactiveConfiguration.class.getClassLoader();

	private static final boolean jackson2Present =
			ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", classLoader) &&
					ClassUtils.isPresent("com.fasterxml.jackson.core.JsonGenerator", classLoader);

	private static final boolean jaxb2Present =
			ClassUtils.isPresent("javax.xml.bind.Binder", classLoader);


	private PathMatchConfigurer pathMatchConfigurer;

	private List<HttpMessageConverter<?>> messageConverters;

	private ApplicationContext applicationContext;


	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}


	@Bean
	public RequestMappingHandlerMapping requestMappingHandlerMapping() {
		RequestMappingHandlerMapping mapping = createRequestMappingHandlerMapping();
		mapping.setOrder(0);
		mapping.setContentTypeResolver(mvcContentTypeResolver());

		PathMatchConfigurer configurer = getPathMatchConfigurer();
		if (configurer.isUseSuffixPatternMatch() != null) {
			mapping.setUseSuffixPatternMatch(configurer.isUseSuffixPatternMatch());
		}
		if (configurer.isUseRegisteredSuffixPatternMatch() != null) {
			mapping.setUseRegisteredSuffixPatternMatch(configurer.isUseRegisteredSuffixPatternMatch());
		}
		if (configurer.isUseTrailingSlashMatch() != null) {
			mapping.setUseTrailingSlashMatch(configurer.isUseTrailingSlashMatch());
		}
		if (configurer.getPathMatcher() != null) {
			mapping.setPathMatcher(configurer.getPathMatcher());
		}
		if (configurer.getPathHelper() != null) {
			mapping.setPathHelper(configurer.getPathHelper());
		}

		return mapping;
	}

	/**
	 * Override to plug a sub-class of {@link RequestMappingHandlerMapping}.
	 */
	protected RequestMappingHandlerMapping createRequestMappingHandlerMapping() {
		return new RequestMappingHandlerMapping();
	}

	@Bean
	public RequestedContentTypeResolver mvcContentTypeResolver() {
		RequestedContentTypeResolverBuilder builder = new RequestedContentTypeResolverBuilder();
		builder.mediaTypes(getDefaultMediaTypeMappings());
		configureRequestedContentTypeResolver(builder);
		return builder.build();
	}

	/**
	 * Override to configure media type mappings.
	 * @see RequestedContentTypeResolverBuilder#mediaTypes(Map)
	 */
	protected Map<String, MediaType> getDefaultMediaTypeMappings() {
		Map<String, MediaType> map = new HashMap<>();
		if (jackson2Present) {
			map.put("json", MediaType.APPLICATION_JSON);
		}
		return map;
	}

	/**
	 * Override to configure how the requested content type is resolved.
	 */
	protected void configureRequestedContentTypeResolver(RequestedContentTypeResolverBuilder builder) {
	}

	/**
	 * Callback for building the {@link PathMatchConfigurer}. This method is
	 * final, use {@link #configurePathMatching} to customize path matching.
	 */
	protected final PathMatchConfigurer getPathMatchConfigurer() {
		if (this.pathMatchConfigurer == null) {
			this.pathMatchConfigurer = new PathMatchConfigurer();
			configurePathMatching(this.pathMatchConfigurer);
		}
		return this.pathMatchConfigurer;
	}

	/**
	 * Override to configure path matching options.
	 */
	public void configurePathMatching(PathMatchConfigurer configurer) {
	}

	@Bean
	public RequestMappingHandlerAdapter requestMappingHandlerAdapter() {
		RequestMappingHandlerAdapter adapter = createRequestMappingHandlerAdapter();

		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();
		addArgumentResolvers(resolvers);
		if (!resolvers.isEmpty()) {
			adapter.setCustomArgumentResolvers(resolvers);
		}

		adapter.setMessageConverters(getMessageConverters());
		adapter.setConversionService(mvcConversionService());

		return adapter;
	}

	/**
	 * Override to plug a sub-class of {@link RequestMappingHandlerAdapter}.
	 */
	protected RequestMappingHandlerAdapter createRequestMappingHandlerAdapter() {
		return new RequestMappingHandlerAdapter();
	}

	/**
	 * Provide custom argument resolvers without overriding the built-in ones.
	 */
	protected void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
	}

	/**
	 * Main method to access message converters to use for decoding
	 * controller method arguments and encoding return values.
	 * <p>Use {@link #configureMessageConverters} to configure the list or
	 * {@link #extendMessageConverters} to add in addition to the default ones.
	 */
	protected final List<HttpMessageConverter<?>> getMessageConverters() {
		if (this.messageConverters == null) {
			this.messageConverters = new ArrayList<>();
			configureMessageConverters(this.messageConverters);
			if (this.messageConverters.isEmpty()) {
				addDefaultHttpMessageConverters(this.messageConverters);
			}
			extendMessageConverters(this.messageConverters);
		}
		return this.messageConverters;
	}

	/**
	 * Override to configure the message converters to use for decoding
	 * controller method arguments and encoding return values.
	 * <p>If no converters are specified, default will be added via
	 * {@link #addDefaultHttpMessageConverters}.
	 * @param converters a list to add converters to, initially an empty
	 */
	protected void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
	}

	/**
	 * Adds default converters that sub-classes can call from
	 * {@link #configureMessageConverters(List)}.
	 */
	protected final void addDefaultHttpMessageConverters(List<HttpMessageConverter<?>> converters) {
		converters.add(converter(new ByteBufferEncoder(), new ByteBufferDecoder()));
		converters.add(converter(new StringEncoder(), new StringDecoder()));
		converters.add(new ResourceHttpMessageConverter());
		if (jaxb2Present) {
			converters.add(converter(new Jaxb2Encoder(), new Jaxb2Decoder()));
		}
		if (jackson2Present) {
			JsonObjectDecoder objectDecoder = new JsonObjectDecoder();
			converters.add(converter(new JacksonJsonEncoder(), new JacksonJsonDecoder(objectDecoder)));
		}
	}

	private static <T> HttpMessageConverter<T> converter(Encoder<T> encoder, Decoder<T> decoder) {
		return new CodecHttpMessageConverter<>(encoder, decoder);
	}

	/**
	 * Override this to modify the list of converters after it has been
	 * configured, for example to add some in addition to the default ones.
	 */
	protected void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
	}

	// TODO: switch to DefaultFormattingConversionService

	@Bean
	public GenericConversionService mvcConversionService() {
		GenericConversionService service = new GenericConversionService();
		addFormatters(service);
		return service;
	}

	// TODO: switch to FormatterRegistry

	/**
	 * Override to add custom {@link Converter}s and {@link Formatter}s.
	 * <p>By default this method method registers:
	 * <ul>
	 * <li>{@link ReactiveStreamsToCompletableFutureConverter}
	 * <li>{@link ReactiveStreamsToRxJava1Converter}
	 * </ul>
	 */
	protected void addFormatters(ConverterRegistry registry) {
		registry.addConverter(new ReactiveStreamsToCompletableFutureConverter());
		if (DependencyUtils.hasRxJava1()) {
			registry.addConverter(new ReactiveStreamsToRxJava1Converter());
		}
	}

	@Bean
	public SimpleHandlerAdapter simpleHandlerAdapter() {
		return new SimpleHandlerAdapter();
	}

	@Bean
	public ResponseBodyResultHandler responseBodyResultHandler() {
		return new ResponseBodyResultHandler(getMessageConverters(), mvcConversionService());
	}

	@Bean
	public SimpleResultHandler simpleResultHandler() {
		return new SimpleResultHandler(mvcConversionService());
	}

	@Bean
	public ViewResolutionResultHandler viewResolutionResultHandler() {
		ViewResolverRegistry registry = new ViewResolverRegistry(this.applicationContext);
		configureViewResolvers(registry);
		List<ViewResolver> resolvers = registry.getViewResolvers();
		ViewResolutionResultHandler handler = new ViewResolutionResultHandler(resolvers, mvcConversionService());
		handler.setDefaultViews(registry.getDefaultViews());
		handler.setOrder(registry.getOrder());
		return handler;

	}

	/**
	 * Override this to configure view resolution.
	 */
	protected void configureViewResolvers(ViewResolverRegistry registry) {
	}

}
