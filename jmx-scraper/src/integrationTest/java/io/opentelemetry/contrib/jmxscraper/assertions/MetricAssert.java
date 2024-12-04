/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.contrib.jmxscraper.assertions;

import static io.opentelemetry.contrib.jmxscraper.assertions.DataPointAttributes.attribute;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.internal.Integers;
import org.assertj.core.internal.Iterables;
import org.assertj.core.internal.Objects;

public class MetricAssert extends AbstractAssert<MetricAssert, Metric> {

  private static final Objects objects = Objects.instance();
  private static final Iterables iterables = Iterables.instance();
  private static final Integers integers = Integers.instance();

  private boolean strict;

  private boolean descriptionChecked;
  private boolean unitChecked;
  private boolean typeChecked;
  private boolean dataPointAttributesChecked;

  MetricAssert(Metric actual) {
    super(actual, MetricAssert.class);
  }

  public void setStrict(boolean strict) {
    this.strict = strict;
  }

  public void strictCheck() {
    strictCheck("description", /* expectedCheckStatus= */ true, descriptionChecked);
    strictCheck("unit", /* expectedCheckStatus= */ true, unitChecked);
    strictCheck("type", /* expectedCheckStatus= */ true, typeChecked);
    strictCheck(
        "data point attributes", /* expectedCheckStatus= */ true, dataPointAttributesChecked);
  }

  private void strictCheck(
      String metricProperty, boolean expectedCheckStatus, boolean actualCheckStatus) {
    if (!strict) {
      return;
    }
    String failMsgPrefix = expectedCheckStatus ? "Missing" : "Duplicate";
    info.description(
        "%s assertion on %s for metric '%s'", failMsgPrefix, metricProperty, actual.getName());
    objects.assertEqual(info, actualCheckStatus, expectedCheckStatus);
  }

  /**
   * Verifies metric description
   *
   * @param description expected description
   * @return this
   */
  @CanIgnoreReturnValue
  public MetricAssert hasDescription(String description) {
    isNotNull();

    info.description("unexpected description for metric '%s'", actual.getName());
    objects.assertEqual(info, actual.getDescription(), description);
    strictCheck("description", /* expectedCheckStatus= */ false, descriptionChecked);
    descriptionChecked = true;
    return this;
  }

  /**
   * Verifies metric unit
   *
   * @param unit expected unit
   * @return this
   */
  @CanIgnoreReturnValue
  public MetricAssert hasUnit(String unit) {
    isNotNull();

    info.description("unexpected unit for metric '%s'", actual.getName());
    objects.assertEqual(info, actual.getUnit(), unit);
    strictCheck("unit", /* expectedCheckStatus= */ false, unitChecked);
    unitChecked = true;
    return this;
  }

  /**
   * Verifies the metric is a gauge
   *
   * @return this
   */
  @CanIgnoreReturnValue
  public MetricAssert isGauge() {
    isNotNull();

    info.description("gauge expected for metric '%s'", actual.getName());
    objects.assertEqual(info, actual.hasGauge(), true);
    strictCheck("type", /* expectedCheckStatus= */ false, typeChecked);
    typeChecked = true;
    return this;
  }

  @CanIgnoreReturnValue
  private MetricAssert hasSum(boolean monotonic) {
    isNotNull();

    info.description("sum expected for metric '%s'", actual.getName());
    objects.assertEqual(info, actual.hasSum(), true);

    String sumType = monotonic ? "monotonic" : "non-monotonic";
    info.description("sum for metric '%s' is expected to be %s", actual.getName(), sumType);
    objects.assertEqual(info, actual.getSum().getIsMonotonic(), monotonic);
    return this;
  }

  /**
   * Verifies the metric is a counter
   *
   * @return this
   */
  @CanIgnoreReturnValue
  public MetricAssert isCounter() {
    // counters have a monotonic sum as their value can't decrease
    hasSum(true);
    strictCheck("type", /* expectedCheckStatus= */ false, typeChecked);
    typeChecked = true;
    return this;
  }

  /**
   * Verifies the metric is an up-down counter
   *
   * @return this
   */
  @CanIgnoreReturnValue
  public MetricAssert isUpDownCounter() {
    // up down counters are non-monotonic as their value can increase & decrease
    hasSum(false);
    strictCheck("type", /* expectedCheckStatus= */ false, typeChecked);
    typeChecked = true;
    return this;
  }

  /**
   * Verifies that there is no attribute in any of data points.
   *
   * @return this
   */
  @CanIgnoreReturnValue
  public MetricAssert hasDataPointsWithoutAttributes() {
    isNotNull();

    return checkDataPoints(
        dataPoints -> {
          dataPointsCommonCheck(dataPoints);

          // all data points must not have any attribute
          for (NumberDataPoint dataPoint : dataPoints) {
            info.description(
                "no attribute expected on data point for metric '%s'", actual.getName());
            iterables.assertEmpty(info, dataPoint.getAttributesList());
          }
        });
  }

