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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.observables.BlockingObservable;
import rx.observers.TestSubscriber;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Arjen Poutsma
 */
public class WriteAndFlushOperatorTests {

	@Test
	public void singleChunk() throws Exception {
		ByteBuf foo = Unpooled.wrappedBuffer("foo".getBytes(UTF_8));
		ByteBuf bar = Unpooled.wrappedBuffer("bar".getBytes(UTF_8));

		Observable<ByteBuf> chunk = Observable.just(foo, bar);
		Observable<Observable<ByteBuf>> chunks = Observable.just(chunk);

		RxNettyServerHttpResponse.WriteAndFlushOperator operator =
				new RxNettyServerHttpResponse.WriteAndFlushOperator(chunks);
		Func1<ByteBuf, Boolean> flushSelector = operator.flushSelector();

		ByteBuf[] expectedBufs = new ByteBuf[]{foo, bar};
		boolean[] expectedFlushes = new boolean[]{false, true};

		Observable<ByteBuf> obs = Observable.create(operator);

		TestSubscriber<ByteBuf> testSubscriber = new TestSubscriber<>();
		obs.subscribe(testSubscriber);
		testSubscriber.assertNoErrors();
		testSubscriber.assertCompleted();

		BlockingObservable<ByteBuf> blockingObs = obs.toBlocking();
		blockingObs.forEach(new Action1<ByteBuf>() {
			int idx = 0;
			@Override
			public void call(ByteBuf buf) {
				if (idx > 1) {
					fail("Too many elements: " + idx);
				}
				assertEquals(expectedBufs[this.idx], buf);
				assertEquals("Invalid flush at " + this.idx, expectedFlushes[this.idx],
						flushSelector.call(buf));
				this.idx++;
			}
		});
	}

	@Test
	public void multipleChunks() throws Exception {
		ByteBuf foo = Unpooled.wrappedBuffer("foo".getBytes(UTF_8));
		ByteBuf bar = Unpooled.wrappedBuffer("bar".getBytes(UTF_8));
		ByteBuf baz = Unpooled.wrappedBuffer("baz".getBytes(UTF_8));
		ByteBuf qux = Unpooled.wrappedBuffer("qux".getBytes(UTF_8));

		Observable<ByteBuf> chunk1 = Observable.just(foo, bar, baz);
		Observable<ByteBuf> chunk2 = Observable.just(qux);
		Observable<Observable<ByteBuf>> chunks = Observable.just(chunk1, chunk2);

		RxNettyServerHttpResponse.WriteAndFlushOperator operator =
				new RxNettyServerHttpResponse.WriteAndFlushOperator(chunks);
		Func1<ByteBuf, Boolean> flushSelector = operator.flushSelector();

		ByteBuf[] expectedBufs = new ByteBuf[]{foo, bar, baz, qux};
		boolean[] expectedFlushes = new boolean[]{false, false, true, true};

		Observable<ByteBuf> obs = Observable.create(operator);

		TestSubscriber<ByteBuf> testSubscriber = new TestSubscriber<>();
		obs.subscribe(testSubscriber);
		testSubscriber.assertNoErrors();
		testSubscriber.assertCompleted();

		BlockingObservable<ByteBuf> blockingObs = obs.toBlocking();
		blockingObs.forEach(new Action1<ByteBuf>() {
			int idx = 0;
			@Override
			public void call(ByteBuf buf) {
				if (idx > 3) {
					fail("Too many elements: " + idx);
				}
				assertEquals(expectedBufs[this.idx], buf);
				assertEquals("Invalid flush at " + this.idx, expectedFlushes[this.idx],
						flushSelector.call(buf));
				this.idx++;
			}
		});
	}

}