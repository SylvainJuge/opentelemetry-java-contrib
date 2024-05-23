package io.opentelemetry.contrib.stacktrace.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.contrib.semconvprocessor.SemconvField;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;

public class Attributes {

  private Attributes() {
  }

  @SemconvField(CodeIncubatingAttributes.class)
  public static final AttributeKey<String> CODE_STACKTRACE = AttributeKey.stringKey("code.stacktrace");

  @SemconvField(value = CodeIncubatingAttributes.class, field = "CODE_STACKTRACE")
  public static final AttributeKey<String> CODE_STACKTRACE_ATTRIBUTE = AttributeKey.stringKey("code.stacktrace");

}
