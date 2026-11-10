package com.devrats.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class StudyPhotoModeIsolationMigrationTest {

    private static final String CHALLENGE_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PARTICIPANT_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String PARTICIPANT_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String PARTICIPANT_C = "cccccccc-cccc-cccc-cccc-cccccccccccc";

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Test
    void shouldRemoveCommitOnlyDaysAndRecalculatePhotoProgress() throws Exception {
        flyway("7").migrate();
        LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        seed(today);

        flyway("8").migrate();

        try (Connection connection = connection()) {
            assertParticipant(connection, PARTICIPANT_A, 2, 2, 2, today);
            //---- A sequência continua viva quando a última foto foi ontem (StreakHelper).
            assertParticipant(connection, PARTICIPANT_B, 3, 3, 3, today.minusDays(1));
            assertParticipant(connection, PARTICIPANT_C, 0, 0, 0, null);
            assertThat(count(connection,
                    "SELECT count(*) FROM member_daily_activity WHERE challenge_id = '" + CHALLENGE_ID + "'"))
                    .isEqualTo(5);
            assertThat(count(connection,
                    "SELECT count(*) FROM member_daily_activity WHERE challenge_id = '" + CHALLENGE_ID
                            + "' AND valid_commit_count > 0"))
                    .isZero();
            assertThat(count(connection,
                    "SELECT count(*) FROM challenge_repositories WHERE challenge_id = '" + CHALLENGE_ID + "'"))
                    .isZero();
        }
    }

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void seed(LocalDate today) throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("SET session_replication_role = replica");
            statement.execute("""
                    INSERT INTO challenges (id, group_id, sequence_no, verification_mode, target_valid_days,
                                            timezone, status, starts_at, tiebreak_rule, created_by_user_id,
                                            created_at, updated_at)
                    VALUES ('%s', '22222222-2222-2222-2222-222222222222', 1, 'STUDY_PHOTO', 5,
                            'America/Sao_Paulo', 'ACTIVE', '%sT00:00:00Z', 'TIE',
                            '33333333-3333-3333-3333-333333333333', now(), now())
                    """.formatted(CHALLENGE_ID, today.minusDays(10)));
            statement.execute("""
                    INSERT INTO challenge_repositories (id, challenge_id, github_repository_id, slot, added_at)
                    VALUES (gen_random_uuid(), '%s', '12121212-1212-1212-1212-121212121212', 1, now())
                    """.formatted(CHALLENGE_ID));
            statement.execute("""
                    INSERT INTO challenge_participants (id, challenge_id, user_id, group_member_id, status, joined_at,
                                                        valid_days, current_streak, best_streak, last_valid_date)
                    VALUES ('%s', '%s', '33333333-3333-3333-3333-333333333333',
                            '44444444-4444-4444-4444-444444444444', 'ACTIVE', '%sT00:00:00Z',
                            4, 4, 4, '%s'),
                           ('%s', '%s', '55555555-5555-5555-5555-555555555555',
                            '66666666-6666-6666-6666-666666666666', 'ACTIVE', '%sT00:00:00Z',
                            3, 0, 3, '%s'),
                           ('%s', '%s', '77777777-7777-7777-7777-777777777777',
                            '88888888-8888-8888-8888-888888888888', 'ACTIVE', '%sT00:00:00Z',
                            3, 0, 3, '%s')
                    """.formatted(PARTICIPANT_A, CHALLENGE_ID, today.minusDays(10), today,
                    PARTICIPANT_B, CHALLENGE_ID, today.minusDays(10), today.minusDays(1),
                    PARTICIPANT_C, CHALLENGE_ID, today.minusDays(10), today.minusDays(6)));

            insertDay(statement, PARTICIPANT_A, today.minusDays(8), true, 1, 0);
            insertDay(statement, PARTICIPANT_A, today.minusDays(7), true, 2, 0);
            insertDay(statement, PARTICIPANT_A, today.minusDays(1), true, 2, 1);
            insertDay(statement, PARTICIPANT_A, today, true, 1, 1);
            insertDay(statement, PARTICIPANT_B, today.minusDays(3), false, 0, 1);
            insertDay(statement, PARTICIPANT_B, today.minusDays(2), false, 0, 1);
            insertDay(statement, PARTICIPANT_B, today.minusDays(1), false, 0, 1);
            insertDay(statement, PARTICIPANT_C, today.minusDays(8), true, 1, 0);
            insertDay(statement, PARTICIPANT_C, today.minusDays(7), true, 1, 0);
            insertDay(statement, PARTICIPANT_C, today.minusDays(6), true, 1, 0);

            statement.execute("RESET session_replication_role");
        }
    }

    private void insertDay(Statement statement, String participantId, LocalDate date, boolean commit,
                           int commitCount, int photoCount) throws SQLException {
        statement.execute("""
                INSERT INTO member_daily_activity (id, challenge_id, participant_id, user_id, activity_date,
                                                   first_eligible_commit_at, valid_commit_count, photo_count,
                                                   first_photo_at, points, validation_status, validated_at)
                VALUES (gen_random_uuid(), '%s', '%s', '99999999-9999-9999-9999-999999999999', '%s',
                        %s, %d, %d, %s, 1.0, 'VALID', now())
                """.formatted(CHALLENGE_ID, participantId, date,
                commit ? "'" + date + "T12:00:00Z'" : "NULL", commitCount, photoCount,
                photoCount > 0 ? "'" + date + "T15:00:00Z'" : "NULL"));
    }

    private void assertParticipant(Connection connection, String participantId, int validDays, int currentStreak,
                                   int bestStreak, LocalDate lastValidDate) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT valid_days, current_streak, best_streak, last_valid_date
                     FROM challenge_participants WHERE id = '%s'
                     """.formatted(participantId))) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt("valid_days")).isEqualTo(validDays);
            assertThat(result.getInt("current_streak")).isEqualTo(currentStreak);
            assertThat(result.getInt("best_streak")).isEqualTo(bestStreak);
            assertThat(result.getObject("last_valid_date", LocalDate.class)).isEqualTo(lastValidDate);
        }
    }

    private long count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }
}
