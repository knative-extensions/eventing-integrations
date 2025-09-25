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

import dev.knative.eventing.connector.AwsSnsSinkTestBase;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.SnsClientBuilder;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import java.util.Collections;
import java.util.Map;

public class SnsSetupResource implements QuarkusTestResourceLifecycleManager {

    private final SnsClient snsClient;
    private final SqsClient sqsClient;

    public SnsSetupResource() {
        Config config = ConfigProvider.getConfig();

        String snsRegion = config.getValue("quarkus.sns.aws.region", String.class);
        String snsAccessKey = config.getValue("quarkus.sns.aws.credentials.static-provider.access-key-id", String.class);
        String snsSecretKey = config.getValue("quarkus.sns.aws.credentials.static-provider.secret-access-key", String.class);

        String sqsRegion = config.getValue("quarkus.sqs.aws.region", String.class);
        String sqsAccessKey = config.getValue("quarkus.sqs.aws.credentials.static-provider.access-key-id", String.class);
        String sqsSecretKey = config.getValue("quarkus.sqs.aws.credentials.static-provider.secret-access-key", String.class);

        SnsClientBuilder snsClientBuilder = SnsClient.builder()
                .region(Region.of(snsRegion))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(snsAccessKey, snsSecretKey)));

        SqsClientBuilder sqsClientBuilder = SqsClient.builder()
                .region(Region.of(sqsRegion))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(sqsAccessKey, sqsSecretKey)));

        snsClient = snsClientBuilder.build();
        sqsClient = sqsClientBuilder.build();
    }

    @Override
    public Map<String, String> start() {
        AwsSnsSinkTestBase.setupSnsAndSqs(sqsClient, snsClient);
        return Collections.emptyMap();
    }

    @Override
    public void stop() {
        // noop
    }
}
