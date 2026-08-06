import { Search } from 'lucide-react';
import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { useEditorStore } from '../../stores/editorStore';

export function SearchBox({ workspaceId }: { workspaceId: string }) {
  const [query, setQuery] = useState('');
  const store = useEditorStore();
  const search = useMutation({
    mutationFn: () => api.search(workspaceId, query)
  });

  return (
    <div className="search-box">
      <Search size={16} />
      <input
        value={query}
        placeholder="Search architecture"
        onChange={(event) => setQuery(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter' && query.trim()) search.mutate();
          if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'f') event.currentTarget.focus();
        }}
      />
      {search.data && (
        <div className="search-results">
          {search.data.results.map((result) => (
            <button
              key={`${result.kind}-${result.id}`}
              onClick={() => {
                if (result.viewId) store.setView(result.viewId);
                if (result.elementId) store.selectElement(result.elementId);
                if (result.relationshipId) store.selectRelationship(result.relationshipId);
                store.highlight(result.elementId ?? result.relationshipId ?? result.viewId ?? undefined);
              }}
            >
              <strong>{result.label}</strong>
              <span>{result.kind} · {result.matchedFields.join(', ')}</span>
            </button>
          ))}
          {search.data.results.length === 0 && <div className="empty-state">No matches</div>}
        </div>
      )}
    </div>
  );
}
