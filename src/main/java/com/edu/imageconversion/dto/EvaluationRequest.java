
package com.edu.imageconversion.dto;

import java.util.List;

public record EvaluationRequest(
        List<String> correctAnswers,
        List<ParticipantAnswers> participants
) {
}
