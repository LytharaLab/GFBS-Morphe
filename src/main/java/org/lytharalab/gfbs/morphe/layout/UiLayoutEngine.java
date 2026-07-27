package org.lytharalab.gfbs.morphe.layout;

import org.lytharalab.gfbs.morphe.core.UiElement;
import org.lytharalab.gfbs.morphe.core.UiInsets;
import org.lytharalab.gfbs.morphe.core.UiLength;
import org.lytharalab.gfbs.morphe.core.UiRect;
import org.lytharalab.gfbs.morphe.core.UiRoot;
import org.lytharalab.gfbs.morphe.core.UiSize;
import org.lytharalab.gfbs.morphe.core.UiStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic flex/grid/absolute layout engine with no Minecraft dependency.
 */
public final class UiLayoutEngine {
    public void layout(UiRoot root) {
        layoutChildren(root, 0);
        root.clearDirtyRecursively();
    }

    private void layoutChildren(UiElement parent, int depth) {
        if (depth > UiElement.MAX_TREE_DEPTH) {
            throw new IllegalStateException("UI tree exceeds maximum layout depth");
        }

        UiRect content = parent.bounds().inset(parent.style().padding());
        content = new UiRect(
            content.x() + parent.childOffsetX(),
            content.y() + parent.childOffsetY(),
            content.width(),
            content.height()
        );

        List<UiElement> flow = new ArrayList<>();
        List<UiElement> absolute = new ArrayList<>();
        for (UiElement child : parent.children()) {
            if (!child.style().visible()) {
                child.setBounds(UiRect.ZERO);
            } else if (child.style().position() == UiStyle.Position.ABSOLUTE) {
                absolute.add(child);
            } else {
                flow.add(child);
            }
        }

        switch (parent.style().layout()) {
            case FREE -> layoutFree(flow, content, depth);
            case ROW -> layoutLinear(flow, content, true, parent.style(), depth);
            case COLUMN -> layoutLinear(flow, content, false, parent.style(), depth);
            case GRID -> layoutGrid(flow, content, parent.style(), depth);
        }

        for (UiElement child : absolute) {
            layoutAbsolute(child, content, depth);
        }
        parent.afterLayout();
    }

    private void layoutFree(List<UiElement> children, UiRect content, int depth) {
        for (UiElement child : children) {
            UiInsets margin = child.style().margin();
            UiSize preferred = child.measure(content.width(), content.height());
            double width = resolve(
                child.style().width(),
                content.width(),
                preferred.width() > 0 ? preferred.width() : content.width() - margin.horizontal(),
                child.style().minWidth(),
                child.style().maxWidth()
            );
            double height = resolve(
                child.style().height(),
                content.height(),
                preferred.height(),
                child.style().minHeight(),
                child.style().maxHeight()
            );
            if (child.style().height().isAuto() && height <= 0) {
                height = Math.max(0, content.height() - margin.vertical());
            }
            double x = content.x() + margin.left() + child.style().left().resolve(content.width(), 0);
            double y = content.y() + margin.top() + child.style().top().resolve(content.height(), 0);
            setAndLayout(child, new UiRect(x, y, width, height), depth);
        }
    }

    private void layoutAbsolute(UiElement child, UiRect content, int depth) {
        UiInsets margin = child.style().margin();
        UiSize preferred = child.measure(content.width(), content.height());
        double width = resolve(
            child.style().width(),
            content.width(),
            preferred.width(),
            child.style().minWidth(),
            child.style().maxWidth()
        );
        double height = resolve(
            child.style().height(),
            content.height(),
            preferred.height(),
            child.style().minHeight(),
            child.style().maxHeight()
        );
        double x = content.x() + margin.left() + child.style().left().resolve(content.width(), 0);
        double y = content.y() + margin.top() + child.style().top().resolve(content.height(), 0);
        setAndLayout(child, new UiRect(x, y, width, height), depth);
    }

    private void layoutLinear(
        List<UiElement> children,
        UiRect content,
        boolean horizontal,
        UiStyle parentStyle,
        int depth
    ) {
        if (children.isEmpty()) {
            return;
        }

        double mainAvailable = horizontal ? content.width() : content.height();
        double crossAvailable = horizontal ? content.height() : content.width();
        List<LinearItem> items = new ArrayList<>(children.size());
        double used = 0;
        double flexTotal = 0;

        for (UiElement child : children) {
            UiStyle style = child.style();
            UiInsets margin = style.margin();
            UiSize preferred = child.measure(content.width(), content.height());
            UiLength mainLength = horizontal ? style.width() : style.height();
            double preferredMain = horizontal ? preferred.width() : preferred.height();
            double main = resolve(
                mainLength,
                mainAvailable,
                preferredMain,
                horizontal ? style.minWidth() : style.minHeight(),
                horizontal ? style.maxWidth() : style.maxHeight()
            );
            double margins = horizontal ? margin.horizontal() : margin.vertical();
            used += main + margins;
            flexTotal += style.flexGrow();
            items.add(new LinearItem(child, main));
        }
        used += parentStyle.gap() * Math.max(0, children.size() - 1);

        double remaining = Math.max(0, mainAvailable - used);
        if (flexTotal > 0 && remaining > 0) {
            for (LinearItem item : items) {
                if (item.element.style().flexGrow() > 0) {
                    item.mainSize += remaining * item.element.style().flexGrow() / flexTotal;
                }
            }
            remaining = 0;
        }

        Spacing spacing = spacing(parentStyle.justifyContent(), remaining, children.size(), parentStyle.gap());
        double cursor = (horizontal ? content.x() : content.y()) + spacing.leading;
        for (LinearItem item : items) {
            UiElement child = item.element;
            UiStyle style = child.style();
            UiInsets margin = style.margin();
            UiSize preferred = child.measure(content.width(), content.height());
            UiStyle.Align align = style.alignSelf() != null ? style.alignSelf() : parentStyle.alignItems();
            UiLength crossLength = horizontal ? style.height() : style.width();
            double preferredCross = horizontal ? preferred.height() : preferred.width();
            double crossMargins = horizontal ? margin.vertical() : margin.horizontal();
            double automaticCross = align == UiStyle.Align.STRETCH
                ? Math.max(0, crossAvailable - crossMargins)
                : preferredCross;
            double cross = resolve(
                crossLength,
                crossAvailable,
                automaticCross,
                horizontal ? style.minHeight() : style.minWidth(),
                horizontal ? style.maxHeight() : style.maxWidth()
            );
            double crossOffset = alignOffset(align, crossAvailable, cross, crossMargins, horizontal ? margin.top() : margin.left());

            UiRect bounds;
            if (horizontal) {
                double x = cursor + margin.left();
                double y = content.y() + crossOffset;
                bounds = new UiRect(x, y, item.mainSize, cross);
                cursor += margin.left() + item.mainSize + margin.right() + spacing.gap;
            } else {
                double x = content.x() + crossOffset;
                double y = cursor + margin.top();
                bounds = new UiRect(x, y, cross, item.mainSize);
                cursor += margin.top() + item.mainSize + margin.bottom() + spacing.gap;
            }
            setAndLayout(child, bounds, depth);
        }
    }

