package dev.calendarcopilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CalendarCopilotApplication {

	public static void main(String[] args) {
		SpringApplication.run(CalendarCopilotApplication.class, args);
	}

}
