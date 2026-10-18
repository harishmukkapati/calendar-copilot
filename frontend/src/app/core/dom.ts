/** Reads the current value from an input/textarea event (used with signal bindings). */
export function inputValue(event: Event): string {
  return (event.target as HTMLInputElement | HTMLTextAreaElement).value;
}

export function inputChecked(event: Event): boolean {
  return (event.target as HTMLInputElement).checked;
}
