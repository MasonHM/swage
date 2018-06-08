/*
 * Copyright 2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not
 * use this file except in compliance with the License. A copy of the License
 * is located at
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.amazon.swage.metrics;

import java.time.Instant;

import software.amazon.swage.collection.TypedMap;

/**
 * MetricRecorder is used to record the events that occur as an application is
 * executing.
 *
 * <p>We support two types of measure:
 * <ul>
 *     <li><b>Gauges</b> which record the actual value at a point in time.</li>
 *     <li><b>Counters</b> which record the change in a value.</li>
 * </ul>
 *
 * <p>The metrics captured are associated with the currently executing unit of
 * work (aka task) such as a service request, as captured by a context dimensions object.
 * Recording a metric is done by sending a MetricRecorder instance the current
 * context dimensions and the specific event to record.
 *
 * <p>The MetricRecorder provides a Context abstraction wrapping the context dimensions
 * and a reference to the MetricRecorder for convenient usage - metric-recording
 * tasks need only reference a Context object, with the context dimensions and
 * MetricRecorder being handled by the task defining layer.

 * <p>A recorder instance may also have 'global' context associated with it, such
 * as application name or running host.
 *
 * <p>This object is thread safe.  It can be shared across the lifetime of an
 * application for multiple different tasks/requests.
 *
 * <p>The methods here will not throw Exceptions related to their work.
 * Any problems recording metrics events will not impact the application itself,
 * and so will be ignored/swallowed. Implementations are free to log or take
 * other appropriate action.
 *
 * Usage
 * <pre>
 * {@code
// During app initialization create or inject the appropriate recorder implementation
MetricRecorder recorder = new RecorderImpl();

// Sometime later, per task/request/operation, create an object that captures
// the context and will be provided to the task code
TypedMap contextData = DomainSpecificBuilder
                            .withId(taskId)
                            .somethingElse(somethingElse)
                            .build();
SomeContext metricCtx = recorder.context(contextData);

// Inside task/request/operation, record some metrics
metricCtx.record(StandardMetric.Time, sometime, Unit.MILLISECOND);
 * }
 * </pre>
 *
 * MetricRecorder implementations should provide record() and count() methods.
 * They must ensure that the thread-safety and non-exceptiony promises are kept.
 *
 * <p>There is an implicit assumption that task code will only ever emit to one
 * MetricRecorder instance.  If multiple recording destinations are required, a
 * composite MetricRecorder implementation may be used, fanning out metric events
 * as appropriate.
 *
 * TODO: JSR-305 @ThreadSafe
 * TODO: add explicit shutdown/lifecycle management?
 */
public abstract class MetricRecorder {

    /**
     * Construct a new Context object for the given dimensions.
     *
     * The returned Context is a light wrapper around the provided dimensions object.
     * Context objects created with the same dimensions object will be functionally
     * equivalent, and MetricRecorder implementations are free to return the
     * same object if called multiple times with the same dimensions object.
     * The logical context scope is defined by object identity on the context
     * dimensions object.  Two context dimensions objects with the same values will be
     * treated as different contexts.
     *
     * @param metadata An object to be used for identifying the Context.
     * @return A new Context object wrapping the provided dimensions object.
     */
    public MetricContext context(TypedMap metadata) {
        return new MetricContext(this, metadata);
    }


    /**
     * Record the value of a specific metric, as gauged at a specific time
     * in the given context.
     *
     * This method will fail silently, as application code is not expected
     * to depend on success or failure.
     *
     * @param label The metric being recorded.
     * @param value Gauged value, with units.
     * @param unit  Type of the value, e.g. seconds, percent, etc.
     * @param time  When the value was sampled.
     * @param context Identifying dimensions about context the value belongs in.
     */
    protected abstract void record(
            Metric label,
            Number value,
            Unit unit,
            Instant time,
            MetricContext context);

    /**
     * Count the increase or decrease of a metric in the given context.
     *
     * These are explicitly aggregated values, where only the total number of
     * occurrences in a context matter.
     * Examples include counting number of times a method is called, or keeping
     * track of the number of errors encountered, or the amount of dimensions sent
     * while in the given context.
     *
     * If you need to distinguish between occurrences of an event, or care that
     * an event did not occur (for ratios of success for example) then you want
     * to record those individually and not use a count.
     * Changes to the count are not timestamped as only the total value of all
     * counts for a metric have any meaning - if the individual change needs to
     * be tracked, you probably want to record the change as a gauged event.
     *
     * This method will fail silently, as application code is not expected
     * to depend on success or failure.
     *
     * @param label the metric to capture
     * @param delta the increase (or decrease) in the value
     * @param context the context the value belongs in.
     */
    protected abstract void count(Metric label, long delta, MetricContext context);


    /**
     * Indicate that the task represented by the given context is finished, and
     * will emit no more metric events.
     *
     * Once close() is called for a given context, the MetricRecorder may clean
     * up, aggregate, and/or flush any dimensions associated with that context.
     *
     * After close() is called for a given context, any subsequent calls to this
     * recorder referencing that context will result in undefined behavior.
     * MetricRecorder implementations may throw an IllegalStateException in such
     * a case, but have no obligation to do so.
     *
     * The logical context scope is defined by object identity on the context
     * dimensions object.  Two context dimensions objects with the same values will be
     * treated as different contexts.
     *
     * The default implementation of this method is a no-op.  MetricRecorder
     * implementations should implement this method as appropriate, but may
     * ignore if no buffering or aggregation is present.
     *
     * @param context The context being closed.
     */
    protected void close(MetricContext context) {
    }

}
