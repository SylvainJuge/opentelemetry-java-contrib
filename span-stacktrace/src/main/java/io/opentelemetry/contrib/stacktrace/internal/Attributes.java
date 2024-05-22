package io.opentelemetry.contrib.stacktrace.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.contrib.semconvprocessor.SemconvField;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;

public class Attributes {

  private Attributes() {
  }

  @SemconvField(type = CodeIncubatingAttributes.class, field = "CODE_STACKTRACE")
  public static final AttributeKey<String> CODE_STACKTRACE = AttributeKey.stringKey("code.stacktrace");

}