    private void layoutGrid(List<UiElement> children, UiRect content, UiStyle parentStyle, int depth) {
        if (children.isEmpty()) {
            return;
        }
        int columns = Math.max(1, parentStyle.gridColumns());
        double cellWidth = Math.max(0, (content.width() - parentStyle.gap() * (columns - 1)) / columns);
        List<GridItem> items = new ArrayList<>();
        List<Double> rowHeights = new ArrayList<>();
        int row = 0;
        int column = 0;

        for (UiElement child : children) {
            int span = Math.min(columns, Math.max(1, child.style().gridSpan()));
            if (column + span > columns) {
                row++;
                column = 0;
            }
            double availableWidth = cellWidth * span + parentStyle.gap() * (span - 1);
            UiSize preferred = child.measure(availableWidth, content.height());
            UiInsets margin = child.style().margin();
            double width = resolve(
                child.style().width(),
                availableWidth,
                Math.max(0, availableWidth - margin.horizontal()),
                child.style().minWidth(),
                child.style().maxWidth()
            );
            double height = resolve(
                child.style().height(),
                content.height(),
                preferred.height(),
                child.style().minHeight(),
                child.style().maxHeight()
            );
            ensureRow(rowHeights, row);
            rowHeights.set(row, Math.max(rowHeights.get(row), height + margin.vertical()));
            items.add(new GridItem(child, row, column, span, width, height));
            column += span;
            if (column >= columns) {
                row++;
                column = 0;
            }
        }

        double[] rowOffsets = new double[rowHeights.size()];
        for (int i = 1; i < rowOffsets.length; i++) {
            rowOffsets[i] = rowOffsets[i - 1] + rowHeights.get(i - 1) + parentStyle.gap();
        }

        for (GridItem item : items) {
            UiInsets margin = item.element.style().margin();
            double x = content.x() + item.column * (cellWidth + parentStyle.gap()) + margin.left();
            double y = content.y() + rowOffsets[item.row] + margin.top();
            setAndLayout(item.element, new UiRect(x, y, item.width, item.height), depth);
        }
    }

    private void setAndLayout(UiElement element, UiRect bounds, int depth) {
        element.setBounds(bounds);
        layoutChildren(element, depth + 1);
    }

    private static double resolve(UiLength length, double reference, double automatic, double min, double max) {
        double resolved = length.resolve(reference, automatic);
        return Math.max(min, Math.min(max, Math.max(0, resolved)));
    }

    private static double alignOffset(
        UiStyle.Align align,
        double available,
        double size,
        double totalMargins,
        double leadingMargin
    ) {
        double free = Math.max(0, available - size - totalMargins);
        return switch (align) {
            case START, STRETCH -> leadingMargin;
            case CENTER -> leadingMargin + free / 2.0;
            case END -> leadingMargin + free;
        };
    }

    private static Spacing spacing(UiStyle.Justify justify, double remaining, int count, double baseGap) {
        if (count <= 0) {
            return new Spacing(0, baseGap);
        }
        return switch (justify) {
            case START -> new Spacing(0, baseGap);
            case CENTER -> new Spacing(remaining / 2.0, baseGap);
            case END -> new Spacing(remaining, baseGap);
            case SPACE_BETWEEN -> new Spacing(0, count > 1 ? baseGap + remaining / (count - 1) : baseGap);
            case SPACE_AROUND -> {
                double unit = remaining / count;
                yield new Spacing(unit / 2.0, baseGap + unit);
            }
            case SPACE_EVENLY -> {
                double unit = remaining / (count + 1);
                yield new Spacing(unit, baseGap + unit);
            }
        };
    }

    private static void ensureRow(List<Double> rows, int row) {
        while (rows.size() <= row) {
            rows.add(0.0);
        }
    }

    private static final class LinearItem {
        private final UiElement element;
        private double mainSize;

        private LinearItem(UiElement element, double mainSize) {
            this.element = element;
            this.mainSize = mainSize;
        }
    }

    private record GridItem(
        UiElement element,
        int row,
        int column,
        int span,
        double width,
        double height
    ) {
    }

    private record Spacing(double leading, double gap) {
    }
}
