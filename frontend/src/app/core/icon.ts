import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { ICONS, IconName } from './icons';

@Component({
  selector: 'app-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" [attr.stroke-width]="strokeWidth()"
    stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path [attr.d]="path()" /></svg>`,
  styles: `:host { display: inline-flex; flex: none; width: 18px; height: 18px; } svg { width: 100%; height: 100%; }`,
})
export class Icon {
  readonly name = input.required<IconName>();
  readonly strokeWidth = input(2);
  protected readonly path = computed(() => ICONS[this.name()]);
}
