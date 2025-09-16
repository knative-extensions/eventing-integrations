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

package dev.knative.eventing.connector.local;

import dev.knative.eventing.connector.AwsEventBridgeSinkTestBase;
import io.quarkus.test.junit.QuarkusTest;
import org.citrusframework.actions.testcontainers.aws2.AwsService;
import org.citrusframework.testcontainers.aws2.LocalStackContainer;
import org.citrusframework.testcontainers.aws2.quarkus.LocalStackContainerSupport;
import org.citrusframework.testcontainers.quarkus.ContainerLifecycleListener;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutRuleResponse;
import software.amazon.awssdk.services.eventbridge.model.Target;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@QuarkusTest
@LocalStackContainerSupport(services = { AwsService.EVENT_BRIDGE, AwsService.SQS }, containerLifecycleListener = AwsEventBridgeSinkMockedTest.class)
public class AwsEventBridgeSinkMockedTest extends AwsEventBridgeSinkTestBase implements ContainerLifecycleListener<LocalStackContainer> {

    private final String eventBusName = "default";

    @Override
    public Map<String, String> started(LocalStackContainer container) {
        EventBridgeClient eventBridgeClient = container.getClient(AwsService.EVENT_BRIDGE);

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
        SqsClient sqsClient = container.getClient(AwsService.SQS);
        CreateQueueResponse createQueueResponse = sqsClient.createQueue(b -> b.queueName(sqsQueueName));

        // Modify access policy for the queue just created, so EventBridge rule is allowed to send messages
        String queueUrl = createQueueResponse.queueUrl();
        Map<QueueAttributeName, String> queueAttributes = sqsClient.getQueueAttributes(b -> b.queueUrl(queueUrl).attributeNames(QueueAttributeName.QUEUE_ARN)).attributes();
        String queueArn = queueAttributes.get(QueueAttributeName.QUEUE_ARN);

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
        conf.put("camel.kamelet.aws-eventbridge-sink.resourcesArn", "arn:aws:s3:us-east-1:000000000000:my-bucket");
        conf.put("camel.kamelet.aws-eventbridge-sink.eventSource", "aws.s3");
        conf.put("camel.kamelet.aws-eventbridge-sink.detailType", "Object Created");
        conf.put("camel.kamelet.aws-eventbridge-sink.uriEndpointOverride", container.getServiceEndpoint().toString());
        conf.put("camel.kamelet.aws-eventbridge-sink.overrideEndpoint", "true");
        conf.put("camel.kamelet.aws-eventbridge-sink.forcePathStyle", "true");
        conf.put("camel.component.aws2-eventbridge.autowired-enabled", "false");

        conf.put("quarkus.sqs.endpoint-override", container.getServiceEndpoint().toString());
        conf.put("quarkus.sqs.aws.region", container.getRegion());
        conf.put("quarkus.sqs.aws.credentials.type", "static");
        conf.put("quarkus.sqs.aws.credentials.static-provider.access-key-id", container.getAccessKey());
        conf.put("quarkus.sqs.aws.credentials.static-provider.secret-access-key", container.getSecretKey());

        return conf;
    }
}
