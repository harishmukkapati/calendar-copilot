-- All-day events (holidays, trips, breaks) span whole days and show as bars without a time.
ALTER TABLE calendar_event ADD COLUMN all_day BOOLEAN NOT NULL DEFAULT FALSE;
