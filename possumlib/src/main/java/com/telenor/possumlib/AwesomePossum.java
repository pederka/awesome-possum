package com.telenor.possumlib;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.telenor.possumlib.constants.Constants;
import com.telenor.possumlib.constants.Messaging;
import com.telenor.possumlib.exceptions.GatheringNotAuthorizedException;
import com.telenor.possumlib.interfaces.IPossumMessage;
import com.telenor.possumlib.interfaces.IPossumTrust;
import com.telenor.possumlib.services.AuthenticationService;
import com.telenor.possumlib.services.DataCollectionService;
import com.telenor.possumlib.services.DataUploadService;
import com.telenor.possumlib.services.SendKurtService;
import com.telenor.possumlib.services.VerificationService;
import com.telenor.possumlib.utils.Has;

import net.danlew.android.joda.JodaTimeAndroid;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * SDK for handling all things related to the Awesome Possum project
 * <p>
 * The project is owned by Telenor Digital AS in collaboration with NR (Norsk Regnesentral).
 * The goal of the project is to help alleviate/remove passwords and the problem they cause by
 * identifying the user of the phone with the sensors available. Using these, it can perform
 * <br><br>
 * * Gait analysis<br>
 * * Face recognition<br>
 * * Ambient sound patterning<br>
 * * Positional placement over time<br>
 * * Behavioural analysis<br><br>
 * And more to come. Combined with Tensorflow and neural networks, it calculates the trustscore
 * (or likelyhood of accuracy) of you being you and by summing up all the different sensors input
 * can return a score granting you verification that you are you.
 */
public final class AwesomePossum {
    private static boolean initComplete = false;
    private static BroadcastReceiver trustReceiver;
    private static JsonParser parser;
    private static BroadcastReceiver serviceMessageReceiver;
    private static final String tag = AwesomePossum.class.getName();
    private static Queue<IPossumTrust> trustListeners = new ConcurrentLinkedQueue<>();
    private static Queue<IPossumMessage> messageListeners = new ConcurrentLinkedQueue<>();
    private static SharedPreferences preferences;
    private static boolean isListening;
    private static DateTime lastAuthenticated;

