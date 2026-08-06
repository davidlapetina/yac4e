import { Search, X } from 'lucide-react';
import { useMutation } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { api } from '../../api/client';
import { useEditorStore } from '../../stores/editorStore';

export function SearchBox({ workspaceId }: { workspaceId: string }) {
  const [query, setQuery] = useState('');
  const [resultsOpen, setResultsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const store = useEditorStore();
  const search = useMutation({
    mutationFn: () => api.search(workspaceId, query),
    onSuccess: () => setResultsOpen(true)
  });

  // Results used to stay on screen indefinitely: selecting a hit, clearing the box, or clicking
  // elsewhere all left the dropdown covering the editor.
  useEffect(() => {
    if (!resultsOpen) return undefined;
    const onPointerDown = (event: MouseEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) setResultsOpen(false);
    };
    document.addEventListener('mousedown', onPointerDown);
    return () => document.removeEventListener('mousedown', onPointerDown);
  }, [resultsOpen]);

  const clear = () => {
    setQuery('');
    setResultsOpen(false);
    search.reset();
  };

  const results = search.data?.results ?? [];

  return (
    <div className="search-box" ref={containerRef}>
      <Search size={16} />
      <input
        value={query}
        placeholder="Search architecture"
        aria-label="Search architecture"
        onChange={(event) => {
          setQuery(event.target.value);
          if (!event.target.value.trim()) {
            setResultsOpen(false);
            search.reset();
          }
        }}
        onFocus={() => {
          if (search.data) setResultsOpen(true);
        }}
        onKeyDown={(event) => {
          if (event.key === 'Enter' && query.trim()) search.mutate();
          if (event.key === 'Escape') {
            event.stopPropagation();
            if (resultsOpen) setResultsOpen(false);
            else clear();
          }
        }}
      />
      {query && (
        <button type="button" className="search-clear" onClick={clear} title="Clear search" aria-label="Clear search">
          <X size={14} />
        </button>
      )}
      {resultsOpen && (
        <div className="search-results">
          <div className="search-results-header">
            {search.isPending ? 'Searching…' : `${results.length} result${results.length === 1 ? '' : 's'}`}
          </div>
          {results.map((result) => (
            <button
              key={`${result.kind}-${result.id}`}
              onClick={() => {
                if (result.viewId) store.setView(result.viewId);
                if (result.elementId) store.selectElement(result.elementId);
                if (result.relationshipId) store.selectRelationship(result.relationshipId);
                store.highlight(result.elementId ?? result.relationshipId ?? result.viewId ?? undefined);
                setResultsOpen(false);
              }}
            >
              <strong>{result.label}</strong>
              <span>{result.kind} · {result.matchedFields.join(', ')}</span>
            </button>
          ))}
          {!search.isPending && results.length === 0 && <div className="empty-state">No matches</div>}
        </div>
      )}
    </div>
  );
}
