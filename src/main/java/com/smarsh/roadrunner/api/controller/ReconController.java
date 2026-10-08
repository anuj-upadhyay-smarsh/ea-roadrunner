package com.smarsh.roadrunner.api.controller;

import com.smarsh.roadrunner.api.service.ReconService;
import com.smarsh.roadrunner.common.model.ReconResult;
import com.smarsh.roadrunner.engine.registry.EngineRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/recon")
@RequiredArgsConstructor
public class ReconController {

    private final ReconService reconService;
    private final EngineRegistry engineRegistry;

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @GetMapping("/engines")
    public ResponseEntity<List<EngineRegistry.EngineInfo>> listEngines() {
        return ResponseEntity.ok(engineRegistry.getEngineInfoList());
    }

    @PostMapping("/execute")
    public ResponseEntity<ReconResult> executeRecon(
            @RequestParam String engineType,
            @RequestBody(required = false) Map<String, Object> parameters,
            @RequestParam(name = "async", defaultValue = "false") boolean async) throws Exception {

        Map<String, Object> params = parameters != null ? parameters : Map.of();

        if (async) {
            reconService.executeReconAsync(engineType, params);
            return ResponseEntity.accepted().build();
        }

        ReconResult result = reconService.executeReconSync(engineType, params);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status/{reconId}")
    public ResponseEntity<ReconResult> getReconStatus(@PathVariable String reconId) {
        return reconService.getReconStatus(reconId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
