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

import dev.knative.eventing.connector.AwsDdbStreamsSourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import org.citrusframework.actions.testcontainers.aws2.AwsService;
import org.citrusframework.http.endpoint.builder.HttpEndpoints;
import org.citrusframework.http.server.HttpServer;
import org.citrusframework.quarkus.CitrusSupport;
import org.citrusframework.spi.BindToRegistry;
import org.citrusframework.testcontainers.aws2.LocalStackContainer;
import org.citrusframework.testcontainers.aws2.quarkus.LocalStackContainerSupport;
import org.citrusframework.testcontainers.quarkus.ContainerLifecycleListener;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;

@QuarkusTest
@CitrusSupport
@LocalStackContainerSupport(services = AwsService.DYNAMODB, containerLifecycleListener = AwsDdbStreamsSourceMockedTest.class)
public class AwsDdbStreamsSourceMockedTest extends AwsDdbStreamsSourceTestBase implements ContainerLifecycleListener<LocalStackContainer> {

    @BindToRegistry
    private HttpServer knativeBroker = HttpEndpoints.http()
            .server()
            .port(8080)
            .timeout(5000L)
            .autoStart(true)
            .build();

    @Override
    protected HttpServer getKnativeBroker() {
        return knativeBroker;
    }

    @Override
    public Map<String, String> started(LocalStackContainer container) {
        DynamoDbClient ddbClient = container.getClient(AwsService.DYNAMODB);
        setupDdb(ddbClient);

        Map<String, String> conf = new HashMap<>();
        conf.put("camel.kamelet.aws-ddb-streams-source.accessKey", container.getAccessKey());
        conf.put("camel.kamelet.aws-ddb-streams-source.secretKey", container.getSecretKey());
        conf.put("camel.kamelet.aws-ddb-streams-source.region", container.getRegion());
        conf.put("camel.kamelet.aws-ddb-streams-source.table", ddbTableName);
        conf.put("camel.kamelet.aws-ddb-streams-source.uriEndpointOverride", container.getServiceEndpoint().toString());
        conf.put("camel.kamelet.aws-ddb-streams-source.overrideEndpoint", "true");
        conf.put("camel.kamelet.aws-ddb-streams-source.streamIteratorType", "FROM_START");
        conf.put("camel.component.aws2-ddb.autowired-enabled", "false");

        conf.put("quarkus.dynamodb.endpoint-override", container.getServiceEndpoint().toString());
        conf.put("quarkus.dynamodb.aws.region", container.getRegion());
        conf.put("quarkus.dynamodb.aws.credentials.type", "static");
        conf.put("quarkus.dynamodb.aws.credentials.static-provider.access-key-id", container.getAccessKey());
        conf.put("quarkus.dynamodb.aws.credentials.static-provider.secret-access-key", container.getSecretKey());

        return conf;
    }
}
