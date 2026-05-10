import { useEffect, useState, useCallback } from 'react';
import { NativeModules, NativeEventEmitter, Platform } from 'react-native';

const { ReelsTrackerModule } = NativeModules;
const eventEmitter = Platform.OS === 'android' && ReelsTrackerModule
  ? new NativeEventEmitter(ReelsTrackerModule)
  : null;

export interface ReelData {
  id: number;
  username: string;
  caption: string;
  watchTimeMs: number;
  completionPercent: number;
  liked: boolean;
  commented: boolean;
  shared: boolean;
  thumbnailPath?: string;
  ocrText: string;
  timestamp: number;
}

export interface Stats {
  totalReels: number;
  totalWatchTimeMs: number;
  avgWatchTimeMs: number;
  avgCompletionPercent: number;
  totalLikes: number;
  totalComments: number;
  todayReels: number;
  todayWatchTimeMs: number;
}

export interface ServiceStatus {
  isRunning: boolean;
  isInReelsView: boolean;
  currentReelId: number;
  reelCounter: number;
  currentUsername: string;
}

export function useReelsTracker() {
  const [isEnabled, setIsEnabled] = useState(false);
  const [status, setStatus] = useState<ServiceStatus>({
    isRunning: false, isInReelsView: false, currentReelId: -1,
    reelCounter: 0, currentUsername: '',
  });
  const [stats, setStats] = useState<Stats>({
    totalReels: 0, totalWatchTimeMs: 0, avgWatchTimeMs: 0,
    avgCompletionPercent: 0, totalLikes: 0, totalComments: 0,
    todayReels: 0, todayWatchTimeMs: 0,
  });
  const [recentReels, setRecentReels] = useState<ReelData[]>([]);
  const [liveEvent, setLiveEvent] = useState<{ name: string; data: any } | null>(null);

  const checkService = useCallback(async () => {
    if (!ReelsTrackerModule) return;
    try {
      const enabled = await ReelsTrackerModule.isServiceEnabled();
      setIsEnabled(enabled);
      const raw = await ReelsTrackerModule.getServiceStatus();
      setStatus(JSON.parse(raw));
    } catch (e) { console.warn('checkService error', e); }
  }, []);

  const refreshStats = useCallback(async () => {
    if (!ReelsTrackerModule) return;
    try {
      const raw = await ReelsTrackerModule.getStats();
      setStats(JSON.parse(raw));
    } catch (e) { console.warn('refreshStats error', e); }
  }, []);

  const refreshReels = useCallback(async (limit = 50) => {
    if (!ReelsTrackerModule) return;
    try {
      const raw = await ReelsTrackerModule.getRecentReels(limit);
      setRecentReels(JSON.parse(raw));
    } catch (e) { console.warn('refreshReels error', e); }
  }, []);

  const openSettings = useCallback(() => {
    ReelsTrackerModule?.openAccessibilitySettings();
  }, []);

  const setToggle = useCallback(async (key: string, value: boolean) => {
    if (!ReelsTrackerModule) return;
    await ReelsTrackerModule.setToggle(key, value);
  }, []);

  const clearHistory = useCallback(async () => {
    if (!ReelsTrackerModule) return;
    try {
      await ReelsTrackerModule.clearHistory();
      setRecentReels([]);
      await refreshStats();
    } catch (e) { console.warn('clearHistory error', e); }
  }, [refreshStats]);

  useEffect(() => {
    checkService();
    refreshStats();
    refreshReels();

    if (!eventEmitter) return;

    const subs = [
      eventEmitter.addListener('onReelScrolled', (e) => {
        setLiveEvent({ name: 'scroll', data: JSON.parse(e.data) });
        refreshStats();
        refreshReels();
      }),
      eventEmitter.addListener('onEngagement', (e) => {
        setLiveEvent({ name: 'engagement', data: JSON.parse(e.data) });
        refreshStats();
      }),
      eventEmitter.addListener('onEnterReels', () => {
        setLiveEvent({ name: 'enter', data: {} });
        checkService();
      }),
      eventEmitter.addListener('onExitReels', (e) => {
        setLiveEvent({ name: 'exit', data: JSON.parse(e.data) });
        checkService();
        refreshStats();
      }),
      eventEmitter.addListener('onProgressUpdate', (e) => {
        setLiveEvent({ name: 'progress', data: JSON.parse(e.data) });
      }),
    ];

    const interval = setInterval(() => { checkService(); refreshStats(); }, 10000);
    return () => { subs.forEach(s => s.remove()); clearInterval(interval); };
  }, []);

  return {
    isEnabled, status, stats, recentReels, liveEvent,
    openSettings, setToggle, refreshStats, refreshReels,
    checkService, clearHistory,
  };
}
