/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.contrib.jmxscraper.config;

import static io.opentelemetry.contrib.jmxscraper.util.StringUtils.isBlank;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

@SuppressWarnings({"SystemOut", "SystemExitOutsideMain"})
public class JmxScraperConfigFactory {
  private static final String PREFIX = "otel.";
  private static final String SERVICE_URL = PREFIX + "jmx.service.url";
  private static final String CUSTOM_JMX_SCRAPING_CONFIG =
      PREFIX + "jmx.custom.jmx.scraping.config";
  private static final String TARGET_SYSTEM = PREFIX + "jmx.target.system";
  private static final String INTERVAL_MILLISECONDS = PREFIX + "jmx.interval.milliseconds";
  private static final String METRICS_EXPORTER_TYPE = PREFIX + "metrics.exporter";
  private static final String EXPORTER = PREFIX + "exporter.";
  private static final String REGISTRY_SSL = PREFIX + "jmx.remote.registry.ssl";
  private static final String EXPORTER_INTERVAL = PREFIX + "metric.export.interval";

  private static final String OTLP_ENDPOINT = EXPORTER + "otlp.endpoint";

  private static final String PROMETHEUS_HOST = EXPORTER + "prometheus.host";
  private static final String PROMETHEUS_PORT = EXPORTER + "prometheus.port";

  private static final String JMX_USERNAME = PREFIX + "jmx.username";
  private static final String JMX_PASSWORD = PREFIX + "jmx.password";
  private static final String JMX_REMOTE_PROFILE = PREFIX + "jmx.remote.profile";
  private static final String JMX_REALM = PREFIX + "jmx.realm";

  // These properties need to be copied into System Properties if provided via the property
  // file so that they are available to the JMX Connection builder
  private static final List<String> JAVA_SYSTEM_PROPERTIES =
      Arrays.asList(
          "javax.net.ssl.keyStore",
          "javax.net.ssl.keyStorePassword",
          "javax.net.ssl.keyStoreType",
          "javax.net.ssl.trustStore",
          "javax.net.ssl.trustStorePassword",
          "javax.net.ssl.trustStoreType");

  private static final List<String> AVAILABLE_TARGET_SYSTEMS =
      Arrays.asList(
          "activemq",
          "cassandra",
          "hbase",
          "hadoop",
          "jetty",
          "jvm",
          "kafka",
          "kafka-consumer",
          "kafka-producer",
          "solr",
          "tomcat",
          "wildfly");

  private Properties properties = new Properties();

  /**
   * Create {@link JmxScraperConfig} object basing on command line options
   *
   * @param args application commandline arguments
   */
  public JmxScraperConfig createConfigFromArgs(List<String> args) {
    if (!args.isEmpty() && (args.size() != 2 || !args.get(0).equalsIgnoreCase("-config"))) {
      System.out.println(
          "Usage: java io.opentelemetry.contrib.jmxscraper.JmxScraper "
              + "-config <path_to_config.properties or - for stdin>");
      System.exit(1);
    }

    Properties loadedProperties = new Properties();
    if (args.size() == 2) {
      String path = args.get(1);
      if (path.trim().equals("-")) {
        loadPropertiesFromStdin(loadedProperties);
      } else {
        loadPropertiesFromPath(loadedProperties, path);
      }
    }

    JmxScraperConfig config = createConfig(loadedProperties);
    validateConfig(config);
    return config;
  }

  private static void loadPropertiesFromStdin(Properties props) {
    try (InputStream is = new DataInputStream(System.in)) {
      props.load(is);
    } catch (IOException e) {
      System.out.println("Failed to read config properties from stdin: " + e.getMessage());
      System.exit(1);
    }
  }

  private static void loadPropertiesFromPath(Properties props, String path) {
    try (InputStream is = Files.newInputStream(Paths.get(path))) {
      props.load(is);
    } catch (IOException e) {
      System.out.println(
          "Failed to read config properties file at '" + path + "': " + e.getMessage());
      System.exit(1);
    }
  }

