package org.lytharalab.gfbs.morphe.widget;

import org.lytharalab.gfbs.morphe.core.UiCanvas;
import org.lytharalab.gfbs.morphe.core.UiFrame;
import org.lytharalab.gfbs.morphe.core.UiRect;
import org.lytharalab.gfbs.morphe.core.UiSize;

public class TextElement extends PanelElement {
    private String text = "";

    public TextElement() {
        this("text");
        setProperty("pointer_events", false);
    }

    protected TextElement(String type) {
        super(type);
    }

    public String text() {
        return text;
    }

    @Override
    protected Object getWidgetProperty(String property) {
        if (property.equals("text")) {
            return text;
        }
        return super.getWidgetProperty(property);
    }

    @Override
    protected boolean setWidgetProperty(String property, Object value) {
        if (property.equals("text")) {
            text = value == null ? "" : value.toString();
            return true;
        }
        return super.setWidgetProperty(property, value);
    }

    @Override
    public UiSize measure(double availableWidth, double availableHeight) {
        double width = Math.min(availableWidth, Math.max(1, text.length()) * style().fontSize() * 0.58);
        if (style().wrapText() && width > 0) {
            double rawWidth = Math.max(1, text.length()) * style().fontSize() * 0.58;
            int lines = Math.max(1, (int) Math.ceil(rawWidth / width));
            return new UiSize(width + style().padding().horizontal(), lines * style().fontSize() + style().padding().vertical());
        }
        return new UiSize(
            width + style().padding().horizontal(),
            style().fontSize() + style().padding().vertical()
        );
    }

    @Override
    protected void renderContent(UiCanvas canvas, UiFrame frame) {
        if (text.isEmpty() || style().opacity() <= 0) {
            return;
        }
        UiRect content = bounds().inset(style().padding());
        canvas.text(
            text,
            content,
            style().foreground().multiplyAlpha(style().opacity()),
            style().fontSize(),
            style().textAlign(),
            style().verticalAlign(),
            style().wrapText(),
            style().textShadow()
        );
    }
}
