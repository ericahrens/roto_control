package com.bitwig.extensions.controllers.melbourneinstruments.control;

import com.bitwig.extension.controller.api.AbsoluteHardwareKnob;
import com.bitwig.extension.controller.api.AbsoluteHardwareValueMatcher;
import com.bitwig.extension.controller.api.HardwareButton;
import com.bitwig.extension.controller.api.HardwareSurface;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.SettableDoubleValue;
import com.bitwig.extensions.controllers.melbourneinstruments.MidiProcessor;
import com.bitwig.extensions.framework.values.BasicDoubleValue;

public class RotoKnob {
    private static final int MIDI_CHANNEL = 0xF;
    private static final int FIRST_MSB_CC = 0xC;
    private static final int LSB_OFFSET = 0x20;
    private static final int FIRST_TOUCH_CC = 0x34;

    private final int midiBase;
    private final int index;
    private int lowValue;
    private int highValue;
    private final BasicDoubleValue knobValue = new BasicDoubleValue();
    private final MidiProcessor midiProcessor;
    private boolean active;
    private final HardwareButton touchButton;
    private final AbsoluteHardwareKnob nativeAbsoluteKnob;

    private int lastSentLow = -1;
    private int lastSentHigh = -1;

    public RotoKnob(final int index, final HardwareSurface surface, final MidiProcessor midiProcessor) {
        this.midiBase = index + FIRST_MSB_CC;
        this.index = index;
        this.midiProcessor = midiProcessor;
        midiProcessor.setCcKnobMatcher(this);
        this.touchButton = surface.createHardwareButton("TOUCH_%d".formatted(index + 1));
        midiProcessor.setCcMatcher(this.touchButton, FIRST_TOUCH_CC + index);
        this.touchButton.isPressed().markInterested();

        final MidiIn midiIn = midiProcessor.getHost().getMidiInPort(0);
        final AbsoluteHardwareValueMatcher msbMatcher = midiIn.createAbsoluteCCValueMatcher(MIDI_CHANNEL, midiBase);
        final AbsoluteHardwareValueMatcher lsbMatcher =
            midiIn.createAbsoluteCCValueMatcher(MIDI_CHANNEL, midiBase + LSB_OFFSET);

        this.nativeAbsoluteKnob = surface.createAbsoluteHardwareKnob("ROTO_NATIVE_%d".formatted(index + 1));
        nativeAbsoluteKnob.setIndexInGroup(index);
        nativeAbsoluteKnob.setAdjustValueMatcher(midiIn.createSequencedValueMatcher(msbMatcher, lsbMatcher, false));
        nativeAbsoluteKnob.disableTakeOver();
        nativeAbsoluteKnob.setHardwareButton(touchButton);
        nativeAbsoluteKnob.value().addValueObserver(this::handleNativeValueChanged);
    }

    private void handleNativeValueChanged(final double value) {
        final int fourteenBitValue = (int) Math.round(value * 16383.0);
        this.highValue = fourteenBitValue >> 7;
        this.lowValue = fourteenBitValue & 0x7F;
        // The native matcher consumes the raw MSB/LSB events. Re-enter the existing local dispatch
        // so every non-native mode and custom binding continues to receive the physical value.
        knobValue.setForce(value);
    }

    public AbsoluteHardwareKnob getNativeAbsoluteKnob() {
        return nativeAbsoluteKnob;
    }

    public void setActive(final boolean active) {
        this.active = active;
    }

    public int getMidiBase() {
        return midiBase;
    }

    public int getIndex() {
        return index;
    }

    public int getLowValue() {
        return lowValue;
    }

    public int getHighValue() {
        return highValue;
    }

    public void setHighByteValue(final int value) {
        this.highValue = value;
    }

    public void updateBytes(final int low, final int highValue) {
        this.lowValue = low;
        this.highValue = highValue;
    }

    public void setLowValue(final int lowValue) {
        this.lowValue = lowValue;
        final int value = lowValue | (highValue << 7);
        knobValue.setForce(Math.min(1, value / 16382.0));
    }

    public HardwareButton getTouchButton() {
        return touchButton;
    }

    public SettableDoubleValue getKnobValue() {
        return knobValue;
    }

    public void updatePosition(final boolean force) {
        if (active) {
            if (force || lastSentHigh != highValue || lastSentLow != lowValue) {
                midiProcessor.sendCCHiRes(midiBase, highValue, lowValue);
                lastSentLow = lowValue;
                lastSentHigh = highValue;
            }
        }
    }

    public void updateParameterValue(String value) {
        midiProcessor.placeParameterUpdate(false, index, value);
    }

    public void placeParameterUpdateDirect(String value) {
        midiProcessor.placeParameterUpdateDirect(false, index, value);
    }

}