    /**
     * Main method of controlling all sensors. This static class is init'ed by calling this method.
     * This is usually done in the application level
     *
     * @param context context for further reference
     */
    private static void init(@NonNull Context context) {
        if (initComplete) return;
        context = context.getApplicationContext();// Important since context needs to be equal for receivers
//        ConnectSdk.sdkInitialize(context);
        initComplete = true;
        parser = new JsonParser();
        preferences = context.getSharedPreferences(Constants.SHARED_PREFERENCES, Context.MODE_PRIVATE);
        JodaTimeAndroid.init(context);
        trustReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleTrustIntent(intent);
            }
        };
        serviceMessageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleServiceIntent(context, intent);
            }
        };
        context.registerReceiver(trustReceiver, new IntentFilter(Messaging.POSSUM_TRUST));
        context.registerReceiver(serviceMessageReceiver, new IntentFilter(Messaging.POSSUM_MESSAGE));

        // In case of first time start, set installation time
        long startTime = preferences.getLong(Constants.START_TIME, 0);
        if (startTime == 0) {
            preferences.edit().putLong(Constants.START_TIME,
                    DateTime.now().getMillis()).apply();
        }
    }

    private static void handleTrustIntent(Intent intent) {
        String detectorsString = intent.getStringExtra("detectors");
        if (detectorsString == null) return;
        JsonArray detectors = (JsonArray) parser.parse(detectorsString);
        int combinedTrustScore = intent.getIntExtra("totalTrustScore", 0);
        for (JsonElement detectorEl : detectors) {
            JsonObject detectorTrust = detectorEl.getAsJsonObject();
            int detectorType = detectorTrust.get("detectorType").getAsInt();
            int newTrustScore = intent.getIntExtra("trustScore", 0);
            for (IPossumTrust listener : trustListeners) {
                listener.changeInTrust(detectorType, newTrustScore, combinedTrustScore);
            }
        }
    }

    /**
     * Starts a verification of whether Awesome Possum should be terminated and unauthorized.
     * Should the gathering be done or the AP project be done, the started service will send
     * an intent with information that it should be terminated
     * @param context a valid android context
     * @param identityPoolId the identity pool id it will use
     */
    private static void requestVerification(@NonNull Context context, @NonNull String identityPoolId) {
        Intent intent = new Intent(context, VerificationService.class);
        intent.putExtra("identityPoolId", identityPoolId);
        context.startService(intent);
    }

    /**
     * Sets the last authentication attempt time
     *
     * @param authenticationDate a datetime for the last attempted authentication
     */
    private static void setAuthenticationDate(@NonNull DateTime authenticationDate) {
        AwesomePossum.lastAuthenticated = authenticationDate;
    }

    /**
     * Starts an attempt to authenticate
     *
     * @param context a valid android context
     * @return true if it starts an attempt, false if too little time has passed
     */
    public static boolean authenticate(@NonNull Context context) {
        // TODO: Should this be a separate method or should it be part of the "listen" method?
        if (lastAuthenticated == null || lastAuthenticated.plusMinutes(2).isBeforeNow()) {
            setAuthenticationDate(DateTime.now());
            Intent intent = new Intent(context, AuthenticationService.class);
            context.sendBroadcast(intent);
            return true;
        } else return false;
    }

    private static void handleServiceIntent(@NonNull Context context, @NonNull Intent intent) {
        init(context);
        String messageType = intent.getStringExtra(Messaging.POSSUM_MESSAGE_TYPE);
        if (messageType == null) return;
        SharedPreferences.Editor editor;
        switch (messageType) {
            case Messaging.VERIFICATION_SUCCESS:
                // Successfully uploaded authentication
                String tempKurt = preferences.getString(Constants.ENCRYPTED_TEMP_KURT, null);
                editor = preferences.edit();
                editor.putString(Constants.ENCRYPTED_KURT, tempKurt);
                editor.putString(Constants.ENCRYPTED_TEMP_KURT, null);
                editor.apply();
                break;
            case Messaging.POSSUM_TERMINATE:
                Log.d(tag, "Found that the AwesomePossum should be terminated. Initialize shutdown procedure");
                editor = preferences.edit();
                editor.putString(Constants.ENCRYPTED_KURT, null);
                editor.putString(Constants.ENCRYPTED_TEMP_KURT, null);
                editor.apply();
                break;
            default:
                Log.d(tag, "Unhandled message:"+messageType);
        }
        for (IPossumMessage listener : messageListeners) {
            listener.possumMessageReceived(messageType, intent.getStringExtra(Messaging.POSSUM_MESSAGE));
        }
    }

    /**
     * Starts to listen/gather data while app is running
     *
     * @param encryptedKurt the encrypted kurt id
     * @param identityPoolId the identity pool id it will verify with
     * @throws GatheringNotAuthorizedException If the user hasn't accepted the app, this exception is thrown
     */
    public static void startListening(@NonNull Context context, @NonNull String encryptedKurt, @NonNull String identityPoolId) throws GatheringNotAuthorizedException {
        init(context);
        if (isAuthorized(context)) {
            requestVerification(context, identityPoolId);
            Intent intent = new Intent(context, DataCollectionService.class);
            intent.putExtra("isLearning", false);
            intent.putExtra("encryptedKurt", encryptedKurt);
            context.startService(intent);
            isListening = true;
        } else throw new GatheringNotAuthorizedException();
    }

    /**
     * Check for whether the AwesomePossum is actively listening for sensorData
     *
     * @return true if the Collector service is running, false if not
     */
    public static boolean isListening() {
        return isListening;
    }

    /**
     * Enables you to request the needed permissions
     *
     * @param activity an android activity to handle the requesting
     * @return true if no permissions are missing, else false
     */
    public static boolean requestNeededPermissions(@NonNull Activity activity) {
        init(activity);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (missingPermissions(activity).size() > 0) {
                ActivityCompat.requestPermissions(activity, missingPermissions(activity).toArray(new String[]{}), Constants.AwesomePossumPermissionsRequestCode);
                return false;
            } else return true;
        } else return true;
    }

    /**
     * Add listener for trustScore changes. Not implemented yet.
     *
     * @param listener listener for changes to trustScore
     */
    public static void addTrustListener(@NonNull IPossumTrust listener) {
        trustListeners.add(listener);
    }

    /**
     * Remove listener for trustScore changes. Not implemented yet.
     *
     * @param listener listener for changes to trustScore
     */
    public static void removeTrustListener(IPossumTrust listener) {
        trustListeners.remove(listener);
    }

    /**
     * Used for finding out if any part of your system is being notified of authentication calls
     *
     * @return true if you have something listening to authentication, false if not
     */
    public static boolean isAuthenticating() {
        return trustListeners.size() > 0;
    }

    /**
     * This method is stops listening and clears up all resources used. Run this in your
     * application onDestroy - but remember to call startUpload after it.
     *
     * @param context a valid android context
     */
    public static void stopListening(@NonNull Context context) {
        context.stopService(new Intent(context, DataCollectionService.class));
        isListening = false;
        if (initComplete) {
            context = context.getApplicationContext(); // Important since context needs to be equal
            context.unregisterReceiver(serviceMessageReceiver);
            context.unregisterReceiver(trustReceiver);
        }
        initComplete = false;
        trustListeners.clear();
    }

    /**
     * Add a interface listener for messages
     *
     * @param messageListener a listener you want to add
     */
    public static void addMessageListener(IPossumMessage messageListener) {
        messageListeners.add(messageListener);
    }

    /**
     * Remove a specific listener for messages
     *
     * @param messageListener a listener you want to remove
     */
    public static void removeMessageListener(IPossumMessage messageListener) {
        messageListeners.remove(messageListener);
    }

    /**
     * Removes all listeners for Possum messages. Quick and easy way to clean up before terminating.
     */
    public static void removeAllMessageListeners() {
        messageListeners.clear();
    }

    /**
     * @param context           a valid android context
     * @param encryptedKurt     the encrypted key identifying the user
     * @param identityPoolId    the identity pool id it will use
     * @return true if upload was started, false if no network to upload on or not initialized
     */
    public static boolean startUpload(@NonNull Context context, @NonNull String encryptedKurt, @NonNull String identityPoolId) {
        if (preferences == null) return false;
        Intent intent = new Intent(context, DataUploadService.class);
        intent.putExtra("encryptedKurt", encryptedKurt);
        intent.putExtra("identityPoolId", identityPoolId);
        boolean startedUpload;
        if (Has.network(context)) {
            context.startService(intent);
            startedUpload = true;
        } else startedUpload = false;
        return startedUpload;

    }

    /**
     * Checks whether the user is learning from the gathering or not
     *
     * @return true if user is learning as well as gathering, false if not or if library not
     * initialized
     */
    public static boolean isLearning() {
        return initComplete && preferences.getBoolean(Constants.IS_LEARNING, false);
    }

    /**
     * Handy little method for confirming the version of the library in-app.
     *
     * @return the versionName of the library
     */
    public static String versionName() {
        return BuildConfig.VERSION_NAME;
    }

    /**
     * Change whether user should learn from the data gathered or not. Not used yet.
     *
     * @param context    a valid android context
     * @param isLearning true for learning, false to stop learning
     */
    public static void setLearning(@NonNull Context context, boolean isLearning) {
        init(context);
        preferences.edit().putBoolean(Constants.IS_LEARNING, isLearning).apply();
        Intent intent = new Intent(Messaging.POSSUM_MESSAGE);
        intent.putExtra(Messaging.POSSUM_MESSAGE_TYPE, Messaging.LEARNING);
        context.sendBroadcast(intent);
        Log.i(tag, "Is learning now set to:" + isLearning);
    }

    /**
     * Sends a request to the service (if it is listening) that you want an update on the sensors
     * status. To receive it you will need to startListening for a Broadcast event with the action
     * Messaging.POSSUM_MESSAGE ("PossumMessage")
     * <p>
     * The resulting intent will contain a jsonArray with jsonObjects for all detectors used
     *
     * @param context a valid android context
     */
    public static void requestDetectorStatus(@NonNull Context context) {
        Intent intent = new Intent(Messaging.POSSUM_MESSAGE);
        intent.putExtra(Messaging.POSSUM_MESSAGE_TYPE, Messaging.REQUEST_DETECTORS);
        context.sendBroadcast(intent);
    }

    private static List<String> dangerousPermissions() {
        // Populate dangerous permissions
        List<String> dangerousPermissions = new ArrayList<>();
        dangerousPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        dangerousPermissions.add(Manifest.permission.CAMERA);
        dangerousPermissions.add(Manifest.permission.RECORD_AUDIO);
        return dangerousPermissions;
    }

    /**
     * Fire this method to tell the system that the user has approved of using the AwesomePossum
     * library. Until it is done, no data will be collected
     *
     * @param encryptedKurt the unique id reflecting the user who is authorized
     * @param identityPoolId    the identity pool id you are using
     * @return true if authorized, false if not initialized yet
     */
    public static boolean authorizeGathering(@NonNull Context context, @NonNull String encryptedKurt, @NonNull String identityPoolId) {
        init(context);
        String previouslyStoredKurt = preferences.getString(Constants.ENCRYPTED_KURT, null);
        if (previouslyStoredKurt == null || !encryptedKurt.equals(previouslyStoredKurt)) {
            preferences.edit().putString(Constants.ENCRYPTED_TEMP_KURT, encryptedKurt).apply();
            if (Has.network(context)) {
                Intent intent = new Intent(context, SendKurtService.class);
                intent.putExtra("encryptedKurt", encryptedKurt);
                intent.putExtra("identityPoolId", identityPoolId);
                context.startService(intent);
            }
        }
        return true;
    }

    /**
     * Call this method to check whether user has allowed the Awesome Possum to gather data
     *
     * @return true if allowed, false if not allowed yet
     */
    public static boolean isAuthorized(@NonNull Context context) {
        init(context);
        return (preferences.getString(Constants.ENCRYPTED_KURT, null) != null || preferences.getString(Constants.ENCRYPTED_TEMP_KURT, null) != null);
    }

    /**
     * A default dialog for requesting authorization. Alternatively, manually create a dialog and
     * make sure it calls AwesomePossum.authorizeGathering() then before it starts to startListening, calls
     * AwesomePossum.requestNeededPermissions(Activity) to
     *
     * @param activity      an android activity
     * @param encryptedKurt the user id the dialog will authorize
     * @param bucket        the bucket the encrypted kurt will be uploaded to
     * @param title         the title of the dialog
     * @param message       the message of the dialog
     * @param ok            the ok button text of the dialog
     * @param cancel        the cancel button text of the dialog
     * @return a dialog that can be show()'ed
     */
    public static Dialog getAuthorizeDialog(@NonNull final Activity activity,
                                            @NonNull final String encryptedKurt,
                                            @NonNull final String bucket,
                                            String title,
                                            String message,
                                            String ok,
                                            String cancel) {
        init(activity);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                authorizeGathering(activity, encryptedKurt, bucket);
                requestNeededPermissions(activity);
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        return builder.create();
    }

    private static List<String> missingPermissions(@NonNull Context context) {
        List<String> missingPermissions = new ArrayList<>();
        for (String permission : dangerousPermissions()) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        return missingPermissions;
    }

    /**
     * Handy function for finding out if the user has disabled or missing permissions. Should it be
     * so, you can call the requestNeededPermissions to ask for all the lacking rights.
     *
     * @param context a valid android context
     * @return true if there are missing permissions, false if all are granted
     */
    public static boolean hasMissingPermissions(@NonNull Context context) {
        return missingPermissions(context).size() > 0;
    }
}