package org.lytharalab.gfbs.morphe.client;

import com.mojang.math.Axis;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.lytharalab.gfbs.morphe.core.UiCanvas;
import org.lytharalab.gfbs.morphe.core.UiColor;
import org.lytharalab.gfbs.morphe.core.UiImageRegion;
import org.lytharalab.gfbs.morphe.core.UiRect;
import org.lytharalab.gfbs.morphe.core.UiStyle;
import org.lytharalab.gfbs.morphe.core.UiTransform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MinecraftUiCanvas implements UiCanvas {
    private static final Map<ResourceLocation, ImageSize> IMAGE_SIZES = new ConcurrentHashMap<>();

    private final GuiGraphics graphics;
    private final Font font;

    public MinecraftUiCanvas(GuiGraphics graphics) {
        this.graphics = graphics;
        this.font = Minecraft.getInstance().font;
    }

    @Override
    public <T> T backend(Class<T> type) {
        if (type == null) {
            return null;
        }
        if (type.isInstance(graphics)) {
            return type.cast(graphics);
        }
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        return null;
    }

    @Override
    public void pushTransform(UiRect bounds, double rotationDegrees) {
        pushTransform(bounds, new UiTransform(0, 0, 1, 1, rotationDegrees, 0.5, 0.5));
    }

    @Override
    public void pushTransform(UiRect bounds, UiTransform transform) {
        graphics.pose().pushPose();
        double pivotX = bounds.x() + bounds.width() * transform.pivotX();
        double pivotY = bounds.y() + bounds.height() * transform.pivotY();
        graphics.pose().translate(transform.translateX(), transform.translateY(), 0);
        if (transform.rotation() != 0 || transform.scaleX() != 1 || transform.scaleY() != 1) {
            graphics.pose().translate(pivotX, pivotY, 0);
            if (transform.rotation() != 0) {
                graphics.pose().mulPose(Axis.ZP.rotationDegrees((float) transform.rotation()));
            }
            graphics.pose().scale((float) transform.scaleX(), (float) transform.scaleY(), 1);
            graphics.pose().translate(-pivotX, -pivotY, 0);
        }
    }

    @Override
    public void popTransform() {
        graphics.pose().popPose();
    }

    @Override
    public void pushClip(UiRect bounds) {
        graphics.enableScissor(
            floor(bounds.x()),
            floor(bounds.y()),
            ceil(bounds.right()),
            ceil(bounds.bottom())
        );
    }

    @Override
    public void popClip() {
        graphics.disableScissor();
    }

    @Override
    public void fill(UiRect bounds, UiColor color, double radius) {
        int left = floor(bounds.x());
        int top = floor(bounds.y());
        int right = ceil(bounds.right());
        int bottom = ceil(bounds.bottom());
        int rounded = Math.max(0, Math.min(
            (int) Math.round(radius),
            Math.min((right - left) / 2, (bottom - top) / 2)
        ));
        if (rounded <= 1) {
            graphics.fill(left, top, right, bottom, color.argb());
            return;
        }

        graphics.fill(left + rounded, top, right - rounded, bottom, color.argb());
        graphics.fill(left, top + rounded, right, bottom - rounded, color.argb());
        for (int row = 0; row < rounded; row++) {
            double y = rounded - row - 0.5;
            int inset = (int) Math.ceil(rounded - Math.sqrt(Math.max(0, rounded * rounded - y * y)));
            graphics.fill(left + inset, top + row, right - inset, top + row + 1, color.argb());
            graphics.fill(left + inset, bottom - row - 1, right - inset, bottom - row, color.argb());
        }
    }

    @Override
    public void stroke(UiRect bounds, UiColor color, double width, double radius) {
        int thickness = Math.max(1, (int) Math.round(width));
        int left = floor(bounds.x());
        int top = floor(bounds.y());
        int right = ceil(bounds.right());
        int bottom = ceil(bounds.bottom());
        graphics.fill(left, top, right, Math.min(bottom, top + thickness), color.argb());
        graphics.fill(left, Math.max(top, bottom - thickness), right, bottom, color.argb());
        graphics.fill(left, top, Math.min(right, left + thickness), bottom, color.argb());
        graphics.fill(Math.max(left, right - thickness), top, right, bottom, color.argb());
    }

    @Override
    public void text(
        String text,
        UiRect bounds,
        UiColor color,
        int fontSize,
        UiStyle.TextAlign horizontal,
        UiStyle.VerticalAlign vertical,
        boolean wrap,
        boolean shadow
    ) {
        if (text.isEmpty() || bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        float scale = Math.max(0.1f, fontSize / (float) font.lineHeight);
        int logicalWidth = Math.max(1, (int) Math.floor(bounds.width() / scale));
        Component component = component(text);
        List<FormattedCharSequence> lines = wrap
            ? font.split(component, logicalWidth)
            : List.of(component.getVisualOrderText());
        double renderedHeight = lines.size() * font.lineHeight * scale;
        double startY = switch (vertical) {
            case TOP -> bounds.y();
            case CENTER -> bounds.y() + (bounds.height() - renderedHeight) / 2.0;
            case BOTTOM -> bounds.bottom() - renderedHeight;
        };

        for (int index = 0; index < lines.size(); index++) {
            FormattedCharSequence line = lines.get(index);
            double renderedWidth = font.width(line) * scale;
            double x = switch (horizontal) {
                case LEFT -> bounds.x();
                case CENTER -> bounds.x() + (bounds.width() - renderedWidth) / 2.0;
                case RIGHT -> bounds.right() - renderedWidth;
            };
            double y = startY + index * font.lineHeight * scale;
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, line, 0, 0, color.argb(), shadow);
            graphics.pose().popPose();
        }
    }

    @Override
    public void image(String resource, UiRect bounds, UiColor tint, UiStyle.ImageFit fit) {
        image(resource, bounds, tint, fit, UiImageRegion.FULL);
    }

    @Override
    public void image(
        String resource,
        UiRect bounds,
        UiColor tint,
        UiStyle.ImageFit fit,
        UiImageRegion imageRegion
    ) {
        ResourceLocation location = ResourceLocation.tryParse(resource);
        if (location == null) {
            return;
        }
        float alpha = tint.alpha() / 255.0f;
        graphics.setColor(
            tint.red() / 255.0f,
            tint.green() / 255.0f,
            tint.blue() / 255.0f,
            alpha
        );
        ImageSize source = IMAGE_SIZES.computeIfAbsent(location, MinecraftUiCanvas::readImageSize);
        BlitRegion region = fit(bounds, source, fit, imageRegion);
        graphics.blit(
            location,
            floor(region.destination.x()),
            floor(region.destination.y()),
            Math.max(0, (int) Math.round(region.destination.width())),
            Math.max(0, (int) Math.round(region.destination.height())),
            (float) region.sourceX,
            (float) region.sourceY,
            Math.max(1, (int) Math.round(region.sourceWidth)),
            Math.max(1, (int) Math.round(region.sourceHeight)),
            source.width,
            source.height
        );
        graphics.setColor(1, 1, 1, 1);
    }

    @Override
    public void item(String itemId, int count, UiRect bounds, boolean decorations) {
        ResourceLocation location = ResourceLocation.tryParse(itemId);
        if (location == null) {
            return;
        }
        var item = ForgeRegistries.ITEMS.getValue(location);
        if (item == null || bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        ItemStack stack = new ItemStack(item, Math.max(1, count));
        graphics.pose().pushPose();
        graphics.pose().translate(bounds.x(), bounds.y(), 0);
        graphics.pose().scale((float) (bounds.width() / 16.0), (float) (bounds.height() / 16.0), 1);
        graphics.renderItem(stack, 0, 0);
        if (decorations) {
            graphics.renderItemDecorations(font, stack, 0, 0);
        }
        graphics.pose().popPose();
    }

    public static void clearImageSizeCache() {
        IMAGE_SIZES.clear();
    }

    @Override
    public int textWidth(String text, int fontSize) {
        float scale = Math.max(0.1f, fontSize / (float) font.lineHeight);
        return Math.round(font.width(component(text)) * scale);
    }

    @Override
    public int lineHeight(int fontSize) {
        return Math.max(1, fontSize);
    }

    @Override
    public List<String> wrapText(String text, int maxWidth, int fontSize) {
        if (maxWidth <= 0 || text.isBlank()) {
            return List.of(text);
        }
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && textWidth(candidate, fontSize) > maxWidth) {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            } else {
                current.setLength(0);
                current.append(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static Component component(String text) {
        return text.startsWith("@") && text.length() > 1
            ? Component.translatable(text.substring(1))
            : Component.literal(text);
    }

    private static ImageSize readImageSize(ResourceLocation location) {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(location);
            if (resource.isPresent()) {
                try (var input = resource.get().open(); NativeImage image = NativeImage.read(input)) {
                    return new ImageSize(Math.max(1, image.getWidth()), Math.max(1, image.getHeight()));
                }
            }
        } catch (Exception ignored) {
        }
        return new ImageSize(1, 1);
    }

    private static BlitRegion fit(
        UiRect bounds,
        ImageSize source,
        UiStyle.ImageFit fit,
        UiImageRegion imageRegion
    ) {
        double sourceX = imageRegion.u0() * source.width;
        double sourceY = imageRegion.v0() * source.height;
        double regionWidth = Math.max(1, Math.abs(imageRegion.u1() - imageRegion.u0()) * source.width);
        double regionHeight = Math.max(1, Math.abs(imageRegion.v1() - imageRegion.v0()) * source.height);
        if (fit == UiStyle.ImageFit.STRETCH || bounds.width() <= 0 || bounds.height() <= 0) {
            return new BlitRegion(bounds, sourceX, sourceY, regionWidth, regionHeight);
        }
        double sourceRatio = regionWidth / regionHeight;
        double targetRatio = bounds.width() / bounds.height();
        if (fit == UiStyle.ImageFit.CONTAIN) {
            double scale = Math.min(bounds.width() / regionWidth, bounds.height() / regionHeight);
            double width = regionWidth * scale;
            double height = regionHeight * scale;
            return new BlitRegion(
                new UiRect(
                    bounds.centerX() - width / 2.0,
                    bounds.centerY() - height / 2.0,
                    width,
                    height
                ),
                sourceX,
                sourceY,
                regionWidth,
                regionHeight
            );
        }
        if (sourceRatio > targetRatio) {
            double sourceWidth = regionHeight * targetRatio;
            return new BlitRegion(
                bounds,
                sourceX + (regionWidth - sourceWidth) / 2.0,
                sourceY,
                sourceWidth,
                regionHeight
            );
        }
        double sourceHeight = regionWidth / targetRatio;
        return new BlitRegion(
            bounds,
            sourceX,
            sourceY + (regionHeight - sourceHeight) / 2.0,
            regionWidth,
            sourceHeight
        );
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static int ceil(double value) {
        return (int) Math.ceil(value);
    }

    private record ImageSize(int width, int height) {
    }

    private record BlitRegion(
        UiRect destination,
        double sourceX,
        double sourceY,
        double sourceWidth,
        double sourceHeight
    ) {
    }
}
