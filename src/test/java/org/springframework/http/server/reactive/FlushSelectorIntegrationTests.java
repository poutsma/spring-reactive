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

package org.springframework.http.server.reactive;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.function.Predicate;

import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.boot.JettyHttpServer;
import org.springframework.http.server.reactive.boot.RxNettyHttpServer;
import org.springframework.http.server.reactive.boot.TomcatHttpServer;
import org.springframework.http.server.reactive.boot.UndertowHttpServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * @author Arjen Poutsma
 */
public class FlushSelectorIntegrationTests extends AbstractHttpHandlerIntegrationTests {


	@Override
	protected HttpHandler createHttpHandler() {
		return new FlushSelectorHandler();
	}

	@Test
	public void flushSelector() throws  Exception {
		assumeTrue(server instanceof RxNettyHttpServer ||
		server instanceof JettyHttpServer ||
		server instanceof TomcatHttpServer ||
		server instanceof UndertowHttpServer
		);
		RestTemplate restTemplate = new RestTemplate();

		ResponseEntity<String> response = restTemplate
				.getForEntity("http://localhost:{port}", String.class, port);

		assertTrue(response.hasBody());
	}

	private static class FlushSelectorHandler implements HttpHandler {

		@Override
		public Mono<Void> handle(ServerHttpRequest request, ServerHttpResponse response) {
			Flux<DataBuffer> responseBody = Flux.range(1, 1000).
					map(integer -> Integer.toString(integer)).
					map(s -> s.getBytes(StandardCharsets.UTF_8)).
							map(bytes -> {
								DataBuffer dataBuffer = response.bufferFactory()
										.allocateBuffer(bytes.length + 1);
								dataBuffer.write(bytes);
								dataBuffer.write((byte) '\n');
								return dataBuffer;
							});
			Predicate<DataBuffer> flushSelector = new Predicate<DataBuffer>() {
				private boolean result = true;

				@Override
				public boolean test(DataBuffer dataBuffer) {
					result = !result;
					return result;
				}
			};
			return response.writeWith(responseBody, flushSelector);
		}
	}
}
