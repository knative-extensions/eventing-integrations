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

package dev.knative.eventing;

import io.vertx.ext.web.client.WebClientOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.camel.CamelContext;
import org.apache.camel.component.knative.http.KnativeOidcClientOptions;
import org.apache.camel.component.knative.http.KnativeSslClientOptions;

@ApplicationScoped
public class SourceOptions {

    @Named("knativeHttpClientOptions")
    public WebClientOptions knativeHttpClientOptions(CamelContext camelContext) {
        KnativeOidcClientOptions oidcClientOptions = new KnativeOidcClientOptions();
        oidcClientOptions.setCamelContext(camelContext);
        if (oidcClientOptions.isOidcEnabled()) {
            return oidcClientOptions;
        }

        return new KnativeSslClientOptions(camelContext);
    }
}
