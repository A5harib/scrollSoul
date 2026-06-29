import { useEffect, useState, useCallback, useRef } from 'react';
import { NativeModules, NativeEventEmitter, Platform } from 'react-native';
import { 
  loadReels, 
  insertReel, 
  updateReel, 
  clearAllReels, 
  calculateStatsFromSql 
} from '../utils/reelsStore';

const { ReelsTrackerModule } = NativeModules;
const eventEmitter = Platform.OS === 'android' && ReelsTrackerModule
  ? new NativeEventEmitter(ReelsTrackerModule)
  : null;

// Space to add Groq API key:
// You can define it in process.env.GROQ_API_KEY or assign it here directly.
declare const process: any;
const GROQ_API_KEY = (typeof process !== 'undefined' && process.env ? process.env.GROQ_API_KEY : "") || "YOUR_GROQ_API_KEY_HERE";

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
  hourlyActivity?: number[];
  topCreators?: Array<{ username: string; watchTimeMs: number; count: number }>;
  topKeywords?: Array<{ text: string; value: number }>;
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
    isRunning: false,
    isInReelsView: false,
    currentReelId: -1,
    reelCounter: 0,
    currentUsername: '',
  });
  
  const [stats, setStats] = useState<Stats>({
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
    topKeywords: [],
  });
  
  const [recentReels, setRecentReels] = useState<ReelData[]>([]);
  const [liveEvent, setLiveEvent] = useState<{ name: string; data: any } | null>(null);

  // References to keep track of active state and scroll settling
  const activeReelId = useRef<number>(-1);
  const activeReelTimestamp = useRef<number>(0);
  const settlingTimer = useRef<NodeJS.Timeout | null>(null);
  
  const checkService = useCallback(async () => {
    if (!ReelsTrackerModule) return;
    try {
      const enabled = await ReelsTrackerModule.isServiceEnabled();
      setIsEnabled(enabled);
      const raw = await ReelsTrackerModule.getServiceStatus();
      const s = JSON.parse(raw);
      setStatus(prev => ({
        ...prev,
        isRunning: s.isRunning,
        isInReelsView: s.isInReelsView,
      }));
    } catch (e) {
      console.warn('checkService error', e);
    }
  }, []);

  const refreshStatsAndReels = useCallback(async () => {
    try {
      const reels = await loadReels();
      setRecentReels(reels);
      const computed = await calculateStatsFromSql();
      setStats(computed);
      
      // Sync overlay count with today's count calculated in JS
      if (ReelsTrackerModule) {
        ReelsTrackerModule.updateOverlay(`Reels Today: ${computed.todayReels}`);
      }
    } catch (e) {
      console.warn('refreshStatsAndReels error', e);
    }
  }, []);

  const openSettings = useCallback(() => {
    ReelsTrackerModule?.openAccessibilitySettings();
  }, []);

  const setToggle = useCallback(async (key: string, value: boolean) => {
    if (!ReelsTrackerModule) return;
    await ReelsTrackerModule.setToggle(key, value);
  }, []);

  const clearHistory = useCallback(async () => {
    try {
      await clearAllReels();
      setRecentReels([]);
      setStats({
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
        topKeywords: [],
      });
      if (ReelsTrackerModule) {
        ReelsTrackerModule.updateOverlay("Reels Today: 0");
      }
    } catch (e) {
      console.warn('clearHistory error', e);
    }
  }, []);

  // Groq OCR API integration with Fallback mechanism
  const performGroqOcr = async (base64Image: string): Promise<{ username: string; caption: string; ocrText: string }> => {
    if (!GROQ_API_KEY || GROQ_API_KEY === "YOUR_GROQ_API_KEY_HERE") {
      console.warn("Groq API key is not configured. Please add the key in env or useReelsTracker.ts.");
      return { username: "", caption: "", ocrText: "Groq key missing" };
    }

    const payload = {
      model: "meta-llama/llama-4-scout-17b-16e-instruct",
      messages: [
        {
          role: "user",
          content: [
            {
              type: "text",
              text: "Extract the Instagram username and caption text visible in this Reel. Return JSON ONLY in this format: { \"username\": \"...\", \"caption\": \"...\" } without markdown packaging."
            },
            {
              type: "image_url",
              image_url: {
                url: `data:image/jpeg;base64,${base64Image}`
              }
            }
          ]
        }
      ],
      response_format: { type: "json_object" }
    };

    // Primary request
    try {
      const response = await fetch("https://api.groq.com/openai/v1/chat/completions", {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${GROQ_API_KEY}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        throw new Error(`Primary Groq model call failed: ${response.status} ${response.statusText}`);
      }

      const resJson = await response.json();
      const content = resJson.choices?.[0]?.message?.content || "";
      const parsed = JSON.parse(content);
      return {
        username: parsed.username || "",
        caption: parsed.caption || "",
        ocrText: content
      };
    } catch (err) {
      console.warn("Groq primary model failed, attempting Qwen fallback...", err);
      
      // Fallback model request
      const fallbackPayload = {
        ...payload,
        model: "qwen/qwen3.6-27b"
      };

      try {
        const response = await fetch("https://api.groq.com/openai/v1/chat/completions", {
          method: "POST",
          headers: {
            "Authorization": `Bearer ${GROQ_API_KEY}`,
            "Content-Type": "application/json"
          },
          body: JSON.stringify(fallbackPayload)
        });

        if (!response.ok) {
          throw new Error(`Fallback Qwen model call failed: ${response.status} ${response.statusText}`);
        }

        const resJson = await response.json();
        const content = resJson.choices?.[0]?.message?.content || "";
        const parsed = JSON.parse(content);
        return {
          username: parsed.username || "",
          caption: parsed.caption || "",
          ocrText: content
        };
      } catch (fallbackErr) {
        console.error("OCR Pipeline completely failed:", fallbackErr);
        return { username: "", caption: "", ocrText: `OCR processing error: ${fallbackErr}` };
      }
    }
  };

  useEffect(() => {
    checkService();
    refreshStatsAndReels();

    if (!eventEmitter) return;

    const subs = [
      eventEmitter.addListener('onReelScrolled', async (e) => {
        const data = JSON.parse(e.data); // data has: username, caption, likeCount, timestamp
        setLiveEvent({ name: 'scroll', data });

        // 1. Finalize watch time of the previous reel if active
        if (activeReelId.current !== -1) {
          const watchTime = Date.now() - activeReelTimestamp.current;
          await updateReel(activeReelId.current, { watchTimeMs: watchTime });
          activeReelId.current = -1;
        }

        // 2. Cancel any pending settled scroll timers (the user is scrolling rapidly)
        if (settlingTimer.current) {
          clearTimeout(settlingTimer.current);
          settlingTimer.current = null;
        }

        // 3. Start a settling timer for 1.5 seconds (the "Perfect Scrolling Logic")
        // This ensures OCR and database insertion only run once the user has successfully scrolled and paused
        const scrapedUser = data.username || '';
        const scrapedCaption = data.caption || '';
        const scrollTime = Date.now();

        settlingTimer.current = setTimeout(async () => {
          // Settled on new reel! Let's insert the new reel record into the JS database
          const currentId = await insertReel(scrapedUser, scrapedCaption);
          activeReelId.current = currentId;
          activeReelTimestamp.current = scrollTime;

          setStatus(prev => ({
            ...prev,
            currentReelId: currentId,
            currentUsername: scrapedUser || 'Scanning...',
          }));

          // Trigger OCR if enabled in preferences
          const ocrEnabled = await ReelsTrackerModule?.getToggle("toggle_ocr");
          if (ocrEnabled && ReelsTrackerModule) {
            try {
              const base64 = await ReelsTrackerModule.takeScreenshot();
              
              // Run OCR asynchronously
              performGroqOcr(base64).then(async (ocrResults) => {
                await updateReel(currentId, {
                  username: ocrResults.username || scrapedUser || 'unknown',
                  caption: ocrResults.caption || scrapedCaption || '',
                  ocrText: ocrResults.ocrText
                });
                refreshStatsAndReels();
              });

            } catch (err) {
              console.warn("Screenshot capture error:", err);
            }
          }

          refreshStatsAndReels();
        }, 1500);
      }),

      eventEmitter.addListener('onProgressUpdate', async (e) => {
        const data = JSON.parse(e.data); // data has: completionPercent, liked, commented, shared
        setLiveEvent({ name: 'progress', data });

        if (activeReelId.current !== -1) {
          await updateReel(activeReelId.current, {
            completionPercent: data.completionPercent,
            liked: data.liked,
            commented: data.commented,
            shared: data.shared
          });
          
          // Smooth real-time update in UI
          const reels = await loadReels();
          setRecentReels(reels);
          const computed = await calculateStatsFromSql();
          setStats(computed);
        }
      }),

      eventEmitter.addListener('onEnterReels', () => {
        setLiveEvent({ name: 'enter', data: {} });
        checkService();
        activeReelTimestamp.current = Date.now();
        refreshStatsAndReels();
      }),

      eventEmitter.addListener('onExitReels', async (e) => {
        setLiveEvent({ name: 'exit', data: {} });
        checkService();

        // Finalize last active reel on exit
        if (activeReelId.current !== -1) {
          const watchTime = Date.now() - activeReelTimestamp.current;
          await updateReel(activeReelId.current, { watchTimeMs: watchTime });
          activeReelId.current = -1;
        }

        if (settlingTimer.current) {
          clearTimeout(settlingTimer.current);
          settlingTimer.current = null;
        }

        refreshStatsAndReels();
      }),
    ];

    const interval = setInterval(() => {
      checkService();
      refreshStatsAndReels();
    }, 15000);

    return () => {
      subs.forEach(s => s.remove());
      clearInterval(interval);
      if (settlingTimer.current) {
        clearTimeout(settlingTimer.current);
      }
    };
  }, [checkService, refreshStatsAndReels]);

  return {
    isEnabled,
    status,
    stats,
    recentReels,
    liveEvent,
    openSettings,
    setToggle,
    refreshStats: refreshStatsAndReels,
    refreshReels: refreshStatsAndReels,
    checkService,
    clearHistory,
  };
}
