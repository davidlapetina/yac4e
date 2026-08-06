import type {
  ArchitectureElement,
  ArchitectureElementType,
  ArchitectureRelationship,
  DiagramView,
  DiagramViewElement,
  DiagramViewRelationship,
  ExternalLink,
  AgentProposal,
  AgentProposalSummary,
  ImportPreview,
  LayoutDirection,
  Metadata,
  MetadataDefinition,
  RelationshipType,
  SearchResult,
  ValidationResponse,
  Workspace
} from '../types/model';

export interface ApiErrorPayload {
  code: string;
  message: string;
  details: Record<string, unknown>;
  timestamp: string;
}

export class ApiClientError extends Error {
  constructor(public payload: ApiErrorPayload, public status: number) {
    super(payload.message);
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const isFormData = init.body instanceof FormData;
  const response = await fetch(`/api${path}`, {
    ...init,
    headers: isFormData ? init.headers : {
      'Content-Type': 'application/json',
      ...(init.headers ?? {})
    }
  });
  if (!response.ok) {
    const payload = (await response.json().catch(() => ({
      code: 'REQUEST_FAILED',
      message: response.statusText,
      details: {},
      timestamp: new Date().toISOString()
    }))) as ApiErrorPayload;
    throw new ApiClientError(payload, response.status);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export interface ElementInput {
  type: ArchitectureElementType;
  name: string;
  description: string;
  parentElementId?: string | null;
  technology?: string | null;
  metadata: Metadata;
  version?: number;
}

export interface RelationshipInput {
  sourceElementId: string;
  targetElementId: string;
  type: RelationshipType;
  description: string;
  technology?: string | null;
  protocol?: string | null;
  metadata: Metadata;
  version?: number;
}

export interface ViewInput {
  name: string;
  description: string;
  type: DiagramView['type'];
  scopeElementId?: string | null;
  layoutDirection: LayoutDirection;
  settings: Metadata;
  version?: number;
}

export interface LayoutPayload {
  viewVersion: number;
  elements: Array<Omit<DiagramViewElement, 'id' | 'viewId' | 'displaySettings'> & { displaySettings?: Metadata }>;
  relationships: Array<Omit<DiagramViewRelationship, 'id' | 'viewId' | 'displaySettings'> & { displaySettings?: Metadata }>;
}

export const api = {
  workspaces: () => request<Workspace[]>('/workspaces'),
  createWorkspace: (body: { name: string; description: string }) => request<Workspace>('/workspaces', { method: 'POST', body: JSON.stringify(body) }),
  elements: (workspaceId: string) => request<ArchitectureElement[]>(`/workspaces/${workspaceId}/elements`),
  createElement: (workspaceId: string, body: ElementInput) => request<ArchitectureElement>(`/workspaces/${workspaceId}/elements`, { method: 'POST', body: JSON.stringify(body) }),
  updateElement: (workspaceId: string, elementId: string, body: ElementInput) => request<ArchitectureElement>(`/workspaces/${workspaceId}/elements/${elementId}`, { method: 'PUT', body: JSON.stringify(body) }),
  relationships: (workspaceId: string) => request<ArchitectureRelationship[]>(`/workspaces/${workspaceId}/relationships`),
  createRelationship: (workspaceId: string, body: RelationshipInput) => request<ArchitectureRelationship>(`/workspaces/${workspaceId}/relationships`, { method: 'POST', body: JSON.stringify(body) }),
  updateRelationship: (workspaceId: string, relationshipId: string, body: RelationshipInput) => request<ArchitectureRelationship>(`/workspaces/${workspaceId}/relationships/${relationshipId}`, { method: 'PUT', body: JSON.stringify(body) }),
  views: (workspaceId: string) => request<DiagramView[]>(`/workspaces/${workspaceId}/views`),
  createView: (workspaceId: string, body: ViewInput) => request<DiagramView>(`/workspaces/${workspaceId}/views`, { method: 'POST', body: JSON.stringify(body) }),
  addElementToView: (workspaceId: string, viewId: string, body: LayoutPayload['elements'][number]) => request<DiagramViewElement>(`/workspaces/${workspaceId}/views/${viewId}/elements`, { method: 'POST', body: JSON.stringify(body) }),
  addRelationshipToView: (workspaceId: string, viewId: string, body: LayoutPayload['relationships'][number]) => request<DiagramViewRelationship>(`/workspaces/${workspaceId}/views/${viewId}/relationships`, { method: 'POST', body: JSON.stringify(body) }),
  removeElementFromView: (workspaceId: string, viewId: string, elementId: string) => request<void>(`/workspaces/${workspaceId}/views/${viewId}/elements/${elementId}`, { method: 'DELETE' }),
  removeRelationshipFromView: (workspaceId: string, viewId: string, relationshipId: string) => request<void>(`/workspaces/${workspaceId}/views/${viewId}/relationships/${relationshipId}`, { method: 'DELETE' }),
  saveLayout: (workspaceId: string, viewId: string, body: LayoutPayload) => request<DiagramView>(`/workspaces/${workspaceId}/views/${viewId}/layout`, { method: 'PUT', body: JSON.stringify(body) }),
  links: (workspaceId: string, elementId: string) => request<ExternalLink[]>(`/workspaces/${workspaceId}/elements/${elementId}/links`),
  createLink: (workspaceId: string, elementId: string, body: Omit<ExternalLink, 'id' | 'elementId' | 'createdAt' | 'updatedAt'>) => request<ExternalLink>(`/workspaces/${workspaceId}/elements/${elementId}/links`, { method: 'POST', body: JSON.stringify(body) }),
  metadataDefinitions: (workspaceId: string) => request<MetadataDefinition[]>(`/workspaces/${workspaceId}/metadata-definitions`),
  createMetadataDefinition: (workspaceId: string, body: Omit<MetadataDefinition, 'id' | 'workspaceId'>) => request<MetadataDefinition>(`/workspaces/${workspaceId}/metadata-definitions`, { method: 'POST', body: JSON.stringify(body) }),
  updateMetadataDefinition: (workspaceId: string, definitionId: string, body: Omit<MetadataDefinition, 'id' | 'workspaceId'>) => request<MetadataDefinition>(`/workspaces/${workspaceId}/metadata-definitions/${definitionId}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteMetadataDefinition: (workspaceId: string, definitionId: string) => request<void>(`/workspaces/${workspaceId}/metadata-definitions/${definitionId}`, { method: 'DELETE' }),
  validation: (workspaceId: string) => request<ValidationResponse>(`/workspaces/${workspaceId}/validation`),
  search: (workspaceId: string, q: string) => request<{ results: SearchResult[] }>(`/workspaces/${workspaceId}/search?q=${encodeURIComponent(q)}`),
  exportModelJsonUrl: (workspaceId: string) => `/api/workspaces/${workspaceId}/exports/model.json`,
  exportModelYamlUrl: (workspaceId: string) => `/api/workspaces/${workspaceId}/exports/model.yaml`,
  importModel: (body: string, contentType: 'application/json' | 'application/yaml') => request<{ workspaceId: string; elementCount: number; relationshipCount: number; viewCount: number }>('/imports/model', {
    method: 'POST',
    headers: { 'Content-Type': contentType },
    body
  }),
  validateStructurizr: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return request<ImportPreview>('/imports/structurizr/validate', {
      method: 'POST',
      headers: {},
      body: form
    });
  },
  importStructurizr: (file: File, options: { mode: 'CREATE_NEW' | 'REPLACE'; workspaceName?: string; targetWorkspaceId?: string }) => {
    const form = new FormData();
    form.append('file', file);
    form.append('options', JSON.stringify(options));
    return request<{ workspaceId: string; elementCount: number; relationshipCount: number; viewCount: number }>('/imports/structurizr', {
      method: 'POST',
      headers: {},
      body: form
    });
  },
  proposals: (workspaceId: string) => request<AgentProposalSummary[]>(`/agent/workspaces/${workspaceId}/proposals`),
  proposal: (workspaceId: string, proposalId: string) => request<AgentProposal>(`/agent/workspaces/${workspaceId}/proposals/${proposalId}`),
  applyProposal: (workspaceId: string, proposalId: string) => request<AgentProposal>(`/agent/workspaces/${workspaceId}/proposals/${proposalId}/apply`, { method: 'POST' }),
  rejectProposal: (workspaceId: string, proposalId: string) => request<AgentProposal>(`/agent/workspaces/${workspaceId}/proposals/${proposalId}/reject`, { method: 'POST' })
};
