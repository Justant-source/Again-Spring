import { api } from '../client';
import type { ContentResponse } from './contentApi';

export interface Template {
  id: number;
  platform: string;
  name: string;
  bodyTemplate: string;
  variablesJson?: string;
  isActive: boolean;
  createdBy?: number;
  createdAt: string;
  updatedAt: string;
}

export interface TemplateRequest {
  platform: string;
  name: string;
  bodyTemplate: string;
  variablesJson?: string;
  isActive?: boolean;
}

export async function getTemplates(platform?: string, activeOnly?: boolean): Promise<Template[]> {
  const params = new URLSearchParams();
  if (platform) params.set('platform', platform);
  if (activeOnly !== undefined) params.set('activeOnly', String(activeOnly));
  const query = params.toString() ? `?${params}` : '';
  const res = await api.get<Template[]>(`/api/admin/marketing/templates${query}`);
  return res.data;
}

export async function getTemplate(id: number): Promise<Template> {
  const res = await api.get<Template>(`/api/admin/marketing/templates/${id}`);
  return res.data;
}

export async function createTemplate(data: TemplateRequest): Promise<Template> {
  const res = await api.post<Template>('/api/admin/marketing/templates', data);
  return res.data;
}

export async function updateTemplate(id: number, data: TemplateRequest): Promise<Template> {
  const res = await api.put<Template>(`/api/admin/marketing/templates/${id}`, data);
  return res.data;
}

export async function toggleTemplateActive(id: number): Promise<Template> {
  const res = await api.post<Template>(`/api/admin/marketing/templates/${id}/toggle`);
  return res.data;
}

export async function deleteTemplate(id: number): Promise<void> {
  await api.delete(`/api/admin/marketing/templates/${id}`);
}

export async function generateFromTemplate(
  templateId: number,
  simulationId: number,
  platform: string | undefined,
  variables: Record<string, string>
): Promise<ContentResponse> {
  const res = await api.post<ContentResponse>(
    `/api/admin/marketing/contents/from-template/${templateId}`,
    { simulationId, platform, variables }
  );
  return res.data;
}
