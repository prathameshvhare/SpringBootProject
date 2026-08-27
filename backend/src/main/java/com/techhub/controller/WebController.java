package com.techhub.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
public class WebController {

    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }

    @GetMapping("/api/status")
    @ResponseBody
    public ResponseEntity<?> rootApiStatus() {
        return ResponseEntity.ok(Map.of(
            "status", "Online",
            "message", "Career Recommendation System REST API Server",
            "version", "1.0"
        ));
    }
}
