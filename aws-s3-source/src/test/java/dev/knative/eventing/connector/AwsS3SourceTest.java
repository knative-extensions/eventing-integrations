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

import java.util.Collections;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.citrusframework.GherkinTestActionRunner;
import org.citrusframework.annotations.CitrusResource;
import org.citrusframework.context.TestContext;
import org.citrusframework.http.endpoint.builder.HttpEndpoints;
import org.citrusframework.http.server.HttpServer;
import org.citrusframework.quarkus.CitrusSupport;
import org.citrusframework.spi.BindToRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;

import static org.citrusframework.http.actions.HttpActionBuilder.http;

@QuarkusTest
@CitrusSupport
@QuarkusTestResource(LocalstackTestResource.class)
public class AwsS3SourceTest {

    @CitrusResource
    private GherkinTestActionRunner tc;

    private final String s3Key = "message.txt";
    private final String s3Data = "Hello from AWS S3!";
    private final String s3BucketName = "mybucket";

    @LocalstackTestResource.Injected
    public S3Client s3Client;

    @BindToRegistry
    public HttpServer knativeBroker = HttpEndpoints.http()
            .server()
            .port(8080)
            .autoStart(true)
            .build();

    @Test
    public void shouldProduceEvents() {
        tc.given(this::uploadS3File);

        tc.when(
            http().server(knativeBroker)
                    .receive()
                    .post()
                    .message()
                    .body(s3Data)
                    .header("ce-id", "@matches([0-9A-Z]{15}-[0-9]{16})@")
                    .header("ce-type", "dev.knative.connector.event.aws-s3")
                    .header("ce-source", "dev.knative.eventing.aws-s3-source")
                    .header("ce-subject", "aws-s3-source")
        );

        tc.then(
            http().server(knativeBroker)
                    .send()
                    .response(HttpStatus.OK)
        );
    }

    private void uploadS3File(TestContext context) {
        CreateMultipartUploadResponse initResponse = s3Client.createMultipartUpload(b -> b.bucket(s3BucketName).key(s3Key));
        String etag = s3Client.uploadPart(b -> b.bucket(s3BucketName)
                        .key(s3Key)
                        .uploadId(initResponse.uploadId())
                        .partNumber(1),
                RequestBody.fromString(s3Data)).eTag();
        s3Client.completeMultipartUpload(b -> b.bucket(s3BucketName)
                .multipartUpload(CompletedMultipartUpload.builder()
                        .parts(Collections.singletonList(CompletedPart.builder()
                                .partNumber(1)
                                .eTag(etag).build())).build())
                .key(s3Key)
                .uploadId(initResponse.uploadId()));
    }
}
