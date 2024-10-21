package dev.knative.eventing.source;

import io.quarkus.tls.runtime.keystores.TrustAllOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.ext.web.client.WebClientOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.PropertiesComponent;

/**
 * Knative client options able to configure secure Http transport options.
 * SSL options are added automatically when enabled via system property or environment variable settings.
 */
public class KnativeHttpClientOptions extends WebClientOptions implements CamelContextAware {

    private static final String PROPERTY_PREFIX = "camel.knative.client.ssl.";

    private CamelContext camelContext;

    public KnativeHttpClientOptions() {
    }

    public KnativeHttpClientOptions(CamelContext camelContext) {
        this.camelContext = camelContext;
        configureOptions(camelContext);
    }

    public void configureOptions() {
        if (camelContext == null) {
            throw new RuntimeCamelException("Missing Camel context for Knative Http client options");
        }

        configureOptions(camelContext);
    }

    /**
     * Configures this web client options instance based on properties and environment variables resolved with the given Camel context.
     * @param camelContext
     */
    public void configureOptions(CamelContext camelContext) {
        PropertiesComponent propertiesComponent = camelContext.getPropertiesComponent();

        boolean sslEnabled = Boolean.parseBoolean(
                propertiesComponent.resolveProperty(PROPERTY_PREFIX + "enabled").orElse("false"));

        if (sslEnabled) {
            this.setSsl(true);

            boolean verifyHostname = Boolean.parseBoolean(
                    propertiesComponent.resolveProperty(PROPERTY_PREFIX + "verify.hostname").orElse("true"));
            this.setVerifyHost(verifyHostname);

            String keystorePath = propertiesComponent.resolveProperty(PROPERTY_PREFIX + "keystore.path").orElse("");
            String keystorePassword = propertiesComponent.resolveProperty(PROPERTY_PREFIX + "keystore.password").orElse("");

            if (!keystorePath.isEmpty()) {
                if (keystorePath.endsWith(".p12")) {
                    this.setKeyCertOptions(new PfxOptions().setPath(keystorePath).setPassword(keystorePassword));
                } else {
                    this.setKeyCertOptions(new JksOptions().setPath(keystorePath).setPassword(keystorePassword));
                }
            } else {
                String keyPath = propertiesComponent.resolveProperty(PROPERTY_PREFIX + "key.path").orElse("");
                String keyCertPath = propertiesComponent.resolveProperty(PROPERTY_PREFIX + "key.cert.path").orElse(keyPath);

                if (!keyPath.isEmpty()) {
                    this.setKeyCertOptions(new PemKeyCertOptions().setKeyPath(keyPath).setCertPath(keyCertPath));
                }
            }

            String truststorePath = propertiesComponent.resolveProperty(PROPERTY_PREFIX + "truststore.path").orElse("");
            String truststorePassword = propertiesComponent.resolveProperty(PROPERTY_PREFIX + "truststore.password").orElse("");

            if (!truststorePath.isEmpty()) {
                if (truststorePath.endsWith(".p12")) {
                    this.setTrustOptions(new PfxOptions().setPath(truststorePath).setPassword(truststorePassword));
                } else {
                    this.setTrustOptions(new JksOptions().setPath(truststorePath).setPassword(truststorePassword));
                }
            } else {
                String trustCertPath = propertiesComponent.resolveProperty(PROPERTY_PREFIX + "trust.cert.path").orElse("");

                if (!trustCertPath.isEmpty()) {
                    this.setTrustOptions(new PemTrustOptions().addCertPath(trustCertPath));
                } else {
                    this.setTrustOptions(TrustAllOptions.INSTANCE);
                }
            }
        }
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
        configureOptions();
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }
}
