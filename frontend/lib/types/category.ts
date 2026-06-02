export type RelationType = 'couple' | 'marriage' | 'friend' | 'family' | 'parent_child' | 'korean_specific' | 'work';

export interface MinorCategory {
  id: string;
  label: string;
  allowCustomInput: boolean;
}

export interface MiddleCategory {
  id: string;
  label: string;
  minors: MinorCategory[];
}

export interface MajorCategory {
  id: string;
  label: string;
  relationType: RelationType;
  middles: MiddleCategory[];
}

export interface CategoryTree {
  major: MajorCategory[];
}
