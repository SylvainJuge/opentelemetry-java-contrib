plugins {
  id("otel.java-conventions")
}

description = "OpenTelemetry Java semconv processor"

dependencies {
  api("io.opentelemetry:opentelemetry-sdk")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")

  compileOnly("com.google.auto.service:auto-service-annotations")
  annotationProcessor("com.google.auto.service:auto-service")
}
