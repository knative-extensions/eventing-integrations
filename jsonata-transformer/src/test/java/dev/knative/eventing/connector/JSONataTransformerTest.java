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
import org.citrusframework.TestCaseRunner;
import org.citrusframework.annotations.CitrusResource;
import org.citrusframework.http.client.HttpClient;
import org.citrusframework.http.endpoint.builder.HttpEndpoints;
import org.citrusframework.http.server.HttpServer;
import org.citrusframework.quarkus.CitrusSupport;
import org.citrusframework.spi.BindToRegistry;
import org.citrusframework.spi.Resources;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.citrusframework.http.actions.HttpActionBuilder.http;

@QuarkusTest
@CitrusSupport
public class JSONataTransformerTest {

    @CitrusResource
    private TestCaseRunner tc;

    @BindToRegistry
    public HttpClient knativeTrigger = HttpEndpoints.http()
                .client()
                .requestUrl("http://localhost:8081")
                .build();

    @BindToRegistry
    public HttpServer knativeBroker = HttpEndpoints.http()
            .server()
            .port(12345)
            .timeout(5000L)
            .autoStart(true)
            .build();

    @Test
    public void shouldTransformEvents() {
        tc.variable("ce-id", "citrus:randomUUID()");

        tc.when(
            http().client(knativeTrigger)
                    .send()
                    .post("/")
                    .fork(true)
                    .message()
                    .body(Resources.fromClasspath("ce_apiserversource_kubevirt.json"))
        );

        tc.then(
            http().server(knativeBroker)
                    .receive()
                    .post()
                    .message()
                    .body(Resources.fromClasspath("ce_apiserversource_kubevirt_transformed.json"))
                    .header("ce-id", "@ignore@")
                    .header("ce-type", "dev.knative.eventing.transformer")
                    .header("ce-source", "dev.knative.eventing.jsonata-transformer")
                    .header("ce-subject", "jsonata-transformer")
        );

        tc.and(
            http().server(knativeBroker)
                    .send()
                    .response(HttpStatus.OK)
        );

        tc.and(
            http().client(knativeTrigger)
                    .receive()
                    .response(HttpStatus.NO_CONTENT)
        );
    }

}
