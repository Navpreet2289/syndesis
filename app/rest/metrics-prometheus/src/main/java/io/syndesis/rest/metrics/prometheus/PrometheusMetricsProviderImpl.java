/*
 * Copyright (C) 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.syndesis.rest.metrics.prometheus;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.syndesis.model.metrics.IntegrationDeploymentMetrics;
import io.syndesis.model.metrics.IntegrationMetricsSummary;
import io.syndesis.rest.metrics.MetricsProvider;

@Component
@ConditionalOnProperty(value = "metrics.kind", havingValue = "prometheus")
public class PrometheusMetricsProviderImpl implements MetricsProvider {

    private static final BinaryOperator<Long> SUM_LONGS = (aLong, aLong2) -> aLong == null ? aLong2 :
        aLong2 == null ? aLong : aLong + aLong2;
    private static final BinaryOperator<Date> MAX_DATE = (date1, date2) -> date1 == null ? date2 :
        date2 == null ? date1 : date1.after(date2) ? date1 : date2;

    private final HttpClient httpClient = new HttpClient();

    private final String serviceName;
    private final String integrationIdLabel;
    private final String deploymentVersionLabel;
    private final String metricsHistoryRange;

    protected PrometheusMetricsProviderImpl(PrometheusConfigurationProperties config) {
        this.serviceName = config.getService();
        this.integrationIdLabel = config.getIntegrationIdLabel();
        this.deploymentVersionLabel = config.getDeploymentVersionLabel();
        this.metricsHistoryRange = config.getMetricsHistoryRange();
    }

    @Override
    public IntegrationMetricsSummary getIntegrationMetricsSummary(String integrationId) {

        // aggregate values across versions

        final Map<String, Long> totalMessagesMap = getMetricValues(integrationId,
            "org_apache_camel_ExchangesTotal", deploymentVersionLabel, Long.class, SUM_LONGS);
        final Map<String, Long> failedMessagesMap = getMetricValues(integrationId,
            "org_apache_camel_ExchangesFailed", deploymentVersionLabel, Long.class, SUM_LONGS);

        final Map<String, Date> startTimeMap = getMetricValues(integrationId,
            "io_syndesis_camel_StartTimestamp", deploymentVersionLabel, Date.class, MAX_DATE);
        final Map<String, Date> lastProcessingTimeMap = getMetricValues(integrationId,
            "io_syndesis_camel_LastExchangeCompletedTimestamp", deploymentVersionLabel, Date.class, MAX_DATE);

        return createIntegrationMetricsSummary(totalMessagesMap, failedMessagesMap, startTimeMap,
            lastProcessingTimeMap);
    }

    private IntegrationMetricsSummary createIntegrationMetricsSummary(Map<String, Long> totalMessagesMap,
                                                                      Map<String, Long> failedMessagesMap,
                                                                      Map<String, Date> startTimeMap,
                                                                      Map<String, Date> lastProcessingTimeMap) {

        final long[] totalMessages = {0L};
        final long[] totalErrors = {0L};
        final Date[] startTime = { null };
        final Date[] lastProcessedTime = { null };

        final List<IntegrationDeploymentMetrics> deploymentMetrics = totalMessagesMap.entrySet().stream().map(entry -> {
            final String version = entry.getKey();
            final Long messages = entry.getValue();

            final Long errors = failedMessagesMap.get(version);
            final Date start = startTimeMap.get(version);
            final Date lastProcessed = lastProcessingTimeMap.get(version);

            // aggregate values while we are at it
            totalMessages[0] = SUM_LONGS.apply(totalMessages[0], messages);
            totalErrors[0] = SUM_LONGS.apply(totalErrors[0], errors);
            startTime[0] = MAX_DATE.apply(startTime[0], start);
            lastProcessedTime[0] = MAX_DATE.apply(lastProcessedTime[0], lastProcessed);

            return new IntegrationDeploymentMetrics.Builder()
                .version(version)
                .messages(messages)
                .errors(errors)
                .start(Optional.ofNullable(start))
                .lastProcessed(Optional.ofNullable(lastProcessed))
                .build();
        }).sorted(Comparator.comparing(IntegrationDeploymentMetrics::getVersion)).collect(Collectors.toList());

        return new IntegrationMetricsSummary.Builder()
                .integrationDeploymentMetrics(deploymentMetrics)
                .start(Optional.ofNullable(startTime[0]))
                .lastProcessed(Optional.ofNullable(lastProcessedTime[0]))
                .messages(totalMessages[0])
                .errors(totalErrors[0])
                .build();
    }

    @Override
    public IntegrationMetricsSummary getTotalIntegrationMetricsSummary() {
        final Optional<Long> totalMessages = getMetricValue("org_apache_camel_ExchangesTotal", Long.class, SUM_LONGS);
        final Optional<Long> failedMessages = getMetricValue("org_apache_camel_ExchangesFailed", Long.class, SUM_LONGS);

        final Optional<Date> startTime = getMetricValue("io_syndesis_camel_StartTimestamp", Date.class, MAX_DATE);
        final Optional<Date> lastProcessingTime = getMetricValue("io_syndesis_camel_LastExchangeCompletedTimestamp", Date.class, MAX_DATE);

        return new IntegrationMetricsSummary.Builder()
            .start(startTime)
            .lastProcessed(lastProcessingTime)
            .messages(totalMessages.orElse(0L))
            .errors(failedMessages.orElse(0L))
            .build();
    }

    private <T> Map<String, T> getMetricValues(String integrationId, String metric, String label, Class<? extends T> clazz, BinaryOperator<T> mergeFunction) {
        HttpQuery queryTotalMessages = createHttpQuery(integrationId, metric);
        QueryResult response = httpClient.queryPrometheus(queryTotalMessages);
        validateResponse(response);
        return QueryResult.getValueMap(response, label, clazz, mergeFunction);
    }

    private <T> Optional<T> getMetricValue(String metric, Class<? extends T> clazz, BinaryOperator<T> mergeFunction) {
        HttpQuery queryTotalMessages = createHttpQuery(metric);
        QueryResult response = httpClient.queryPrometheus(queryTotalMessages);
        validateResponse(response);
        return QueryResult.getMergedValue(response, clazz, mergeFunction);
    }

    private HttpQuery createHttpQuery(String integrationId, String metric) {
        return new HttpQuery.Builder()
                .host(serviceName)
                .function("max_over_time")
                .metric(metric)
                .addLabelValues(HttpQuery.LabelValue.Builder.of(integrationId, this.integrationIdLabel))
                .addLabelValues(HttpQuery.LabelValue.Builder.of("context", "type"))
                .range(metricsHistoryRange)
                .build();
    }

    private HttpQuery createHttpQuery(String metric) {
        return new HttpQuery.Builder()
                .host(serviceName)
                .metric(metric)
                .addLabelValues(HttpQuery.LabelValue.Builder.of("integration", "component"))
                .addLabelValues(HttpQuery.LabelValue.Builder.of("context", "type"))
                .build();
    }

    private static void validateResponse(QueryResult response) {
        if (response.isError()) {
            throw new IllegalArgumentException(
                String.format("Error Type: %s, Error: %s", response.getErrorType(), response.getError()));
        }
    }

}
