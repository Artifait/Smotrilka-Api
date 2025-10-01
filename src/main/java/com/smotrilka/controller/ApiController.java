package com.smotrilka.controller;

import com.smotrilka.DTOs.RegisterRequest;
import com.smotrilka.DTOs.LinkRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.smotrilka.DTOs.ReactionRequest;
import org.springframework.http.HttpStatus;


import com.smotrilka.repository.DatabaseJdbc;


@RestController
@RequestMapping
public class ApiController {
    private final DatabaseJdbc db;
    private final Logger log = LoggerFactory.getLogger(getClass());

    public ApiController(DatabaseJdbc db) {
        this.db = db;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (request.getLogin() == null || request.getPassword() == null) {
            return ResponseEntity.badRequest().body("Login and password required");
        }
        boolean ok = db.registerUser(request);
        return ok ? ResponseEntity.ok("User registered")
                : ResponseEntity.status(409).body("User already exists");
    }

    @PostMapping("/link")
    public ResponseEntity<?> addLink(@RequestBody LinkRequest request) {
        if (request.getLogin() == null || request.getPassword() == null ||
                request.getName() == null || request.getType() == null || request.getLink() == null) {
            return ResponseEntity.badRequest().body("All fields required");
        }

        boolean ok = db.addLink(request);
        return ok ? ResponseEntity.ok("Link added")
                : ResponseEntity.status(403).body("Invalid credentials");
    }

    @PostMapping("/react")
    public ResponseEntity<?> react(@RequestBody ReactionRequest request) {
        if (request.getLogin() == null || request.getPassword() == null || request.getLinkId() == null || request.getReaction() == null) {
            return ResponseEntity.badRequest().body("All fields required: login,password,linkId,reaction");
        }

        String result = db.processReaction(request);

        switch (result) {
            case "unauthorized":
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid credentials");
            case "link-not-found":
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Link not found");
            case "no-change":
                return ResponseEntity.ok("No change");
            case "added":
                return ResponseEntity.ok("Reaction added");
            case "removed":
                return ResponseEntity.ok("Reaction removed");
            case "changed":
                return ResponseEntity.ok("Reaction changed");
            default:
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unknown result");
        }
    }
}