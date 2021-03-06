/**
 * Copyright 2016-2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazonaws.mobileconnectors.pinpoint.targeting.notification;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.amazonaws.mobileconnectors.pinpoint.analytics.AnalyticsEvent;
import com.amazonaws.mobileconnectors.pinpoint.internal.core.PinpointContext;
import com.amazonaws.mobileconnectors.pinpoint.internal.core.system.AndroidPreferences;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

/**
 * NotificationClient is the entry point into the Amazon Mobile Analytics SDK to
 * handle Pinpoint notifications.
 */
public class NotificationClient {
    /**
     * Intent Key for GCM bundle.
     */
    public static final String INTENT_SNS_NOTIFICATION_FROM = "from";

    /**
     * Intent Key for GCM bundle data.
     */
    public static final String INTENT_SNS_NOTIFICATION_DATA = "data";
    // Pinpoint
    protected static final String PINPOINT_PUSH_KEY_PREFIX = "pinpoint.";
    // Campaign

    protected static final String CAMPAIGN_PUSH_KEY_PREFIX = PINPOINT_PUSH_KEY_PREFIX + "campaign.";
    protected static final String CAMPAIGN_ID_ATTRIBUTE_KEY = "campaign_id";
    protected static final String CAMPAIGN_ID_PUSH_KEY = CAMPAIGN_PUSH_KEY_PREFIX + CAMPAIGN_ID_ATTRIBUTE_KEY;
    protected static final String CAMPAIGN_ACTIVITY_ID_ATTRIBUTE_KEY = "campaign_activity_id";
    protected static final String CAMPAIGN_ACTIVITY_ID_PUSH_KEY =
        CAMPAIGN_PUSH_KEY_PREFIX + CAMPAIGN_ACTIVITY_ID_ATTRIBUTE_KEY;
    protected static final String CAMPAIGN_TREATMENT_ID_ATTRIBUTE_KEY = "treatment_id";
    protected static final String CAMPAIGN_TREATMENT_ID_PUSH_KEY =
        CAMPAIGN_PUSH_KEY_PREFIX + CAMPAIGN_TREATMENT_ID_ATTRIBUTE_KEY;
    private static final Log log = LogFactory.getLog(NotificationClient.class);
    private static final String GCM_TOKEN_PREF_KEY = "AWSPINPOINT.GCMTOKEN";
    // Notification
    private static final String GCM_NOTIFICATION_PUSH_KEY_PREFIX = PINPOINT_PUSH_KEY_PREFIX + "notification.";
    private static final String NOTIFICATION_SILENT_PUSH_KEY = GCM_NOTIFICATION_PUSH_KEY_PREFIX + "silentPush";
    private static final String NOTIFICATION_TITLE_PUSH_KEY = GCM_NOTIFICATION_PUSH_KEY_PREFIX + "title";
    private static final String NOTIFICATION_BODY_PUSH_KEY = GCM_NOTIFICATION_PUSH_KEY_PREFIX + "body";
    private static final String NOTIFICATION_COLOR_PUSH_KEY = GCM_NOTIFICATION_PUSH_KEY_PREFIX + "color";
    private static final String NOTIFICATION_ICON_PUSH_KEY = GCM_NOTIFICATION_PUSH_KEY_PREFIX + "icon";
    private static final String CAMPAIGN_IMAGE_PUSH_KEY = GCM_NOTIFICATION_PUSH_KEY_PREFIX + "imageUrl";
    private static final String CAMPAIGN_IMAGE_ICON_PUSH_KEY = GCM_NOTIFICATION_PUSH_KEY_PREFIX + "imageIconUrl";
    private static final String CAMPAIGN_IMAGE_SMALL_ICON_PUSH_KEY =
        GCM_NOTIFICATION_PUSH_KEY_PREFIX + "imageSmallIconUrl";
    // Engage Attributes
    private static final String CAMPAIGN_URL_PUSH_KEY = PINPOINT_PUSH_KEY_PREFIX + "url";
    private static final String CAMPAIGN_DEEP_LINK_PUSH_KEY = PINPOINT_PUSH_KEY_PREFIX + "deeplink";
    private static final String CAMPAIGN_OPEN_APP_PUSH_KEY = PINPOINT_PUSH_KEY_PREFIX + "openApp";
    private static final String REQUEST_ID = "requestId";
    private static final int INVALID_RESOURCE = 0;
    private static final int ANDROID_JELLYBEAN = 16;
    private static final int ANDROID_KITKAT = 19;
    private static final int ANDROID_LOLLIPOP = 21;
    private static final int ANDROID_MARSHMALLOW = 23;

