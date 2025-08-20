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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
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
import org.citrusframework.testcontainers.aws2.LocalStackContainer;
import org.citrusframework.testcontainers.aws2.quarkus.LocalStackContainerSupport;
import org.citrusframework.testcontainers.quarkus.ContainerLifecycleListener;
import org.citrusframework.validation.context.DefaultMessageValidationContext;
import org.citrusframework.validation.json.JsonTextMessageValidator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutRuleResponse;
import software.amazon.awssdk.services.eventbridge.model.Target;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import static org.citrusframework.http.actions.HttpActionBuilder.http;

@QuarkusTest
@CitrusSupport
@LocalStackContainerSupport(services = { LocalStackContainer.Service.EVENT_BRIDGE, LocalStackContainer.Service.SQS }, containerLifecycleListener = AwsEventBridgeSinkTest.class)
public class AwsEventBridgeSinkTest implements ContainerLifecycleListener<LocalStackContainer> {

    @CitrusResource
    private TestCaseRunner tc;

    private final String eventBusName = "default";
    private final String eventData = """
            { "message": "Hello from AWS EventBridge!" }
            """;
    private final String sqsQueueName = "event-queue";

    @CitrusResource
    private LocalStackContainer localStackContainer;

    @BindToRegistry
    public HttpClient knativeTrigger = HttpEndpoints.http()
                .client()
                .requestUrl("http://localhost:8081")
                .build();

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
        SqsClient sqsClient = localStackContainer.getClient(LocalStackContainer.Service.SQS);

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

    @Override
    public Map<String, String> started(LocalStackContainer container) {
        EventBridgeClient eventBridgeClient = container.getClient(LocalStackContainer.Service.EVENT_BRIDGE);

        // Add an EventBridge rule on the event
        PutRuleResponse putRuleResponse = eventBridgeClient.putRule(b -> b.name("events-cdc")
                .eventBusName(eventBusName)
                .eventPattern("""
                    {
                        "source": ["knative-connect.aws.s3"],
                        "detail-type": ["Object Created"]
                    }
                    """));

        // Create SQS queue acting as an EventBridge notification endpoint
        SqsClient sqsClient = container.getClient(LocalStackContainer.Service.SQS);
        CreateQueueResponse createQueueResponse = sqsClient.createQueue(b -> b.queueName(sqsQueueName));

        // Modify access policy for the queue just created, so EventBridge rule is allowed to send messages
        String queueUrl = createQueueResponse.queueUrl();
        String queueArn = "arn:aws:sqs:%s:000000000000:%s".formatted(container.getRegion(), sqsQueueName);

        sqsClient.setQueueAttributes(b -> b.queueUrl(queueUrl).attributes(Collections.singletonMap(QueueAttributeName.POLICY, """
                {
                    "Version": "2012-10-17",
                    "Id": "%s/SQSDefaultPolicy",
                    "Statement":
                    [
                        {
                            "Sid": "EventsToMyQueue",
                            "Effect": "Allow",
                            "Principal": {
                                "Service": "events.amazonaws.com"
                            },
                            "Action": "sqs:SendMessage",
                            "Resource": "%s",
                            "Condition": {
                                "ArnEquals": {
                                    "aws:SourceArn": "%s"
                                }
                            }
                        }
                    ]
                }
                """.formatted(queueArn, queueArn, putRuleResponse.ruleArn()))));

        // Add a target for EventBridge rule which will be the SQS Queue just created
        eventBridgeClient.putTargets(b -> b.rule("events-cdc")
                .eventBusName(eventBusName)
                .targets(Target.builder().id("sqs-sub").arn(queueArn).build()));

        Map<String, String> conf = new HashMap<>();
        conf.put("camel.kamelet.aws-eventbridge-sink.accessKey", container.getAccessKey());
        conf.put("camel.kamelet.aws-eventbridge-sink.secretKey", container.getSecretKey());
        conf.put("camel.kamelet.aws-eventbridge-sink.region", container.getRegion());
        conf.put("camel.kamelet.aws-eventbridge-sink.eventbusNameOrArn", eventBusName);
        conf.put("camel.kamelet.aws-eventbridge-sink.uriEndpointOverride", container.getServiceEndpoint().toString());
        conf.put("camel.kamelet.aws-eventbridge-sink.overrideEndpoint", "true");
        conf.put("camel.kamelet.aws-eventbridge-sink.forcePathStyle", "true");

        return conf;
    }
}
