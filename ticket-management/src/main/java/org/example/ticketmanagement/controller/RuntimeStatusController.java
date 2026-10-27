package org.example.ticketmanagement.controller;

import org.example.ticketmanagement.pojo.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RuntimeStatusController {

    private final ApplicationAvailability availability;
    private final boolean tradingEnabled;
    private final String instanceId;

    @Value("${customer-service.enabled:true}")
    private boolean customerServiceEnabled;

    public RuntimeStatusController(ApplicationAvailability availability,
                                   @Value("${trade.enabled:false}") boolean tradingEnabled,
                                   @Value("${app.instance-id:default}") String instanceId) {
        this.availability = availability;
        this.tradingEnabled = tradingEnabled;
        this.instanceId = instanceId;
    }

    @GetMapping("/api/public/app-status")
    public Result<Map<String, Object>> status() {
        return Result.success(Map.of(
                "ready", availability.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC,
                "tradingEnabled", tradingEnabled,
                "instanceId", instanceId,
                "customerServiceEnabled", customerServiceEnabled));
    }
}
