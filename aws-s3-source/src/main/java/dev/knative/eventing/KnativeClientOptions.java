package dev.knative.eventing;

import io.quarkus.tls.runtime.keystores.TrustAllOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.ext.web.client.WebClientOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.PropertiesComponent;

/**
 * Knative client options able to configure secure Http transport options.
 * SSL options are added automatically when enabled via system property or environment variable settings.
 */
@ApplicationScoped
public class KnativeClientOptions {

    private static final String PROPERTY_PREFIX = "camel.knative.client.ssl.";

    @Named("knativeClientOptions")
    public WebClientOptions knativeClientOptions(CamelContext camelContext) {
        PropertiesComponent propertiesComponent = camelContext.getPropertiesComponent();

        boolean sslEnabled = Boolean.parseBoolean(
                propertiesComponent.resolveProperty(PROPERTY_PREFIX + "enabled").orElse("false"));

        WebClientOptions options = new WebClientOptions();
        if (sslEnabled) {
            options.setSsl(true);

            boolean verifyHostname = Boolean.parseBoolean(
                    propertiesComponent.resolveProperty(PROPERTY_PREFIX + "verify.hostname").orElse("true"));
            options.setVerifyHost(verifyHostname);

            String keystorePath = propertiesComponent.resolveProperty(PROPERTY_PREFIX + "keystore.path").orElse("");
            String keystorePassword = propertiesComponent.resolveProperty(PROPERTY_PREFIX + "keystore.password").orElse("");

            if (!keystorePath.isEmpty()) {
                if (keystorePath.endsWith(".p12")) {
                    options.setKeyCertOptions(new PfxOptions().setPath(keystorePath).setPassword(keystorePassword));
                } else {
                    options.setKeyCertOptions(new JksOptions().setPath(keystorePath).setPassword(keystorePassword));
                }
            } else {
                String keyPath = propertiesComponent.resolveProperty(PROPERTY_PREFIX + "key.path").orElse("");
                String keyCertPath = propertiesComponent.resolveProperty(PROPERTY_PREFIX + "key.cert.path").orElse(keyPath);

                if (!keyPath.isEmpty()) {
                    options.setKeyCertOptions(new PemKeyCertOptions().setKeyPath(keyPath).setCertPath(keyCertPath));
                }
            }

            String truststorePath = propertiesComponent.resolveProperty(PROPERTY_PREFIX + "truststore.path").orElse("");
            String truststorePassword = propertiesComponent.resolveProperty(PROPERTY_PREFIX + "truststore.password").orElse("");

            if (!truststorePath.isEmpty()) {
                if (truststorePath.endsWith(".p12")) {
                    options.setTrustOptions(new PfxOptions().setPath(truststorePath).setPassword(truststorePassword));
                } else {
                    options.setTrustOptions(new JksOptions().setPath(truststorePath).setPassword(truststorePassword));
                }
            } else {
                String trustCertPath = propertiesComponent.resolveProperty(PROPERTY_PREFIX + "trust.cert.path").orElse("");

                if (!trustCertPath.isEmpty()) {
                    options.setTrustOptions(new PemTrustOptions().addCertPath(trustCertPath));
                } else {
                    options.setTrustOptions(TrustAllOptions.INSTANCE);
                }
            }
        }
        return options;
    }

}