  @CanIgnoreReturnValue
  private MetricAssert checkDataPoints(Consumer<List<NumberDataPoint>> listConsumer) {
    // in practice usually one set of data points is provided but the
    // protobuf does not enforce that, so we have to ensure checking at least one
    int count = 0;
    if (actual.hasGauge()) {
      count++;
      listConsumer.accept(actual.getGauge().getDataPointsList());
    }
    if (actual.hasSum()) {
      count++;
      listConsumer.accept(actual.getSum().getDataPointsList());
    }
    info.description("at least one set of data points expected for metric '%s'", actual.getName());
    integers.assertGreaterThan(info, count, 0);

    strictCheck(
        "data point attributes", /* expectedCheckStatus= */ false, dataPointAttributesChecked);
    dataPointAttributesChecked = true;
    return this;
  }

  // TODO: To be removed and calls will be replaced with hasDataPointsWithAttributes()
  @CanIgnoreReturnValue
  public MetricAssert hasTypedDataPoints(Collection<String> types) {
    return checkDataPoints(
        dataPoints -> {
          dataPointsCommonCheck(dataPoints);

          Set<String> foundValues = new HashSet<>();
          for (NumberDataPoint dataPoint : dataPoints) {
            List<KeyValue> attributes = dataPoint.getAttributesList();

            info.description(
                "expected exactly one 'name' attribute for typed data point in metric '%s'",
                actual.getName());
            iterables.assertHasSize(info, attributes, 1);

            objects.assertEqual(info, attributes.get(0).getKey(), "name");
            foundValues.add(attributes.get(0).getValue().getStringValue());
          }
          info.description(
              "missing or unexpected type attribute for metric '%s'", actual.getName());
          iterables.assertContainsExactlyInAnyOrder(info, foundValues, types.toArray());
        });
  }

  private void dataPointsCommonCheck(List<NumberDataPoint> dataPoints) {
    info.description("unable to retrieve data points from metric '%s'", actual.getName());
    objects.assertNotNull(info, dataPoints);

    // at least one data point must be reported
    info.description("at least one data point expected for metric '%s'", actual.getName());
    iterables.assertNotEmpty(info, dataPoints);
  }

  /**
   * Verifies that all data points have the same expected one attribute
   *
   * @param expectedAttribute expected attribute
   * @return this
   */
  @CanIgnoreReturnValue
  public final MetricAssert hasDataPointsWithOneAttribute(AttributeMatcher expectedAttribute) {
    return hasDataPointsWithAttributes(Collections.singleton(expectedAttribute));
  }

  /**
   * Verifies that every provided attribute matcher set matches attributes of at least one metric
   * data point. Attribute matcher set is considered matching data point attributes if each matcher
   * matches exactly one data point attribute
   *
   * @param attributeSets array of attribute matcher sets
   * @return this
   */
  @SafeVarargs
  @CanIgnoreReturnValue
  @SuppressWarnings("varargs") // required to avoid warning
  public final MetricAssert hasDataPointsWithAttributes(Set<AttributeMatcher>... attributeSets) {
    return checkDataPoints(
        dataPoints -> {
          dataPointsCommonCheck(dataPoints);

          boolean[] matchedSets = new boolean[attributeSets.length];

          // validate each datapoint attributes match exactly one of the provided attributes sets
          for (NumberDataPoint dataPoint : dataPoints) {
            Set<AttributeMatcher> dataPointAttributes = toSet(dataPoint.getAttributesList());
            int matchCount = 0;
            for (int i = 0; i < attributeSets.length; i++) {
              if (attributeSets[i].equals(dataPointAttributes)) {
                matchedSets[i] = true;
                matchCount++;
              }
            }

            info.description(
                "data point attributes '%s' for metric '%s' must match exactly one of the attribute sets '%s'",
                dataPointAttributes, actual.getName(), Arrays.asList(attributeSets));
            integers.assertEqual(info, matchCount, 1);
          }

          // check that all attribute sets matched at least once
          for (int i = 0; i < matchedSets.length; i++) {
            info.description(
                "no data point matched attribute set '%s' for metric '%s'",
                attributeSets[i], actual.getName());
            objects.assertEqual(info, matchedSets[i], true);
          }
        });
  }

  private static Set<AttributeMatcher> toSet(List<KeyValue> list) {
    return list.stream()
        .map(entry -> attribute(entry.getKey(), entry.getValue().getStringValue()))
        .collect(Collectors.toSet());
  }
}
