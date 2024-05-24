import java.util.*

plugins {
  id("otel.java-conventions")
}

description = "OpenTelemetry Java span stacktrace capture module"

dependencies {
  api("io.opentelemetry:opentelemetry-sdk")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")

  compileOnly("io.opentelemetry.semconv:opentelemetry-semconv-incubating:1.25.0-alpha")

  compileOnly(project(":semconv-processor"))
  annotationProcessor(project(":semconv-processor"))
}

tasks {
  compileJava {
    options.forkOptions.jvmArgs?.addAll(Arrays.asList("--module-path", ""));

//    options.forkOptions.jvmArgs?.add("-Xdebug")
//    options.forkOptions.jvmArgs?.add("-Xrunjdwp:transport=dt_socket,server=n,address=localhost:5005,suspend=y")
  }
}
