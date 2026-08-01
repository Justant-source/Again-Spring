export type ThreadEditorItem = {
  /** holding: ref / published: commentId string */
  key: string;
  parentKey?: string | null;
  authorId: string;
  body: string;
  type: 'COMMENT' | 'REPLY';
  /** datetime-local KST */
  atLocal: string;
  status?: string;
};

export type ThreadEditorValue = {
  title: string;
  body: string;
  category: string;
  /** 글 시각 (datetime-local KST) */
  postAtLocal: string;
  items: ThreadEditorItem[];
};

export const THREAD_CATEGORY_OPTIONS = [
  { value: 'COUPLE', label: '연인' },
  { value: 'MARRIED', label: '부부' },
  { value: 'FRIEND', label: '친구' },
  { value: 'FAMILY', label: '가족' },
  { value: 'WORK', label: '직장' },
  { value: 'OTHER', label: '기타' },
] as const;
