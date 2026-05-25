import { api } from '../client';
import type { Report } from '@/lib/types';

export interface SimulationMessageResponse {
  id: number;
  sender: 'USER_A' | 'USER_B' | 'MEDIATOR_TO_A' | 'MEDIATOR_TO_B';
  content: string;
  charCount: number;
  isFinalizeSuggestion: boolean;
  isPartnerJoinNotice: boolean;
  createdAt: string;
  status: 'streaming' | 'complete';
}

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

export async function deleteSimulation(id: number): Promise<void> {
  await api.delete(`/api/admin/marketing/simulations/${id}`);
}

export async function getSimulationMessages(id: number): Promise<SimulationMessageResponse[]> {
  const res = await api.get<SimulationMessageResponse[]>(`/api/admin/marketing/simulations/${id}/messages`);
  return res.data;
}

export async function getSimulationReport(id: number): Promise<Report> {
  const res = await api.get<Report>(`/api/admin/marketing/simulations/${id}/report`);
  return res.data;
}
