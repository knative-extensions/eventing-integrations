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

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.citrusframework.GherkinTestActionRunner;
import org.citrusframework.annotations.CitrusResource;
import org.citrusframework.context.TestContext;
import org.citrusframework.exceptions.CitrusRuntimeException;
import org.citrusframework.http.endpoint.builder.HttpEndpoints;
import org.citrusframework.http.server.HttpServer;
import org.citrusframework.quarkus.CitrusSupport;
import org.citrusframework.spi.BindToRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;

import static org.citrusframework.http.actions.HttpActionBuilder.http;

@QuarkusTest
@CitrusSupport
@QuarkusTestResource(LocalstackTestResource.class)
public class AwsSqsSourceTest {

    @CitrusResource
    private GherkinTestActionRunner tc;

    private final String sqsData = "Hello from AWS SQS!";
    private final String sqsQueueName = "myqueue";

    @LocalstackTestResource.Injected
    public SqsClient sqsClient;

    @BindToRegistry
    public HttpServer knativeBroker = HttpEndpoints.http()
            .server()
            .port(8080)
            .autoStart(true)
            .build();

    @Test
    public void shouldProduceEvents() {
        tc.given(this::sendSqsEvent);

        tc.when(
            http().server(knativeBroker)
                    .receive()
                    .post()
                    .message()
                    .body(sqsData)
                    .header("ce-id", "@matches([0-9A-Z]{15}-[0-9]{16})@")
                    .header("ce-type", "dev.knative.connector.event.aws-sqs")
                    .header("ce-source", "dev.knative.eventing.aws-sqs-source")
                    .header("ce-subject", "aws-sqs-source")
        );

        tc.then(
            http().server(knativeBroker)
                    .send()
                    .response(HttpStatus.OK)
        );
    }

    private void sendSqsEvent(TestContext context) {
        ListQueuesResponse listQueuesResult = sqsClient.listQueues(b ->
                b.maxResults(100).queueNamePrefix(sqsQueueName));

        String queueUrl = listQueuesResult.queueUrls().stream()
                .filter(url -> url.endsWith("/" + sqsQueueName))
                .findFirst()
                .orElseThrow(() -> new CitrusRuntimeException("Queue %s not found".formatted(sqsQueueName)));

        sqsClient.sendMessage(b -> b.queueUrl(queueUrl).messageBody(sqsData));
    }
}