    private static final String AWS_EVENT_TYPE_OPENED = "_campaign.opened_notification";
    private static final String AWS_EVENT_TYPE_RECEIVED_FOREGROUND = "_campaign.received_foreground";
    private static final String AWS_EVENT_TYPE_RECEIVED_BACKGROUND = "_campaign.received_background";
    private static final String GCM_INTENT_ACTION = "com.google.android.c2dm.intent.RECEIVE";
    private static final String FCM_INTENT_ACTION = "com.amazonaws.intent.fcm.NOTIFICATION_OPEN";
    private static final String CHECK_OP_NO_THROW = "checkOpNoThrow";
    private static final String OP_POST_NOTIFICATION = "OP_POST_NOTIFICATION";
    private static final String APP_OPS_MODE_ALLOWED = "MODE_ALLOWED";
    private static final String APP_OPS_SERVICE = "APP_OPS_SERVICE";
    private final PinpointContext pinpointContext;
    private final List<GCMTokenRegisteredHandler> gcmTokenRegisteredHandlers;
    private volatile String theGCMToken;
    private Constructor<?> notificationBuilderConstructor = null;
    private Class<?> notificationBuilderClass = null;
    private Class<?> notificationBigTextStyleClass = null;
    private Class<?> notificationBigPictureStyleClass = null;
    private Class<?> notificationStyleClass = null;
    private Class<?> iconClass = null;
    private Method setContentTitleMethod;
    private Method setContentTextMethod;
    private Method setSmallIconMethod;
    private Method setSmallIconResIdMethod;
    private Method setLargeIconMethod;
    private Method setContentIntent;
    private Method setStyleMethod;
    private Method buildMethod;
    private Method bigTextMethod;
    private Method bigPictureMethod;
    private Method setSummaryMethod;
    private Method setPriorityMethod;
    private Method setSoundMethod;
    private Method createWithBitmapMethod;
    private Bitmap notificationImage;
    private Class<?> appOpsClass = null;
    private Method checkOpNoThrowMethod = null;
    private Field opPostNotificationField = null;
    private Field modeAllowedField = null;

    /**
     * Constructor.
     *
     * @param pinpointContext the pinpoint context. {@link PinpointContext}
     */
    public NotificationClient(final PinpointContext pinpointContext) {
        this.pinpointContext = pinpointContext;
        this.gcmTokenRegisteredHandlers = new ArrayList<GCMTokenRegisteredHandler>();
        this.loadGCMToken();
    }

