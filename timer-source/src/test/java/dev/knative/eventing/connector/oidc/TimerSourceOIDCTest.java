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

package dev.knative.eventing.connector.oidc;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import org.citrusframework.TestCaseRunner;
import org.citrusframework.annotations.CitrusResource;
import org.citrusframework.http.endpoint.builder.HttpEndpoints;
import org.citrusframework.http.server.HttpServer;
import org.citrusframework.quarkus.ApplicationPropertiesSupplier;
import org.citrusframework.quarkus.CitrusSupport;
import org.citrusframework.spi.BindToRegistry;
import org.citrusframework.spi.Resources;
import org.citrusframework.util.FileUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.citrusframework.actions.CreateVariablesAction.Builder.createVariables;
import static org.citrusframework.http.actions.HttpActionBuilder.http;

@QuarkusTest
@CitrusSupport(applicationPropertiesSupplier = TimerSourceOIDCTest.class)
public class TimerSourceOIDCTest implements ApplicationPropertiesSupplier {

    @CitrusResource
    private TestCaseRunner tc;

    @BindToRegistry
    public HttpServer knativeBroker = HttpEndpoints.http()
            .server()
            .port(8088)
            .autoStart(true)
            .build();

    @Test
    public void shouldProduceEvents() throws IOException {
        String message = "Hello from timer-source!";

        String token = FileUtils.readToString(Resources.create("oidc/token")).trim();
        tc.given(createVariables().variable("token", token));

        tc.when(
            http().server(knativeBroker)
                    .receive()
                    .post()
                    .message()
                    .body(message)
                    .header("Authorization", "Bearer ${token}")
                    .header("ce-id", "@matches([0-9A-Z]{15}-[0-9]{16})@")
                    .header("ce-type", "dev.knative.eventing.timer")
                    .header("ce-source", "dev.knative.eventing.timer-source")
                    .header("ce-subject", "timer-source")
        );

        tc.then(
            http().server(knativeBroker)
                    .send()
                    .response(HttpStatus.OK)
        );
    }

    @Override
    public Map<String, String> get() {
        Map<String, String> conf = new HashMap<>();
        conf.put("k.sink", "http://localhost:8088");
        conf.put("camel.kamelet.timer-source.message", "Hello from timer-source!");
        conf.put("camel.knative.client.oidc.enabled", "true");
        conf.put("camel.knative.client.oidc.token.path", "oidc/token");
        return conf;
    }
}
