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
import org.citrusframework.quarkus.CitrusSupport;
import org.citrusframework.spi.BindToRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.citrusframework.http.actions.HttpActionBuilder.http;

@QuarkusTest
@CitrusSupport
public class LogSinkTest {

    @CitrusResource
    private TestCaseRunner tc;

    @BindToRegistry
    public HttpClient knativeTrigger = HttpEndpoints.http()
                .client()
                .requestUrl("http://localhost:8081")
                .build();

    @Test
    public void shouldConsumeEvents() {
        tc.when(
            http().client(knativeTrigger)
                    .send()
                    .post("/")
                    .message()
                    .body("Timer source event!")
                    .header("ce-id", "citrus:randomPattern([0-9A-Z]{15}-[0-9]{16})")
                    .header("ce-type", "dev.knative.eventing.timer")
                    .header("ce-source", "dev.knative.eventing.timer-source")
                    .header("ce-subject", "timer-source")
        );

        tc.then(
            http().client(knativeTrigger)
                    .receive()
                    .response(HttpStatus.NO_CONTENT)
        );
    }

}
