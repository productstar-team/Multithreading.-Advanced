
package com.edu.imageconversion.services;

import com.edu.imageconversion.dto.EvaluationRequest;
import com.edu.imageconversion.dto.EvaluationResult;
import com.edu.imageconversion.dto.ParticipantAnswers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.stream.IntStream;
import java.util.concurrent.TimeUnit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestServiceTest {

    private final TestService service = new TestService();

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void shouldEvaluateParticipantsCorrectly() throws InterruptedException {
        EvaluationRequest request = new EvaluationRequest(
                List.of("A", "C", "B"),
                List.of(
                        new ParticipantAnswers(
                                "user1", List.of("A", "C", "B")),
                        new ParticipantAnswers(
                                "user2", List.of("A", "B", "B")),
                        new ParticipantAnswers(
                                "user3", List.of("D", "C", "A"))
                )
        );

        List<EvaluationResult> results = service.evaluate(request);

        assertEquals(3, results.size());
        assertEquals(3, results.get(0).correct());
        assertEquals(2, results.get(1).correct());
        assertEquals(1, results.get(2).correct());
    }

    @Test
    void shouldRejectInvalidAnswers() {
        EvaluationRequest request = new EvaluationRequest(
                List.of("A", "B"),
                List.of(
                        new ParticipantAnswers(
                                "user1", List.of("A"))
                )
        );

        assertThrows(IllegalArgumentException.class,
                () -> service.evaluate(request));
    }

    @Test
    void shouldRejectEmptyParticipants() {
        EvaluationRequest request = new EvaluationRequest(
                List.of("A", "B"),
                List.of()
        );

        assertThrows(IllegalArgumentException.class,
                () -> service.evaluate(request));
    }


    @Test
    void shouldProcessParticipantsUsingMultipleThreads()
            throws InterruptedException {

        List<ParticipantAnswers> participants =
                IntStream.range(0, 8)
                        .mapToObj(i -> new ParticipantAnswers(
                                "user" + i,
                                List.of("A", "C", "B")))
                        .toList();

        EvaluationRequest request = new EvaluationRequest(
                List.of("A", "C", "B"),
                participants
        );

        long start = System.nanoTime();

        List<EvaluationResult> results = service.evaluate(request);

        long elapsed = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - start
        );

        long distinctThreads = results.stream()
                .map(EvaluationResult::threadName)
                .distinct()
                .count();

        assertEquals(8, results.size());
        assertTrue(distinctThreads > 1);

        System.out.println("Processing time: " + elapsed + " ms");
        System.out.println("Threads used: " + distinctThreads);
    }

}
