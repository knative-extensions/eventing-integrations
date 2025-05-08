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

import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import org.citrusframework.TestCaseRunner;
import org.citrusframework.annotations.CitrusResource;
import org.citrusframework.context.TestContext;
import org.citrusframework.exceptions.CitrusRuntimeException;
import org.citrusframework.http.endpoint.builder.HttpEndpoints;
import org.citrusframework.http.server.HttpServer;
import org.citrusframework.quarkus.CitrusSupport;
import org.citrusframework.spi.BindToRegistry;
import org.citrusframework.testcontainers.aws2.LocalStackContainer;
import org.citrusframework.testcontainers.aws2.quarkus.LocalStackContainerSupport;
import org.citrusframework.testcontainers.quarkus.ContainerLifecycleListener;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;

import static org.citrusframework.http.actions.HttpActionBuilder.http;

@QuarkusTest
@CitrusSupport
@LocalStackContainerSupport(services = LocalStackContainer.Service.SQS, containerLifecycleListener = AwsSqsSourceTest.class)
public class AwsSqsSourceTest implements ContainerLifecycleListener<LocalStackContainer> {

    @CitrusResource
    private TestCaseRunner tc;

    private final String sqsData = "Hello from AWS SQS!";
    private final String sqsQueueName = "myqueue";

    @CitrusResource
    private LocalStackContainer localStackContainer;

    @BindToRegistry
    public HttpServer knativeBroker = HttpEndpoints.http()
            .server()
            .port(8080)
            .timeout(5000L)
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
                    .header("ce-type", "dev.knative.eventing.aws-sqs")
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
        SqsClient sqsClient = localStackContainer.getClient(LocalStackContainer.Service.SQS);

        ListQueuesResponse listQueuesResult = sqsClient.listQueues(b ->
                b.maxResults(100).queueNamePrefix(sqsQueueName));

        String queueUrl = listQueuesResult.queueUrls().stream()
                .filter(url -> url.endsWith("/" + sqsQueueName))
                .findFirst()
                .orElseThrow(() -> new CitrusRuntimeException("Queue %s not found".formatted(sqsQueueName)));

        sqsClient.sendMessage(b -> b.queueUrl(queueUrl).messageBody(sqsData));
    }

    @Override
    public Map<String, String> started(LocalStackContainer container) {
        // Create SQS queue acting as a SNS notification endpoint
        SqsClient sqsClient = container.getClient(LocalStackContainer.Service.SQS);
        sqsClient.createQueue(b -> b.queueName(sqsQueueName));

        Map<String, String> conf = new HashMap<>();
        conf.put("camel.kamelet.aws-sqs-source.accessKey", container.getAccessKey());
        conf.put("camel.kamelet.aws-sqs-source.secretKey", container.getSecretKey());
        conf.put("camel.kamelet.aws-sqs-source.region", container.getRegion());
        conf.put("camel.kamelet.aws-sqs-source.queueNameOrArn", sqsQueueName);
        conf.put("camel.kamelet.aws-sqs-source.uriEndpointOverride", container.getServiceEndpoint().toString());
        conf.put("camel.kamelet.aws-sqs-source.overrideEndpoint", "true");
        return conf;
    }
}
