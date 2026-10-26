export type SiteVariant = 'nothing-stark' | 'apple-pro' | 'mono-utility';

export interface ReleaseInfo {
  tag: string;
  name: string;
  apkUrl: string;
  size?: string;
  publishedAt?: string;
  changelog?: string;
}

export interface SupabaseStats {
  verifiedBadges: number;
  activeUsers: number;
}

export interface FeatureItem {
  id: string;
  title: string;
  category: 'privacy' | 'modules' | 'badges' | 'media' | 'system';
  tag: string;
  shortDesc: string;
  fullDesc: string;
  icon: string;
  badge?: string;
}

export interface YuiModuleItem {
  id: string;
  name: string;
  version: string;
  author: string;
  description: string;
  tag: string;
  stars: number;
  icon: string;
  verified: boolean;
}
