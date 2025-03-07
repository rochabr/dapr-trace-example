package com.example.publisher;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.dapr.client.DaprClient;
import io.dapr.client.DaprClientBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import reactor.core.publisher.Mono;

@RestController
public class PublisherController {

    private static final Logger logger = LoggerFactory.getLogger(PublisherController.class);
    private static final String PUBSUB_NAME = "pulsar-pubsub";
    private static final String TOPIC_NAME = "orders";

    private final DaprClient daprClient;
    private final Tracer tracer;

    @Autowired
    public PublisherController(Tracer tracer) {
        this.daprClient = new DaprClientBuilder().build();
        this.tracer = tracer;
    }

    @GetMapping("/publish")
    public Mono<String> publishEvent() {
        // Create a custom trace ID and span ID
        String customTraceId = generateCustomTraceId();
        String customSpanId = generateCustomSpanId();
        
        logger.info("Generated custom trace ID: {}, span ID: {}", customTraceId, customSpanId);

        // Create a custom span context with your trace ID
        SpanContext customSpanContext = SpanContext.create(
                customTraceId,
                customSpanId,
                TraceFlags.getSampled(),
                TraceState.getDefault()
        );

        // Create a span with the custom context
        Span span = tracer.spanBuilder("publish-event")
                .setParent(Context.current().with(Span.wrap(customSpanContext)))
                .setSpanKind(SpanKind.PRODUCER)
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            // Verify the span has our custom trace ID
            SpanContext spanContext = span.getSpanContext();
            logger.info("Current span trace ID: {}, span ID: {}", 
                    spanContext.getTraceId(), spanContext.getSpanId());
            
            // Create order data
            Map<String, Object> order = new HashMap<>();
            String orderId = UUID.randomUUID().toString();
            order.put("orderId", orderId);
            order.put("customer", "test-customer");
            order.put("amount", 100.00);
            order.put("timestamp", System.currentTimeMillis());
            
            // Setting trace parent in the metadata to modify
            Map<String, String> metadata = new HashMap<>();
            String traceparent = String.format("00-%s-%s-%02x", 
                    spanContext.getTraceId(), 
                    spanContext.getSpanId(),
                    spanContext.getTraceFlags().asByte());
            metadata.put("cloudevent.traceparent", traceparent);
            
            logger.info("Publishing with traceparent: {}", traceparent);
            
            // Publish event with metadata
            return daprClient.publishEvent(
                    PUBSUB_NAME,
                    TOPIC_NAME,
                    order,
                    metadata
            ).then(Mono.fromCallable(() -> {
                String response = "Published order with ID: " + orderId + 
                        ", custom trace ID: " + customTraceId;
                span.setStatus(StatusCode.OK);
                logger.info(response);
                return response;
            }));
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            logger.error("Error publishing event", e);
            return Mono.error(e);
        } finally {
            span.end();
        }
    }
    
    // Helper methods to generate trace and span IDs in the correct format
    private String generateCustomTraceId() {
        // Must be 32 hex characters (16 bytes)
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String uuid2 = UUID.randomUUID().toString().replace("-", "");
        return uuid + uuid2.substring(0, 32 - uuid.length());
    }

    private String generateCustomSpanId() {
        // Must be 16 hex characters (8 bytes)
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}