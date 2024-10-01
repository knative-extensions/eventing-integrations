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
import software.amazon.awssdk.services.s3.S3Client;

public class LocalstackTestResource implements QuarkusTestResourceLifecycleManager {

    private final LocalStackContainer localStackContainer =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.7.2"))
                    .withServices(LocalStackContainer.Service.S3);

    private S3Client s3Client;

    @Override
    public Map<String, String> start() {
        localStackContainer.start();

        s3Client = S3Client.builder()
                .endpointOverride(localStackContainer.getEndpoint())
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(localStackContainer.getAccessKey(), localStackContainer.getSecretKey())
                        )
                )
                .forcePathStyle(true)
                .region(Region.of(localStackContainer.getRegion()))
                .build();

        s3Client.createBucket(b -> b.bucket("mybucket"));

        Map<String, String> conf = new HashMap<>();
        conf.put("camel.kamelet.aws-s3-source.accessKey", localStackContainer.getAccessKey());
        conf.put("camel.kamelet.aws-s3-source.secretKey", localStackContainer.getSecretKey());
        conf.put("camel.kamelet.aws-s3-source.region", localStackContainer.getRegion());
        conf.put("camel.kamelet.aws-s3-source.bucketNameOrArn", "mybucket");
        conf.put("camel.kamelet.aws-s3-source.uriEndpointOverride", localStackContainer.getEndpoint().toString());
        conf.put("camel.kamelet.aws-s3-source.overrideEndpoint", "true");
        conf.put("camel.kamelet.aws-s3-source.forcePathStyle", "true");

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
        testInjector.injectIntoFields(s3Client,
                new TestInjector.MatchesType(S3Client.class));
    }

    /**
     * Annotation marks fields in test class for injection by this test resource.
     */
    @interface Injected {
    }
}
