package de.be.thaw.util.job.jobs;

import android.app.NotificationManager;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobRequest;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

import de.be.thaw.R;
import de.be.thaw.cache.ScheduleUtil;
import de.be.thaw.model.schedule.ScheduleItem;
import de.be.thaw.util.Preference;
import de.be.thaw.util.TimeUtil;

/**
 * Job which is displaying a notification about the next event.
 * It is updated once the event has come to an end.
 *
 * Created by Benjamin Eder on 17.03.2017.
 */
public class StaticWeekPlanNotificationJob extends Job {

	public static final String TAG = "StaticWeekPlanNotificationJob";
	public static final int NOTIFICATION_ID = 2;

	/**
	 * How much time to wait after a event happened before searching for the next event.
	 * (in minutes). (At least one!)
	 */
	private static final int BUFFER = 1;

	private static int runningJobId = -1;

	@NonNull
	@Override
	protected Result onRunJob(Params params) {
		// Get stored entries for this week
		Calendar cal = Calendar.getInstance();

		List<ScheduleItem> items = null;
		try {
			items = ScheduleUtil.retrieve(getContext());
		} catch (IOException e) {
			e.printStackTrace();
		}


		Calendar eventCal = Calendar.getInstance();
		ScheduleItem event = null;
		if (items != null) {
			long difference = Long.MAX_VALUE;

			for (ScheduleItem item : items) {
				eventCal.setTime(item.getStart());

				if (cal.get(Calendar.DAY_OF_MONTH) == eventCal.get(Calendar.DAY_OF_MONTH)) {
					if (!item.isEventCancelled()) {
						eventCal.setTime(item.getStart());

						long newDiff = eventCal.getTimeInMillis() - cal.getTimeInMillis();
						if (newDiff > 0 && newDiff < difference) {
							event = item;
							difference = newDiff;
						}
					}
				}
			}
		}

		// Issue notification
		issueNotification(event);

		// Reschedule job
		runningJobId = -1;
		if (event != null) {
			eventCal.setTime(event.getStart());

			scheduleJob(eventCal.getTimeInMillis() - cal.getTimeInMillis() + TimeUnit.MINUTES.toMillis(BUFFER));
		} else {
			scheduleJob(TimeUnit.HOURS.toMillis(1));
		}

		return Result.SUCCESS;
	}

	/**
	 * Issue notification about the next event.
	 */
	private void issueNotification(ScheduleItem item) {
		if (item != null) {
			Calendar cal = Calendar.getInstance();
			cal.setTime(item.getStart());

			String start = TimeUtil.getTimeString(cal);
			String room = item.getLocation();

			NotificationCompat.Builder notificationBuilder =
					new NotificationCompat.Builder(getContext(), getContext().getString(R.string.channelId))
							.setSmallIcon(R.drawable.notification_icon)
							.setContentTitle(getContext().getResources().getString(R.string.staticEventNotificationTitle))
							.setContentText(item.getTitle() + " - " + room + " - " + start)
							.setOngoing(true)
							.setPriority(NotificationCompat.PRIORITY_LOW);

			NotificationManager notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
		} else {
			NotificationManager notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.cancel(NOTIFICATION_ID);
		}
	}

	/**
	 * Schedule the job to be executed.
	 */
	public static void scheduleJob() {
		scheduleJob(TimeUnit.SECONDS.toMillis(1)); // Issue it instantly
	}

	/**
	 * Schedule the job to be executed but wait some time before issuing it.
	 *
	 * @param millisToWait
	 */
	private static void scheduleJob(long millisToWait) {
		if (runningJobId == -1) {
			runningJobId = new JobRequest.Builder(TAG)
					.setExact(millisToWait)
					.build()
					.schedule();
		}
	}

	/**
	 * Cancel this job.
	 */
	public static void cancelJob(Context context) {
		JobManager.instance().cancelAllForTag(TAG);

		runningJobId = -1;

		// Cancel notification
		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(NOTIFICATION_ID);
	}

	/**
	 * Return whether this job is activated (Activatable via settings by user).
	 * @return
	 */
	public static boolean isActivated(Context context) {
		return Preference.STATIC_WEEK_PLAN_NOTIFICATION_ENABLED.getBoolean(context);
	}

}
