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
        //---- Only enable when the app is behind a trusted proxy; otherwise XFF is spoofable.
        private boolean trustForwardedHeaders = false;
    }

    @Getter
    @Setter
    public static class Retention {
        private int auditDays = 730;
        private int notificationDays = 180;
    }
}
