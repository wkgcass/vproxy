package io.vproxy.ui.calculator;

import io.vproxy.vfx.manager.font.FontManager;
import io.vproxy.vfx.manager.font.FontProvider;
import io.vproxy.vfx.manager.font.FontSettings;
import io.vproxy.vfx.theme.impl.DarkTheme;
import io.vproxy.vfx.theme.impl.DarkThemeFontProvider;

public class CustomTheme extends DarkTheme {
    @Override
    public FontProvider fontProvider() {
        return new CustomThemeFontProvider();
    }
}

class CustomThemeFontProvider extends DarkThemeFontProvider {
    @Override
    protected void defaultFont(FontSettings settings) {
        super.defaultFont(settings);
        settings.setFamily(FontManager.FONT_NAME_JetBrainsMono);
    }
}
