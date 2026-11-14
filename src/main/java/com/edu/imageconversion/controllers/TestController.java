
package com.edu.imageconversion.controllers;

import com.edu.imageconversion.dto.EvaluationRequest;
import com.edu.imageconversion.services.TestService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tests")
public class TestController {

    private final TestService testService;

    public TestController(TestService testService) {
        this.testService = testService;
    }

    @Operation(summary = "Evaluate participant answers in parallel")
    @PostMapping("/evaluate")
    public ResponseEntity<?> evaluate(
            @RequestBody EvaluationRequest request) {

        try {
            return ResponseEntity.ok(testService.evaluate(request));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(503)
                    .body(Map.of("error", "Evaluation interrupted"));
        }
    }
}
