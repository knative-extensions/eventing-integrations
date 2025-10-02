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

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.citrusframework.TestCaseRunner;
import org.citrusframework.annotations.CitrusResource;
import org.citrusframework.context.TestContext;
import org.citrusframework.exceptions.CitrusRuntimeException;
import org.citrusframework.http.client.HttpClient;
import org.citrusframework.http.endpoint.builder.HttpEndpoints;
import org.citrusframework.quarkus.CitrusSupport;
import org.citrusframework.spi.BindToRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.citrusframework.http.actions.HttpActionBuilder.http;

@QuarkusTest
@CitrusSupport
public abstract class AwsS3SinkTestBase {

    @CitrusResource
    protected TestCaseRunner tc;

    @Inject
    protected S3Client s3Client;

    protected final String s3Key = "message.txt";
    protected final String s3Data = "Hello from AWS S3!";
    protected final String s3BucketName = "aws3-s3-sink-bucket";

    @BindToRegistry
    public HttpClient knativeTrigger = HttpEndpoints.http()
            .client()
            .requestUrl("http://localhost:8081")
            .build();

    @Test
    public void shouldConsumeEvents() {
        tc.given(
                http().client(knativeTrigger)
                        .send()
                        .post("/")
                        .message()
                        .body(s3Data)
                        .header("ce-id", "@matches([0-9A-Z]{15}-[0-9]{16})@")
                        .header("ce-type", "dev.knative.eventing.aws.s3")
                        .header("ce-source", "dev.knative.eventing.aws-s3-source")
                        .header("ce-subject", "aws-s3-source")
        );

        tc.when(
                http().client(knativeTrigger)
                        .receive()
                        .response(HttpStatus.NO_CONTENT)
        );

        tc.then(this::verifyS3File);
    }

    protected void verifyS3File(TestContext context) {
        try {
            ResponseInputStream<GetObjectResponse> getObjectResponse = s3Client.getObject(b -> b.bucket(s3BucketName).key(s3Key));
            ByteArrayOutputStream content = new ByteArrayOutputStream();
            getObjectResponse.transferTo(content);
            Assertions.assertEquals(s3Data, content.toString());
        } catch (NoSuchKeyException e) {
            Assertions.fail("File not found in S3 bucket: " + s3Key);
        } catch (IOException e) {
            throw new CitrusRuntimeException("Failed to verify S3 file content", e);
        } finally {
            // Cleanup is now performed for both mock and real tests
            System.out.println("Cleaning up S3 object: " + s3Key);
            s3Client.deleteObject(b -> b.bucket(s3BucketName).key(s3Key));
        }
    }
}
