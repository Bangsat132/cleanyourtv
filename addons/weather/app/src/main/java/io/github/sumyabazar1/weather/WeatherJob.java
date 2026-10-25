package io.github.sumyabazar1.weather;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * Keeps the launcher's weather line and card picture fresh every 30 minutes without a service of its own: a periodic
 * job the system schedules and runs, which fetches the reading once, publishes it, and stops.
 *
 * The job asks for a network and is not persisted across reboots (the TV's own boot is a busy time, and the line fills
 * in again on the next run or when the page is opened). Nothing is shown, no notification is posted, and a failure of
 * the fetch only means the last reading stays as it was: the next run tries again.
 */
public final class WeatherJob extends JobService {
    /** The job id; the launcher schedules nothing, so one id is enough. */
    private static final int JOB_ID = 7210;
    /** How often the reading is taken: the launcher shows the same interval. */
    static final long INTERVAL_MS = 30 * 60_000L;

    /** Schedules the periodic run, replacing any earlier one; safe to call on every start of the app. */
    static void schedule(Context ctx) {
        JobScheduler scheduler = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(ctx, WeatherJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(INTERVAL_MS)
                .setPersisted(false)
                .build();
        scheduler.schedule(job);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        final PendingResult pending = new PendingResult(params);
        new Thread(() -> {
            boolean done;
            try {
                done = run();
            } catch (RuntimeException e) {
                android.util.Log.w("WeatherCard", "the periodic reading failed: " + e);
                done = false;
            }
            jobFinished(pending.params, !done);
        }, "weather-job").start();
        return true;   // the work runs on its own thread and calls jobFinished
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;   // the system stopped it (no network, or a new run): ask for another try
    }

    /** One reading, if a place has been chosen and the build carries a key. */
    private boolean run() {
        SharedPreferences prefs = getSharedPreferences("place", MODE_PRIVATE);
        if (!prefs.contains("name") || !Forecast.hasKey()) return true;   // nothing to do, not a failure
        double lat = Double.longBitsToDouble(prefs.getLong("lat", 0));
        double lon = Double.longBitsToDouble(prefs.getLong("lon", 0));
        String name = prefs.getString("name", "");
        boolean fahrenheit = "f".equalsIgnoreCase(getString(R.string.temperature_unit).trim());
        int days = Forecast.forecastDays(getResources().getInteger(R.integer.forecast_days));
        try {
            Forecast.Report report = Forecast.fetch(lat, lon, days);
            WeatherInfo.publish(this, report, name, fahrenheit);
            WeatherWidget.publish(this, report, name, fahrenheit);
            WeatherWidget.invalidate(this);
            return true;
        } catch (Exception e) {
            android.util.Log.w("WeatherCard", String.format(Locale.ROOT, "no reading this run: %s", e));
            return false;
        }
    }

    /** The job parameters, carried to the worker thread without capturing the service's own fields. */
    private static final class PendingResult {
        final JobParameters params;

        PendingResult(JobParameters params) {
            this.params = params;
        }
    }
}
