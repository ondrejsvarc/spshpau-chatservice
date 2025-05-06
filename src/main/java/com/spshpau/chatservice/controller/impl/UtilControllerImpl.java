package com.spshpau.chatservice.controller.impl;

import com.spshpau.chatservice.controller.UtilController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/util")
@RequiredArgsConstructor
public class UtilControllerImpl implements UtilController {
    @Override
    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("Pong!");
    }

    @Override
    @GetMapping("/auth")
    public ResponseEntity<String> auth() {
        return ResponseEntity.ok("You have sent a valid authentication token!");
    }
}
