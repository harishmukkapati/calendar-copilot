package dev.calendarcopilot.workspace;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.stereotype.Component;

import dev.calendarcopilot.event.CalendarEvent;
import dev.calendarcopilot.event.EventRepository;
import dev.calendarcopilot.task.Task;
import dev.calendarcopilot.task.TaskOrigin;
import dev.calendarcopilot.task.TaskRepository;
import dev.calendarcopilot.task.TaskStatus;

/**
 * Fills a new guest workspace with fictional coursework so the calendar is
 * understandable on first open. Dates are relative to "today" in the visitor's zone.
 */
@Component
class DemoDataSeeder {

    private final TaskRepository tasks;
    private final EventRepository events;
    private final Clock clock;

    DemoDataSeeder(TaskRepository tasks, EventRepository events, Clock clock) {
        this.tasks = tasks;
        this.events = events;
        this.clock = clock;
    }

    void seed(UUID workspaceId, ZoneId zone) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Seed s = new Seed(workspaceId, zone, today);

        Task paper = s.task("Philosophy Paper 4: Personal identity", 6, "23:59", 0,
                "1,500-word argumentative essay on whether personal identity survives memory loss.");
        s.subtask(paper, "Reread Locke and Parfit readings", 0, TaskStatus.DONE);
        s.subtask(paper, "Choose a thesis", 1, TaskStatus.DONE);
        s.subtask(paper, "Write an outline", 2, TaskStatus.TODO);
        s.subtask(paper, "Draft body sections", 3, TaskStatus.TODO);
        s.subtask(paper, "Proofread and submit", 4, TaskStatus.TODO);

        Task lab = s.task("Chemistry lab report: Acid-base titration", 2, "17:00", 1,
                "Report for Lab 5. Include data table, titration curve, and error analysis.");
        s.subtask(lab, "Analyze titration data", 0, TaskStatus.DONE);
        s.subtask(lab, "Plot the titration curve", 1, TaskStatus.TODO);
        s.subtask(lab, "Write the discussion section", 2, TaskStatus.TODO);

        Task exam = s.task("Study for statistics midterm", 9, "09:00", 2,
                "Covers chapters 6-9: sampling distributions, confidence intervals, hypothesis tests.");
        s.subtask(exam, "Review hypothesis testing notes", 0, TaskStatus.TODO);
        s.subtask(exam, "Complete practice exam 1", 1, TaskStatus.TODO);
        s.subtask(exam, "Bring questions to office hours", 2, TaskStatus.TODO);

        Task library = s.task("Return library books", -1, "18:00", 3, null);
        library.setStatus(TaskStatus.DONE);

        s.task("Submit internship application", 13, "23:59", 4,
                "Fictional company. Update resume and write a short cover letter.");

        s.event("Gym session", 0, "18:00", "19:00", "green");
        s.event("Team project meeting", 1, "15:00", "16:00", "purple");
        s.event("Gym session", 2, "18:00", "19:00", "green");
        s.event("Statistics lecture", 3, "10:00", "11:15", "blue");
        s.event("Gym session", 4, "18:00", "19:00", "green");
        s.event("Office hours: Prof. Rivera", 5, "14:00", "15:00", "orange");
        s.event("Statistics lecture", 10, "10:00", "11:15", "blue");
        s.event("Statistics midterm", 9, "09:00", "10:30", "red");
    }

    /** Small helper so the seed data above reads like a list. */
    private final class Seed {
        private final UUID workspaceId;
        private final ZoneId zone;
        private final LocalDate today;

        Seed(UUID workspaceId, ZoneId zone, LocalDate today) {
            this.workspaceId = workspaceId;
            this.zone = zone;
            this.today = today;
        }

        Task task(String title, int daysFromToday, String dueTime, int sortOrder, String description) {
            Task task = new Task(workspaceId, null, title, TaskOrigin.MANUAL);
            task.setDescription(description);
            task.setDueAt(at(daysFromToday, dueTime));
            task.setSortOrder(sortOrder);
            return tasks.save(task);
        }

        void subtask(Task parent, String title, int sortOrder, TaskStatus status) {
            Task task = new Task(workspaceId, parent.getId(), title, TaskOrigin.MANUAL);
            task.setSortOrder(sortOrder);
            task.setStatus(status);
            tasks.save(task);
        }

        void event(String title, int daysFromToday, String start, String end, String color) {
            events.save(new CalendarEvent(workspaceId, title, at(daysFromToday, start), at(daysFromToday, end), color));
        }

        private Instant at(int daysFromToday, String time) {
            return today.plusDays(daysFromToday).atTime(LocalTime.parse(time)).atZone(zone).toInstant();
        }
    }
}
