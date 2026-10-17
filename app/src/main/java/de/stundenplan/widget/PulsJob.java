package de.stundenplan.widget;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Täglicher Abgleich mit PULS im Hintergrund (Android entscheidet, wann – ggf. nur ohne mobile Daten). */
public class PulsJob extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try {
                PulsDienst.abgleichen(getApplicationContext(), false);
            } finally {
                jobFinished(params, false);
            }
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
