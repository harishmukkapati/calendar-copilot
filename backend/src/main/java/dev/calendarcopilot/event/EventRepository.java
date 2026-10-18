package dev.calendarcopilot.event;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EventRepository extends JpaRepository<CalendarEvent, Long> {

    List<CalendarEvent> findByWorkspaceIdOrderByStartAtAsc(UUID workspaceId);

    Optional<CalendarEvent> findByIdAndWorkspaceId(Long id, UUID workspaceId);

    long countByWorkspaceId(UUID workspaceId);

    @Modifying
    @Query("delete from CalendarEvent e where e.id = :id and e.workspaceId = :workspaceId")
    int deleteByIdAndWorkspaceId(Long id, UUID workspaceId);
}
