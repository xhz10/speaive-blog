export const humorMechanisms = {
  LITERAL: "字面误读", CAUSAL_REVERSAL: "因果倒置", CONCEPT_MISMATCH: "概念错配",
  DAILY_LIFE: "生活自嘲", PERSONIFICATION: "物件拟人"
} as const;

export interface HumorMaterial {
  id: string; excerpt: string; sourceUrl: string; sourceTitle: string;
  sourceKind: "PRIMARY_LISTING" | "PRIMARY_POST" | "REPRINT";
  topics: string[]; mechanism: keyof typeof humorMechanisms;
  analysis: string; adaptation: string; quality: number; enabled: boolean;
  version: number; createdAt: string; updatedAt: string; score: number;
}
export interface HumorMaterialList { items: HumorMaterial[] }
