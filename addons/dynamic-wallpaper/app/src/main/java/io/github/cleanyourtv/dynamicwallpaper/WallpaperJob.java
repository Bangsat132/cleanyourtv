package io.github.cleanyourtv.dynamicwallpaper;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/**
 * Takes a reading and publishes the picture it chose, every 30 minutes, without a service of its own: a periodic job
 * the system schedules and runs, which fetches once, writes the picture and stops.
 *
 * The job asks for a network and is not persisted across reboots (the TV's own boot is a busy time, and the picture
 * already on disk is the right one until the next run). Nothing is shown, no notification is posted, and a failed
 * fetch only means the last reading and its picture stay: the next run tries again.
 */
public final class WallpaperJob extends JobService {
    /** The job id; this app schedules nothing else. */
    private static final int JOB_ID = 7310;

    /** Schedules the periodic run, replacing any earlier one; safe to call on every start of the app. */
    static void schedule(Context ctx) {
        JobScheduler scheduler = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(ctx, WallpaperJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(Weather.REFRESH_MS)
                .setPersisted(false)
                .build();
        try {
            scheduler.schedule(job);
        } catch (RuntimeException e) {
            // A refusing firmware must not take the app down: the picture then comes only from the page, which
            // publishes a reading each time it is opened.
            android.util.Log.w(Weather.TAG, "could not schedule the 30 minute job: " + e);
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try {
                publish(this, false);
            } finally {
                jobFinished(params, false);
            }
        }, "wallpaper-fetch").start();
        return true;   // the work runs on our thread
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;   // the next periodic run is soon enough
    }

    /**
     * Takes a reading (or the cache) and publishes the picture for it. Returns the reading shown, or null when there
     * has never been one; the caller decides what to say about that.
     */
    static Weather.Reading publish(Context ctx, boolean force) {
        Weather.Reading reading = Weather.get(ctx, force);
        if (reading == null) return null;
        String place = ctx.getString(R.string.place_name);
        Wallpapers.publish(ctx, reading, place, condition(ctx, reading));
        return reading;
    }

    /** The reading's own words for the sky, in the TV's language, from this build's strings. */
    static String condition(Context ctx, Weather.Reading reading) {
        int id = ctx.getResources().getIdentifier(Mood.labelKey(reading.mood()), "string", ctx.getPackageName());
        return id == 0 ? reading.mood() : ctx.getString(id);
    }
}
