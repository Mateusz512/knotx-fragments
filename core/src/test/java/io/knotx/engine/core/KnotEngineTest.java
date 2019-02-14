/*
 * Copyright (C) 2019 Knot.x Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * The code comes from https://github.com/tomaszmichalak/vertx-rx-map-reduce.
 */
package io.knotx.engine.core;

import static io.knotx.engine.core.EntryLogTestHelper.verifyLogEntries;

import com.google.common.collect.Lists;
import io.knotx.engine.api.FragmentEvent;
import io.knotx.engine.api.FragmentEvent.Status;
import io.knotx.engine.api.FragmentEventResult;
import io.knotx.engine.api.KnotFlow;
import io.knotx.engine.api.KnotProcessingFatalException;
import io.knotx.engine.api.TraceableKnotOptions;
import io.knotx.engine.core.EntryLogTestHelper.Operation;
import io.knotx.fragment.Fragment;
import io.knotx.knotengine.core.junit.MockKnotProxy;
import io.knotx.server.api.context.ClientRequest;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.reactivex.core.Vertx;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class KnotEngineTest {

  @Test
  void execute_whenEventWithNoKnot_expectEventWithUnprocessedStatus(
      VertxTestContext testContext, Vertx vertx) throws Throwable {
    // given
    KnotFlow knotFlow = null;

    // when
    verifyExecution(testContext, vertx, knotFlow, events -> {
      // then
      Assertions.assertEquals(1, events.size());
      Assertions.assertEquals(Status.UNPROCESSED, events.get(0).getStatus());
      Assertions.assertTrue(events.get(0).getLog().getJsonArray("operations").isEmpty());
    });
  }

  @Test
  void execute_whenEventWithInvalidAddressInKnotFlow_expectEventWithUnprocessedStatus(
      VertxTestContext testContext, Vertx vertx) throws Throwable {
    // given
    KnotFlow knotFlow = new KnotFlow("invalidAddress", Collections.emptyMap());

    // when
    verifyExecution(testContext, vertx, knotFlow, events -> {
      // then
      Assertions.assertEquals(1, events.size());
      Assertions.assertEquals(Status.UNPROCESSED, events.get(0).getStatus());
      Assertions.assertTrue(events.get(0).getLog().getJsonArray("operations").isEmpty());
    });
  }

  @Test
  void execute_whenEventAndNotProcessingKnot_expectEventWithSkippedLogEntryAndUnprocessedStatus(
      VertxTestContext testContext, Vertx vertx) throws Throwable {
    // given
    createNotProcessingKnot(vertx, "aAddress");
    KnotFlow knotFlow = new KnotFlow("aAddress", Collections.emptyMap());

    // when
    verifyExecution(testContext, vertx, knotFlow, events -> {
      // then
      Assertions.assertEquals(1, events.size());
      Assertions.assertEquals(Status.UNPROCESSED, events.get(0).getStatus());
      Assertions.assertTrue(
          verifyLogEntries(events.get(0).getLog(), Arrays.asList(
              Operation.of("aAddress", "RECEIVED"),
              Operation.of("aAddress", "SKIPPED")
          )));
    });
  }

  @Test
  void execute_whenEventAndProcessingKnot_expectEventWithProcessedLogEntriesAndSuccessStatus(
      VertxTestContext testContext, Vertx vertx) throws Throwable {
    // given
    createSuccessKnot(vertx, "aAddress", "next");
    KnotFlow knotFlow = new KnotFlow("aAddress", Collections.emptyMap());

    // when
    verifyExecution(testContext, vertx, knotFlow, events -> {
      // then
      Assertions.assertEquals(1, events.size());
      Assertions.assertEquals(Status.SUCCESS, events.get(0).getStatus());
      Assertions.assertTrue(
          verifyLogEntries(events.get(0).getLog(), Arrays.asList(
              Operation.of("aAddress", "RECEIVED"),
              Operation.of("aAddress", "PROCESSED")
          )));
    });
  }

  @Test
  void execute_whenEventAndFailingKnot_expectEventWithErrorLogEntryAndFailureStatus(
      VertxTestContext testContext, Vertx vertx) throws Throwable {
    // given
    createFailingKnot(vertx, "bAddress", false);
    KnotFlow knotFlow = new KnotFlow("bAddress", Collections.emptyMap());

    // when
    verifyExecution(testContext, vertx, knotFlow, events -> {
      // then
      Assertions.assertEquals(1, events.size());
      Assertions.assertEquals(Status.FAILURE, events.get(0).getStatus());
      Assertions.assertTrue(
          verifyLogEntries(events.get(0).getLog(), Arrays.asList(
              Operation.of("bAddress", "RECEIVED"),
              Operation.of("bAddress", "ERROR")
          )));
    });
  }

  @Test
  void execute_whenEventAndFailingKnotWithFatalException_expectEngineFailure(
      VertxTestContext testContext, Vertx vertx) throws Throwable {
    // given
    createFailingKnot(vertx, "aAddress", true);
    KnotFlow knotFlow = new KnotFlow("aAddress", Collections.emptyMap());

    // when
    // then
    verifyFailingSingle(testContext, vertx, knotFlow);
  }

  @Test
  void execute_whenEventAndTwoProcessingKnots_expectEventProcessedLogEntriesAndSuccessStatus(
      VertxTestContext testContext, Vertx vertx) throws Throwable {
    // given
    createSuccessKnot(vertx, "aAddress", "next");
    createSuccessKnot(vertx, "bAddress", null);
    KnotFlow knotFlow = new KnotFlow("aAddress",
        Collections.singletonMap("next", new KnotFlow("bAddress", Collections.emptyMap())));

    // when
    verifyExecution(testContext, vertx, knotFlow, events -> {
      // then
      Assertions.assertEquals(1, events.size());
      Assertions.assertEquals(Status.SUCCESS, events.get(0).getStatus());
      Assertions.assertTrue(
          verifyLogEntries(events.get(0).getLog(), Arrays.asList(
              Operation.of("aAddress", "RECEIVED"),
              Operation.of("aAddress", "PROCESSED"),
              Operation.of("bAddress", "RECEIVED"),
              Operation.of("bAddress", "PROCESSED")
          )));
    });
  }

  @Test
  void execute_whenEventAndFailingKnotAndFallbackKnot_expectEventWithErrorAndProcessedLogEntriesAndSuccessStatus(
      VertxTestContext testContext, Vertx vertx) throws Throwable {
    // given
    createFailingKnot(vertx, "aAddress", false);
    createSuccessKnot(vertx, "bAddress", null);
    KnotFlow knotFlow = new KnotFlow("aAddress",
        Collections.singletonMap("error", new KnotFlow("bAddress", Collections.emptyMap())));

    // when
    verifyExecution(testContext, vertx, knotFlow, events -> {
      // then
      Assertions.assertEquals(1, events.size());
      Assertions.assertEquals(Status.SUCCESS, events.get(0).getStatus());
      Assertions.assertTrue(
          verifyLogEntries(events.get(0).getLog(), Arrays.asList(
              Operation.of("aAddress", "RECEIVED"),
              Operation.of("aAddress", "ERROR"),
              Operation.of("bAddress", "RECEIVED"),
              Operation.of("bAddress", "PROCESSED")
          )));
    });
  }

  @Test
  void execute_whenEventAndFailingKnotAndNotProcessingFallbackKnot_expectFragmentEventWithFailureStatus(
      VertxTestContext testContext, Vertx vertx) throws Throwable {
    // given
    createFailingKnot(vertx, "aAddress", false);
    createNotProcessingKnot(vertx, "bAddress");
    KnotFlow knotFlow = new KnotFlow("aAddress",
        Collections.singletonMap("error", new KnotFlow("bAddress", Collections.emptyMap())));

    // when
    verifyExecution(testContext, vertx, knotFlow, events -> {
      // then
      Assertions.assertEquals(1, events.size());
      Assertions.assertEquals(Status.FAILURE, events.get(0).getStatus());
      Assertions.assertTrue(
          verifyLogEntries(events.get(0).getLog(), Arrays.asList(
              Operation.of("aAddress", "RECEIVED"),
              Operation.of("aAddress", "ERROR"),
              Operation.of("bAddress", "RECEIVED"),
              Operation.of("bAddress", "SKIPPED")
          )));
    });
  }

  @Test
  void execute_whenEventAndFailingKnotAndNoFallbackKnot_expectFragmentEventWithFailureStatus(
      VertxTestContext testContext, Vertx vertx) throws Throwable {
    // given
    createFailingKnot(vertx, "aAddress", false);
    KnotFlow knotFlow = new KnotFlow("aAddress",
        Collections.singletonMap("next", new KnotFlow("someAddress", Collections.emptyMap())));

    // when
    verifyExecution(testContext, vertx, knotFlow, events -> {
      //then
      Assertions.assertEquals(1, events.size());
      Assertions.assertEquals(Status.FAILURE, events.get(0).getStatus());
      Assertions.assertTrue(
          verifyLogEntries(events.get(0).getLog(), Arrays.asList(
              Operation.of("aAddress", "RECEIVED"),
              Operation.of("aAddress", "ERROR")
          )));
    });
  }

  @Test
  void execute_whenTwoEventsAndProcessingKnot_expectTwoEventWithSuccessStatus(
      VertxTestContext testContext, Vertx vertx) throws Throwable {
    // given
    createSuccessKnot(vertx, "aAddress", null);
    createSuccessKnot(vertx, "bAddress", null);

    KnotFlow firstKnotFlow = new KnotFlow("aAddress", Collections.emptyMap());
    KnotFlow secondKnotFlow = new KnotFlow("bAddress", Collections.emptyMap());

    // when
    // when
    verifyExecution(testContext, vertx, Lists.newArrayList(firstKnotFlow, secondKnotFlow),
        events -> {
          //then
          Assertions.assertEquals(2, events.size());
          Assertions.assertEquals(Status.SUCCESS, events.get(0).getStatus());
          Assertions.assertTrue(
              verifyLogEntries(events.get(0).getLog(), Arrays.asList(
                  Operation.of("aAddress", "RECEIVED"),
                  Operation.of("aAddress", "PROCESSED")
              )));
          Assertions.assertEquals(Status.SUCCESS, events.get(1).getStatus());
          Assertions.assertTrue(
              verifyLogEntries(events.get(1).getLog(), Arrays.asList(
                  Operation.of("bAddress", "RECEIVED"),
                  Operation.of("bAddress", "PROCESSED")
              )));
        });
  }

  private void createNotProcessingKnot(Vertx vertx, final String address) {
    MockKnotProxy.register(vertx.getDelegate(), address,
        fragmentContext -> Maybe.empty()
    );
  }

  private void createSuccessKnot(Vertx vertx, final String address, final String transition) {
    MockKnotProxy.register(vertx.getDelegate(), address,
        fragmentContext ->
        {
          FragmentEvent fragmentEvent = fragmentContext.getFragmentEvent();
          return Maybe.just(new FragmentEventResult(fragmentEvent, transition));
        }
    );
  }

  private void createFailingKnot(Vertx vertx, final String address, boolean exitOnError) {
    MockKnotProxy
        .register(vertx.getDelegate(), address,
            new TraceableKnotOptions("next", "error", exitOnError),
            fragmentContext -> {
              Fragment anyFragment = new Fragment("body", new JsonObject(), "");
              throw new KnotProcessingFatalException(anyFragment);
            });
  }

  private void verifyExecution(VertxTestContext testContext, Vertx vertx, KnotFlow knotFlow,
      Consumer<List<FragmentEvent>> successConsumer) throws Throwable {
    verifyExecution(testContext, vertx, Collections.singletonList(knotFlow), successConsumer);
  }

  private void verifyExecution(VertxTestContext testContext, Vertx vertx, List<KnotFlow> knotFlow,
      Consumer<List<FragmentEvent>> successConsumer) throws Throwable {
    // prepare
    List<FragmentEvent> events = knotFlow.stream()
        .map(flow -> new FragmentEvent(new Fragment("type", new JsonObject(), "body"), flow))
        .collect(
            Collectors.toList());
    KnotEngine engine = new KnotEngine(vertx,
        new KnotEngineHandlerOptions(Collections.emptyList(), new DeliveryOptions()));

    // execute
    Single<List<FragmentEvent>> execute = engine.execute(events, new ClientRequest());

    // verify
    execute.subscribe(
        onSuccess -> testContext.verify(() -> {
          successConsumer.accept(onSuccess);
          testContext.completeNow();
        }), testContext::failNow);

    Assertions.assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS));
    if (testContext.failed()) {
      throw testContext.causeOfFailure();
    }
  }

  private void verifyFailingSingle(VertxTestContext testContext, Vertx vertx, KnotFlow knotFlow)
      throws Throwable {
    // given
    KnotEngine engine = new KnotEngine(vertx,
        new KnotEngineHandlerOptions(Collections.emptyList(), new DeliveryOptions()));

    // when
    Single<List<FragmentEvent>> execute = engine
        .execute(Collections
                .singletonList(
                    new FragmentEvent(new Fragment("type", new JsonObject(), "body"), knotFlow)),
            new ClientRequest());

    // then
    execute.subscribe(
        onSuccess -> testContext.failNow(new IllegalStateException()),
        error -> testContext.completeNow());

    Assertions.assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS));
    if (testContext.failed()) {
      throw testContext.causeOfFailure();
    }
  }

}