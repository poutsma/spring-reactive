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

package org.springframework.http.converter.reactive;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.test.TestSubscriber;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.support.JacksonJsonEncoder;
import org.springframework.core.codec.support.Pojo;
import org.springframework.core.codec.support.StringEncoder;
import org.springframework.core.io.buffer.AbstractDataBufferAllocatingTestCase;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.MockServerHttpResponse;
import org.springframework.web.reactive.sse.SseEvent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.converter.reactive.SseHttpMessageConverter.EVENT_STREAM_MIME_TYPE;


/**
 * @author Sebastien Deleuze
 */
public class SseHttpMessageConverterTests extends AbstractDataBufferAllocatingTestCase {

	@Test(expected = IllegalArgumentException.class)
	public void createWithoutEncoder() {
		new SseHttpMessageConverter(Collections.emptyList());
	}

	@Test(expected = IllegalArgumentException.class)
	public void createWithoutStringEncoder() {
		new SseHttpMessageConverter(Arrays.asList(new JacksonJsonEncoder()));
	}

	@Test
	public void nullMimeType() {
		HttpMessageConverter converter = new SseHttpMessageConverter(Arrays.asList(new JacksonJsonEncoder(), new StringEncoder()));
		assertTrue(converter.canWrite(ResolvableType.forClass(Object.class), null));
	}

	@Test
	public void unsupportedMimeType() {
		HttpMessageConverter converter = new SseHttpMessageConverter(Arrays.asList(new JacksonJsonEncoder(), new StringEncoder()));
		assertFalse(converter.canWrite(ResolvableType.forClass(Object.class), MediaType.TEXT_PLAIN));
	}

	@Test
	public void supportedMimeType() {
		HttpMessageConverter converter = new SseHttpMessageConverter(Arrays.asList(new JacksonJsonEncoder(), new StringEncoder()));
		assertTrue(converter.canWrite(ResolvableType.forClass(Object.class), EVENT_STREAM_MIME_TYPE));
	}

	@Test
	public void encodeServerSentEvent() {
		HttpMessageConverter<Object> converter = new SseHttpMessageConverter<Object>(Arrays.asList(new JacksonJsonEncoder(), new StringEncoder()));
		SseEvent event = new SseEvent();
		event.setId("c42");
		event.setName("foo");
		event.setComment("bla\nbla bla\nbla bla bla");
		event.setReconnectTime(123L);
		MockServerHttpResponse outputMessage = new MockServerHttpResponse();
		TestSubscriber<Void> writeSubscriber = new TestSubscriber<>();
		writeSubscriber
				.bindTo(converter.write(Mono.just(event), ResolvableType.forClass(SseEvent.class), EVENT_STREAM_MIME_TYPE, outputMessage))
				.assertNotComplete();

		TestSubscriber<DataBuffer> bodySubscriber = new TestSubscriber<>();
		bodySubscriber.bindTo(outputMessage.getBody()).
				assertNoError().
				assertValuesWith(
						stringConsumer(
								"id:c42\n" +
								"event:foo\n" +
								"retry:123\n" +
								":bla\n:bla bla\n:bla bla bla\n\n")
				);
	}

	@Test
	public void encodeString() {
		HttpMessageConverter converter = new SseHttpMessageConverter(Arrays.asList(new JacksonJsonEncoder(), new StringEncoder()));
		Flux<String> source = Flux.just("foo", "bar");
		MockServerHttpResponse outputMessage = new MockServerHttpResponse();
		TestSubscriber<Void> writeSubscriber = new TestSubscriber<>();
		writeSubscriber
				.bindTo(converter.write(source, ResolvableType.forClass(String.class), EVENT_STREAM_MIME_TYPE, outputMessage))
				.assertNotComplete();
		TestSubscriber<DataBuffer> bodySubscriber = new TestSubscriber<>();
		bodySubscriber
				.bindTo(outputMessage.getBody())
				.assertNoError()
				.assertValuesWith(
						stringConsumer("data:foo\n\n"),
						stringConsumer("data:bar\n\n")
				);
	}

	@Test
	public void encodeMultilineString() {
		HttpMessageConverter converter = new SseHttpMessageConverter(Arrays.asList(new JacksonJsonEncoder(), new StringEncoder()));
		Flux<String> source = Flux.just("foo\nbar", "foo\nbaz");
		MockServerHttpResponse outputMessage = new MockServerHttpResponse();
		TestSubscriber<Void> writeSubscriber = new TestSubscriber<>();
		writeSubscriber
				.bindTo(converter.write(source, ResolvableType.forClass(String.class), EVENT_STREAM_MIME_TYPE, outputMessage))
				.assertNotComplete();
		TestSubscriber<DataBuffer> bodySubscriber = new TestSubscriber<>();
		bodySubscriber
				.bindTo(outputMessage.getBody())
				.assertNoError()
				.assertValuesWith(
						stringConsumer("data:foo\ndata:bar\n\n"),
						stringConsumer("data:foo\ndata:baz\n\n")
				);
	}


	@Test
	public void encodePojo() {
		HttpMessageConverter converter = new SseHttpMessageConverter(Arrays.asList(new JacksonJsonEncoder(), new StringEncoder()));
		Flux<Pojo> source = Flux.just(new Pojo("foofoo", "barbar"), new Pojo("foofoofoo", "barbarbar"));
		MockServerHttpResponse outputMessage = new MockServerHttpResponse();
		TestSubscriber<Void> writeSubscriber = new TestSubscriber<>();
		writeSubscriber
				.bindTo(converter.write(source, ResolvableType.forClass(Pojo.class), EVENT_STREAM_MIME_TYPE, outputMessage))
				.assertNotComplete();
		TestSubscriber<DataBuffer> bodySubscriber = new TestSubscriber<>();
		bodySubscriber
				.bindTo(outputMessage.getBody())
				.assertNoError()
				.assertValuesWith(
						stringConsumer("data:{\"foo\":\"foofoo\",\"bar\":\"barbar\"}\n\n"),
						stringConsumer("data:{\"foo\":\"foofoofoo\",\"bar\":\"barbarbar\"}\n\n")
				);
	}


}
