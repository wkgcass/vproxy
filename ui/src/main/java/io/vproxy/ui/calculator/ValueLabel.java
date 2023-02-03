package io.vproxy.ui.calculator;

import io.vproxy.vfx.manager.font.FontManager;
import io.vproxy.vfx.ui.wrapper.ThemeLabel;
import javafx.scene.Cursor;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import static io.vproxy.ui.calculator.IPv4CalculatorScene.VALUE_WIDTH;

public class ValueLabel extends ThemeLabel {
    public ValueLabel() {
        FontManager.get().setFont(this);
        setPrefWidth(VALUE_WIDTH);
        setCursor(Cursor.HAND);

        setOnMouseClicked(e -> {
            var clipboard = Clipboard.getSystemClipboard();
            var content = new ClipboardContent();
            content.putString(getText());
            clipboard.setContent(content);
        });
    }
}
