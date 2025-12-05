package com.malbano.resilience4j.samples.bulkhead.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestExecution {
    
    private Integer requestId;
    private String status;  // SUCCESS or FAILED
    private String threadName;
    private String callingThread;  // Only for thread pool
    private Long durationMs;
    private Long startTime;  // Relative to test start
    private String errorReason;
    private String errorType;
}