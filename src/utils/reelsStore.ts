import { NativeModules } from 'react-native';
import { ReelData, Stats } from '../hooks/useReelsTracker';

const { ReelsTrackerModule } = NativeModules;

export async function loadReels(limit = 50): Promise<ReelData[]> {
  if (!ReelsTrackerModule) return [];
  try {
    const raw = await ReelsTrackerModule.getRecentReels(limit);
    if (!raw) return [];
    return JSON.parse(raw);
  } catch (e) {
    console.warn('loadReels error', e);
    return [];
  }
}

export async function insertReel(username: string, caption: string): Promise<number> {
  if (!ReelsTrackerModule) return 0;
  try {
    const id = await ReelsTrackerModule.insertReel(username, caption);
    return id;
  } catch (e) {
    console.warn('insertReel error', e);
    return 0;
  }
}

export async function updateReel(reelId: number, updates: Partial<ReelData>): Promise<void> {
  if (!ReelsTrackerModule || reelId <= 0) return;
  try {
    // Determine which native update endpoint to call based on what field is updated
    if (updates.ocrText !== undefined) {
      await ReelsTrackerModule.updateReelOcrText(reelId, updates.ocrText);
    }
    if (updates.username !== undefined || updates.caption !== undefined) {
      await ReelsTrackerModule.updateReelMetadata(reelId, updates.username || '', updates.caption || '');
    }
    if (updates.watchTimeMs !== undefined || updates.completionPercent !== undefined) {
      await ReelsTrackerModule.updateReelWatchData(
        reelId, 
        updates.watchTimeMs || 0, 
        updates.completionPercent || 0
      );
    }
    if (updates.liked !== undefined || updates.commented !== undefined || updates.shared !== undefined) {
      await ReelsTrackerModule.updateReelEngagement(
        reelId, 
        !!updates.liked, 
        !!updates.commented, 
        !!updates.shared
      );
    }
  } catch (e) {
    console.warn('updateReel error', e);
  }
}

export async function clearAllReels(): Promise<void> {
  if (!ReelsTrackerModule) return;
  try {
    await ReelsTrackerModule.clearHistory();
  } catch (e) {
    console.warn('clearAllReels error', e);
  }
}

export async function calculateStatsFromSql(): Promise<Stats> {
  if (!ReelsTrackerModule) {
    return {
      totalReels: 0,
      totalWatchTimeMs: 0,
      avgWatchTimeMs: 0,
      avgCompletionPercent: 0,
      totalLikes: 0,
      totalComments: 0,
      todayReels: 0,
      todayWatchTimeMs: 0,
      hourlyActivity: Array(24).fill(0),
      topCreators: [],
      topKeywords: []
    };
  }
  try {
    const raw = await ReelsTrackerModule.getStats();
    if (!raw) {
      return {
        totalReels: 0,
        totalWatchTimeMs: 0,
        avgWatchTimeMs: 0,
        avgCompletionPercent: 0,
        totalLikes: 0,
        totalComments: 0,
        todayReels: 0,
        todayWatchTimeMs: 0,
        hourlyActivity: Array(24).fill(0),
        topCreators: [],
        topKeywords: []
      };
    }
    return JSON.parse(raw);
  } catch (e) {
    console.warn('calculateStats error', e);
    return {
      totalReels: 0,
      totalWatchTimeMs: 0,
      avgWatchTimeMs: 0,
      avgCompletionPercent: 0,
      totalLikes: 0,
      totalComments: 0,
      todayReels: 0,
      todayWatchTimeMs: 0,
      hourlyActivity: Array(24).fill(0),
      topCreators: [],
      topKeywords: []
    };
  }
}
