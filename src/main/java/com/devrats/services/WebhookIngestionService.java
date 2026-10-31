package com.devrats.services;

public interface WebhookIngestionService {

    void ingest(String deliveryId, String eventType, byte[] payload, String signature);
}
