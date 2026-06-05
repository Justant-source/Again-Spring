import { api } from '../client';
import type { PageResponse } from '../admin';

export interface AdminInquiry {
  id: string;
  userId: string;
  subject: string;
  category: string;
  status: string; // OPEN, ANSWERED, CLOSED
  assigneeUserId?: string;
  createdAt: string;
  updatedAt: string;
}

export interface InquiryMessage {
  id: number;
  senderRole: string; // USER, ADMIN
  senderUserId: string;
  body: string;
  createdAt: string;
}

export interface InquiryDetailResponse {
  id: string;
  userId: string;
  subject: string;
  category: string;
  status: string;
  assigneeUserId?: string;
  createdAt: string;
  updatedAt: string;
  messages: InquiryMessage[];
}

export async function listInquiries(params?: {
  status?: string;
  page?: number;
  size?: number;
}): Promise<PageResponse<AdminInquiry>> {
  const res = await api.get<PageResponse<AdminInquiry>>('/api/admin/inquiries', {
    params,
  });
  return res.data;
}

export async function getInquiryCount(status?: string): Promise<{ count: number }> {
  const res = await api.get<{ count: number }>('/api/admin/inquiries/count', {
    params: status ? { status } : {},
  });
  return res.data;
}

export async function getInquiryDetail(id: string): Promise<InquiryDetailResponse> {
  const res = await api.get<InquiryDetailResponse>(`/api/admin/inquiries/${id}`);
  return res.data;
}

export async function replyToInquiry(id: string, message: string): Promise<InquiryDetailResponse> {
  const res = await api.post<InquiryDetailResponse>(
    `/api/admin/inquiries/${id}/reply`,
    { message }
  );
  return res.data;
}

export async function closeInquiry(id: string): Promise<void> {
  await api.post(`/api/admin/inquiries/${id}/close`);
}

export async function deleteInquiry(id: string): Promise<void> {
  await api.delete(`/api/admin/inquiries/${id}`);
}
