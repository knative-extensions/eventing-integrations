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

import dev.knative.eventing.connector.AwsS3SourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import org.citrusframework.actions.testcontainers.aws2.AwsService;
import org.citrusframework.http.endpoint.builder.HttpEndpoints;
import org.citrusframework.http.security.HttpSecureConnection;
import org.citrusframework.http.server.HttpServer;
import org.citrusframework.quarkus.CitrusSupport;
import org.citrusframework.spi.BindToRegistry;
import org.citrusframework.testcontainers.aws2.LocalStackContainer;
import org.citrusframework.testcontainers.aws2.quarkus.LocalStackContainerSupport;
import org.citrusframework.testcontainers.quarkus.ContainerLifecycleListener;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.HashMap;
import java.util.Map;

@QuarkusTest
@CitrusSupport
@LocalStackContainerSupport(services = AwsService.S3, containerLifecycleListener = AwsS3SourceMockedSSLTest.class)
public class AwsS3SourceMockedSSLTest extends AwsS3SourceTestBase implements ContainerLifecycleListener<LocalStackContainer> {

    @BindToRegistry
    public HttpServer knativeBroker = HttpEndpoints.http()
            .server()
            .port(8080)
            .timeout(5000L)
            .securePort(8443)
            .secured(HttpSecureConnection.ssl()
                    .keyStore("classpath:keystore/server.jks", "secr3t")
                    .trustStore("classpath:keystore/truststore.jks", "secr3t"))
            .autoStart(true)
            .build();

    @Override
    protected HttpServer getKnativeBroker() {
        return knativeBroker;
    }

    @Override
    public Map<String, String> started(LocalStackContainer container) {
        S3Client s3Client = container.getClient(AwsService.S3);

        s3Client.createBucket(b -> b.bucket(s3BucketName));

        Map<String, String> conf = new HashMap<>();
        conf.put("camel.kamelet.aws-s3-source.accessKey", container.getAccessKey());
        conf.put("camel.kamelet.aws-s3-source.secretKey", container.getSecretKey());
        conf.put("camel.kamelet.aws-s3-source.region", container.getRegion());
        conf.put("camel.kamelet.aws-s3-source.bucketNameOrArn", s3BucketName);
        conf.put("camel.kamelet.aws-s3-source.uriEndpointOverride", container.getServiceEndpoint().toString());
        conf.put("camel.kamelet.aws-s3-source.overrideEndpoint", "true");
        conf.put("camel.kamelet.aws-s3-source.forcePathStyle", "true");
        conf.put("camel.component.aws2-s3.autowired-enabled", "false");

        conf.put("k.sink", "https://localhost:8443");
        conf.put("camel.knative.client.ssl.enabled", "true");
        conf.put("camel.knative.client.ssl.verify.hostname", "false");
        conf.put("camel.knative.client.ssl.key.path", "keystore/client.pem");
        conf.put("camel.knative.client.ssl.key.cert.path", "keystore/client.crt");
        conf.put("camel.knative.client.ssl.truststore.path", "keystore/truststore.jks");
        conf.put("camel.knative.client.ssl.truststore.password", "secr3t");

        conf.put("quarkus.s3.endpoint-override", container.getServiceEndpoint().toString());
        conf.put("quarkus.s3.aws.region", container.getRegion());
        conf.put("quarkus.s3.aws.credentials.static-provider.access-key-id", container.getAccessKey());
        conf.put("quarkus.s3.aws.credentials.static-provider.secret-access-key", container.getSecretKey());

        return conf;
    }
}
