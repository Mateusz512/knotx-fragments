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
 */
package io.knotx.fragments.handler.api;

import io.knotx.fragments.handler.api.domain.FragmentContext;
import io.knotx.fragments.handler.api.domain.FragmentResult;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;

@ProxyGen
@VertxGen
public interface Knot extends Action {

  static Knot createProxy(Vertx vertx, String address) {
    return new KnotVertxEBProxy(vertx, address);
  }

  static Knot createProxyWithOptions(Vertx vertx, String address, DeliveryOptions deliveryOptions) {
    return new KnotVertxEBProxy(vertx, address, deliveryOptions);
  }

  // This method is repeated for VertxGen
  void apply(FragmentContext fragmentContext, Handler<AsyncResult<FragmentResult>> result);

}