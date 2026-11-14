
package com.edu.imageconversion.dto;

public record EvaluationResult(
        String participantId,
        int correct,
        int total,
        String threadName
) {
}
