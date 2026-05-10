package com.scrollmind.tracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite database helper for buffering scraped Reels data locally.
 * Acts as a queue before events are broadcast to React Native and synced to the cloud.
 */
public class TrackerDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "scrollmind.db";
    private static final int DATABASE_VERSION = 1;

    // ── Reels Table ──────────────────────────────────────────────────────────────
    public static final String TABLE_REELS = "reels";
    public static final String COL_ID = "_id";
    public static final String COL_USERNAME = "username";
    public static final String COL_CAPTION = "caption";
    public static final String COL_WATCH_TIME_MS = "watch_time_ms";
    public static final String COL_COMPLETION_PERCENT = "completion_percent";
    public static final String COL_LIKED = "liked";
    public static final String COL_COMMENTED = "commented";
    public static final String COL_SHARED = "shared";
    public static final String COL_THUMBNAIL_PATH = "thumbnail_path";
    public static final String COL_OCR_TEXT = "ocr_text";
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_SYNCED = "synced";

    private static final String CREATE_TABLE_REELS =
            "CREATE TABLE " + TABLE_REELS + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_USERNAME + " TEXT, " +
                    COL_CAPTION + " TEXT, " +
                    COL_WATCH_TIME_MS + " INTEGER DEFAULT 0, " +
                    COL_COMPLETION_PERCENT + " REAL DEFAULT 0.0, " +
                    COL_LIKED + " INTEGER DEFAULT 0, " +
                    COL_COMMENTED + " INTEGER DEFAULT 0, " +
                    COL_SHARED + " INTEGER DEFAULT 0, " +
                    COL_THUMBNAIL_PATH + " TEXT, " +
                    COL_OCR_TEXT + " TEXT, " +
                    COL_TIMESTAMP + " INTEGER DEFAULT 0, " +
                    COL_SYNCED + " INTEGER DEFAULT 0" +
                    ");";

    // ── Stats Table ──────────────────────────────────────────────────────────────
    public static final String TABLE_SESSION_STATS = "session_stats";
    public static final String COL_SESSION_ID = "session_id";
    public static final String COL_SESSION_START = "session_start";
    public static final String COL_SESSION_END = "session_end";
    public static final String COL_REEL_COUNT = "reel_count";
    public static final String COL_TOTAL_WATCH_TIME = "total_watch_time_ms";

    private static final String CREATE_TABLE_SESSION_STATS =
            "CREATE TABLE " + TABLE_SESSION_STATS + " (" +
                    COL_SESSION_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_SESSION_START + " INTEGER, " +
                    COL_SESSION_END + " INTEGER, " +
                    COL_REEL_COUNT + " INTEGER DEFAULT 0, " +
                    COL_TOTAL_WATCH_TIME + " INTEGER DEFAULT 0" +
                    ");";

    // ── Singleton ────────────────────────────────────────────────────────────────
    private static TrackerDatabaseHelper sInstance;

    public static synchronized TrackerDatabaseHelper getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new TrackerDatabaseHelper(context.getApplicationContext());
        }
        return sInstance;
    }

    private TrackerDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_REELS);
        db.execSQL(CREATE_TABLE_SESSION_STATS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_REELS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SESSION_STATS);
        onCreate(db);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    //  REEL CRUD
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Inserts a new reel record and returns its row ID.
     */
    public long insertReel(String username, String caption) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_USERNAME, username);
        cv.put(COL_CAPTION, caption);
        cv.put(COL_TIMESTAMP, System.currentTimeMillis());
        cv.put(COL_SYNCED, 0);
        return db.insert(TABLE_REELS, null, cv);
    }

    public void updateReelThumbnail(long reelId, String path) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_THUMBNAIL_PATH, path);
        db.update(TABLE_REELS, cv, COL_ID + " = ?", new String[]{String.valueOf(reelId)});
    }

    /**
     * Updates watch time and completion for an existing reel record.
     */
    public void updateReelWatchData(long reelId, long watchTimeMs, double completionPercent) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_WATCH_TIME_MS, watchTimeMs);
        cv.put(COL_COMPLETION_PERCENT, completionPercent);
        db.update(TABLE_REELS, cv, COL_ID + " = ?", new String[]{String.valueOf(reelId)});
    }

    /**
     * Marks a like, comment, or share on a reel.
     */
    public void updateReelEngagement(long reelId, boolean liked, boolean commented, boolean shared) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        if (liked) cv.put(COL_LIKED, 1);
        if (commented) cv.put(COL_COMMENTED, 1);
        if (shared) cv.put(COL_SHARED, 1);
        if (cv.size() > 0) {
            db.update(TABLE_REELS, cv, COL_ID + " = ?", new String[]{String.valueOf(reelId)});
        }
    }

    /**
     * Stores OCR-extracted text for a reel.
     */
    public void updateReelOcrText(long reelId, String ocrText) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_OCR_TEXT, ocrText);
        db.update(TABLE_REELS, cv, COL_ID + " = ?", new String[]{String.valueOf(reelId)});
    }

    /**
     * Returns the number of reels watched today.
     */
    public int getTodayReelCount() {
        SQLiteDatabase db = getReadableDatabase();
        long todayStart = getTodayStartMillis();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_REELS + " WHERE " + COL_TIMESTAMP + " >= ?",
                new String[]{String.valueOf(todayStart)});
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }

    /**
     * Returns the total number of reels tracked.
     */
    public int getTotalReelCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_REELS, null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }

    /**
     * Returns all un-synced reels as a JSON array string (for React Native).
     */
    public String getUnsyncedReelsJSON() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_REELS, null,
                COL_SYNCED + " = 0", null, null, null,
                COL_TIMESTAMP + " DESC", "100");

        JSONArray arr = new JSONArray();
        while (cursor.moveToNext()) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)));
                obj.put("username", cursor.getString(cursor.getColumnIndexOrThrow(COL_USERNAME)));
                obj.put("caption", cursor.getString(cursor.getColumnIndexOrThrow(COL_CAPTION)));
                obj.put("watchTimeMs", cursor.getLong(cursor.getColumnIndexOrThrow(COL_WATCH_TIME_MS)));
                obj.put("completionPercent", cursor.getDouble(cursor.getColumnIndexOrThrow(COL_COMPLETION_PERCENT)));
                obj.put("liked", cursor.getInt(cursor.getColumnIndexOrThrow(COL_LIKED)) == 1);
                obj.put("commented", cursor.getInt(cursor.getColumnIndexOrThrow(COL_COMMENTED)) == 1);
                obj.put("shared", cursor.getInt(cursor.getColumnIndexOrThrow(COL_SHARED)) == 1);
                obj.put("thumbnailPath", cursor.getString(cursor.getColumnIndexOrThrow(COL_THUMBNAIL_PATH)));
                obj.put("ocrText", cursor.getString(cursor.getColumnIndexOrThrow(COL_OCR_TEXT)));
                obj.put("timestamp", cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP)));
                arr.put(obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        cursor.close();
        return arr.toString();
    }

    /**
     * Returns the most recent N reels as a JSON array string.
     */
    public String getRecentReelsJSON(int limit) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_REELS, null,
                null, null, null, null,
                COL_TIMESTAMP + " DESC", String.valueOf(limit));

        JSONArray arr = new JSONArray();
        while (cursor.moveToNext()) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)));
                obj.put("username", cursor.getString(cursor.getColumnIndexOrThrow(COL_USERNAME)));
                obj.put("caption", cursor.getString(cursor.getColumnIndexOrThrow(COL_CAPTION)));
                obj.put("watchTimeMs", cursor.getLong(cursor.getColumnIndexOrThrow(COL_WATCH_TIME_MS)));
                obj.put("completionPercent", cursor.getDouble(cursor.getColumnIndexOrThrow(COL_COMPLETION_PERCENT)));
                obj.put("liked", cursor.getInt(cursor.getColumnIndexOrThrow(COL_LIKED)) == 1);
                obj.put("commented", cursor.getInt(cursor.getColumnIndexOrThrow(COL_COMMENTED)) == 1);
                obj.put("shared", cursor.getInt(cursor.getColumnIndexOrThrow(COL_SHARED)) == 1);
                obj.put("thumbnailPath", cursor.getString(cursor.getColumnIndexOrThrow(COL_THUMBNAIL_PATH)));
                obj.put("ocrText", cursor.getString(cursor.getColumnIndexOrThrow(COL_OCR_TEXT)));
                obj.put("timestamp", cursor.getLong(cursor.getColumnIndexOrThrow(COL_TIMESTAMP)));
                arr.put(obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        cursor.close();
        return arr.toString();
    }

    /**
     * Marks reels as synced after they have been sent to React Native / cloud.
     */
    public void markReelsSynced(List<Long> ids) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_SYNCED, 1);
        for (Long id : ids) {
            db.update(TABLE_REELS, cv, COL_ID + " = ?", new String[]{String.valueOf(id)});
        }
    }

    /**
     * Returns aggregate stats as a JSON string.
     */
    public String getAggregateStatsJSON() {
        SQLiteDatabase db = getReadableDatabase();
        JSONObject stats = new JSONObject();
        try {
            // Total reels
            Cursor c1 = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_REELS, null);
            if (c1.moveToFirst()) stats.put("totalReels", c1.getInt(0));
            c1.close();

            // Total watch time
            Cursor c2 = db.rawQuery("SELECT SUM(" + COL_WATCH_TIME_MS + ") FROM " + TABLE_REELS, null);
            if (c2.moveToFirst()) stats.put("totalWatchTimeMs", c2.getLong(0));
            c2.close();

            // Average watch time
            Cursor c3 = db.rawQuery("SELECT AVG(" + COL_WATCH_TIME_MS + ") FROM " + TABLE_REELS, null);
            if (c3.moveToFirst()) stats.put("avgWatchTimeMs", c3.getDouble(0));
            c3.close();

            // Average completion
            Cursor c4 = db.rawQuery("SELECT AVG(" + COL_COMPLETION_PERCENT + ") FROM " + TABLE_REELS, null);
            if (c4.moveToFirst()) stats.put("avgCompletionPercent", c4.getDouble(0));
            c4.close();

            // Total likes
            Cursor c5 = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_REELS + " WHERE " + COL_LIKED + " = 1", null);
            if (c5.moveToFirst()) stats.put("totalLikes", c5.getInt(0));
            c5.close();

            // Total comments
            Cursor c6 = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_REELS + " WHERE " + COL_COMMENTED + " = 1", null);
            if (c6.moveToFirst()) stats.put("totalComments", c6.getInt(0));
            c6.close();

            // Today's reels count
            long todayStart = getTodayStartMillis();
            Cursor c7 = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_REELS + " WHERE " + COL_TIMESTAMP + " >= ?",
                    new String[]{String.valueOf(todayStart)});
            if (c7.moveToFirst()) stats.put("todayReels", c7.getInt(0));
            c7.close();

            // Today's watch time
            Cursor c8 = db.rawQuery("SELECT SUM(" + COL_WATCH_TIME_MS + ") FROM " + TABLE_REELS + " WHERE " + COL_TIMESTAMP + " >= ?",
                    new String[]{String.valueOf(todayStart)});
            if (c8.moveToFirst()) stats.put("todayWatchTimeMs", c8.getLong(0));
            c8.close();

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return stats.toString();
    }

    /**
     * Deletes all reel records from the database.
     */
    public void clearAllReels() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_REELS, null, null);
    }

    private long getTodayStartMillis() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
