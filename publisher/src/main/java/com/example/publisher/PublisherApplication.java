package com.example.publisher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

@SpringBootApplication
public class PublisherApplication {

    public static void main(String[] args) {
        // Initialize OpenTelemetry
        initializeOpenTelemetry();
        
        SpringApplication.run(PublisherApplication.class, args);
    }
    
    private static void initializeOpenTelemetry() {
        // Configure OpenTelemetry to use Zipkin exporter
        Resource resource = Resource.getDefault()
                .merge(Resource.create(
                    io.opentelemetry.api.common.Attributes.of(AttributeKey.stringKey("service.name"), "publisher"        
                )));
        
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(
                        ZipkinSpanExporter.builder()
                                .setEndpoint("http://localhost:9411/api/v2/spans")
                                .build())
                        .build())
                .setResource(resource)
                .build();
        
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();
    }
    
    @Bean
    public Tracer tracer() {
        return GlobalOpenTelemetry.getTracer("publisher-tracer");
    }
}