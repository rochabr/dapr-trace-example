package com.example.subscriber;

import io.dapr.Topic;
import io.dapr.client.domain.CloudEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class SubscriberController {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberController.class);

    @Topic(name = "orders", pubsubName = "pulsar-pubsub")
    @PostMapping(path = "/orders")
    public Mono<Void> receiveOrder(
            @RequestBody(required = false) CloudEvent<Map<String, Object>> cloudEvent,
            @RequestHeader Map<String, String> headers) {
        
        // Log headers to see trace context
        if (headers.containsKey("traceparent")) {
            logger.info("Received traceparent header: {}", headers.get("traceparent"));
        } else {
            logger.warn("No traceparent header received");
        }

        try {
            // Process the order
            if (cloudEvent != null && cloudEvent.getData() != null) {
                Map<String, Object> order = cloudEvent.getData();
                String orderId = order.get("orderId") != null ? order.get("orderId").toString() : "unknown";
                
                logger.info("Processing order: ID={}, Data={}", orderId, order);
                
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