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

import dev.knative.eventing.connector.AwsSnsSinkTestBase;
import io.quarkus.test.junit.QuarkusTest;
import org.citrusframework.actions.testcontainers.aws2.AwsService;
import org.citrusframework.testcontainers.aws2.LocalStackContainer;
import org.citrusframework.testcontainers.aws2.quarkus.LocalStackContainerSupport;
import org.citrusframework.testcontainers.quarkus.ContainerLifecycleListener;
import org.junit.jupiter.api.Assertions;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicResponse;
import software.amazon.awssdk.services.sns.model.SubscribeResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.HashMap;
import java.util.Map;

@QuarkusTest
@LocalStackContainerSupport(services = { AwsService.SNS, AwsService.SQS },
        containerLifecycleListener = AwsSnsSinkMockedTest.class)
public class AwsSnsSinkMockedTest extends AwsSnsSinkTestBase implements ContainerLifecycleListener<LocalStackContainer> {

    @Override
    public Map<String, String> started(LocalStackContainer container) {
        // Create SQS queue acting as a SNS notification endpoint
        SqsClient sqsClient = container.getClient(AwsService.SQS);

        CreateQueueResponse createQueueResponse = sqsClient.createQueue(b -> b.queueName(sqsQueueName));
        String queueUrl = createQueueResponse.queueUrl();
        Map<QueueAttributeName, String> queueAttributes = sqsClient.getQueueAttributes(b -> b.queueUrl(queueUrl).attributeNames(QueueAttributeName.QUEUE_ARN)).attributes();
        String queueArn = queueAttributes.get(QueueAttributeName.QUEUE_ARN);

        // Create SNS topic
        SnsClient snsClient = container.getClient(AwsService.SNS);

        CreateTopicResponse topicResponse = snsClient.createTopic(b -> b.name(snsTopicName));

        // Subscribe SQS Queue to SNS topic as a notification endpoint
        SubscribeResponse subscribeResponse = snsClient.subscribe(b -> b.protocol("sqs")
                .returnSubscriptionArn(true)
                .endpoint(queueArn)
                .topicArn(topicResponse.topicArn()));

        Assertions.assertTrue(subscribeResponse.sdkHttpResponse().isSuccessful());

        Map<String, String> conf = new HashMap<>();
        conf.put("camel.kamelet.aws-sns-sink.accessKey", container.getAccessKey());
        conf.put("camel.kamelet.aws-sns-sink.secretKey", container.getSecretKey());
        conf.put("camel.kamelet.aws-sns-sink.region", container.getRegion());
        conf.put("camel.kamelet.aws-sns-sink.topicNameOrArn", snsTopicName);
        conf.put("camel.kamelet.aws-sns-sink.uriEndpointOverride", container.getServiceEndpoint().toString());
        conf.put("camel.kamelet.aws-sns-sink.overrideEndpoint", "true");
        conf.put("camel.component.aws2-sns.autowired-enabled", "false");

        conf.put("quarkus.sqs.endpoint-override", container.getServiceEndpoint().toString());
        conf.put("quarkus.sqs.aws.region", container.getRegion());
        conf.put("quarkus.sqs.aws.credentials.type", "static");
        conf.put("quarkus.sqs.aws.credentials.static-provider.access-key-id", container.getAccessKey());
        conf.put("quarkus.sqs.aws.credentials.static-provider.secret-access-key", container.getSecretKey());

        return conf;
    }
}
