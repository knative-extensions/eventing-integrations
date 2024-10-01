/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.knative.eventing.connector.maven.plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fabric8.kubernetes.api.model.AnyType;
import org.apache.camel.v1.CamelCatalog;
import org.apache.camel.v1.Kamelet;
import org.apache.camel.v1.kameletspec.definition.Properties;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

/**
 * Used to leverage Kamelet properties specification to generate connector specification.
 */
@Mojo(name = "generate", threadSafe = true, defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class GenerateConnectorSpecMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.basedir}")
    protected File output;
    @Parameter(property = "kn-connector.kamelet.name", required = true)
    protected String kameletName;
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;
    @Component
    protected MavenProjectHelper projectHelper;
    @Component
    private MavenSession session;
    @Parameter(defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping execution");
            return;
        }

        // need to make a detour over Json to support additional properties set on Kamelet
        Map<String, Object> raw = yaml().load(this.getClass().getClassLoader().getResourceAsStream("kamelets/" + kameletName + ".kamelet.yaml"));
        Kamelet kamelet = json().convertValue(raw, Kamelet.class);

        generatePropertiesAdoc(kamelet);
        generatePropertiesSpec(kamelet);
        generateDependenciesSpec(kamelet);
        generatePropertiesCrdSpec(kamelet);
    }

    private void generatePropertiesAdoc(Kamelet kamelet) throws MojoExecutionException {
        StringBuilder asciiDoc = new StringBuilder();

        asciiDoc.append("|===")
                .append(System.lineSeparator())
                .append("|Property |Required |EnvVar |Description")
                .append(System.lineSeparator())
                .append(System.lineSeparator());

        kamelet.getSpec().getDefinition().getProperties().forEach((name, prop) -> {
            String envVar = "CAMEL_KAMELET_%s_%s=%s".formatted(
                    kameletName.replace("-", "_").toUpperCase(),
                    name.toUpperCase(),
                    Optional.ofNullable(prop.get_default()).map(AnyType::getValue).orElse("<the_%s>".formatted(name)));

            asciiDoc.append("|")
                    .append(name)
                    .append(System.lineSeparator())
                    .append("|")
                    .append(isRequired(kamelet, name) ? "yes" : "no")
                    .append(System.lineSeparator())
                    .append("|")
                    .append(envVar)
                    .append(System.lineSeparator())
                    .append("|")
                    .append(Optional.ofNullable(prop.getDescription()).orElse(""))
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
        });

        asciiDoc.append("|===")
                .append(System.lineSeparator());

        try {
            Files.writeString(Paths.get(output.getAbsolutePath(), "properties.adoc"), asciiDoc.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }
    }

    private void generatePropertiesSpec(Kamelet kamelet) throws MojoExecutionException {
        try {
            Files.createDirectories(Paths.get(output.getAbsolutePath(), "target/metadata"));
            Files.writeString(Paths.get(output.getAbsolutePath(), "target/metadata/properties.json"), json().writer().withDefaultPrettyPrinter()
                            .writeValueAsString(kamelet.getSpec().getDefinition()),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }
    }

    private void generateDependenciesSpec(Kamelet kamelet) throws MojoExecutionException {
        try {
            Files.createDirectories(Paths.get(output.getAbsolutePath(), "target/metadata"));
            Files.writeString(Paths.get(output.getAbsolutePath(), "target/metadata/dependencies.json"), json().writer().withDefaultPrettyPrinter()
                            .writeValueAsString(kamelet.getSpec().getDependencies()
                                    .stream()
                                    .filter(it -> !"camel:core".equals(it))
                                    .filter(it -> !"camel:kamelet".equals(it))
                                    .filter(it -> !it.contains("org.apache.camel.kamelets:camel-kamelets-utils"))
                                    .collect(Collectors.toList())),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }
    }

    private void generatePropertiesCrdSpec(Kamelet kamelet) throws MojoExecutionException {
        try {
            Map<String, Object> spec = new LinkedHashMap<>();
            Map<String, Object> kameletSpec = new LinkedHashMap<>();

            String name;
            if (kameletName.endsWith("-source")) {
                name = kameletName.substring(0, kameletName.length() - "-source".length());
            } else if (kameletName.endsWith("-sink")) {
                name = kameletName.substring(0, kameletName.length() - "-sink".length());
            } else {
                name = kameletName;
            }
            if (name.contains("-")) {
                String[] nameTokens = name.split("-", 2);
                kameletSpec.put(nameTokens[0], createObject(Collections.singletonMap(nameTokens[1],
                        createObject(asPropertiesMap(kamelet.getSpec().getDefinition().getProperties())))));
            } else {
                kameletSpec.put(name, createObject(asPropertiesMap(kamelet.getSpec().getDefinition().getProperties())));
            }

            spec.put("spec", createObject(kameletSpec));

            Files.writeString(Paths.get(output.getAbsolutePath(), "properties.yaml"), yaml().dumpAsMap(spec),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }
    }

    private static Map<String, Object> asPropertiesMap(Map<String, Properties> properties) {
        Map<String, Object> result = new LinkedHashMap<>();
        properties.forEach((name, props) -> {
            result.put(name, asMap(props));
        });
        return result;
    }

    private static Map<String, Object> asMap(Properties properties) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", properties.getType());
        result.put("title", properties.getTitle());
        result.put("description", properties.getDescription());

        if (properties.get_default() != null) {
            result.put("default", properties.get_default().getValue());
        }

        if (properties.getExample() != null) {
            result.put("example", properties.getExample().getValue());
        }
        return result;
    }

    private static Map<String, Object> createObject(Map<String, Object> properties) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("type", "object");
        o.put("properties", properties);
        return o;
    }

    private static boolean isRequired(Kamelet kamelet, String propertyName) {
        if (kamelet.getSpec().getDefinition().getRequired() == null) {
            return false;
        }

        return kamelet.getSpec().getDefinition().getRequired().stream().anyMatch(propertyName::equals);
    }

    private static Yaml yaml() {
        Representer representer = new Representer(new DumperOptions()) {
            @Override
            protected NodeTuple representJavaBeanProperty(Object javaBean, Property property, Object propertyValue, Tag customTag) {
                // if value of property is null, ignore it.
                if (propertyValue == null || (propertyValue instanceof Collection && ((Collection<?>) propertyValue).isEmpty()) ||
                        (propertyValue instanceof Map && ((Map<?, ?>) propertyValue).isEmpty())) {
                    return null;
                } else {
                    return super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
                }
            }
        };
        representer.getPropertyUtils().setSkipMissingProperties(true);
        return new Yaml(representer);
    }

    private static ObjectMapper json() {
        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING)
                .enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
                .disable(JsonParser.Feature.AUTO_CLOSE_SOURCE)
                .enable(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES)
                .build()
                .setDefaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_EMPTY, JsonInclude.Include.NON_EMPTY));
    }

}
