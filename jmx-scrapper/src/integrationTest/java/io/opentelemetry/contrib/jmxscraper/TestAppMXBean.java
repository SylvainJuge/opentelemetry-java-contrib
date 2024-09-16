package io.opentelemetry.contrib.jmxscraper;

@SuppressWarnings("unused")
public interface TestAppMXBean {

  int getIntValue();

  void stopApp();
}
