import { AfterViewInit, Directive, ElementRef, inject, output } from '@angular/core';

/**
 * Opens a native <dialog> as a modal when it renders. The browser provides focus
 * trapping, Escape-to-close and the backdrop. Clicking the backdrop also closes it.
 */
@Directive({
  selector: 'dialog[appModalDialog]',
  host: {
    class: 'modal',
    '(close)': 'closed.emit()',
    '(click)': 'onClick($event)',
  },
})
export class ModalDialog implements AfterViewInit {
  private readonly dialog = inject<ElementRef<HTMLDialogElement>>(ElementRef).nativeElement;
  readonly closed = output<void>();

  ngAfterViewInit(): void {
    this.dialog.showModal();
  }

  close(): void {
    this.dialog.close();
  }

  protected onClick(event: MouseEvent): void {
    // The dialog element itself only receives clicks on its backdrop area.
    if (event.target === this.dialog) {
      this.dialog.close();
    }
  }
}
