import { describe, expect, it } from 'vitest';
import { isEditableTarget } from '../../src/features/diagrams/DiagramCanvas';

function element<T extends HTMLElement>(html: string): T {
  const host = document.createElement('div');
  host.innerHTML = html;
  return host.firstElementChild as T;
}

describe('canvas keyboard shortcut targeting', () => {
  it('treats text entry fields as editable so Backspace does not delete diagram nodes', () => {
    expect(isEditableTarget(element('<input type="text" />'))).toBe(true);
    expect(isEditableTarget(element('<textarea></textarea>'))).toBe(true);
    expect(isEditableTarget(element('<select><option>a</option></select>'))).toBe(true);
  });

  it('treats contenteditable regions as editable', () => {
    const div = element<HTMLDivElement>('<div contenteditable="true"></div>');
    // jsdom does not derive isContentEditable from the attribute, so set it explicitly.
    Object.defineProperty(div, 'isContentEditable', { value: true });
    expect(isEditableTarget(div)).toBe(true);
  });

  it('does not treat the canvas or plain elements as editable', () => {
    expect(isEditableTarget(element('<div class="canvas-shell"></div>'))).toBe(false);
    expect(isEditableTarget(element('<button>Save</button>'))).toBe(false);
    expect(isEditableTarget(null)).toBe(false);
  });

  it('ignores disabled fields, which cannot receive typed input', () => {
    expect(isEditableTarget(element('<input type="text" disabled />'))).toBe(false);
  });
});
