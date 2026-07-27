package org.lytharalab.gfbs.morphe.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Transparent input surface for an interactive HUD. Closing it returns the
 * same HUD to passive rendering instead of destroying the document.
 */
final class MorpheHudInteractionScreen extends Screen {
    private final ResourceLocation owner;
    private boolean closing;

    MorpheHudInteractionScreen(ResourceLocation owner) {
        super(Component.translatable("gfbs_morphe.screen.hud_interaction"));
        this.owner = owner;
    }

    ResourceLocation owner() {
        return owner;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MorpheHudManager.get().pointerMoved(mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        MorpheHudManager.get().pointerMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return MorpheHudManager.get().pointerDown(mouseX, mouseY, button)
            || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return MorpheHudManager.get().pointerUp(mouseX, mouseY, button)
            || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return MorpheHudManager.get().pointerDragged(mouseX, mouseY, button, dragX, dragY)
            || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return MorpheHudManager.get().scrolled(mouseX, mouseY, delta)
            || super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return MorpheHudManager.get().keyDown(keyCode, scanCode, modifiers)
            || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return MorpheHudManager.get().keyUp(keyCode, scanCode, modifiers)
            || super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return MorpheHudManager.get().charTyped(character, modifiers)
            || super.charTyped(character, modifiers);
    }

    @Override
    public void onClose() {
        closing = true;
        MorpheHudManager.get().setInteractive(owner, false);
    }

    @Override
    public void removed() {
        if (!closing) {
            MorpheHudManager.get().interactionScreenClosed(owner);
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
