package com.malbano.resilience4j.samples.bulkhead.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkheadExecutionReport {
    
    private String bulkheadType;
    private BulkheadConfiguration configuration;
    private Integer totalRequests;
    private Integer succeeded;
    private Integer failed;
    private Long totalDurationMs;
    private List<RequestExecution> executions;
    private String summary;
}