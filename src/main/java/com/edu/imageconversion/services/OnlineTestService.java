package com.edu.imageconversion.services;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class OnlineTestService {

    public List<String> checkAnswers(List<String> answers) {
        ExecutorService executorService = Executors.newFixedThreadPool(3);

        try {
            List<Future<String>> futures = new ArrayList<>();

            for (String answer : answers) {
                Future<String> future = executorService.submit(() ->
                        checkAnswer(answer)
                );

                futures.add(future);
            }

            List<String> results = new ArrayList<>();

            for (Future<String> future : futures) {
                results.add(future.get());
            }

            return results;

        } catch (Exception e) {
            throw new RuntimeException("Failed to check answers", e);
        } finally {
            executorService.shutdown();
        }
    }

    private String checkAnswer(String answer) {
        String threadName = Thread.currentThread().getName();

        return "Answer: " + answer
                + " | checked by: " + threadName;
    }
}