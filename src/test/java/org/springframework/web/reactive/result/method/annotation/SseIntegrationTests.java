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

package org.springframework.web.reactive.result.method.annotation;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import static org.springframework.web.client.reactive.HttpRequestBuilders.get;
import static org.springframework.web.client.reactive.WebResponseExtractors.bodyStream;
import reactor.core.publisher.Flux;
import reactor.core.test.TestSubscriber;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.codec.support.ByteBufferDecoder;
import org.springframework.core.codec.support.JacksonJsonDecoder;
import org.springframework.core.codec.support.JsonObjectDecoder;
import org.springframework.core.codec.support.StringDecoder;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.convert.support.ReactiveStreamsToCompletableFutureConverter;
import org.springframework.core.convert.support.ReactiveStreamsToRxJava1Converter;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorHttpClientRequestFactory;
import org.springframework.http.converter.reactive.HttpMessageConverter;
import org.springframework.http.converter.reactive.SseHttpMessageConverter;
import org.springframework.http.server.reactive.AbstractHttpHandlerIntegrationTests;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.reactive.WebClient;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.reactive.result.SimpleResultHandler;
import org.springframework.web.reactive.sse.SseEvent;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

/**
 * @author Sebastien Deleuze
 */
public class SseIntegrationTests extends AbstractHttpHandlerIntegrationTests {

	private AnnotationConfigApplicationContext wac;

	private WebClient webClient;

	@Before
	public void setup() throws Exception {
		super.setup();
		this.webClient = new WebClient(new ReactorHttpClientRequestFactory());
		this.webClient.setMessageDecoders(Arrays.asList(
				new ByteBufferDecoder(),
				new StringDecoder(false),
				new JacksonJsonDecoder(new JsonObjectDecoder())));
	}

	@Override
	protected HttpHandler createHttpHandler() {
		this.wac = new AnnotationConfigApplicationContext();
		this.wac.register(TestConfiguration.class);
		this.wac.refresh();

		DispatcherHandler webHandler = new DispatcherHandler();
		webHandler.setApplicationContext(this.wac);

		return WebHttpHandlerBuilder.webHandler(webHandler).build();
	}

	@Test
	public void sseAsString() throws Exception {

		Flux<String> result = this.webClient
				.perform(get("http://localhost:" + port + "/sse/string")
				.accept(new MediaType("text", "event-stream")))
				.extract(bodyStream(String.class));

		TestSubscriber
				.subscribe(result)
				.awaitAndAssertNextValues("data:foo 0\n\n", "data:foo 1\n\n", "data:foo 2\n\n");
	}


	@RestController
	@SuppressWarnings("unused")
	static class SseController {

		@RequestMapping("/sse/string")
		Flux<String> string() {
			return Flux.interval(Duration.ofMillis(100)).map(l -> "foo " + l);
		}

		@RequestMapping("/sse/person")
		Flux<Person> person() {
			return Flux.interval(Duration.ofMillis(100)).map(l -> new Person("foo"));
		}

		@RequestMapping("/sse-raw")
		Flux<SseEvent> sse() {
			return Flux.interval(Duration.ofMillis(100)).map(l -> {
				SseEvent event = new SseEvent();
				event.setId(Long.toString(l));
				event.setData("foo\nbar");
				event.setComment("bar\nbaz");
				return event;
			});
		}

	}

	@Configuration
	@SuppressWarnings("unused")
	static class TestConfiguration {

		private DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();

		@Bean
		public SseController sseController() {
			return new SseController();
		}

		@Bean
		public RequestMappingHandlerMapping handlerMapping() {
			return new RequestMappingHandlerMapping();
		}

		@Bean
		public RequestMappingHandlerAdapter handlerAdapter() {
			RequestMappingHandlerAdapter handlerAdapter = new RequestMappingHandlerAdapter();
			handlerAdapter.setConversionService(conversionService());
			return handlerAdapter;
		}

		@Bean
		public ConversionService conversionService() {
			GenericConversionService service = new GenericConversionService();
			service.addConverter(new ReactiveStreamsToCompletableFutureConverter());
			service.addConverter(new ReactiveStreamsToRxJava1Converter());
			return service;
		}

		@Bean
		public ResponseBodyResultHandler responseBodyResultHandler() {
			List<HttpMessageConverter<?>> converters = Arrays.asList(new SseHttpMessageConverter());
			return new ResponseBodyResultHandler(converters, conversionService());
		}

		@Bean
		public SimpleResultHandler simpleHandlerResultHandler() {
			return new SimpleResultHandler(conversionService());
		}

	}

	private static class Person {

		private String name;

		@SuppressWarnings("unused")
		public Person() {
		}

		public Person(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			Person person = (Person) o;
			return !(this.name != null ? !this.name.equals(person.name) : person.name != null);
		}

		@Override
		public int hashCode() {
			return this.name != null ? this.name.hashCode() : 0;
		}

		@Override
		public String toString() {
			return "Person{" +
					"name='" + name + '\'' +
					'}';
		}
	}

}