    /**
     * Add a GCM token register handler.
     *
     * @param handler the GCM register handler.
     */
    public void addGCMTokenRegisteredHandler(final GCMTokenRegisteredHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("GCMTokenRegisteredHandler cannot be null.");
        }
        gcmTokenRegisteredHandlers.add(handler);
    }

    /**
     * Remove the gcm register handler.
     *
     * @param handler the gcm register handler.
     */
    public void removeGCMTokenRegisteredHandler(final GCMTokenRegisteredHandler handler) {
        gcmTokenRegisteredHandlers.remove(handler);
    }

    /**
     * This method should be called once the device token has been received from
     * the GCM api in order to enable being targeted for campaign push
     * notifications.
     *
     * @param deviceToken the GCM device token.
     */
    public void registerGCMDeviceToken(final String deviceToken) {
        theGCMToken = deviceToken;
        // Persist the GCM token to shared preferences.
        final AndroidPreferences prefs = pinpointContext.getSystem().getPreferences();
        prefs.putString(GCM_TOKEN_PREF_KEY, deviceToken);
        for (final GCMTokenRegisteredHandler handler : gcmTokenRegisteredHandlers) {
            handler.tokenRegistered(deviceToken);
        }
    }

    private void loadGCMToken() {
        final AndroidPreferences prefs = pinpointContext.getSystem().getPreferences();
        // Load the GCM token from shared preferences.
        theGCMToken = prefs.getString(GCM_TOKEN_PREF_KEY, null);
    }

    /**
     * get the GCM device token.
     *
     * @return the GCM device token.
     */
    public String getGCMDeviceToken() {
        this.loadGCMToken();
        return theGCMToken;
    }

    private boolean isForeground() {
        // Gets a list of running processes.
        final ActivityManager am = (ActivityManager) pinpointContext.getApplicationContext().getSystemService(
            Context.ACTIVITY_SERVICE);
        final List<ActivityManager.RunningAppProcessInfo> tasks = am.getRunningAppProcesses();

        // On some versions of android the first item in the list is what runs
        // in the foreground,
        // but this is not true on all versions. Check the process importance to
        // see if the app
        // is in the foreground.
        final String packageName = pinpointContext.getApplicationContext().getPackageName();
        for (final ActivityManager.RunningAppProcessInfo appProcess : tasks) {
            final String processName = appProcess.processName;
            if (ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND == appProcess.importance &&
                packageName.equals(processName)) {
                return true;
            }
        }
        return false;
    }

    private void addGlobalCampaignAttributes(
        final java.util.Map<java.lang.String, java.lang.String> campaignAttribs) {
        for (final Map.Entry<String, String> entry : campaignAttribs.entrySet()) {
            if (entry.getValue() != null) {
                this.pinpointContext.getAnalyticsClient().addGlobalAttribute(entry.getKey(), entry.getValue());
            }
        }
    }

    void addCampaignAttributesToEvent(final AnalyticsEvent pushEvent,
                                      final java.util.Map<java.lang.String, java.lang.String> campaignAttribs) {
        for (final Map.Entry<String, String> entry : campaignAttribs.entrySet()) {
            if (entry.getValue() != null) {
                pushEvent.addAttribute(entry.getKey(), entry.getValue());
            }
        }
    }

    private int getNotificationIconResourceId(
        final String drawableResourceName) {
        final PackageManager packageManager = pinpointContext.getApplicationContext().getPackageManager();
        try {
            final String packageName = pinpointContext.getApplicationContext().getPackageName();
            final ApplicationInfo applicationInfo = packageManager
                .getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            final Resources resources = packageManager.getResourcesForApplication(applicationInfo);

            if (drawableResourceName != null) {
                final int resId = resources.getIdentifier(drawableResourceName, "drawable", packageName);
                if (resId != INVALID_RESOURCE) {
                    return resId;
                }
            }
            return applicationInfo.icon;

        } catch (final PackageManager.NameNotFoundException ex) {
            log.error("Can't find icon for our application package.", ex);
            // 0 is an invalid resource id, so use it to indicate failure to
            // retrieve the resource.
            return INVALID_RESOURCE;
        }
    }

    private Notification createLegacyNotification(final int iconResId, final String title, final String contentText,
                                                  final PendingIntent contentIntent) {
        final Notification notification = new Notification();
        notification.icon = iconResId;
        notification
            .setLatestEventInfo(this.pinpointContext.getApplicationContext(), title, contentText, contentIntent);
        notification.contentIntent = contentIntent;
        return notification;
    }

    private boolean initClassesAndMethodsByReflection() {
        if (notificationBuilderClass != null) {
            return true;
        }
        try {
            notificationBuilderClass = Class.forName("android.app.Notification$Builder"); //API Level 11
            notificationBigTextStyleClass = Class.forName("android.app.Notification$BigTextStyle"); //API Level 16
            notificationStyleClass = Class.forName("android.app.Notification$Style"); //API Level 16
            notificationBigPictureStyleClass = Class.forName("android.app.Notification$BigPictureStyle"); //API Level 16
            if (android.os.Build.VERSION.SDK_INT >= ANDROID_MARSHMALLOW) {
                iconClass = Class.forName("android.graphics.drawable.Icon"); //API Level 23
            }
            if (!buildMethodsByReflection()) {
                // fall back to creating the legacy notification.
                return false;
            }
            return true;
        } catch (final ClassNotFoundException ex) {
            log.debug("Failed to get notification builder classes by reflection : " + ex.getMessage(), ex);
            return false;
        }
    }

    private boolean buildMethodsByReflection() {
        try {
            notificationBuilderConstructor = notificationBuilderClass.getDeclaredConstructor(Context.class);
            setContentTitleMethod = notificationBuilderClass.getDeclaredMethod("setContentTitle", CharSequence.class);
            setContentTextMethod = notificationBuilderClass.getDeclaredMethod("setContentText", CharSequence.class);
            setContentIntent = notificationBuilderClass.getDeclaredMethod("setContentIntent", PendingIntent.class);
            setStyleMethod = notificationBuilderClass.getDeclaredMethod("setStyle", notificationStyleClass);
            setSmallIconResIdMethod = notificationBuilderClass.getDeclaredMethod("setSmallIcon", int.class);
            buildMethod = notificationBuilderClass.getDeclaredMethod("build");
            bigTextMethod = notificationBigTextStyleClass.getDeclaredMethod("bigText", CharSequence.class);
            bigPictureMethod = notificationBigPictureStyleClass.getDeclaredMethod("bigPicture", Bitmap.class);
            setSummaryMethod = notificationBigPictureStyleClass.getDeclaredMethod("setSummaryText", CharSequence.class);
            setLargeIconMethod = notificationBuilderClass.getDeclaredMethod("setLargeIcon", Bitmap.class);
            setPriorityMethod = notificationBuilderClass.getDeclaredMethod("setPriority", int.class);
            setSoundMethod = notificationBuilderClass.getDeclaredMethod("setSound", Uri.class);

            if (android.os.Build.VERSION.SDK_INT >= ANDROID_MARSHMALLOW) {
                setSmallIconMethod = notificationBuilderClass.getDeclaredMethod("setSmallIcon", iconClass);
                createWithBitmapMethod = iconClass.getDeclaredMethod("createWithBitmap", Bitmap.class);
            }
            return true;
        } catch (final NoSuchMethodException ex) {
            log.debug("Failed to get notification builder methods by reflection. : " + ex.getMessage(), ex);
            return false;
        }
    }

    private boolean buildNotificationIcons(final int iconResId,
                                           final String imageIconUrl,
                                           final String imageSmallIconUrl,
                                           final Object notificationBuilder) {
        try {
            if (imageIconUrl != null) {
                try {
                    setLargeIconMethod.invoke(notificationBuilder, new DownloadImageTask().execute(imageIconUrl).get());
                } catch (final InterruptedException e) {
                    log.error("Interrupted when downloading image : " + e.getMessage(), e);
                } catch (final ExecutionException e) {
                    log.error("Failed execute download image thread : " + e.getMessage(), e);
                }
            }
            if (imageSmallIconUrl != null && iconClass != null
                && android.os.Build.VERSION.SDK_INT >= ANDROID_MARSHMALLOW) {
                try {
                    final Bitmap iconBitmap = new DownloadImageTask().execute(imageSmallIconUrl).get();
                    setSmallIconMethod
                        .invoke(notificationBuilder, createWithBitmapMethod.invoke(iconClass, iconBitmap));
                } catch (final InterruptedException e) {
                    log.error("Interrupted when downloading small icon : " + e.getMessage(), e);
                    setSmallIconResIdMethod.invoke(notificationBuilder, iconResId);
                } catch (final ExecutionException e) {
                    log.error("Failed execute download image small icon thread : " + e.getMessage(), e);
                    setSmallIconResIdMethod.invoke(notificationBuilder, iconResId);
                }
            } else {
                setSmallIconResIdMethod.invoke(notificationBuilder, iconResId);
            }
            return true;
        } catch (final InvocationTargetException ex) {
            log.debug("Can't invoke notification builder methods. : " + ex.getMessage(), ex);
            return false;
        } catch (final IllegalAccessException ex) {
            log.debug("Can't access notification builder methods. : " + ex.getMessage(), ex);
            return false;
        }
    }

    private Notification createNotification(final int iconResId, final String title, final String contentText,
                                            final String imageUrl, final String imageIconUrl,
                                            final String imageSmallIconUrl,
                                            final PendingIntent contentIntent) {
        log.info("Create Notification:" + title + ", Content:" + contentText);
        if (android.os.Build.VERSION.SDK_INT < ANDROID_JELLYBEAN) {
            return createLegacyNotification(iconResId, title, contentText, contentIntent);
        }

        if (!initClassesAndMethodsByReflection()) {
            // fall back to creating the legacy notification.
            return createLegacyNotification(iconResId, title, contentText, contentIntent);
        }

        final Object notificationBuilder;
        final Object bigTextStyle;
        final Object bigPictureStyle;

        try {
            notificationBuilder = notificationBuilderConstructor.newInstance(pinpointContext.getApplicationContext());
            bigTextStyle = notificationBigTextStyleClass.newInstance();
            bigPictureStyle = notificationBigPictureStyleClass.newInstance();
        } catch (final InvocationTargetException ex) {
            log.debug("Can't invoke notification builder constructor. : " + ex.getMessage(), ex);
            return createLegacyNotification(iconResId, title, contentText, contentIntent);
        } catch (final IllegalAccessException ex) {
            log.debug(
                "Can't access notification builder or bigTextStyle or bigPictureStyle classes. : " + ex.getMessage(),
                ex);
            return createLegacyNotification(iconResId, title, contentText, contentIntent);
        } catch (final InstantiationException ex) {
            log.debug(
                "Exception while instantiating notification builder or bigTextStyle or bigPictureStyle classes. : " +
                ex.getMessage(), ex);
            return createLegacyNotification(iconResId, title, contentText, contentIntent);
        }

        try {
            setContentTitleMethod.invoke(notificationBuilder, title);
            setContentTextMethod.invoke(notificationBuilder, contentText);
            setContentIntent.invoke(notificationBuilder, contentIntent);
            setPriorityMethod.invoke(notificationBuilder, 1);
            final Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            setSoundMethod.invoke(notificationBuilder, defaultSoundUri);

            if (!buildNotificationIcons(iconResId, imageIconUrl, imageSmallIconUrl, notificationBuilder)) {
                return createLegacyNotification(iconResId, title, contentText, contentIntent);
            }

            if (imageUrl != null) {
                try {
                    notificationImage = new DownloadImageTask().execute(imageUrl).get();
                    if (notificationImage != null) {
                        bigPictureMethod.invoke(bigPictureStyle, notificationImage);
                        setSummaryMethod.invoke(bigPictureStyle, contentText);
                        setStyleMethod.invoke(notificationBuilder, bigPictureStyle);
                    } else {
                        bigTextMethod.invoke(bigTextStyle, contentText);
                        setStyleMethod.invoke(notificationBuilder, bigTextStyle);
                    }
                } catch (final InterruptedException e) {
                    log.error("Interrupted when downloading image : " + e.getMessage(), e);
                } catch (final ExecutionException e) {
                    log.error("Failed execute download image thread : " + e.getMessage(), e);
                }
            }

            return (Notification) buildMethod.invoke(notificationBuilder);
        } catch (final InvocationTargetException ex) {
            log.debug("Can't invoke notification builder methods. : " + ex.getMessage(), ex);
            return createLegacyNotification(iconResId, title, contentText, contentIntent);
        } catch (final IllegalAccessException ex) {
            log.debug("Can't access notification builder methods. : " + ex.getMessage(), ex);
            return createLegacyNotification(iconResId, title, contentText, contentIntent);
        }
    }

    private PendingIntent createOpenAppPendingIntent(final Bundle pushBundle, final Class<?> targetClass,
                                                     String campaignId, int requestId, String intentAction) {
        PendingIntent contentIntent = null;
        if (intentAction == GCM_INTENT_ACTION) {
            contentIntent = PendingIntent.getService(pinpointContext.getApplicationContext(), requestId,
                                                     this.notificationIntent(pushBundle, campaignId, requestId,
                                                                             GCM_INTENT_ACTION, targetClass),
                                                     PendingIntent.FLAG_ONE_SHOT);
        } else {
            contentIntent = PendingIntent.getBroadcast(pinpointContext.getApplicationContext(), requestId,
                                                       this.notificationIntent(pushBundle, campaignId, requestId,
                                                                               FCM_INTENT_ACTION, targetClass),
                                                       PendingIntent.FLAG_ONE_SHOT);
            PinpointNotificationReceiver.setWeakNotificationClient(this);
        }
        return contentIntent;

    }

    private Intent notificationIntent(final Bundle pushBundle, String campaignId, int requestId, String intentAction,
                                      final Class<?> targetClass) {
        final Intent notificationIntent = new Intent(pinpointContext.getApplicationContext(), targetClass);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        notificationIntent.setAction(intentAction);
        notificationIntent.putExtras(pushBundle);
        notificationIntent.putExtra(INTENT_SNS_NOTIFICATION_FROM, AWS_EVENT_TYPE_OPENED);
        notificationIntent.putExtra(CAMPAIGN_ID_PUSH_KEY, campaignId);
        notificationIntent.putExtra(REQUEST_ID, requestId);
        notificationIntent.setPackage(pinpointContext.getApplicationContext().getPackageName());
        return notificationIntent;
    }

    private boolean displayNotification(final Bundle pushBundle, final Class<?> targetClass, String imageUrl,
                                        String iconImageUrl, String iconSmallImageUrl,
                                        Map<String, String> campaignAttributes, String intentAction) {
        log.info("Display Notification: " + pushBundle.toString());

        final String title = pushBundle.getString(NOTIFICATION_TITLE_PUSH_KEY);
        final String message = pushBundle.getString(NOTIFICATION_BODY_PUSH_KEY);

        final String campaignId = campaignAttributes.get(CAMPAIGN_ID_ATTRIBUTE_KEY);
        final String activityId = campaignAttributes.get(CAMPAIGN_ACTIVITY_ID_ATTRIBUTE_KEY);

        final int requestID = (campaignId + ":" + activityId + ":" + System.currentTimeMillis()).hashCode();

        final int iconResId = getNotificationIconResourceId(pushBundle.getString(NOTIFICATION_ICON_PUSH_KEY));
        if (iconResId == 0) {
            return false;
        }

        final Notification notification = createNotification(iconResId, title, message, imageUrl, iconImageUrl,
                                                             iconSmallImageUrl,
                                                             this.createOpenAppPendingIntent(pushBundle, targetClass,
                                                                                             campaignId, requestID,
                                                                                             intentAction));

        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        notification.defaults |= Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE;

        if (android.os.Build.VERSION.SDK_INT >= ANDROID_LOLLIPOP) {
            log.info("SDK greater than 21 detected: " + android.os.Build.VERSION.SDK_INT);

            final String colorString = pushBundle.getString(NOTIFICATION_COLOR_PUSH_KEY);
            if (colorString != null) {
                int color;
                try {
                    color = Color.parseColor(colorString);
                } catch (final IllegalArgumentException ex) {
                    log.warn("Couldn't parse campaign notification color.", ex);
                    color = 0;
                }
                Exception exception = null;
                try {
                    final Field colorField = notification.getClass().getDeclaredField("color");
                    colorField.setAccessible(true);
                    colorField.set(notification, color);
                } catch (final IllegalAccessException ex) {
                    exception = ex;
                } catch (final NoSuchFieldException ex) {
                    exception = ex;
                }
                if (exception != null) {
                    log.error("Couldn't set campaign notification color : " + exception.getMessage(), exception);
                }
            }
        }

        final NotificationManager notificationManager = (NotificationManager) pinpointContext.getApplicationContext()
                                                                                             .getSystemService(
                                                                                                 Context.NOTIFICATION_SERVICE);

        notificationManager.notify(requestID, notification);
        return true;
    }

    private boolean openApp() {
        final Intent launchIntent = pinpointContext.getApplicationContext().getPackageManager()
                                                   .getLaunchIntentForPackage(
                                                       pinpointContext.getApplicationContext().getPackageName());

        if (launchIntent == null) {
            log.error("Couldn't get app launch intent for campaign notification.");
            return false;
        }
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        launchIntent.setPackage(null);
        pinpointContext.getApplicationContext().startActivity(launchIntent);
        return true;
    }

    private void openURL(final String url, final boolean noSchemeValidation) {
        final String validatedUrl;
        if (url.startsWith("http://") || url.startsWith("https://") || noSchemeValidation) {
            validatedUrl = url;
        } else {
            validatedUrl = "http://" + url;
        }

        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(validatedUrl));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(pinpointContext.getApplicationContext().getPackageManager()) != null) {
            pinpointContext.getApplicationContext().startActivity(intent);
        }
    }

    /**
     * Handles pinpoint FCM push messages by posting a local notification when
     * the app is in the background, or sending a local broadcast if the app is
     * in the foreground. Also on Api level 19 devices and above, if local
     * notifications have been disabled and the app is in the background, a
     * local broadcast is sent.
     *
     * @param from the from string received by the FCM service,
     * @param data the bundle received from the FCM service
     * @return {@link CampaignPushResult}.
     */
    public CampaignPushResult handleFCMCampaignPush(final String from,
                                                    final Map<String, String> data) {
        log.info("Handling FCM Notification: " + data.toString());

        final Bundle bundle = new Bundle();
        for (final Map.Entry<String, String> entry : data.entrySet()) {
            bundle.putString(entry.getKey(), entry.getValue());
        }
        return handleCampaignPush(from, bundle, PinpointNotificationReceiver.class, FCM_INTENT_ACTION);
    }

    /**
     * Handles pinpoint GCM push messages by posting a local notification when
     * the app is in the background, or sending a local broadcast if the app is
     * in the foreground. Also on Api level 19 devices and above, if local
     * notifications have been disabled and the app is in the background, a
     * local broadcast is sent.
     *
     * @param from         the from string received by the GCM service
     * @param data         the bundle received from the GCM service
     * @param serviceClass the class extending GCMListenerService that handles
     *                     receiving GCM messages.
     * @return {@link CampaignPushResult}.
     */
    public CampaignPushResult handleGCMCampaignPush(final String from, final Bundle data,
                                                    final Class<? extends Service> serviceClass) {
        log.info("Handling GCM Notification: " + data.toString());
        return handleCampaignPush(from, data, serviceClass, GCM_INTENT_ACTION);
    }

    /* pkg */
    CampaignPushResult handleNotificationOpen(
        Map<String, String> campaignAttributes,
        final Bundle data) {
        // Add any campaign global attributes
        if (campaignAttributes != null) {
            // Stop Session
            if (this.pinpointContext.getSessionClient() != null) {
                this.pinpointContext.getSessionClient().stopSession();
            }
            addGlobalCampaignAttributes(campaignAttributes);

            final AnalyticsEvent pushEvent = this.pinpointContext.getAnalyticsClient().createEvent(
                AWS_EVENT_TYPE_OPENED);
            this.pinpointContext.getAnalyticsClient().recordEvent(pushEvent);
            this.pinpointContext.getAnalyticsClient().submitEvents();

            final String url = data.getString(CAMPAIGN_URL_PUSH_KEY);
            if (url != null) {
                openURL(url, false);
                return CampaignPushResult.NOTIFICATION_OPENED;
            }
            final String deepLink = data.getString(CAMPAIGN_DEEP_LINK_PUSH_KEY);
            if (deepLink != null) {
                openURL(deepLink, true);
                return CampaignPushResult.NOTIFICATION_OPENED;
            }
            final String openApp = data.getString(CAMPAIGN_OPEN_APP_PUSH_KEY);
            if (openApp == null) {
                log.warn(
                    "No key/value present to determine action for campaign notification, default to open app.");
            }
            openApp();
        }
        return CampaignPushResult.NOTIFICATION_OPENED;
    }

    private CampaignPushResult handleCampaignPush(final String from,
                                                  final Bundle data,
                                                  final Class<?> targetClass,
                                                  String intentAction) {
        // Check if push data contains a Campaign Id
        if (!data.containsKey(CAMPAIGN_ID_PUSH_KEY)) {
            return CampaignPushResult.NOT_HANDLED;
        }
        final boolean isAppInForeground = isForeground();

        final String imageUrl = data.getString(CAMPAIGN_IMAGE_PUSH_KEY);
        final String imageIconUrl = data.getString(CAMPAIGN_IMAGE_ICON_PUSH_KEY);
        final String imageSmallIconUrl = data.getString(CAMPAIGN_IMAGE_SMALL_ICON_PUSH_KEY);
        final Map<String, String> campaignAttributes = new HashMap<String, String>();

        campaignAttributes.put(CAMPAIGN_ID_ATTRIBUTE_KEY, data.getString(CAMPAIGN_ID_PUSH_KEY));
        campaignAttributes.put(CAMPAIGN_TREATMENT_ID_ATTRIBUTE_KEY, data.getString(CAMPAIGN_TREATMENT_ID_PUSH_KEY));
        campaignAttributes.put(CAMPAIGN_ACTIVITY_ID_ATTRIBUTE_KEY, data.getString(CAMPAIGN_ACTIVITY_ID_PUSH_KEY));

        this.pinpointContext.getAnalyticsClient().setCampaignAttributes(campaignAttributes);
        log.info("Campaign Attributes are:" + campaignAttributes);

        if (AWS_EVENT_TYPE_OPENED.equals(from)) {
            return this.handleNotificationOpen(campaignAttributes, data);
        }

        if (campaignAttributes != null) {
            // Create the push event.
            String eventType = null;
            if (isAppInForeground) {
                eventType = AWS_EVENT_TYPE_RECEIVED_FOREGROUND;
            } else {
                eventType = AWS_EVENT_TYPE_RECEIVED_BACKGROUND;
            }
            final AnalyticsEvent pushEvent = this.pinpointContext.getAnalyticsClient().createEvent(eventType);

            // Add the campaign attributes.
            addCampaignAttributesToEvent(pushEvent, campaignAttributes);
            pushEvent.addAttribute("isAppInForeground", Boolean.toString(isAppInForeground));
            try {

                if (isAppInForeground) {
                    // Notify the caller that the app was in the foreground.
                    return CampaignPushResult.APP_IN_FOREGROUND;
                } else {
                    // Display a notification with an icon, title, message,
                    // image, and default sound.
                    if ("1".equalsIgnoreCase(data.getString(NOTIFICATION_SILENT_PUSH_KEY))) {
                        return CampaignPushResult.SILENT;
                    }

                    // App is in the background; attempt to display a
                    // notification in the notification center.
                    if (!areAppNotificationsEnabled() ||
                        !displayNotification(data, targetClass, imageUrl, imageIconUrl, imageSmallIconUrl,
                                             campaignAttributes, intentAction)) {
                        // Local app notifications have been disabled by the
                        // user from Settings -> App Info
                        // or we couldn't display the notification for some
                        // reason.

                        pushEvent.addAttribute("isOptedOut", "true");
                        // We can't post a notification, so delegate to the
                        // passed in handler.
                        return CampaignPushResult.OPTED_OUT;
                    }
                }
            } finally {
                this.pinpointContext.getAnalyticsClient()
                                    .recordEvent(pushEvent);
                this.pinpointContext.getAnalyticsClient().submitEvents();
            }
        }
        return CampaignPushResult.POSTED_NOTIFICATION;
    }

    /**
     * On devices using Android API level 19 and above this method properly
     * returns whether local notifications are enabled for the app. For devices
     * before API level 19, this method always returns true. Disabling
     * notifications was a feature added on devices supporting API Level 16 and
     * above, so devices from API level 16 to 18 will return true from this
     * method even when local notifications have been disabled for the app.
     *
     * @return true if local notifications are enabled for this app, otherwise
     * false.
     */
    public boolean areAppNotificationsEnabled() {
        if (android.os.Build.VERSION.SDK_INT < ANDROID_KITKAT) {
            return true;
        }

        final String appOpsServiceName;
        try {
            final Field appOpsServiceNameField = Context.class.getDeclaredField(APP_OPS_SERVICE);
            appOpsServiceName = (String) appOpsServiceNameField.get(String.class);
        } catch (final NoSuchFieldException e) {
            log.error(e.getMessage(), e);
            return true;
        } catch (final IllegalAccessException e) {
            log.error(e.getMessage(), e);
            return true;
        }

        final Object mAppOps = pinpointContext.getApplicationContext().getSystemService(appOpsServiceName);
        if (mAppOps == null) {
            return true;
        }

        final ApplicationInfo appInfo = pinpointContext.getApplicationContext().getApplicationInfo();
        final String pkg = pinpointContext.getApplicationContext().getPackageName();

        final int uid = appInfo.uid;

        try {
            if (appOpsClass == null || checkOpNoThrowMethod == null || opPostNotificationField == null ||
                modeAllowedField == null) {
                appOpsClass = Class.forName(mAppOps.getClass().getName());
                checkOpNoThrowMethod = appOpsClass.getMethod(CHECK_OP_NO_THROW, Integer.TYPE, Integer.TYPE,
                                                             String.class);

                opPostNotificationField = appOpsClass.getDeclaredField(OP_POST_NOTIFICATION);
                modeAllowedField = appOpsClass.getDeclaredField(APP_OPS_MODE_ALLOWED);
            }

            final int postNotificationValue = opPostNotificationField.getInt(null);
            final int opPostNotificationMode = (Integer) checkOpNoThrowMethod.invoke(mAppOps, postNotificationValue,
                                                                                     uid, pkg);
            final int modeAllowed = modeAllowedField.getInt(null);
            return (modeAllowed == opPostNotificationMode);

        } catch (final ClassNotFoundException e) {
            log.error(e.getMessage(), e);
        } catch (final NoSuchMethodException e) {
            log.error(e.getMessage(), e);
        } catch (final NoSuchFieldException e) {
            log.error(e.getMessage(), e);
        } catch (final InvocationTargetException e) {
            log.error(e.getMessage(), e);
        } catch (final IllegalAccessException e) {
            log.error(e.getMessage(), e);
        }
        return true;
    }

    /**
     * Result values of handling a pinpoint push message.
     */
    public enum CampaignPushResult {
        /**
         * The message wasn't for pinpoint.
         */
        NOT_HANDLED,
        /**
         * The SDK handled the message and posted a local notification.
         */
        POSTED_NOTIFICATION,
        /**
         * The SDK handled the message, but no notification was posted, since
         * the app was in the foreground.
         */
        APP_IN_FOREGROUND,
        /**
         * The SDK handled the message, but no notification was posted, since
         * the app was opted out.
         */
        OPTED_OUT,
        /**
         * The SDK handled the message that indicated the local campaign
         * notification was opened.
         */
        NOTIFICATION_OPENED,
        /**
         * The SDK handled the message that indicated the local campaign
         * notification was opened.
         */
        SILENT
    }

    private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {

        @Override
        protected Bitmap doInBackground(String... urls) {

            try {
                return BitmapFactory.decodeStream((new URL(urls[0])).openConnection().getInputStream());
            } catch (final IOException ex) {
                log.error("Cannot download or find image for rich notification.", ex);
                return null;
            }
        }
    }
}
