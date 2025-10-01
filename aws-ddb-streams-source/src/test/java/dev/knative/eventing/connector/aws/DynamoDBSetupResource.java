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

import dev.knative.eventing.connector.AwsDdbStreamsSourceTestBase;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.util.Collections;
import java.util.Map;

public class DynamoDBSetupResource implements QuarkusTestResourceLifecycleManager {

    private final DynamoDbClient dynamoDbClient;

    public DynamoDBSetupResource() {
        Config config = ConfigProvider.getConfig();
        String region = config.getValue("quarkus.dynamodb.aws.region", String.class);
        String accessKey = config.getValue("quarkus.dynamodb.aws.credentials.static-provider.access-key-id", String.class);
        String secretKey = config.getValue("quarkus.dynamodb.aws.credentials.static-provider.secret-access-key", String.class);

        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)));

        dynamoDbClient = builder.build();
    }

    @Override
    public Map<String, String> start() {
        AwsDdbStreamsSourceTestBase.setupDdb(dynamoDbClient);
        return Collections.emptyMap();
    }

    @Override
    public void stop() {
        // noop
    }
}
