/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.opentracingshim;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import java.util.Objects;

/** 
 * Builder for creating an OpenTracing {@link io.opentracing.Tracer} that is implemented using the
 * OpenTelemetry APIs.
 */
public class OpenTracingShimBuilder{
    private Tracer tracer = getTracer(GlobalOpenTelemetry.getTracerProvider());
    private OpenTracingPropagators propagators = OpenTracingPropagators.builder().build();

    /** Returns a new {@code OpenTracingShimBuilder} instance. */
    public static OpenTracingShimBuilder builder(){
        return new OpenTracingShimBuilder();
    }

    /** Set a {@code Tracer} instance to create this shim. */
    public OpenTracingShimBuilder setTracer(Tracer tracer){
        Objects.requireNonNull(tracer, "tracer");
        this.tracer = tracer;
        return this;
    }
    
    /** Set a {@code OpenTracingPropagators} instance to create this shim. */
    public OpenTracingShimBuilder setPropagators(OpenTracingPropagators propagators){
        Objects.requireNonNull(propagators, "propagators");
        this.propagators = propagators;
        return this;
    }
    
    /**
     * Set a {@code OpenTelemetry} instance to create this shim.
     * The {@code Tracer} and {@code OpenTracingPropagators} instances required to
     * create the shim will be obtained from the {@code OpenTelemetry}.
     */
    public OpenTracingShimBuilder setOpentelemetry(OpenTelemetry openTelemetry){
        setTracer(getTracer(openTelemetry.getTracerProvider()));
        setPropagators(OpenTracingPropagators.builder()
            .setTextMap(openTelemetry.getPropagators().getTextMapPropagator())
            .setHttpHeaders(openTelemetry.getPropagators().getTextMapPropagator())
            .build());
        return this;
    }

   /**
    * Constructs a new instance of OpenTracing {@link io.opentracing.Tracer} based on the
    * builder's values.
    *
    * @return a {@code io.opentracing.Tracer}.
    */
    public io.opentracing.Tracer build(){
        return new TracerShim(new TelemetryInfo(tracer, propagators));
    }

    private static Tracer getTracer(TracerProvider tracerProvider) {
        return tracerProvider.get("opentracingshim");
    }
}
