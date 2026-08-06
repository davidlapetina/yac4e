export type Metadata = Record<string, unknown>;

export type ArchitectureElementType =
  | 'PERSON'
  | 'SOFTWARE_SYSTEM'
  | 'CONTAINER'
  | 'COMPONENT'
  | 'DATA_STORE'
  | 'EXTERNAL_SYSTEM';

export type RelationshipType =
  | 'USES'
  | 'CALLS'
  | 'READS_FROM'
  | 'WRITES_TO'
  | 'PUBLISHES_TO'
  | 'SUBSCRIBES_TO'
  | 'DEPENDS_ON'
  | 'OWNS'
  | 'AUTHENTICATES_WITH';

export type DiagramViewType = 'SYSTEM_CONTEXT' | 'CONTAINER' | 'COMPONENT' | 'CUSTOM';
export type LayoutDirection = 'LEFT_TO_RIGHT' | 'TOP_TO_BOTTOM' | 'RIGHT_TO_LEFT' | 'BOTTOM_TO_TOP';
export type LinkProvider = 'CONFLUENCE' | 'NOTION' | 'JIRA' | 'GITHUB' | 'GITLAB' | 'AZURE_DEVOPS' | 'SERVICENOW' | 'LINEAR' | 'SHAREPOINT' | 'GRAFANA' | 'DATADOG' | 'OPENAPI' | 'RUNBOOK' | 'WIKI' | 'OTHER';
export type LinkType = 'DOCUMENTATION' | 'ISSUE' | 'EPIC' | 'REPOSITORY' | 'DASHBOARD' | 'RUNBOOK' | 'API_SPECIFICATION' | 'DEPLOYMENT_PIPELINE' | 'INCIDENT' | 'OTHER';
export type MetadataValueType = 'STRING' | 'TEXT' | 'NUMBER' | 'BOOLEAN' | 'DATE' | 'URL' | 'SINGLE_SELECT' | 'MULTI_SELECT' | 'USER_REFERENCE' | 'TEAM_REFERENCE';
export type Severity = 'ERROR' | 'WARNING' | 'INFO';

export interface Workspace {
  id: string;
  name: string;
  description: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface ArchitectureElement {
  id: string;
  workspaceId: string;
  type: ArchitectureElementType;
  name: string;
  description: string;
  parentElementId?: string | null;
  technology?: string | null;
  metadata: Metadata;
  version: number;
  createdAt: string;
  updatedAt: string;
  linkCount: number;
}

export interface ArchitectureRelationship {
  id: string;
  workspaceId: string;
  sourceElementId: string;
  targetElementId: string;
  type: RelationshipType;
  description: string;
  technology?: string | null;
  protocol?: string | null;
  metadata: Metadata;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface DiagramViewElement {
  id: string;
  viewId: string;
  elementId: string;
  x: number;
  y: number;
  width: number;
  height: number;
  locked: boolean;
  visible: boolean;
  zIndex: number;
  displaySettings: Metadata;
}

export interface DiagramViewRelationship {
  id: string;
  viewId: string;
  relationshipId: string;
  visible: boolean;
  displaySettings: Metadata;
}

export interface DiagramView {
  id: string;
  workspaceId: string;
  name: string;
  description: string;
  type: DiagramViewType;
  scopeElementId?: string | null;
  layoutDirection: LayoutDirection;
  settings: Metadata;
  version: number;
  createdAt: string;
  updatedAt: string;
  elements: DiagramViewElement[];
  relationships: DiagramViewRelationship[];
}

export interface ExternalLink {
  id: string;
  elementId: string;
  provider: LinkProvider;
  type: LinkType;
  label: string;
  url: string;
  externalId?: string | null;
  metadata: Metadata;
  createdAt: string;
  updatedAt: string;
}

export interface MetadataDefinition {
  id: string;
  workspaceId: string;
  key: string;
  label: string;
  description?: string | null;
  valueType: MetadataValueType;
  required: boolean;
  appliesTo: ArchitectureElementType[];
  allowedValues?: unknown;
  defaultValue?: unknown;
  validationRules?: Metadata;
  displayOrder: number;
}

export interface ValidationIssue {
  severity: Severity;
  code: string;
  elementId?: string | null;
  relationshipId?: string | null;
  message: string;
  recommendedAction: string;
}

export interface ValidationResponse {
  errors: ValidationIssue[];
  warnings: ValidationIssue[];
  info: ValidationIssue[];
}

export interface SearchResult {
  kind: 'ELEMENT' | 'RELATIONSHIP' | 'EXTERNAL_LINK' | 'VIEW';
  id: string;
  elementId?: string | null;
  relationshipId?: string | null;
  viewId?: string | null;
  label: string;
  matchedFields: string[];
  snippet: string;
}

export interface ImportMessage {
  code: string;
  fileName?: string | null;
  line?: number | null;
  column?: number | null;
  message: string;
}

export interface ImportPreview {
  valid: boolean;
  workspace: {
    name: string;
    description: string;
    version?: number | null;
  };
  summary: {
    people: number;
    softwareSystems: number;
    containers: number;
    components: number;
    relationships: number;
    views: number;
  };
  warnings: ImportMessage[];
  errors: ImportMessage[];
}

export type AgentProposalStatus = 'PENDING' | 'VALIDATION_FAILED' | 'APPLIED' | 'REJECTED';
export type AgentProposalChangeAction =
  | 'CREATE_ELEMENT'
  | 'UPDATE_ELEMENT'
  | 'CREATE_RELATIONSHIP'
  | 'UPDATE_RELATIONSHIP'
  | 'CREATE_LINK'
  | 'CREATE_METADATA_DEFINITION'
  | 'CREATE_VIEW';
export type AgentProposalChangeStatus = 'PENDING' | 'APPLIED' | 'REJECTED';

export interface ProposalValidationIssue {
  severity: Severity;
  code: string;
  sequenceNumber?: number | null;
  clientReference?: string | null;
  message: string;
  details: Metadata;
}

export interface ProposalValidationResult {
  valid: boolean;
  warnings: ProposalValidationIssue[];
  errors: ProposalValidationIssue[];
  summary: Record<string, number>;
}

export interface AgentProposalSummary {
  id: string;
  workspaceId: string;
  status: AgentProposalStatus;
  summary: string;
  source: Metadata;
  validation: ProposalValidationResult;
  createdAt: string;
  updatedAt: string;
  appliedAt?: string | null;
  rejectedAt?: string | null;
  changeCount: number;
}

export interface AgentProposalChange {
  id: string;
  sequenceNumber: number;
  action: AgentProposalChangeAction;
  status: AgentProposalChangeStatus;
  clientReference?: string | null;
  targetEntityType?: string | null;
  targetEntityId?: string | null;
  resultEntityId?: string | null;
  payload: Metadata;
  evidence: Metadata[];
  validation: ProposalValidationResult;
}

export interface AgentProposal extends Omit<AgentProposalSummary, 'changeCount'> {
  changes: AgentProposalChange[];
}
