
package com.edu.imageconversion.services;

import com.edu.imageconversion.dto.EvaluationRequest;
import com.edu.imageconversion.dto.EvaluationResult;
import com.edu.imageconversion.dto.ParticipantAnswers;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class TestService {

    private final ExecutorService executor =
            Executors.newFixedThreadPool(4);

    public List<EvaluationResult> evaluate(EvaluationRequest request)
            throws InterruptedException {

        validate(request);

        List<Callable<EvaluationResult>> tasks =
                request.participants().stream()
                        .<Callable<EvaluationResult>>map(participant ->
                                () -> checkAnswers(
                                        participant,
                                        request.correctAnswers()))
                        .toList();

        List<Future<EvaluationResult>> futures =
                executor.invokeAll(tasks);

        List<EvaluationResult> results = new ArrayList<>();

        try {
            for (Future<EvaluationResult> future : futures) {
                results.add(future.get());
            }
        } catch (ExecutionException e) {
            throw new IllegalStateException(
                    "Failed to evaluate answers", e.getCause());
        }

        return results;
    }

    private EvaluationResult checkAnswers(
            ParticipantAnswers participant,
            List<String> correctAnswers) throws InterruptedException {

        int correct = 0;

        for (int i = 0; i < correctAnswers.size(); i++) {
            if (Objects.equals(
                    correctAnswers.get(i),
                    participant.answers().get(i))) {
                correct++;
            }
        }

        // Имитация длительной обработки для демонстрации потоков.
        Thread.sleep(200);

        return new EvaluationResult(
                participant.participantId(),
                correct,
                correctAnswers.size(),
                Thread.currentThread().getName()
        );
    }

    private void validate(EvaluationRequest request) {
        if (request == null
                || request.correctAnswers() == null
                || request.correctAnswers().isEmpty()
                || request.participants() == null
                || request.participants().isEmpty()) {
            throw new IllegalArgumentException(
                    "Questions and participants are required");
        }

        for (ParticipantAnswers participant : request.participants()) {
            if (participant == null
                    || participant.participantId() == null
                    || participant.participantId().isBlank()
                    || participant.answers() == null
                    || participant.answers().size()
                    != request.correctAnswers().size()) {
                throw new IllegalArgumentException(
                        "Invalid participant answers");
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
