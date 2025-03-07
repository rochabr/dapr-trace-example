package com.example.subscriber;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.dapr.Topic;
import io.dapr.client.domain.CloudEvent;
import reactor.core.publisher.Mono;

@RestController
public class SubscriberController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);


    @Topic(name = "orders", pubsubName = "pulsar-pubsub")
    @PostMapping(path = "/orders", consumes = "application/cloudevents+json")
    public Mono<Void> receiveOrder(
            @RequestBody(required = false) CloudEvent<Map<String, Object>> cloudEvent,
            @RequestHeader Map<String, String> headers) {
        
        // Extract trace context from headers
        String traceparent = headers.getOrDefault("traceparent", null);
        
        try {
            if (traceparent != null) {
                logger.info("Received traceparent: {}", traceparent);
                
                // Parse the traceparent (format: 00-traceId-spanId-flags)
                String[] parts = traceparent.split("-");
                if (parts.length >= 4) {
                    String traceId = parts[1];
                    String parentSpanId = parts[2];
                    
                    // Log the extraction
                    logger.info("Extracted trace ID: {}, parent span ID: {}", traceId, parentSpanId);
                    
                }
            }
            
            // Process the order
            if (cloudEvent != null && cloudEvent.getData() != null) {
                Map<String, Object> order = cloudEvent.getData();
                String orderId = order.get("orderId") != null ? order.get("orderId").toString() : "unknown";
                
                logger.info("Processing order: ID={}", orderId);
                
                // Simulate processing time
                Thread.sleep(100);
                
                logger.info("Successfully processed order with ID: {}", orderId);
            } else {
                logger.warn("Received empty or invalid order data");
            }
            
            return Mono.empty();
        } catch (Exception e) {
            logger.error("Error processing order", e);
            return Mono.error(e);
        } 
    }
}