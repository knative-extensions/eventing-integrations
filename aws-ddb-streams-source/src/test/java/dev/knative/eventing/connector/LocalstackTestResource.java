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
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.StreamSpecification;
import software.amazon.awssdk.services.dynamodb.model.StreamViewType;

public class LocalstackTestResource implements QuarkusTestResourceLifecycleManager {

    private final LocalStackContainer localStackContainer =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.7.2"))
                    .withServices(LocalStackContainer.Service.DYNAMODB);

    private DynamoDbClient ddbClient;

    @Override
    public Map<String, String> start() {
        localStackContainer.start();

        ddbClient = DynamoDbClient.builder()
                .endpointOverride(localStackContainer.getEndpoint())
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(localStackContainer.getAccessKey(), localStackContainer.getSecretKey())
                        )
                )
                .region(Region.of(localStackContainer.getRegion()))
                .build();

        ddbClient.createTable(b -> {
            b.tableName("movies");
            b.keySchema(KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build());
            b.attributeDefinitions(AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.N).build());

            b.streamSpecification(StreamSpecification.builder()
                    .streamEnabled(true)
                    .streamViewType(StreamViewType.NEW_AND_OLD_IMAGES).build());

            b.provisionedThroughput(
                    ProvisionedThroughput.builder()
                            .readCapacityUnits(1L)
                            .writeCapacityUnits(1L).build());
        });

        Map<String, String> conf = new HashMap<>();
        conf.put("camel.kamelet.aws-ddb-streams-source.accessKey", localStackContainer.getAccessKey());
        conf.put("camel.kamelet.aws-ddb-streams-source.secretKey", localStackContainer.getSecretKey());
        conf.put("camel.kamelet.aws-ddb-streams-source.region", localStackContainer.getRegion());
        conf.put("camel.kamelet.aws-ddb-streams-source.table", "movies");
        conf.put("camel.kamelet.aws-ddb-streams-source.uriEndpointOverride", localStackContainer.getEndpoint().toString());
        conf.put("camel.kamelet.aws-ddb-streams-source.overrideEndpoint", "true");
        conf.put("camel.kamelet.aws-ddb-streams-source.streamIteratorType", "FROM_START");

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
        testInjector.injectIntoFields(ddbClient,
                new TestInjector.MatchesType(DynamoDbClient.class));
    }

    /**
     * Annotation marks fields in test class for injection by this test resource.
     */
    @interface Injected {
    }
}
