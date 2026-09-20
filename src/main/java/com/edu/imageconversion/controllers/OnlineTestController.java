package com.edu.imageconversion.controllers;

import com.edu.imageconversion.services.OnlineTestService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/test")
public class OnlineTestController {

    private final OnlineTestService onlineTestService;

    public OnlineTestController(OnlineTestService onlineTestService) {
        this.onlineTestService = onlineTestService;
    }

    @PostMapping("/check")
    public List<String> checkAnswers(@RequestBody List<String> answers) {
        return onlineTestService.checkAnswers(answers);
    }
}