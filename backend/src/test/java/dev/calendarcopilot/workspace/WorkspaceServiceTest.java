package dev.calendarcopilot.workspace;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.calendarcopilot.common.InvalidRequestException;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");

    @Mock
    private WorkspaceRepository workspaces;
    @Mock
    private DemoDataSeeder seeder;

    private WorkspaceService service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceService(workspaces, seeder, Clock.fixed(NOW, ZoneOffset.UTC), 7);
    }

    @Test
    void cleanupDeletesGuestWorkspacesIdleLongerThanTtl() {
        service.deleteExpiredGuestWorkspaces();

        verify(workspaces).deleteGuestWorkspacesNotSeenSince(Instant.parse("2026-10-11T12:00:00Z"));
    }

    @Test
    void rejectsUnknownTimeZone() {
        assertThatThrownBy(() -> service.create("Not/AZone"))
                .isInstanceOf(InvalidRequestException.class);
    }
}
