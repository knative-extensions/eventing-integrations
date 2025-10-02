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

package dev.knative.eventing.connector.aws;

import dev.knative.eventing.connector.AwsEventBridgeSinkTestBase;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.EventBridgeClientBuilder;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import java.util.Collections;
import java.util.Map;

public class EventBridgeSetupResource implements QuarkusTestResourceLifecycleManager {

    private final EventBridgeClient eventBridgeClient;
    private final SqsClient sqsClient;

    public EventBridgeSetupResource() {
        Config config = ConfigProvider.getConfig();

        String eventBridgeRegion = config.getValue("quarkus.eventbridge.aws.region", String.class);
        String eventBridgeAccessKey = config.getValue("quarkus.eventbridge.aws.credentials.static-provider.access-key-id", String.class);
        String eventBridgeSecretKey = config.getValue("quarkus.eventbridge.aws.credentials.static-provider.secret-access-key", String.class);

        String sqsRegion = config.getValue("quarkus.sqs.aws.region", String.class);
        String sqsAccessKey = config.getValue("quarkus.sqs.aws.credentials.static-provider.access-key-id", String.class);
        String sqsSecretKey = config.getValue("quarkus.sqs.aws.credentials.static-provider.secret-access-key", String.class);

        EventBridgeClientBuilder eventBridgeClientBuilder = EventBridgeClient.builder()
                .region(Region.of(eventBridgeRegion))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(eventBridgeAccessKey, eventBridgeSecretKey)));

        SqsClientBuilder sqsClientBuilder = SqsClient.builder()
                .region(Region.of(sqsRegion))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(sqsAccessKey, sqsSecretKey)));

        eventBridgeClient = eventBridgeClientBuilder.build();
        sqsClient = sqsClientBuilder.build();

    }

    @Override
    public Map<String, String> start() {
        AwsEventBridgeSinkTestBase.setupEventBridgeAndSqs(eventBridgeClient, sqsClient);
        return Collections.emptyMap();
    }

    @Override
    public void stop() {
        // noop
    }
}
