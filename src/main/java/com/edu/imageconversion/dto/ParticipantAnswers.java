
package com.edu.imageconversion.dto;

import java.util.List;

public record ParticipantAnswers(
        String participantId,
        List<String> answers
) {
}
