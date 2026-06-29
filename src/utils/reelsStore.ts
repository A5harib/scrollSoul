import { NativeModules } from 'react-native';
import { ReelData, Stats } from '../hooks/useReelsTracker';

const { ReelsTrackerModule } = NativeModules;

const STORAGE_FILE = 'reels.json';

// Simple stop words for keyword extraction
const STOP_WORDS = new Set([
  'this', 'that', 'with', 'from', 'your', 'their', 'video', 'reels', 
  'instagram', 'what', 'when', 'where', 'like', 'they', 'there',
  'and', 'the', 'for', 'you', 'are', 'was', 'were', 'have', 'has', 'had',
  'but', 'not', 'our', 'out', 'this', 'about', 'will', 'would', 'could'
]);

export async function loadReels(): Promise<ReelData[]> {
  if (!ReelsTrackerModule) return [];
  try {
    const raw = await ReelsTrackerModule.readTextFile(STORAGE_FILE);
    if (!raw) return [];
    return JSON.parse(raw);
  } catch (e) {
    console.warn('loadReels error, returning empty', e);
    return [];
  }
}

export async function saveReels(reels: ReelData[]): Promise<boolean> {
  if (!ReelsTrackerModule) return false;
  try {
    await ReelsTrackerModule.writeTextFile(STORAGE_FILE, JSON.stringify(reels, null, 2));
    return true;
  } catch (e) {
    console.warn('saveReels error', e);
    return false;
  }
}

export async function insertReel(username: string, caption: string): Promise<number> {
  const reels = await loadReels();
  const newId = reels.length > 0 ? Math.max(...reels.map(r => r.id)) + 1 : 1;
  const newReel: ReelData = {
    id: newId,
    username: username || '',
    caption: caption || '',
    watchTimeMs: 0,
    completionPercent: 0,
    liked: false,
    commented: false,
    shared: false,
    ocrText: '',
    timestamp: Date.now()
  };
  reels.unshift(newReel); // add to top
  await saveReels(reels);
  return newId;
}

export async function updateReel(reelId: number, updates: Partial<ReelData>): Promise<void> {
  const reels = await loadReels();
  const idx = reels.findIndex(r => r.id === reelId);
  if (idx !== -1) {
    reels[idx] = { ...reels[idx], ...updates };
    await saveReels(reels);
  }
}

export async function clearAllReels(): Promise<void> {
  await saveReels([]);
}

export function calculateStats(reels: ReelData[]): Stats {
  const totalReels = reels.length;
  let totalWatchTimeMs = 0;
  let totalLikes = 0;
  let totalComments = 0;
  let completionSum = 0;

  const now = new Date();
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();

  let todayReels = 0;
  let todayWatchTimeMs = 0;

  const hourlyActivity = Array(24).fill(0);
  const creatorGroups: Record<string, { watchTimeMs: number; count: number }> = {};
  const wordFreq: Record<string, number> = {};

  reels.forEach(reel => {
    totalWatchTimeMs += reel.watchTimeMs;
    completionSum += reel.completionPercent;
    if (reel.liked) totalLikes++;
    if (reel.commented) totalComments++;

    // Today filter
    if (reel.timestamp >= todayStart) {
      todayReels++;
      todayWatchTimeMs += reel.watchTimeMs;
    }

    // Hourly activity
    const reelDate = new Date(reel.timestamp);
    const hour = reelDate.getHours();
    if (hour >= 0 && hour < 24) {
      hourlyActivity[hour]++;
    }

    // Top creators grouping
    if (reel.username && reel.username.trim() !== '') {
      const u = reel.username.trim();
      if (!creatorGroups[u]) {
        creatorGroups[u] = { watchTimeMs: 0, count: 0 };
      }
      creatorGroups[u].watchTimeMs += reel.watchTimeMs;
      creatorGroups[u].count++;
    }

    // Keyword frequencies from caption and OCR text
    processTextWords(reel.caption, wordFreq);
    processTextWords(reel.ocrText, wordFreq);
  });

  const avgWatchTimeMs = totalReels > 0 ? totalWatchTimeMs / totalReels : 0;
  const avgCompletionPercent = totalReels > 0 ? completionSum / totalReels : 0;

  // Top Creators mapping
  const topCreators = Object.entries(creatorGroups)
    .map(([username, group]) => ({
      username,
      watchTimeMs: group.watchTimeMs,
      count: group.count
    }))
    .sort((a, b) => b.watchTimeMs - a.watchTimeMs)
    .slice(0, 10);

  // Top Keywords mapping
  const topKeywords = Object.entries(wordFreq)
    .map(([text, value]) => ({ text, value }))
    .sort((a, b) => b.value - a.value)
    .slice(0, 40);

  return {
    totalReels,
    totalWatchTimeMs,
    avgWatchTimeMs,
    avgCompletionPercent,
    totalLikes,
    totalComments,
    todayReels,
    todayWatchTimeMs,
    hourlyActivity,
    topCreators,
    topKeywords
  };
}

function processTextWords(text: string | undefined, freq: Record<string, number>) {
  if (!text || text.trim() === '') return;
  const words = text.toLowerCase().split(/[^a-zA-Z0-9_]+/);
  words.forEach(w => {
    if (w.length > 3 && !STOP_WORDS.has(w) && !/^\d+$/.test(w)) {
      freq[w] = (freq[w] || 0) + 1;
    }
  });
}
