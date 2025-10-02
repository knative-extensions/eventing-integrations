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

import dev.knative.eventing.connector.AwsIntegrationTestProfile;
import dev.knative.eventing.connector.AwsS3SourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.citrusframework.http.endpoint.builder.HttpEndpoints;
import org.citrusframework.http.server.HttpServer;
import org.citrusframework.quarkus.CitrusSupport;
import org.citrusframework.spi.BindToRegistry;

@QuarkusTest
@CitrusSupport
@TestProfile(AwsIntegrationTestProfile.class)
public class AwsS3SourceIntegrationTest extends AwsS3SourceTestBase {

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
}
