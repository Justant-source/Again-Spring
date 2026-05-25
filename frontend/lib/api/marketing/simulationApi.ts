import { api } from '../client';

export interface SimulationResponse {
  id: number;
  storyId: number;
  sessionId?: string;
  personaA?: string;
  personaB?: string;
  turnCount?: number;
  actualTurnCount?: number;
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELED';
  conversationLog?: string;
  errorMessage?: string;
  llmCostUsd?: string;   // BE: BigDecimal.toPlainString() → string
  startedAt?: string;
  finishedAt?: string;
  createdAt: string;
}

export interface SimulationSummaryResponse {
  id: number;
  storyId: number;
  turnCount?: number;
  status: string;
  startedAt?: string;
  finishedAt?: string;
  createdAt: string;
}

export async function startSimulation(storyId: number): Promise<SimulationResponse> {
  const res = await api.post<SimulationResponse>('/api/admin/marketing/simulations', null, { params: { storyId } });
  return res.data;
}

export async function getSimulations(status?: string): Promise<SimulationSummaryResponse[]> {
  const res = await api.get<SimulationSummaryResponse[]>('/api/admin/marketing/simulations', {
    params: status ? { status } : undefined,
  });
  return res.data;
}

export async function getSimulation(id: number): Promise<SimulationResponse> {
  const res = await api.get<SimulationResponse>(`/api/admin/marketing/simulations/${id}`);
  return res.data;
}

export async function cancelSimulation(id: number): Promise<SimulationResponse> {
  const res = await api.post<SimulationResponse>(`/api/admin/marketing/simulations/${id}/cancel`);
  return res.data;
}
