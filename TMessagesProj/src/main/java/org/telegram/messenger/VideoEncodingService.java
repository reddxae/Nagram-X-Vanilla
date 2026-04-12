/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class VideoEncodingService extends Service implements NotificationCenter.NotificationCenterDelegate {

    private static final long NOTIFICATION_UPDATE_DELAY_MS = 500L;

    private NotificationCompat.Builder builder;
    private MediaController.VideoConvertMessage currentMessage;
    private static VideoEncodingService instance;
    private NotificationManagerCompat notificationManager;

    int currentAccount;
    String currentPath;
    private int lastNotifiedProgress = -1;
    private boolean lastNotificationIndeterminate = true;
    private long lastNotificationUpdateTime;

    public VideoEncodingService() {
        super();
    }

    public static void start(boolean cancelled) {
        if (instance == null) {
            try {
                Intent intent = new Intent(ApplicationLoader.applicationContext, VideoEncodingService.class);
                ApplicationLoader.applicationContext.startService(intent);
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else if (cancelled) {
            MediaController.VideoConvertMessage messageInController = MediaController.getInstance().getCurrentForegroundConverMessage();
            if (instance.currentMessage != messageInController) {
                if (messageInController != null) {
                    instance.setCurrentMessage(messageInController);
                } else {
                    instance.stopSelf();
                }
            }
        }
    }

    public static void stop() {
        if (instance != null) {
            instance.stopSelf();
        }
    }

    public IBinder onBind(Intent arg2) {
        return null;
    }


    public void onDestroy() {
        super.onDestroy();
        instance = null;
        try {
            stopForeground(true);
        } catch (Throwable ignore) {

        }
        getNotificationManager().cancel(4);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploadProgressChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploadFailed);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploaded);
        currentMessage = null;
        resetNotificationState();
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("VideoEncodingService: destroy video service");
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.fileUploadProgressChanged) {
            String fileName = (String) args[0];
            if (account == currentAccount && currentPath != null && currentPath.equals(fileName)) {
                Long loadedSize = (Long) args[1];
                Long totalSize = (Long) args[2];
                boolean indeterminate = loadedSize == null || totalSize == null || loadedSize < 0 || totalSize <= 0;
                int currentProgress = 0;
                if (indeterminate) {
                    builder.setProgress(100, 0, true);
                } else {
                    float progress = Math.min(1f, loadedSize / (float) totalSize);
                    currentProgress = (int) (progress * 100);
                    builder.setProgress(100, currentProgress, currentProgress == 0);
                }
                updateNotification(currentProgress, indeterminate, !indeterminate && currentProgress >= 100);
            }
        } else if (id == NotificationCenter.fileUploaded || id == NotificationCenter.fileUploadFailed) {
            String fileName = (String) args[0];
            if (account == currentAccount && currentPath != null && currentPath.equals(fileName)) {
                AndroidUtilities.runOnUIThread(() -> {
                    MediaController.VideoConvertMessage message = MediaController.getInstance().getCurrentForegroundConverMessage();
                    if (message != null) {
                        setCurrentMessage(message);
                    } else {
                        stopSelf();
                    }
                });
            }
        }
    }

    private NotificationManagerCompat getNotificationManager() {
        if (notificationManager == null) {
            notificationManager = NotificationManagerCompat.from(ApplicationLoader.applicationContext);
        }
        return notificationManager;
    }

    private void resetNotificationState() {
        lastNotifiedProgress = -1;
        lastNotificationIndeterminate = true;
        lastNotificationUpdateTime = 0;
    }

    private void updateNotification(int currentProgress, boolean indeterminate, boolean force) {
        try {
            MediaController.VideoConvertMessage message = MediaController.getInstance().getCurrentForegroundConverMessage();
            if (message == null) {
                return;
            }
            if (!force && indeterminate == lastNotificationIndeterminate && (indeterminate || currentProgress == lastNotifiedProgress)) {
                return;
            }
            long now = SystemClock.elapsedRealtime();
            // Foreground notification updates can arrive in bursts while a file is being uploaded.
            if (!force && !indeterminate && lastNotificationUpdateTime != 0 && now - lastNotificationUpdateTime < NOTIFICATION_UPDATE_DELAY_MS) {
                return;
            }
            getNotificationManager().notify(4, builder.build());
            lastNotifiedProgress = currentProgress;
            lastNotificationIndeterminate = indeterminate;
            lastNotificationUpdateTime = now;
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isRunning()) {
            return Service.START_NOT_STICKY;
        }
        MediaController.VideoConvertMessage videoConvertMessage = MediaController.getInstance().getCurrentForegroundConverMessage();
        if (videoConvertMessage == null) {
            return Service.START_NOT_STICKY;
        }
        instance = this;
        if (builder == null) {
            NotificationsController.checkOtherNotificationsChannel();
            builder = new NotificationCompat.Builder(ApplicationLoader.applicationContext, NotificationsController.OTHER_NOTIFICATIONS_CHANNEL);
            builder.setSmallIcon(android.R.drawable.stat_sys_upload);
            builder.setWhen(System.currentTimeMillis());
            builder.setChannelId(NotificationsController.OTHER_NOTIFICATIONS_CHANNEL);
            builder.setContentTitle(LocaleController.getString(R.string.NagramX));
        }
        setCurrentMessage(videoConvertMessage);
        try {
            startForeground(4, builder.build());
        } catch (Throwable e) {
            //ignore ForegroundServiceStartNotAllowedException
            FileLog.e(e);
        }
        AndroidUtilities.runOnUIThread(() -> updateNotification(0, true, true));
        return Service.START_NOT_STICKY;
    }

    private void updateBuilderForMessage(MediaController.VideoConvertMessage videoConvertMessage) {
        if (videoConvertMessage == null) {
            return;
        }
        boolean isGif = videoConvertMessage.messageObject != null && MessageObject.isGifMessage(videoConvertMessage.messageObject.messageOwner);
        if (videoConvertMessage.foregroundConversion) {
            builder.setTicker(LocaleController.getString(R.string.ConvertingVideo));
            builder.setContentText(LocaleController.getString(R.string.ConvertingVideo));
        } else if (isGif) {
            builder.setTicker(LocaleController.getString(R.string.SendingGif));
            builder.setContentText(LocaleController.getString(R.string.SendingGif));
        } else {
            builder.setTicker(LocaleController.getString(R.string.SendingVideo));
            builder.setContentText(LocaleController.getString(R.string.SendingVideo));
        }
        int currentProgress = 0;
        builder.setProgress(100, currentProgress, true);
    }

    private void setCurrentMessage(MediaController.VideoConvertMessage message) {
        if (currentMessage == message) {
            return;
        }
        if (currentMessage != null) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploadProgressChanged);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploadFailed);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploaded);
        }
        updateBuilderForMessage(message);
        currentMessage = message;
        currentAccount = message.currentAccount;
        currentPath = message.messageObject.messageOwner.attachPath;
        resetNotificationState();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploadProgressChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploadFailed);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploaded);
        if (isRunning()) {
            updateNotification(0, true, true);
        }
    }

    public static boolean isRunning() {
        return instance != null;
    }

}
