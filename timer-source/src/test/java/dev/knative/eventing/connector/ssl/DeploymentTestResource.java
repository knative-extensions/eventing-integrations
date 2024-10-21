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

package dev.knative.eventing.connector.ssl;

import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class DeploymentTestResource implements QuarkusTestResourceLifecycleManager {

    private final Map<String, String> injectedConf = new HashMap<>();

    @Override
    public void init(Map<String, String> initArgs) {
        injectedConf.putAll(initArgs);
    }

    @Override
    public Map<String, String> start() {
        Map<String, String> conf = new HashMap<>();

        String message = injectedConf.remove("message");
        conf.put("camel.kamelet.timer-source.message", message);

        conf.putAll(injectedConf);

        return conf;
    }

    @Override
    public void stop() {
    }
}
