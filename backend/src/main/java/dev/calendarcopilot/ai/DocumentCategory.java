package dev.calendarcopilot.ai;

/** Which upload slot a file came from; tells the model how to weigh it. */
public enum DocumentCategory {
    INSTRUCTIONS, RUBRIC, SUPPORTING
}
