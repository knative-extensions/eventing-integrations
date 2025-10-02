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

import dev.knative.eventing.connector.AwsSqsSourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import org.citrusframework.actions.testcontainers.aws2.AwsService;
import org.citrusframework.http.endpoint.builder.HttpEndpoints;
import org.citrusframework.http.server.HttpServer;
import org.citrusframework.quarkus.CitrusSupport;
import org.citrusframework.spi.BindToRegistry;
import org.citrusframework.testcontainers.aws2.LocalStackContainer;
import org.citrusframework.testcontainers.aws2.quarkus.LocalStackContainerSupport;
import org.citrusframework.testcontainers.quarkus.ContainerLifecycleListener;

import java.util.HashMap;
import java.util.Map;

@QuarkusTest
@CitrusSupport
@LocalStackContainerSupport(services = AwsService.SQS, containerLifecycleListener = AwsSqsSourceMockedTest.class)
public class AwsSqsSourceMockedTest extends AwsSqsSourceTestBase implements ContainerLifecycleListener<LocalStackContainer> {

    @BindToRegistry
    private HttpServer knativeBroker = HttpEndpoints.http()
                .server()
                .port(8080)
                .timeout(5000L)
                .autoStart(true)
                .build();

    protected HttpServer getKnativeBroker() {
        return knativeBroker;
    }

    @Override
    public Map<String, String> started(LocalStackContainer container) {
        Map<String, String> conf = new HashMap<>();
        conf.put("camel.kamelet.aws-sqs-source.accessKey", container.getAccessKey());
        conf.put("camel.kamelet.aws-sqs-source.secretKey", container.getSecretKey());
        conf.put("camel.kamelet.aws-sqs-source.region", container.getRegion());
        conf.put("camel.kamelet.aws-sqs-source.queueNameOrArn", sqsQueueName);
        conf.put("camel.kamelet.aws-sqs-source.uriEndpointOverride", container.getServiceEndpoint().toString());
        conf.put("camel.kamelet.aws-sqs-source.overrideEndpoint", "true");
        conf.put("camel.kamelet.aws-sqs-source.autoCreateQueue", "true");
        conf.put("camel.component.aws2-sqs.autowired-enabled", "false");

        conf.put("quarkus.sqs.endpoint-override", container.getServiceEndpoint().toString());
        conf.put("quarkus.sqs.aws.region", container.getRegion());
        conf.put("quarkus.sqs.aws.credentials.static-provider.access-key-id", container.getAccessKey());
        conf.put("quarkus.sqs.aws.credentials.static-provider.secret-access-key", container.getSecretKey());

        return conf;
    }

}