  JmxScraperConfig createConfig(Properties props) {
    properties = new Properties();
    // putAll() instead of using constructor defaults
    // to ensure they will be recorded to underlying map
    properties.putAll(props);

    // command line takes precedence so replace any that were specified via config file properties
    properties.putAll(System.getProperties());

    JmxScraperConfig config = new JmxScraperConfig();

    config.serviceUrl = properties.getProperty(SERVICE_URL);
    config.customJmxScrapingConfig = properties.getProperty(CUSTOM_JMX_SCRAPING_CONFIG);
    config.targetSystem =
        properties.getProperty(TARGET_SYSTEM, "").toLowerCase(Locale.ENGLISH).trim();

    List<String> targets =
        Arrays.asList(
            isBlank(config.targetSystem) ? new String[0] : config.targetSystem.split(","));
    config.targetSystems = new LinkedHashSet<>(targets);

    int interval = getProperty(INTERVAL_MILLISECONDS, 10000);
    config.intervalMilliseconds = interval == 0 ? 10000 : interval;
    // set for autoconfigure usage
    getAndSetProperty(EXPORTER_INTERVAL, config.intervalMilliseconds);

    config.metricsExporterType = getAndSetProperty(METRICS_EXPORTER_TYPE, "logging");
    config.otlpExporterEndpoint = properties.getProperty(OTLP_ENDPOINT);

    config.prometheusExporterHost = getAndSetProperty(PROMETHEUS_HOST, "0.0.0.0");
    config.prometheusExporterPort = getAndSetProperty(PROMETHEUS_PORT, 9464);

    config.username = properties.getProperty(JMX_USERNAME);
    config.password = properties.getProperty(JMX_PASSWORD);

    config.remoteProfile = properties.getProperty(JMX_REMOTE_PROFILE);
    config.realm = properties.getProperty(JMX_REALM);

    config.registrySsl = Boolean.parseBoolean(properties.getProperty(REGISTRY_SSL));

    // For the list of System Properties, if they have been set in the properties file
    // they need to be set in Java System Properties.
    JAVA_SYSTEM_PROPERTIES.forEach(
        key -> {
          // As properties file & command line properties are combined into properties
          // at this point, only override if it was not already set via command line
          if (System.getProperty(key) != null) {
            return;
          }
          String value = properties.getProperty(key);
          if (value != null) {
            System.setProperty(key, value);
          }
        });

    return config;
  }

  private int getProperty(String key, int defaultValue) {
    String propVal = properties.getProperty(key);
    if (propVal == null) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(propVal);
    } catch (NumberFormatException e) {
      throw new ConfigurationException("Failed to parse " + key, e);
    }
  }

  /**
   * Similar to getProperty(key, defaultValue) but sets the property to default if not in object.
   */
  private String getAndSetProperty(String key, String defaultValue) {
    String propVal = properties.getProperty(key, defaultValue);
    if (propVal.equals(defaultValue)) {
      properties.setProperty(key, defaultValue);
    }
    return propVal;
  }

  private int getAndSetProperty(String key, int defaultValue) {
    int propVal = getProperty(key, defaultValue);
    if (propVal == defaultValue) {
      properties.setProperty(key, String.valueOf(defaultValue));
    }
    return propVal;
  }

  /** Will determine if parsed config is complete, setting any applicable values and defaults. */
  void validateConfig(JmxScraperConfig config) {
    if (isBlank(config.serviceUrl)) {
      throw new ConfigurationException(SERVICE_URL + " must be specified.");
    }

    if (isBlank(config.customJmxScrapingConfig) && isBlank(config.targetSystem)) {
      throw new ConfigurationException(
          CUSTOM_JMX_SCRAPING_CONFIG + " or " + TARGET_SYSTEM + " must be specified.");
    }

    if (!config.targetSystems.isEmpty()
        && !AVAILABLE_TARGET_SYSTEMS.containsAll(config.targetSystems)) {
      throw new ConfigurationException(
          String.format(
              "%s must specify targets from %s", config.targetSystems, AVAILABLE_TARGET_SYSTEMS));
    }

    if (isBlank(config.otlpExporterEndpoint)
        && (!isBlank(config.metricsExporterType)
            && config.metricsExporterType.equalsIgnoreCase("otlp"))) {
      throw new ConfigurationException(OTLP_ENDPOINT + " must be specified for otlp format.");
    }

    if (config.intervalMilliseconds < 0) {
      throw new ConfigurationException(INTERVAL_MILLISECONDS + " must be positive.");
    }
  }
}
