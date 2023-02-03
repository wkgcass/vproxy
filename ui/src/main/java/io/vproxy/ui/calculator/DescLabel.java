package io.vproxy.ui.calculator;

import io.vproxy.vfx.ui.wrapper.ThemeLabel;
import javafx.geometry.Pos;

public class DescLabel extends ThemeLabel {
    public DescLabel(String text) {
        super(text);
        setPrefWidth(180);
        setAlignment(Pos.CENTER_RIGHT);
    }
}
