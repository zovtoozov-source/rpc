package tech.onetap.module.settings;

import java.util.function.Supplier;

public abstract class Setting implements ISetting {
    private final String name;
    public Supplier<Boolean> visible = () -> true;

    public Setting(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public abstract String getValueAsString();
    public abstract void setValueFromString(String value);
}