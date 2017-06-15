package com.telenor.possumlib.changeevents;

/**
 * An event for storing meta data about whats happening
 */
public class MetaDataChangeEvent extends PossumEvent {
    public MetaDataChangeEvent(String message) {
        super(null, message);
    }
}
