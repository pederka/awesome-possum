package com.telenor.possumlib.utils;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;

import com.telenor.possumlib.abstractdetectors.AbstractDetector;
import com.telenor.possumlib.detectors.Accelerometer;
import com.telenor.possumlib.detectors.AmbientSoundDetector;
import com.telenor.possumlib.detectors.BluetoothDetector;
import com.telenor.possumlib.detectors.GyroScope;
import com.telenor.possumlib.detectors.HardwareDetector;
import com.telenor.possumlib.detectors.ImageDetector;
import com.telenor.possumlib.detectors.LocationDetector;
import com.telenor.possumlib.detectors.MetaDataDetector;
import com.telenor.possumlib.detectors.NetworkDetector;
import com.telenor.possumlib.models.PossumBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Get {
    /**
     * Handy method for returning the supported ABI's
     *
     * @return a formatted text string with supported abis
     */
    public static String supportedABISString() {
        String output = "";
        List<String> supported = supportedABIList();
        for (int i = 0; i < supported.size(); i++) {
            if (i > 0) {
                output += ", ";
            }
            output += supported.get(i);
        }
        return output;
    }

    /**
     * Yields a list of all supported ABIs
     *
     * @return a list of supported abis
     */
    private static List<String> supportedABIList() {
        List<String> supported = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Collections.addAll(supported, Build.SUPPORTED_ABIS);
        } else {
            supported.add(Build.CPU_ABI);
            supported.add(Build.CPU_ABI2);
        }
        return supported;
    }

    public static List<AbstractDetector> Detectors(@NonNull Context context, String uniqueUserId, @NonNull PossumBus eventBus, boolean isAuthenticating) {
        List<AbstractDetector> detectors = new ArrayList<>();
        if (!isAuthenticating) {
            detectors.add(new MetaDataDetector(context, uniqueUserId, eventBus, false)); // Should always be first in line
            detectors.add(new HardwareDetector(context, uniqueUserId, eventBus, false));
        }
        detectors.add(new Accelerometer(context, uniqueUserId, eventBus, isAuthenticating));
        detectors.add(new GyroScope(context, uniqueUserId, eventBus, isAuthenticating));
        detectors.add(new LocationDetector(context, uniqueUserId, eventBus, isAuthenticating));
        detectors.add(new BluetoothDetector(context, uniqueUserId, eventBus, isAuthenticating));
        detectors.add(new NetworkDetector(context, uniqueUserId, eventBus, isAuthenticating));
        detectors.add(new AmbientSoundDetector(context, uniqueUserId, eventBus, isAuthenticating));
        detectors.add(new ImageDetector(context, uniqueUserId, eventBus, isAuthenticating));
        return detectors;
    }
}