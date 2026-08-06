import { beforeEach, describe, expect, it } from 'vitest';
import { useEditorStore } from '../../src/stores/editorStore';

const initial = useEditorStore.getState();

beforeEach(() => {
  useEditorStore.setState({ leftPanelOpen: true, rightPanelOpen: true, bottomPanelOpen: false });
});

describe('panel visibility', () => {
  it('starts with both side panels open and the issue list collapsed', () => {
    expect(initial.leftPanelOpen).toBe(true);
    expect(initial.rightPanelOpen).toBe(true);
    expect(initial.bottomPanelOpen).toBe(false);
  });

  it('toggles the left panel independently of the others', () => {
    useEditorStore.getState().toggleLeftPanel();

    const state = useEditorStore.getState();
    expect(state.leftPanelOpen).toBe(false);
    expect(state.rightPanelOpen).toBe(true);
    expect(state.bottomPanelOpen).toBe(false);
  });

  it('toggles the right panel independently of the others', () => {
    useEditorStore.getState().toggleRightPanel();

    const state = useEditorStore.getState();
    expect(state.rightPanelOpen).toBe(false);
    expect(state.leftPanelOpen).toBe(true);
  });

  it('toggles the validation list independently of the side panels', () => {
    useEditorStore.getState().toggleBottomPanel();

    const state = useEditorStore.getState();
    expect(state.bottomPanelOpen).toBe(true);
    expect(state.leftPanelOpen).toBe(true);
    expect(state.rightPanelOpen).toBe(true);
  });

  it('restores a panel when toggled twice', () => {
    useEditorStore.getState().toggleLeftPanel();
    useEditorStore.getState().toggleLeftPanel();

    expect(useEditorStore.getState().leftPanelOpen).toBe(true);
  });

  it('supports collapsing both side panels at once', () => {
    useEditorStore.getState().toggleLeftPanel();
    useEditorStore.getState().toggleRightPanel();

    const state = useEditorStore.getState();
    expect(state.leftPanelOpen).toBe(false);
    expect(state.rightPanelOpen).toBe(false);
  });

  it('keeps panel state independent of selection changes', () => {
    useEditorStore.getState().toggleLeftPanel();
    useEditorStore.getState().selectElement('e1');
    useEditorStore.getState().setView('v1');

    expect(useEditorStore.getState().leftPanelOpen).toBe(false);
  });
});
