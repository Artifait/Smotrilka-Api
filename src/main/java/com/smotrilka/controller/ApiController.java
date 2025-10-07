package com.smotrilka.controller;

import com.smotrilka.DTOs.RegisterRequest;
import com.smotrilka.DTOs.LinkRequest;
import com.smotrilka.DTOs.SearchResponse;
import com.smotrilka.DTOs.CommentRequest;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.smotrilka.DTOs.ReactionRequest;
import org.springframework.http.HttpStatus;


import com.smotrilka.repository.DatabaseJdbc;

import java.util.Map;


@RestController
@RequestMapping
public class ApiController {
    private final DatabaseJdbc db;
    private final Logger log = LoggerFactory.getLogger(getClass());

    @PostMapping("/favorites/add")
    public ResponseEntity<?> addFavorite(
            @RequestParam String login,
            @RequestParam String password,
            @RequestParam int linkId) {

        boolean added = db.addFavorite(login, password, linkId);

        if (added) {
            return ResponseEntity.ok("Link added to favorites");
        } else {
            return ResponseEntity.badRequest().body("Invalid credentials or link already in favorites");
        }
    }

    @DeleteMapping("/favorites/remove")
    public ResponseEntity<?> removeFavorite(
            @RequestParam String login,
            @RequestParam String password,
            @RequestParam int linkId) {

        boolean removed = db.removeFavorite(login, password, linkId);

        if (removed) {
            return ResponseEntity.ok("Link removed from favorites");
        } else {
            return ResponseEntity.badRequest().body("Invalid credentials or link not found in favorites");
        }
    }

    @GetMapping("/favorites")
    public ResponseEntity<?> getFavorites(@RequestParam int userId) {
        var favorites = db.getFavorites(userId);
        return ResponseEntity.ok(favorites);
    }
    public ApiController(DatabaseJdbc db) {
        this.db = db;
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String q) {
        if (q == null) q = "";
        var results = db.searchLinks(q);
        return ResponseEntity.ok(results);
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
    @PostMapping("/check-username")
    public ResponseEntity<?> checkUsername(@RequestParam String login) {
        boolean taken = db.isUsernameTaken(login);
        return ResponseEntity.ok(Map.of("taken", taken));
    }

    @PostMapping("/link")
    public ResponseEntity<?> addLink(@RequestBody LinkRequest request) {
        if (request.getLogin() == null || request.getPassword() == null ||
                request.getName() == null || request.getLink() == null ||
                request.getTags() == null || request.getTags().isEmpty()) {
            return ResponseEntity.badRequest().body("All fields required (login, password, name, link, tags)");
        }

        if (request.getTags().size() > 10) {
            return ResponseEntity.badRequest().body("Maximum 10 tags allowed");
        }

        boolean ok = db.addLink(request);

        if (ok) {
            log.info("Link '{}' added successfully by user '{}'", request.getName(), request.getLogin());
            return ResponseEntity.ok("Link added");
        } else {
            log.warn("Invalid credentials for user '{}'", request.getLogin());
            return ResponseEntity.status(403).body("Invalid credentials");
        }
    }

    @PostMapping("/comment")
    public ResponseEntity<?> addComment(@RequestBody CommentRequest request) {
        if (request.getLogin() == null || request.getPassword() == null ||
                request.getLinkId() == null || request.getText() == null ||
                request.getText().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("All fields required: login, password, linkId, text");
        }

        boolean ok = db.addComment(request);

        if (!ok) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid credentials or link not found");
        }

        return ResponseEntity.ok("Comment added");
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