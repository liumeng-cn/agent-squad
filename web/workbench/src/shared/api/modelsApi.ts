import { apiGet } from './http'
export type ReasoningMode = 'DEFAULT' | 'OFF' | 'AUTO' | 'ON' | 'LOW' | 'MEDIUM' | 'HIGH'
export type ModelCapability = 'REASONING' | 'TEXT_GENERATION' | 'CODING' | 'TOOL_USE' | 'VISION'
export interface ModelSelection { modelId: string; reasoningMode: ReasoningMode }
export interface ModelOption {
  modelId: string
  displayName: string
  modelName: string
  capabilities: ModelCapability[]
  reasoningModes: ReasoningMode[]
  defaultReasoningMode: ReasoningMode
}
export function modelOptions(): Promise<ModelOption[]> { return apiGet('/api/v1/models') }
