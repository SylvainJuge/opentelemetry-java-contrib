plugins {
  application
  id("com.github.johnrengelman.shadow")

  id("otel.java-conventions")
  id("otel.publish-conventions")
}

description = "JMX metrics scrapper"
otelJava.moduleName.set("io.opentelemetry.contrib.jmxscrapper")

application.mainClass.set("io.opentelemetry.contrib.jmxscrapper.JmxMetrics")

dependencies {
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("io.opentelemetry:opentelemetry-sdk")
  implementation("io.opentelemetry:opentelemetry-sdk-metrics")
  implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  implementation("io.opentelemetry:opentelemetry-sdk-testing")

  implementation("io.opentelemetry.instrumentation:opentelemetry-jmx-metrics")

}

testing {
  suites {
    val integrationTest by registering(JvmTestSuite::class) {
      dependencies {
        implementation("org.testcontainers:junit-jupiter")
        implementation("org.slf4j:slf4j-simple")
      }
    }
  }
}

tasks {

  shadowJar {
    mergeServiceFiles()

    manifest {
      attributes["Implementation-Version"] = project.version
    }
    // This should always be standalone, so remove "-all" to prevent unnecessary artifact.
    archiveClassifier.set("")
  }

  jar {
    archiveClassifier.set("noshadow")
  }

  withType<Test>().configureEach {
    dependsOn(shadowJar)
    dependsOn(named("appJar"))
    systemProperty("shadow.jar.path", shadowJar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("app.jar.path", named<Jar>("appJar").get().archiveFile.get().asFile.absolutePath)
    systemProperty("gradle.project.version", "${project.version}")
  }

  // Because we reconfigure publishing to only include the shadow jar, the Gradle metadata is not correct.
  // Since we are fully bundled and have no dependencies, Gradle metadata wouldn't provide any advantage over
  // the POM anyways so in practice we shouldn't be losing anything.
  withType<GenerateModuleMetadata>().configureEach {
    enabled = false
  }
}

tasks.register<Jar>("appJar") {
  from(sourceSets.get("integrationTest").output)
  archiveClassifier.set("app")
  manifest {
    attributes["Main-Class"] = "io.opentelemetry.contrib.jmxscraper.TestApp"
  }
}


// Don't publish non-shadowed jar (shadowJar is in shadowRuntimeElements)
with(components["java"] as AdhocComponentWithVariants) {
  configurations.forEach {
    withVariantsFromConfiguration(configurations["apiElements"]) {
      skip()
    }
    withVariantsFromConfiguration(configurations["runtimeElements"]) {
      skip()
    }
  }
}
