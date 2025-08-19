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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import org.citrusframework.TestCaseRunner;
import org.citrusframework.annotations.CitrusResource;
import org.citrusframework.context.TestContext;
import org.citrusframework.exceptions.CitrusRuntimeException;
import org.citrusframework.http.client.HttpClient;
import org.citrusframework.http.endpoint.builder.HttpEndpoints;
import org.citrusframework.quarkus.CitrusSupport;
import org.citrusframework.spi.BindToRegistry;
import org.citrusframework.testcontainers.aws2.LocalStackContainer;
import org.citrusframework.testcontainers.aws2.quarkus.LocalStackContainerSupport;
import org.citrusframework.testcontainers.quarkus.ContainerLifecycleListener;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicResponse;
import software.amazon.awssdk.services.sns.model.SubscribeResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import static org.citrusframework.container.RepeatOnErrorUntilTrue.Builder.repeatOnError;
import static org.citrusframework.http.actions.HttpActionBuilder.http;

@QuarkusTest
@LocalStackContainerSupport(services = { LocalStackContainer.Service.SNS, LocalStackContainer.Service.SQS },
                            containerLifecycleListener = AwsSnsSinkTest.class)
@CitrusSupport
public class AwsSnsSinkTest implements ContainerLifecycleListener<LocalStackContainer> {

    @CitrusResource
    private TestCaseRunner tc;

    private final String snsData = "Hello from AWS SNS!";
    private final String snsTopicName = "mytopic";
    private final String sqsQueueName = "myqueue";

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
                    .body(snsData)
                    .header("ce-id", "citrus:randomPattern([0-9A-Z]{15}-[0-9]{16})")
                    .header("ce-type", "dev.knative.eventing.aws.sns")
                    .header("ce-source", "dev.knative.eventing.aws-sns-source")
                    .header("ce-subject", "aws-sns-source")
        );

        tc.when(
            http().client(knativeTrigger)
                    .receive()
                    .response(HttpStatus.NO_CONTENT)
        );

        tc.then(repeatOnError()
                .autoSleep(Duration.ofMillis(1000))
                .until((i, context) -> i > 10)
                .actions(this::verifySnsData));
    }

    private void verifySnsData(TestContext context) {
        SqsClient sqsClient = localStackContainer.getClient(LocalStackContainer.Service.SQS);
        ListQueuesResponse listQueuesResult = sqsClient.listQueues(b ->
                b.maxResults(100).queueNamePrefix(sqsQueueName));

        String queueUrl = listQueuesResult.queueUrls().stream()
                .filter(url -> url.endsWith("/" + sqsQueueName))
                .findFirst()
                .orElseThrow(() -> new CitrusRuntimeException("Queue %s not found".formatted(sqsQueueName)));

        ReceiveMessageResponse receiveMessageResponse = sqsClient.receiveMessage(b ->
                b.queueUrl(queueUrl));

        try {
            Assertions.assertEquals(1, receiveMessageResponse.messages().size());
            Assertions.assertTrue(receiveMessageResponse.messages().get(0).body().contains(snsData));
        } catch (AssertionFailedError error) {
            throw new CitrusRuntimeException("SNS data verification failed", error);
        }
    }

    @Override
    public Map<String, String> started(LocalStackContainer container) {
        // Create SQS queue acting as a SNS notification endpoint
        SqsClient sqsClient = container.getClient(LocalStackContainer.Service.SQS);

        sqsClient.createQueue(b -> b.queueName(sqsQueueName));

        // Create SNS topic
        SnsClient snsClient = container.getClient(LocalStackContainer.Service.SNS);

        CreateTopicResponse topicResponse = snsClient.createTopic(b -> b.name(snsTopicName));

        // Subscribe SQS Queue to SNS topic as a notification endpoint
        SubscribeResponse subscribeResponse = snsClient.subscribe(b -> b.protocol("sqs")
                .returnSubscriptionArn(true)
                .endpoint("arn:aws:sqs:%s:000000000000:%s".formatted(container.getRegion(), sqsQueueName))
                .topicArn(topicResponse.topicArn()));

        Assertions.assertTrue(subscribeResponse.sdkHttpResponse().isSuccessful());

        Map<String, String> conf = new HashMap<>();
        conf.put("camel.kamelet.aws-sns-sink.accessKey", container.getAccessKey());
        conf.put("camel.kamelet.aws-sns-sink.secretKey", container.getSecretKey());
        conf.put("camel.kamelet.aws-sns-sink.region", container.getRegion());
        conf.put("camel.kamelet.aws-sns-sink.topicNameOrArn", snsTopicName);
        conf.put("camel.kamelet.aws-sns-sink.uriEndpointOverride", container.getServiceEndpoint().toString());
        conf.put("camel.kamelet.aws-sns-sink.overrideEndpoint", "true");
        return conf;
    }
}
