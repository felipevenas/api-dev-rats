package com.devstreak.controllers;

import com.devstreak.services.WebhookIngestionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/github")
public class GitHubWebhookController {

    private final WebhookIngestionService ingestionService;

    public GitHubWebhookController(WebhookIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody byte[] payload,
                                        @RequestHeader("X-Hub-Signature-256") String signature,
                                        @RequestHeader("X-GitHub-Delivery") String deliveryId,
                                        @RequestHeader("X-GitHub-Event") String eventType) {
        ingestionService.ingest(deliveryId, eventType, payload, signature);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
