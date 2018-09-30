package app.insti;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Xfermode;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.squareup.picasso.Picasso;

import java.io.IOException;
import java.util.Map;

import app.insti.activity.MainActivity;
import app.insti.notifications.NotificationId;

public class InstiAppFirebaseMessagingService extends FirebaseMessagingService {
    String channel;

    @Override
    public void onNewToken(String s) {
        /* For future functionality */
        super.onNewToken(s);
    }

    /** Convert a string to string map to a bundle */
    private Bundle stringMapToBundle(Map<String, String> map) {
        Bundle bundle = new Bundle();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            bundle.putString(entry.getKey(), entry.getValue());
        }
        return bundle;
    }

    /** Get a PendingIntent to open MainActivity from a notification message */
    private PendingIntent getNotificationIntent(RemoteMessage remoteMessage, Integer notificationId) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra(Constants.MAIN_INTENT_EXTRAS, stringMapToBundle(remoteMessage.getData()));
        return PendingIntent.getActivity(this, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        String TAG = "NOTIFICATION";
        channel = getResources().getString(R.string.default_notification_channel_id);

        // Check if message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            Log.wtf(TAG, "Message data payload: " + remoteMessage.getData());
            String isData = remoteMessage.getData().get(Constants.FCM_BUNDLE_IS_DATA);
            if (isData != null && isData.equals("true")) {
                String type = remoteMessage.getData().get(Constants.FCM_BUNDLE_TYPE);
                String action = remoteMessage.getData().get(Constants.FCM_BUNDLE_ACTION);

                if (type.equals(Constants.DATA_TYPE_EVENT) && action.equals(Constants.FCM_BUNDLE_ACTION_STARTING)) {
                    sendEventStartingNotification(remoteMessage);
                }
            } else {
                sendMessageNotification(remoteMessage);
            }
        }

        super.onMessageReceived(remoteMessage);
    }

    /** Ensure key is in data */
    private boolean ensureKeyExists(RemoteMessage remoteMessage, String key) {
        return (remoteMessage.getData().get(key) != null);
    }

    /** Send a event is starting notification */
    private void sendEventStartingNotification(RemoteMessage remoteMessage) {
        if (!ensureKeyExists(remoteMessage, "name")) { return; }

        final String message = "Event is about to start";

        int notification_id = NotificationId.getID();
        showBitmapNotification(
                this,
                remoteMessage.getData().get("image_url"),
                remoteMessage.getData().get("large_icon"),
                notification_id,
                standardNotificationBuilder()
                    .setContentTitle(remoteMessage.getData().get("name"))
                    .setContentText(message)
                    .setContentIntent(getNotificationIntent(remoteMessage, notification_id)),
                message
        );
    }

    /** Send a standard notification from foreground */
    private void sendMessageNotification(RemoteMessage remoteMessage) {
        /* Get data */
        String title = remoteMessage.getNotification().getTitle();
        String body = remoteMessage.getNotification().getBody();
        Integer notification_id;
        try {
            notification_id = Integer.parseInt(remoteMessage.getData().get(Constants.FCM_BUNDLE_NOTIFICATION_ID));
        } catch (NumberFormatException ignored) {
            return;
        }

        /* Check malformed notifications */
        if (title == null || body == null) { return; }

        /* Build notification */
        Notification notification = standardNotificationBuilder()
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(getNotificationIntent(remoteMessage, notification_id))
                .build();

        /* Show notification */
        showNotification(notification_id, notification);
    }

    /** Show the notification */
    private void showNotification(int id, Notification notification) {
        showNotification(this, id, notification);
    }

    /** Show the notification */
    private static void showNotification(Context context, int id, Notification notification) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(id, notification);
    }

    /** Common builder */
    private NotificationCompat.Builder standardNotificationBuilder() {
        return new NotificationCompat.Builder(this, channel)
                .setSmallIcon(R.drawable.ic_lotusgray)
                .setColor(getResources().getColor(R.color.colorPrimary))
                .setVibrate(new long[]{0, 400})
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
    }

    /** Gets a bitmap from a URL asynchronously and shows notification */
    public static void showBitmapNotification(
            final Context context, final String imageUrl, final String largeIconUrl,
            final int notification_id, final NotificationCompat.Builder builder, final String content){

        new AsyncTask<Void, Void, Bitmap[]>() {
            @Override
            protected Bitmap[] doInBackground(Void... params) {
                try {
                    Bitmap image = Picasso.get().load(imageUrl).get();
                    Bitmap largeIcon = null;
                    if (largeIconUrl != null) {
                         largeIcon = getCroppedBitmap(Picasso.get().load(largeIconUrl).get(), 200);
                    }
                    return new Bitmap[]{image, largeIcon};
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Bitmap[] bitmaps) {
                if (bitmaps[0] != null) {
                    builder.setStyle(
                            new NotificationCompat.BigPictureStyle()
                                    .bigPicture(bitmaps[0])
                                    .setSummaryText(content)
                    );
                }

                if (bitmaps[1] != null) {
                    builder.setLargeIcon(bitmaps[1]);
                }
                showNotification(context, notification_id, builder.build());
                super.onPostExecute(bitmaps);
            }
        }.execute();
    }

    /** Get circular center cropped bitmap */
    public static Bitmap getCroppedBitmap(Bitmap bmp, int radius) {
        Bitmap sbmp;

        if (bmp.getWidth() != radius || bmp.getHeight() != radius) {
            float smallest = Math.min(bmp.getWidth(), bmp.getHeight());
            float factor = smallest / radius;
            sbmp = Bitmap.createScaledBitmap(bmp, (int)(bmp.getWidth() / factor), (int)(bmp.getHeight() / factor), false);
        } else {
            sbmp = bmp;
        }

        Bitmap output = Bitmap.createBitmap(radius, radius,
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final int color = 0xffa19774;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, radius, radius);
        final Rect destRect = new Rect(
                (sbmp.getWidth() - radius) / 2,
                (sbmp.getHeight() - radius) / 2,
                radius + (sbmp.getWidth() - radius) / 2,
                radius + (sbmp.getHeight() - radius) / 2);

        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(Color.parseColor("#BAB399"));
        canvas.drawCircle(radius / 2,
                radius / 2, radius / 2, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(sbmp, destRect, rect, paint);

        return output;
    }
}
