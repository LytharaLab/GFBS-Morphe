package org.lytharalab.gfbs.morphe.core;

public final class UiRoot extends UiElement {
    public UiRoot() {
        super("root");
        setProperty("id", "root");
        setProperty("layout", "free");
        setProperty("pointer_events", false);
    }

    public void resize(double width, double height) {
        setBounds(new UiRect(0, 0, width, height));
        markDirty();
    }
}
