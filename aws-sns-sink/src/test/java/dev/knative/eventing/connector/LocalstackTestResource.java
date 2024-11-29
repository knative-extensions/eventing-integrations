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

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.junit.jupiter.api.Assertions;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicResponse;
import software.amazon.awssdk.services.sns.model.SubscribeResponse;
import software.amazon.awssdk.services.sqs.SqsClient;

public class LocalstackTestResource implements QuarkusTestResourceLifecycleManager {

    private final LocalStackContainer localStackContainer =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.7.2"))
                    .withServices(LocalStackContainer.Service.SQS, LocalStackContainer.Service.SNS);

    private SqsClient sqsClient;
    private SnsClient snsClient;

    private final Map<String, String> injectedConf = new HashMap<>();

    @Override
    public void init(Map<String, String> initArgs) {
        injectedConf.putAll(initArgs);
    }

    @Override
    public Map<String, String> start() {
        localStackContainer.start();

        // Create SQS queue acting as a SNS notification endpoint
        sqsClient = SqsClient.builder()
                .endpointOverride(localStackContainer.getEndpoint())
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(localStackContainer.getAccessKey(), localStackContainer.getSecretKey())
                        )
                )
                .region(Region.of(localStackContainer.getRegion()))
                .build();

        String queueNameOrArn = injectedConf.remove("queueNameOrArn");
        sqsClient.createQueue(b -> b.queueName(queueNameOrArn));

        // Create SNS topic
        snsClient = SnsClient.builder()
                .endpointOverride(localStackContainer.getEndpoint())
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(localStackContainer.getAccessKey(), localStackContainer.getSecretKey())
                        )
                )
                .region(Region.of(localStackContainer.getRegion()))
                .build();

        String topicNameOrArn = injectedConf.remove("topicNameOrArn");
        CreateTopicResponse topicResponse = snsClient.createTopic(b -> b.name(topicNameOrArn));

        // Subscribe SQS Queue to SNS topic as a notification endpoint
        SubscribeResponse subscribeResponse = snsClient.subscribe(b -> b.protocol("sqs")
                .returnSubscriptionArn(true)
                .endpoint("arn:aws:sqs:%s:000000000000:%s".formatted(localStackContainer.getRegion(), queueNameOrArn))
                .topicArn(topicResponse.topicArn()));

        Assertions.assertTrue(subscribeResponse.sdkHttpResponse().isSuccessful());

        Map<String, String> conf = new HashMap<>();
        conf.put("camel.kamelet.aws-sns-sink.accessKey", localStackContainer.getAccessKey());
        conf.put("camel.kamelet.aws-sns-sink.secretKey", localStackContainer.getSecretKey());
        conf.put("camel.kamelet.aws-sns-sink.region", localStackContainer.getRegion());
        conf.put("camel.kamelet.aws-sns-sink.topicNameOrArn", topicNameOrArn);
        conf.put("camel.kamelet.aws-sns-sink.uriEndpointOverride", localStackContainer.getEndpoint().toString());
        conf.put("camel.kamelet.aws-sns-sink.overrideEndpoint", "true");

        conf.putAll(injectedConf);

        return conf;
    }

    @Override
    public void stop() {
        localStackContainer.stop();
    }

    @Override
    public void inject(TestInjector testInjector) {
        testInjector.injectIntoFields(localStackContainer,
                new TestInjector.MatchesType(LocalStackContainer.class));
        testInjector.injectIntoFields(snsClient,
                new TestInjector.MatchesType(SnsClient.class));
        testInjector.injectIntoFields(sqsClient,
                new TestInjector.MatchesType(SqsClient.class));
    }

    /**
     * Annotation marks fields in test class for injection by this test resource.
     */
    @interface Injected {
    }
}
