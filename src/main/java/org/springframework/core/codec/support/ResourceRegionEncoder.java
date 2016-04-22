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

import java.io.IOException;
import java.io.InputStream;

import reactor.core.publisher.Flux;

import org.springframework.core.ResolvableType;
import org.springframework.core.io.ResourceRegion;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferAllocator;
import org.springframework.core.io.buffer.support.DataBufferUtils;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StreamUtils;

/**
 * @author Arjen Poutsma
 */
public class ResourceRegionEncoder extends AbstractSingleValueEncoder<ResourceRegion> {

	public static final int DEFAULT_BUFFER_SIZE = StreamUtils.BUFFER_SIZE;

	private final int bufferSize;

	public ResourceRegionEncoder() {
		this(DEFAULT_BUFFER_SIZE);
	}

	public ResourceRegionEncoder(int bufferSize) {
		super(MimeTypeUtils.ALL);
		Assert.isTrue(bufferSize > 0, "'bufferSize' must be larger than 0");
		this.bufferSize = bufferSize;
	}

	@Override
	public boolean canEncode(ResolvableType type, MimeType mimeType, Object... hints) {
		Class<?> clazz = type.getRawClass();
		return (super.canEncode(type, mimeType, hints) &&
				ResourceRegion.class.isAssignableFrom(clazz));
	}

	@Override
	protected Flux<DataBuffer> encode(ResourceRegion region,
			DataBufferAllocator allocator, ResolvableType type, MimeType mimeType,
			Object... hints) throws IOException {
		InputStream is = region.getResource().getInputStream();
		long position = region.getPosition();

		long skipped = is.skip(position);
		if (skipped < position) {
			throw new IOException("Skipped only " + skipped + " bytes out of " +
					position +
					" required.");
		}
		Flux<DataBuffer> result = DataBufferUtils.read(is, allocator, this.bufferSize);

		long count = region.getCount();
		if (count > 0) {
			result = DataBufferUtils.takeUntilByteCount(result, count);
		}
		return result;
	}

}
