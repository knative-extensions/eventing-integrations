/*
 * Copyright the original author or authors.
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

package dev.knative.eventing.connector;

import io.quarkus.test.junit.QuarkusTest;
import org.citrusframework.GherkinTestActionRunner;
import org.citrusframework.annotations.CitrusResource;
import org.citrusframework.http.endpoint.builder.HttpEndpoints;
import org.citrusframework.http.server.HttpServer;
import org.citrusframework.quarkus.CitrusSupport;
import org.citrusframework.spi.BindToRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.citrusframework.http.actions.HttpActionBuilder.http;

@QuarkusTest
@CitrusSupport
public class TimerSourceTest {

    @CitrusResource
    private GherkinTestActionRunner tc;

    @BindToRegistry
    public HttpServer knativeBroker = HttpEndpoints.http()
                .server()
                .port(8080)
                .autoStart(true)
                .build();

    @Test
    public void shouldProduceEvents() {
        tc.when(
            http().server(knativeBroker)
                    .receive()
                    .post()
                    .message()
                    .body("Timer source event!")
                    .header("ce-id", "@matches([0-9A-Z]{15}-[0-9]{16})@")
                    .header("ce-type", "dev.knative.connector.event.timer")
                    .header("ce-source", "dev.knative.eventing.timer-source")
                    .header("ce-subject", "timer-source")
        );

        tc.then(
            http().server(knativeBroker)
                    .send()
                    .response(HttpStatus.OK)
        );
    }

}
