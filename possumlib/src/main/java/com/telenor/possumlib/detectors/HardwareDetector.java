package com.telenor.possumlib.detectors;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;

import com.google.gson.JsonArray;
import com.telenor.possumlib.abstractdetectors.AbstractDetector;
import com.telenor.possumlib.constants.DetectorType;
import com.telenor.possumlib.models.PossumBus;
import com.telenor.possumlib.utils.Get;

/**
 * Detector meant to detect hardware info, storing it in a seperate file, instead of storing it in
 * the metaData
 */
public class HardwareDetector extends AbstractDetector {
    /**
     * Constructor for the Hardware Detector
     *
     * @param context        a valid android context
     * @param uniqueUserId the unique user id
     * @param eventBus       the event bus used for sending messages to and from
     * @param authenticating whether the detector is used for authentication or data gathering
     */
    public HardwareDetector(Context context, String uniqueUserId, @NonNull PossumBus eventBus, boolean authenticating) {
        super(context, uniqueUserId, eventBus, authenticating);
        findHardwareSpecs();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String requiredPermission() {
        return null;
    }

    @Override
    public long authenticationListenInterval() {
        return 1000;
    }

    @Override
    public int detectorType() {
        return DetectorType.Hardware;
    }

    @Override
    public String detectorName() {
        return "Hardware";
    }

    private void findHardwareSpecs() {
        // It should be sent for each time the app is instantiated, in case he updates his android
        JsonArray array = new JsonArray();
        array.add("HARDWARE_INFO START");
        array.add("Board:"+ Build.BOARD);
        array.add("Brand:"+Build.BRAND);
        array.add("Device:"+Build.DEVICE);
        array.add("Display:"+Build.DISPLAY);
        array.add("Fingerprint:"+Build.FINGERPRINT);
        array.add("Hardware:"+Build.HARDWARE);
        array.add("Host:"+Build.HOST);
        array.add("Id:"+Build.ID);
        array.add("Manufacturer:"+Build.MANUFACTURER);
        array.add("Model:"+Build.MODEL);
        array.add("Product:"+Build.PRODUCT);
        array.add("Serial:"+Build.SERIAL);
        array.add("Version:"+Build.VERSION.SDK_INT+" ("+Build.VERSION.CODENAME+")");
        array.add("SupportedABIS:"+ Get.supportedABISString());
        array.add("HARDWARE_INFO STOP");
        sessionValues.add(array);
        storeData();
    }
}