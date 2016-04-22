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

package org.springframework.core.codec.support;

import java.nio.charset.StandardCharsets;

import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.test.TestSubscriber;

import org.springframework.core.ResolvableType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceRegion;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;

import static org.junit.Assert.assertTrue;

/**
 * @author Arjen Poutsma
 */
public class ResourceRegionEncoderTests extends AbstractAllocatingTestCase {

	private final ResourceRegionEncoder encoder = new ResourceRegionEncoder();

	@Test
	public void canEncode() throws Exception {
		assertTrue(encoder.canEncode(ResolvableType.forClass(ResourceRegion.class),
				MediaType.TEXT_PLAIN));
		assertTrue(encoder.canEncode(ResolvableType.forClass(ResourceRegion.class),
				MediaType.APPLICATION_JSON));
	}

	@Test
	public void encode() throws Exception {
		byte[] bytes = "foobar".getBytes(StandardCharsets.UTF_8);
		Resource resource = new ByteArrayResource(bytes);
		ResourceRegion region = new ResourceRegion(resource, 2, 3);

		Mono<ResourceRegion> source = Mono.just(region);

		Flux<DataBuffer> output = encoder.encode(source, allocator,
				ResolvableType.forClass(ResourceRegion.class), null);

		TestSubscriber<DataBuffer> testSubscriber = new TestSubscriber<>();
		testSubscriber.bindTo(output).assertNoError().assertComplete()
				.assertValues(stringBuffer("oba"));


	}

}