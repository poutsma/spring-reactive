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
import java.util.List;
import java.util.Optional;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.ResolvableType;
import org.springframework.core.codec.CodecException;
import org.springframework.core.codec.Encoder;
import org.springframework.core.codec.support.JacksonJsonEncoder;
import org.springframework.core.codec.support.StringEncoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpInputMessage;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.util.Assert;
import org.springframework.web.reactive.sse.SseEvent;

/**
 * Implementation of {@link HttpMessageConverter} that can stream Server-Sent Events
 * response.
 *
 * It allows to write {@code Flux<ServerSentEvent>}, which is Spring Web Reactive equivalent
 * to Spring MVC {@code SseEmitter}.
 *
 * Sending {@code Flux<String>} or {@code Flux<Pojo>} is equivalent to sending
 * {@code Flux<ServerSentEvent>} with the {@code data} property set to the {@code String} or
 * {@code Pojo} value.
 *
 * @author Sebastien Deleuze
 * @see SseEvent
 * @see <a href="https://www.w3.org/TR/eventsource/">Server-Sent Events W3C recommandation</a>
 */
public class SseHttpMessageConverter<T> implements HttpMessageConverter<T> {

	// TODO Create MediaType.EVENT_STREAM
	public final static MediaType EVENT_STREAM_MIME_TYPE = new MediaType("text", "event-stream");

	private List<Encoder<?>> encoders;

	private Encoder<String> stringEncoder;


	/**
	 * Default constructor that creates a new instance configured with {@link StringEncoder}
	 * and {@link JacksonJsonEncoder} encoders.
	 */
	public SseHttpMessageConverter() {
		this(Arrays.asList(new StringEncoder(), new JacksonJsonEncoder()));
	}

	public SseHttpMessageConverter(List<Encoder<?>> encoders) {
		Assert.notNull(encoders, "'encoders' must not be null");
		Assert.isTrue(encoders.size() > 0, "'encoder' list must contain at least one element");
		Optional<Encoder<?>> stringEncoder = encoders
					.stream()
					.filter(e -> e.canEncode(ResolvableType.forClass(String.class), MediaType.TEXT_PLAIN))
					.findFirst();

		if (stringEncoder.isPresent()) {
			this.stringEncoder = (Encoder<String>)stringEncoder.get();
		}
		else {
			throw new IllegalArgumentException("At least one Encoder<String> must be provided");
		}
		this.encoders = encoders;
	}

	@Override
	public boolean canWrite(ResolvableType type, MediaType mediaType) {
		return (mediaType == null ? true : mediaType.isCompatibleWith(EVENT_STREAM_MIME_TYPE));
	}

	@Override
	public List<MediaType> getWritableMediaTypes() {
		return Arrays.asList(EVENT_STREAM_MIME_TYPE);
	}

	@Override
	public Mono<Void> write(Publisher<? extends T> inputStream, ResolvableType type, MediaType contentType, ReactiveHttpOutputMessage outputMessage) {

		DataBufferFactory bufferFactory = outputMessage.bufferFactory();
		Flux<DataBuffer> body = Flux
				.from(inputStream)
				.concatWith(Flux.never()) // Keep the SSE connection open even for cold stream in order to avoid unexpected Browser reconnection
				.concatMap(data -> (SseEvent.class.equals(type.getRawClass()) ? serialize((SseEvent)data, bufferFactory) : serialize(new SseEvent(data), bufferFactory)));

		outputMessage.getHeaders().add("Content-Type", EVENT_STREAM_MIME_TYPE.toString());

		return outputMessage.writeWith(body, chunk -> true);
	}

	private Mono<DataBuffer> serialize(SseEvent event, DataBufferFactory bufferFactory) {
		StringBuilder sb = new StringBuilder();

		if (event.getId() != null) {
			sb.append("id:");
			sb.append(event.getId());
			sb.append("\n");
		}

		if (event.getName() != null) {
			sb.append("event:");
			sb.append(event.getName());
			sb.append("\n");
		}

		if (event.getReconnectTime() != null) {
			sb.append("retry:");
			sb.append(event.getReconnectTime().toString());
			sb.append("\n");
		}

		if (event.getComment() != null) {
			sb.append(":");
			sb.append(event.getComment().replaceAll("\\n", "\n:"));
			sb.append("\n");
		}

		Object data = event.getData();
		Flux<DataBuffer> dataBuffer = Flux.empty();
		MediaType mediaType = (event.getMediaType() == null ?
				(data instanceof String ? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON_UTF8) : event.getMediaType());
		if (data != null) {
			sb.append("data:");
			if (data instanceof String && mediaType.isCompatibleWith(MediaType.TEXT_PLAIN)) {
				sb.append(((String)data).replaceAll("\\n", "\ndata:")).append("\n");
			}
			else {
				Optional<Encoder<?>> encoder = encoders
					.stream()
					.filter(e -> e.canEncode(ResolvableType.forClass(data.getClass()), mediaType))
					.findFirst();

				if (encoder.isPresent()) {
					// TODO Fix generic conversion if possible
					// TODO We should replace '\n' by '\ndata:' to handle multiline JSON correctly
					dataBuffer = ((Encoder<Object>)encoder.get())
							.encode(Mono.just(data), bufferFactory, ResolvableType.forClass(data.getClass()), mediaType)
							.concatWith(encodeString("\n", bufferFactory));
				}
				else {
					throw new CodecException("No suitable encoder found!");
				}
			}
		}

		return Flux
				.concat(
					encodeString(sb.toString(), bufferFactory),
					dataBuffer,
					encodeString("\n", bufferFactory))
				.reduce((buf1, buf2) -> buf1.write(buf2));
	}

	private Flux<DataBuffer> encodeString(String str, DataBufferFactory bufferFactory) {
		return stringEncoder.encode(Mono.just(str), bufferFactory, ResolvableType.forClass(String.class), MediaType.TEXT_PLAIN);
	}

	@Override
	public boolean canRead(ResolvableType type, MediaType mediaType) {
		return false;
	}

	@Override
	public List<MediaType> getReadableMediaTypes() {
		return Collections.emptyList();
	}

	@Override
	public Flux<T> read(ResolvableType type, ReactiveHttpInputMessage inputMessage) {
		throw new UnsupportedOperationException();
	}

}
