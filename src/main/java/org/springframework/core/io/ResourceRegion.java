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

package org.springframework.core.io;

import org.springframework.util.Assert;

/**
 * @author Arjen Poutsma
 */
public class ResourceRegion {

	private final Resource resource;

	private final long position;

	private final long count;

	public ResourceRegion(Resource resource, int position, int count) {
		Assert.notNull(resource, "'resource' must not be null");
		Assert.isTrue(position >= 0, "'position' must be larger than or equal to 0");
		Assert.isTrue(count >= 0, "'count' must be larger than or equal to 0");
		this.resource = resource;
		this.position = position;
		this.count = count;
	}

	public Resource getResource() {
		return this.resource;
	}

	public long getPosition() {
		return this.position;
	}

	public long getCount() {
		return this.count;
	}
}
