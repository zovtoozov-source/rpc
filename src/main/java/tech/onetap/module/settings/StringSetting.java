package tech.onetap.module.settings;

import lombok.Getter;
import lombok.Setter;

import java.util.function.Supplier;

@Getter
@Setter
public class StringSetting extends Setting {
    private String value;

    public StringSetting(String name, String defaultValue) {
        super(name);
        this.value = defaultValue;
    }

    @Override
    public String getValueAsString() {
        return value;
    }

    @Override
    public void setValueFromString(String value) {
        this.value = value;
    }

    @Override
    public StringSetting setVisible(Supplier<Boolean> visible) {
        this.visible = visible;
        return this;
    }
}
