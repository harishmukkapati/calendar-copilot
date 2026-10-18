/** Inline SVG paths (stroke icons, 24x24 viewBox) so no icon font has to load. */
export const ICONS = {
  chevronLeft: 'M15 18l-6-6 6-6',
  chevronRight: 'M9 18l6-6-6-6',
  chevronDown: 'M6 9l6 6 6-6',
  plus: 'M12 5v14M5 12h14',
  close: 'M18 6L6 18M6 6l12 12',
  check: 'M5 12l5 5L20 7',
  trash: 'M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 002 2h6a2 2 0 002-2l1-12M9 7V4h6v3',
  menu: 'M4 6h16M4 12h16M4 18h16',
  clock: 'M12 7v5l3 2M21 12a9 9 0 11-18 0 9 9 0 0118 0z',
  refresh: 'M4 4v6h6M20 20v-6h-6M5.6 15A8 8 0 0018.4 9M18.4 9H20M5.6 15H4',
  subtask: 'M6 4v10a2 2 0 002 2h10M14 12l4 4-4 4',
} as const;

export type IconName = keyof typeof ICONS;
