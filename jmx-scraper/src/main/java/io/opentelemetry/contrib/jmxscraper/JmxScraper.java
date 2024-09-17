/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.contrib.jmxscraper;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.contrib.jmxscraper.client.JmxRemoteClient;
import io.opentelemetry.contrib.jmxscraper.config.ConfigurationException;
import io.opentelemetry.contrib.jmxscraper.config.JmxScraperConfig;
import io.opentelemetry.contrib.jmxscraper.config.JmxScraperConfigFactory;
import io.opentelemetry.instrumentation.jmx.engine.JmxMetricInsight;
import io.opentelemetry.instrumentation.jmx.engine.MetricConfiguration;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;

public class JmxScraper {
  private static final Logger logger = Logger.getLogger(JmxScraper.class.getName());
  private static final int EXECUTOR_TERMINATION_TIMEOUT_MS = 5000;
  private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
  private final JmxScraperConfig config;
  private final JmxRemoteClient client;
  private final JmxMetricInsight service;
  @Nullable private MBeanServerConnection connection;

  /**
   * Main method to create and run a {@link JmxScraper} instance.
   *
   * @param args - must be of the form "-config {jmx_config_path,'-'}"
   */
  @SuppressWarnings({"SystemOut", "SystemExitOutsideMain"})
  public static void main(String[] args) {
    try {
      JmxScraperConfigFactory factory = new JmxScraperConfigFactory();
      JmxScraperConfig config = JmxScraper.createConfigFromArgs(Arrays.asList(args), factory);

      JmxScraper jmxScraper = new JmxScraper(config);
      jmxScraper.start();

      Runtime.getRuntime().addShutdownHook(new Thread(jmxScraper::shutdown));
    } catch (ArgumentsParsingException e) {
      System.err.println(
          "Usage: java -jar <path_to_jmxscraper.jar> "
              + "-config <path_to_config.properties or - for stdin>");
      System.exit(1);
    } catch (ConfigurationException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }
  }

  /**
   * Create {@link JmxScraperConfig} object basing on command line options
   *
   * @param args application commandline arguments
   */
  static JmxScraperConfig createConfigFromArgs(List<String> args, JmxScraperConfigFactory factory)
      throws ArgumentsParsingException, ConfigurationException {
    if (!args.isEmpty() && (args.size() != 2 || !args.get(0).equalsIgnoreCase("-config"))) {
      throw new ArgumentsParsingException();
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

    return factory.createConfig(loadedProperties);
  }

  private static void loadPropertiesFromStdin(Properties props) throws ConfigurationException {
    try (InputStream is = new DataInputStream(System.in)) {
      props.load(is);
    } catch (IOException e) {
      throw new ConfigurationException("Failed to read config properties from stdin", e);
    }
  }

  private static void loadPropertiesFromPath(Properties props, String path)
      throws ConfigurationException {
    try (InputStream is = Files.newInputStream(Paths.get(path))) {
      props.load(is);
    } catch (IOException e) {
      throw new ConfigurationException("Failed to read config properties file: '" + path + "'", e);
    }
  }

  JmxScraper(JmxScraperConfig config) throws ConfigurationException {
    this.config = config;

    String serviceUrl = config.getServiceUrl();
    if (serviceUrl == null) {
      throw new ConfigurationException("missing service URL");
    }
    int interval = config.getIntervalMilliseconds();
    if (interval < 0) {
      throw new ConfigurationException("interval must be positive");
    }
    this.client = JmxRemoteClient.createNew(serviceUrl);
    this.service = JmxMetricInsight.createService(GlobalOpenTelemetry.get(), interval);
  }

  private void start() {
    try {
      JMXConnector connector = client.connect();
      connection = connector.getMBeanServerConnection();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    service.startRemote(getMetricConfig(config), () -> Collections.singletonList(connection));
    logger.info("JMX scraping started");
  }

  @SuppressWarnings("unused")
  private static MetricConfiguration getMetricConfig(JmxScraperConfig config) {
    MetricConfiguration metricConfig = new MetricConfiguration();

    return metricConfig;
  }

  private void shutdown() {
    logger.info("Shutting down JmxScraper and exporting final metrics.");
    // Prevent new tasks to be submitted
    exec.shutdown();
    try {
      // Wait a while for existing tasks to terminate
      if (!exec.awaitTermination(EXECUTOR_TERMINATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
        // Cancel currently executing tasks
        exec.shutdownNow();
        // Wait a while for tasks to respond to being cancelled
        if (!exec.awaitTermination(EXECUTOR_TERMINATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
          logger.warning("Thread pool did not terminate in time: " + exec);
        }
      }
    } catch (InterruptedException e) {
      // (Re-)Cancel if current thread also interrupted
      exec.shutdownNow();
      // Preserve interrupt status
      Thread.currentThread().interrupt();
    }
  }
}
