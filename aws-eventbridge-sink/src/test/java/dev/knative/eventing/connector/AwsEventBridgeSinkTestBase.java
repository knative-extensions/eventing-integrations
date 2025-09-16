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
import jakarta.inject.Inject;
import org.citrusframework.TestCaseRunner;
import org.citrusframework.annotations.CitrusResource;
import org.citrusframework.context.TestContext;
import org.citrusframework.exceptions.CitrusRuntimeException;
import org.citrusframework.http.client.HttpClient;
import org.citrusframework.http.endpoint.builder.HttpEndpoints;
import org.citrusframework.message.DefaultMessage;
import org.citrusframework.message.Message;
import org.citrusframework.quarkus.CitrusSupport;
import org.citrusframework.spi.BindToRegistry;
import org.citrusframework.validation.context.DefaultMessageValidationContext;
import org.citrusframework.validation.json.JsonTextMessageValidator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import static org.citrusframework.http.actions.HttpActionBuilder.http;

@QuarkusTest
@CitrusSupport
public abstract class AwsEventBridgeSinkTestBase {

    @CitrusResource
    private TestCaseRunner tc;

    private final String eventData = """
            { "message": "Hello from AWS EventBridge!" }
            """;
    protected final String sqsQueueName = "event-queue";

    @BindToRegistry
    public HttpClient knativeTrigger = HttpEndpoints.http()
                .client()
                .requestUrl("http://localhost:8081")
                .build();

    @Inject
    private SqsClient sqsClient;

    @Test
    public void shouldConsumeEvents() {
        tc.given(
            http().client(knativeTrigger)
                    .send()
                    .post("/")
                    .message()
                    .body(eventData)
                    .header("ce-id", "citrus:randomPattern([0-9A-Z]{15}-[0-9]{16})@")
                    .header("ce-type", "dev.knative.eventing.aws.eventbridge")
                    .header("ce-source", "dev.knative.eventing.aws-eventbridge-source")
                    .header("ce-subject", "aws-eventbridge-source")
                    .header("ce-resources-arn", "arn:aws:s3:us-east-1:000000000000:my-bucket")
                    .header("ce-detail-type", "Object Created")
                    .header("ce-event-source", "aws.s3")
        );

        tc.when(
            http().client(knativeTrigger)
                    .receive()
                    .response(HttpStatus.NO_CONTENT)
        );

        tc.then(this::verifySqsEvent);
    }

    private void verifySqsEvent(TestContext context) {
        ListQueuesResponse listQueuesResult = sqsClient.listQueues(b ->
                b.maxResults(100).queueNamePrefix(sqsQueueName));

        String queueUrl = listQueuesResult.queueUrls().stream()
                .filter(url -> url.endsWith("/" + sqsQueueName))
                .findFirst()
                .orElseThrow(() -> new CitrusRuntimeException("Queue %s not found".formatted(sqsQueueName)));

        ReceiveMessageResponse receiveMessageResponse = sqsClient.receiveMessage(b ->
                b.queueUrl(queueUrl));

        Assertions.assertEquals(1, receiveMessageResponse.messages().size());

        JsonTextMessageValidator validator = new JsonTextMessageValidator();
        Message controlMessage = new DefaultMessage("""
                {
                    "version": "0",
                    "id": "@isUUIDv4()@",
                    "detail-type": "Object Created",
                    "source": "knative-connect.aws.s3",
                    "account": "000000000000",
                    "time": "@ignore@",
                    "region": "us-east-1",
                    "resources": [ "arn:aws:s3:us-east-1:000000000000:my-bucket" ],
                    "detail": %s
                }
                """.formatted(eventData));
        validator.validateMessage(new DefaultMessage(receiveMessageResponse.messages().getFirst().body()), controlMessage, context, new DefaultMessageValidationContext());
    }
}
