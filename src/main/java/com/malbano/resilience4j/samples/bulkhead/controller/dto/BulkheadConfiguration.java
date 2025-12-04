package com.malbano.resilience4j.samples.bulkhead.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkheadConfiguration {
    
    // For Semaphore
    private Integer maxConcurrentCalls;
    private String maxWaitDuration;
    
    // For Thread Pool
    private Integer coreThreadPoolSize;
    private Integer maxThreadPoolSize;
    private Integer queueCapacity;
}