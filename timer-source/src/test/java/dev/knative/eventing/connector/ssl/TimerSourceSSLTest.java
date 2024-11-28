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

package dev.knative.eventing.connector.ssl;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;
import org.citrusframework.GherkinTestActionRunner;
import org.citrusframework.annotations.CitrusResource;
import org.citrusframework.http.endpoint.builder.HttpEndpoints;
import org.citrusframework.http.security.HttpSecureConnection;
import org.citrusframework.http.server.HttpServer;
import org.citrusframework.quarkus.CitrusSupport;
import org.citrusframework.spi.BindToRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.citrusframework.http.actions.HttpActionBuilder.http;

@QuarkusTest
@CitrusSupport
@QuarkusTestResource(value = DeploymentTestResource.class, restrictToAnnotatedClass = true, initArgs = {
        @ResourceArg(name = "message", value = "Hello from timer-source!"),
        @ResourceArg(name = "k.sink", value = "https://localhost:8443"),
        @ResourceArg(name = "camel.knative.client.ssl.enabled", value = "true"),
        @ResourceArg(name = "camel.knative.client.ssl.verify.hostname", value = "false"),
        @ResourceArg(name = "camel.knative.client.ssl.key.path", value = "keystore/client.pem"),
        @ResourceArg(name = "camel.knative.client.ssl.key.cert.path", value = "keystore/client.crt"),
        @ResourceArg(name = "camel.knative.client.ssl.truststore.path", value = "keystore/truststore.jks"),
        @ResourceArg(name = "camel.knative.client.ssl.truststore.password", value = "secr3t")
})
public class TimerSourceSSLTest {

    @CitrusResource
    private GherkinTestActionRunner tc;

    @BindToRegistry
    public HttpServer knativeBroker = HttpEndpoints.http()
            .server()
            .port(8080)
            .securePort(8443)
            .secured(HttpSecureConnection.ssl()
                    .keyStore("classpath:keystore/server.jks", "secr3t")
                    .trustStore("classpath:keystore/truststore.jks", "secr3t"))
            .autoStart(true)
            .build();

    @Test
    public void shouldProduceEvents() {
        String message = "Hello from timer-source!";

        tc.when(
            http().server(knativeBroker)
                    .receive()
                    .post()
                    .message()
                    .body(message)
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

}
