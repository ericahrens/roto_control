package com.bitwig.extensions.controllers.melbourneinstruments.binding;

import com.bitwig.extension.controller.api.AbsoluteHardwareControlBinding;
import com.bitwig.extension.controller.api.Parameter;
import com.bitwig.extensions.controllers.melbourneinstruments.control.RotoKnob;
import com.bitwig.extensions.controllers.melbourneinstruments.device.RotoMacroParameter;
import com.bitwig.extensions.framework.Binding;
import com.bitwig.extensions.framework.values.BooleanValueObject;

public class RotoKnobParameterBinding extends Binding<RotoKnob, Parameter> {
    private static final long NATIVE_FEEDBACK_GUARD_MS = 100L;

    private int lowValue;
    private int highValue;
    private long knobChangeTime;
    private long touchRelease;
    private boolean exists;
    private final RotoMacroParameter macroParameter;
    private String displayValue;
    private final BooleanValueObject touchAutomationActive;
    private final boolean nativeParameterBinding;
    private AbsoluteHardwareControlBinding nativeHardwareBinding;

    public RotoKnobParameterBinding(final RotoKnob knob, final Parameter parameter,
        final RotoMacroParameter macroParameter, final BooleanValueObject touchAutomationActive) {
        this(knob, parameter, macroParameter, touchAutomationActive, macroParameter == null);
    }

    public RotoKnobParameterBinding(final RotoKnob knob, final Parameter parameter,
        final RotoMacroParameter macroParameter, final BooleanValueObject touchAutomationActive,
        final boolean nativeParameterBinding) {
        super(knob, knob, parameter);
        this.touchAutomationActive = touchAutomationActive;
        this.nativeParameterBinding = nativeParameterBinding;
        parameter.name().markInterested();
        knob.getKnobValue().addValueObserver(this::handleKnobValueChanged);
        parameter.value().addValueObserver(this::handleParameterValueChanged);
        parameter.exists().addValueObserver(this::handleExists);
        updateValues(parameter.value().get());
        knob.getTouchButton().isPressed().addValueObserver(this::handleTouched);
        this.macroParameter = macroParameter;
        if (this.macroParameter != null) {
            parameter.displayedValue().addValueObserver(this::handleDisplayChanged);
        } else {
            parameter.displayedValue().addValueObserver(this::handleInternalDisplayChange);
        }
        displayValue = parameter.displayedValue().get();
    }

    public RotoKnobParameterBinding(final RotoKnob knob, final Parameter parameter,
        final BooleanValueObject touchAutomationActive) {
        this(knob, parameter, null, touchAutomationActive, true);
    }

    public RotoKnobParameterBinding(final RotoKnob knob, final Parameter parameter,
        final BooleanValueObject touchAutomationActive, final boolean nativeParameterBinding) {
        this(knob, parameter, null, touchAutomationActive, nativeParameterBinding);
    }

    private void handleInternalDisplayChange(String displayValue) {
        this.displayValue = displayValue;
        if (isActive()) {
            final long diff = System.currentTimeMillis() - knobChangeTime;
            if (diff < 300) {
                getSource().updateParameterValue(displayValue);
            }
        }
    }

    private void handleDisplayChanged(final String s) {
        this.displayValue = s;
        if (isActive()) {
            final long diff = System.currentTimeMillis() - knobChangeTime;
            if (diff < 300) {
                getSource().updateParameterValue(displayValue);
            }
        }
    }

    private void handleTouched(final boolean touched) {
        if (isActive() && exists) {
            getTarget().touch(touched);
            if (!touched) {
                knobChangeTime = System.currentTimeMillis();
                touchRelease = knobChangeTime;
            } else {
                getSource().placeParameterUpdateDirect(displayValue);
                touchRelease = -1;
            }
        }
    }

    private boolean updateValues(final double v) {
        final int value = (int) Math.round(v * 16383.0);
        final int newLow = value & 0x7F;
        final int newHigh = value >> 7;
        if (newLow != lowValue || newHigh != highValue) {
            this.lowValue = newLow;
            this.highValue = newHigh;
            return true;
        }
        return false;
    }

    private void handleExists(final boolean exists) {
        this.exists = exists;
        if (isActive()) {
            getSource().setActive(exists);
        }
    }

    private void handleParameterValueChanged(final double v) {
        if (updateValues(v) && isActive()) {
            final RotoKnob knob = getSource();

            knob.updateBytes(lowValue, highValue);
            if (!nativeParameterBinding
                || System.currentTimeMillis() - knobChangeTime >= NATIVE_FEEDBACK_GUARD_MS) {
                knob.updatePosition(false);
            }
        }
    }

    private void handleKnobValueChanged(final double v) {
        if (!isActive()) {
            return;
        }
        knobChangeTime = System.currentTimeMillis();
        this.lowValue = getSource().getLowValue();
        this.highValue = getSource().getHighValue();
        if (nativeParameterBinding) {
            return;
        }
        if (touchRelease != -1 && touchAutomationActive.get()
            && (System.currentTimeMillis() - touchRelease) < 500) {
            return;
        }
        getTarget().value().setImmediately(v);
    }

    @Override
    protected void deactivate() {
        if (nativeHardwareBinding != null) {
            nativeHardwareBinding.removeBinding();
            nativeHardwareBinding = null;
        }
        getTarget().touch(false);
        touchRelease = -1;
    }

    @Override
    protected void activate() {
        final RotoKnob knob = getSource();
        updateValues(getTarget().get());
        knob.setActive(exists);
        knob.updateBytes(lowValue, highValue);
        knob.updatePosition(true);
        if (nativeParameterBinding && nativeHardwareBinding == null) {
            nativeHardwareBinding =
                knob.getNativeAbsoluteKnob().addBindingWithRange(getTarget().value(), 0.0, 1.0);
        }
    }

    public boolean isNativeParameterBinding() {
        return nativeParameterBinding;
    }
}
