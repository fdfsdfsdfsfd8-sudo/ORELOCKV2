package com.example.orelock;

import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.function.DoubleConsumer;

public class ValueSlider extends SliderWidget {
    private final String label;
    private final double min, max, step;
    private final DoubleConsumer consumer;

    public ValueSlider(int x, int y, int width, int height, String label,
                       double min, double max, double step, double current, DoubleConsumer consumer) {
        super(x, y, width, height, Text.empty(), (MathHelper.clamp(current, min, max) - min) / (max - min));
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.consumer = consumer;
        updateMessage();
    }

    private double actual() {
        double v = Math.round((min + this.value * (max - min)) / step) * step;
        return MathHelper.clamp(v, min, max);
    }

    @Override
    protected void updateMessage() {
        if (label == null) return;
        setMessage(Text.literal(label + Cfg.fmt(Math.round(actual() * 100.0) / 100.0)));
    }

    @Override
    protected void applyValue() {
        consumer.accept(actual());
    }
}
