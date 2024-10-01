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
import org.testcontainers.containers.localstack.LocalStackContainer;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

import static org.citrusframework.actions.CreateVariablesAction.Builder.createVariable;
import static org.citrusframework.http.actions.HttpActionBuilder.http;

@QuarkusTest
@CitrusSupport
@QuarkusTestResource(LocalstackTestResource.class)
public class AwsDdbStreamsSourceTest {

    @CitrusResource
    private GherkinTestActionRunner tc;

    private final String ddbTableName = "movies";

    @LocalstackTestResource.Injected
    public LocalStackContainer localStackContainer;

    @LocalstackTestResource.Injected
    public DynamoDbClient ddbClient;

    @BindToRegistry
    public HttpServer knativeBroker = HttpEndpoints.http()
            .server()
            .port(8080)
            .autoStart(true)
            .build();

    @Test
    public void shouldProduceEvents() {
        tc.given(createVariable("aws.region", localStackContainer.getRegion()));

        tc.given(this::putItem);

        tc.when(
            http().server(knativeBroker)
                    .receive()
                    .post()
                    .message()
                    .body("""
                    {
                        "eventID":"@ignore@",
                        "eventName":"INSERT",
                        "eventVersion":"@ignore@",
                        "eventSource":"aws:dynamodb",
                        "awsRegion":"${aws.region}",
                        "dynamodb": {
                            "approximateCreationDateTime":"@ignore@",
                            "keys": {
                                "id": { "n":"1","ss":[],"ns":[],"bs":[],"m":{},"l":[],"type":"N" }
                            },
                            "newImage": {
                                "id": { "n":"1","ss":[],"ns":[],"bs":[],"m":{},"l":[],"type":"N" },
                                "year": { "n":"1977","ss":[],"ns":[],"bs":[],"m":{},"l":[],"type":"N" },
                                "title": { "s":"Star Wars IV","ss":[],"ns":[],"bs":[],"m":{},"l":[],"type":"S" }
                            },
                            "oldImage":{},
                            "sequenceNumber":"@ignore@",
                            "sizeBytes":"@ignore@",
                            "streamViewType":"NEW_AND_OLD_IMAGES"
                        }
                    }
                    """)
                    .header("ce-id", "@matches([0-9A-Z]{15}-[0-9]{16})@")
                    .header("ce-type", "dev.knative.connector.event.aws-ddb-streams")
                    .header("ce-source", "dev.knative.eventing.aws-ddb-streams-source")
                    .header("ce-subject", "aws-ddb-streams-source")
        );

        tc.then(
            http().server(knativeBroker)
                    .send()
                    .response(HttpStatus.OK)
        );
    }

    private void putItem(TestContext context) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().n("1").build());
        item.put("year", AttributeValue.builder().n("1977").build());
        item.put("title", AttributeValue.builder().s("Star Wars IV").build());

        ddbClient.putItem(b -> b.tableName(ddbTableName)
                .item(item)
                .returnValues(ReturnValue.ALL_OLD));
    }
}
