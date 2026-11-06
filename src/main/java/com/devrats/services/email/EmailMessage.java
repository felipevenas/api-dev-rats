package com.devrats.services.email;

import java.util.List;

public record EmailMessage(
        String from,
        String to,
        String subject,
        String html,
        String text,
        String replyTo,
        String idempotencyKey,
        List<EmailTag> tags
) {

    public EmailMessage {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
