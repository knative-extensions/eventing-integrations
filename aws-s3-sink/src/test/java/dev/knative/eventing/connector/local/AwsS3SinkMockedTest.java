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

import dev.knative.eventing.connector.AwsS3SinkTestBase;
import io.quarkus.test.junit.QuarkusTest;
import org.citrusframework.actions.testcontainers.aws2.AwsService;
import org.citrusframework.testcontainers.aws2.LocalStackContainer;
import org.citrusframework.testcontainers.aws2.quarkus.LocalStackContainerSupport;
import org.citrusframework.testcontainers.quarkus.ContainerLifecycleListener;

import java.util.HashMap;
import java.util.Map;

@QuarkusTest
@LocalStackContainerSupport(services = AwsService.S3, containerLifecycleListener = AwsS3SinkMockedTest.class)
public class AwsS3SinkMockedTest extends AwsS3SinkTestBase implements ContainerLifecycleListener<LocalStackContainer> {

    @Override
    public Map<String, String> started(LocalStackContainer container) {
        Map<String, String> conf = new HashMap<>();
        conf.put("camel.kamelet.aws-s3-sink.accessKey", container.getAccessKey());
        conf.put("camel.kamelet.aws-s3-sink.secretKey", container.getSecretKey());
        conf.put("camel.kamelet.aws-s3-sink.region", container.getRegion());
        conf.put("camel.kamelet.aws-s3-sink.bucketNameOrArn", s3BucketName);
        conf.put("camel.kamelet.aws-s3-sink.uriEndpointOverride", container.getServiceEndpoint().toString());
        conf.put("camel.kamelet.aws-s3-sink.overrideEndpoint", "true");
        conf.put("camel.kamelet.aws-s3-sink.forcePathStyle", "true");
        conf.put("camel.kamelet.aws-s3-sink.keyName", s3Key);
        conf.put("camel.kamelet.aws-s3-sink.autoCreateBucket", "true");
        conf.put("camel.component.aws2-s3.autowired-enabled", "false");

        conf.put("quarkus.s3.endpoint-override", container.getServiceEndpoint().toString());
        conf.put("quarkus.s3.aws.region", container.getRegion());
        conf.put("quarkus.s3.aws.credentials.type", "static");
        conf.put("quarkus.s3.aws.credentials.static-provider.access-key-id", container.getAccessKey());
        conf.put("quarkus.s3.aws.credentials.static-provider.secret-access-key", container.getSecretKey());

        return conf;
    }
}
