package dev.calendarcopilot;

import org.springframework.boot.SpringApplication;

public class TestCalendarCopilotApplication {

	public static void main(String[] args) {
		SpringApplication.from(CalendarCopilotApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
