package com.devrats.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private final Cookie cookie = new Cookie();
    private final Auth auth = new Auth();
    private final Cors cors = new Cors();
    private final Groups groups = new Groups();
    private final Challenges challenges = new Challenges();
    private final Webhook webhook = new Webhook();
    private final Reconciliation reconciliation = new Reconciliation();
    private final RateLimit rateLimit = new RateLimit();
    private final Retention retention = new Retention();
    private final Storage storage = new Storage();
    private final Friends friends = new Friends();
    private final Presence presence = new Presence();
    private final Social social = new Social();
    private final Chat chat = new Chat();
    private String frontendUrl = "http://localhost:5173";

    @Getter
    @Setter
    public static class Cookie {
        private boolean secure = true;
    }

    @Getter
    @Setter
    public static class Auth {
        //---- Local development only: returns the reset token in the response when no email provider exists.
        private boolean exposeResetToken = false;
        private Duration resetTokenTtl = Duration.ofMinutes(30);
        private Duration resetRequestCooldown = Duration.ofMinutes(1);
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
    }

    @Getter
    @Setter
    public static class Groups {
        private int maxMembers = 20;
    }

    @Getter
    @Setter
    public static class Challenges {
        private int minTargetDays = 1;
        private int maxTargetDays = 365;
        private int maxRepositories = 3;
        private Duration commitClockSkew = Duration.ofMinutes(5);
        //---- A push with many backdated commits must not fabricate days.
        private Duration maxCommitAge = Duration.ofHours(48);
    }

    @Getter
    @Setter
    public static class Webhook {
        private long maxPayloadBytes = 1_048_576;
        private int retentionDays = 90;
    }

    @Getter
    @Setter
    public static class Reconciliation {
        private Duration interval = Duration.ofMinutes(10);
        private Duration initialLookback = Duration.ofDays(2);
        private int pageSize = 100;
    }

    @Getter
    @Setter
    public static class RateLimit {
        private int authCapacity = 10;
        private Duration authRefill = Duration.ofMinutes(1);
        private int webhookCapacity = 120;
        private Duration webhookRefill = Duration.ofMinutes(1);
        private int globalCapacity = 300;
        private Duration globalRefill = Duration.ofMinutes(1);
        //---- Social buckets are per IP; the per-user limits live in Social.
        private int searchCapacity = 60;
        private Duration searchRefill = Duration.ofMinutes(1);
        private int friendRequestCapacity = 20;
        private Duration friendRequestRefill = Duration.ofHours(1);
        private int heartbeatCapacity = 60;
        private Duration heartbeatRefill = Duration.ofMinutes(1);
        //---- Only enable when the app is behind a trusted proxy; otherwise XFF is spoofable.
        private boolean trustForwardedHeaders = false;
    }

    @Getter
    @Setter
    public static class Friends {
        private int maxPerUser = 1000;
        private int maxPendingOutgoing = 50;
        //---- Cooldown do par após DECLINED, com mensagem neutra para não confirmar a recusa.
        private Duration declinedCooldown = Duration.ofHours(72);
    }

    @Getter
    @Setter
    public static class Presence {
        private Duration onlineWindow = Duration.ofMinutes(2);
        private Duration awayWindow = Duration.ofMinutes(10);
    }

    @Getter
    @Setter
    public static class Social {
        private int searchPerMinute = 20;
        private int profilePerMinute = 60;
        private int friendRequestPerHour = 10;
    }

    @Getter
    @Setter
    public static class Retention {
        private int auditDays = 730;
        private int notificationDays = 180;
        private int presenceDays = 30;
        private int socialTimelineDays = 180;
    }

    @Getter
    @Setter
    public static class Chat {
        private int maxTextChars = 2000;
        private int pageSize = 30;
        private int maxPageSize = 100;
        private long maxImageBytes = 8_388_608;
        private long maxVoiceBytes = 16_777_216;
        private long maxFileBytes = 20_971_520;
        private int maxVoiceSeconds = 300;
        private int messagesPerMinute = 30;
        private int uploadsPerMinute = 10;
        private Duration typingThrottle = Duration.ofSeconds(2);
        private Duration attachmentUrlTtl = Duration.ofMinutes(15);
        private int maxSessionsPerUser = 5;
        private final ChatStorage storage = new ChatStorage();
    }

    @Getter
    @Setter
    public static class ChatStorage {
        private String bucket;
        private String keyPrefix = "chat";

        public boolean isConfigured() {
            return bucket != null && !bucket.isBlank();
        }
    }

    @Getter
    @Setter
    public static class Storage {
        private String endpoint;
        //---- R2 requires "auto"; AWS ignores this when an endpoint override is set.
        private String region = "auto";
        private String bucket;
        private String accessKey;
        private String secretKey;
        private String publicBaseUrl;
        private String keyPrefix = "avatars";
        private long maxAvatarBytes = 2_097_152;

        public boolean isConfigured() {
            return bucket != null && !bucket.isBlank()
                    && hasCredentials()
                    && publicBaseUrl != null && !publicBaseUrl.isBlank();
        }

        public boolean hasCredentials() {
            return accessKey != null && !accessKey.isBlank()
                    && secretKey != null && !secretKey.isBlank();
        }
    }
}
