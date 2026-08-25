package com.techhub.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class WebController {

    @GetMapping("/")
    public ResponseEntity<?> rootApiStatus() {
        return ResponseEntity.ok(Map.of(
            "status", "Online",
            "message", "Career Recommendation System REST API Server",
            "version", "1.0"
        ));
    }
}
