package net.zoogle.levelrpg.client.skilltree;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.zoogle.levelrpg.skilltree.SkillNodeImplementationRegistry;
import net.zoogle.levelrpg.skilltree.SkillNodeStatus;
import net.zoogle.levelrpg.skilltree.SkillTreeNodeDefinition;

import java.util.ArrayList;
import java.util.List;

public final class SkillTreeTooltipRenderer {
    private static final int WIDTH = 188;
    private static final int GAP = 8;
    private static final int PADDING = 7;

    public void render(GuiGraphics graphics, Font font, ResourceLocation skillId, SkillTreeNodeView node, int screenWidth, int screenHeight, SkillTreeCameraTransform transform) {
        SkillTreeNodeDefinition definition = node.definition();
        List<Component> lines = new ArrayList<>();
        if (node.obfuscated()) {
            lines.add(Component.literal("Unknown").withStyle(ChatFormatting.GOLD));
            lines.add(Component.literal("Unknown").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.literal("Reveal this node to identify it.").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.literal(definition.title()).withStyle(ChatFormatting.GOLD));
            lines.add(Component.literal(typeLabel(definition.type())).withStyle(ChatFormatting.BLUE));
        }
        if (!node.obfuscated() && !definition.description().isBlank()) {
            lines.add(Component.literal(definition.description()).withStyle(ChatFormatting.GRAY));
        }
        lines.add(statusText(node.status(), definition).withStyle(statusStyle(node.status())));
        lines.add(Component.literal("Cost: " + definition.cost() + " Insight").withStyle(ChatFormatting.AQUA));
        if (!node.obfuscated() && !SkillNodeImplementationRegistry.isImplemented(skillId, definition)) {
            lines.add(Component.literal("Unimplemented: this node has no gameplay effect yet.").withStyle(ChatFormatting.DARK_RED));
        }

        int contentWidth = WIDTH - PADDING * 2;
        ArrayList<Component> wrapped = new ArrayList<>();
        int textHeight = 0;
        for (Component line : lines) {
            List<net.minecraft.util.FormattedCharSequence> split = font.split(line, contentWidth);
            for (net.minecraft.util.FormattedCharSequence ignored : split) {
                textHeight += font.lineHeight;
            }
            if (line != lines.get(lines.size() - 1)) {
                textHeight += 2;
            }
            wrapped.add(line);
        }

        int boxHeight = textHeight + PADDING * 2;
        int nodeRight = node.left(transform) + node.scaledSize(transform);
        int nodeLeft = node.left(transform);
        int x = nodeRight + GAP;
        if (x + WIDTH > screenWidth - 6) {
            x = nodeLeft - GAP - WIDTH;
        }
        x = Math.max(6, Math.min(x, screenWidth - WIDTH - 6));
        int y = node.centerY(transform) - boxHeight / 2;
        y = Math.max(6, Math.min(y, screenHeight - boxHeight - 6));

        drawMinecraftPanel(graphics, x, y, WIDTH, boxHeight);

        int drawY = y + PADDING;
        for (Component line : wrapped) {
            List<net.minecraft.util.FormattedCharSequence> split = font.split(line, contentWidth);
            int color = line.getStyle().getColor() == null ? 0xFFE8DDC2 : line.getStyle().getColor().getValue();
            for (net.minecraft.util.FormattedCharSequence sequence : split) {
                graphics.drawString(font, sequence, x + PADDING, drawY, color, false);
                drawY += font.lineHeight;
            }
            drawY += 2;
        }
    }

    private static void drawMinecraftPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + 3, y + 3, x + width + 3, y + height + 3, 0x99000000);
        graphics.fill(x, y, x + width, y + height, 0xF021211D);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0xF0393328);
        graphics.fill(x + 4, y + 4, x + width - 4, y + height - 4, 0xF0181816);
        graphics.hLine(x + 1, x + width - 2, y + 1, 0xFF8A7A55);
        graphics.vLine(x + 1, y + 1, y + height - 2, 0xFF8A7A55);
        graphics.hLine(x + 1, x + width - 2, y + height - 2, 0xFF262018);
        graphics.vLine(x + width - 2, y + 1, y + height - 2, 0xFF262018);
    }

    private static MutableComponent statusText(SkillNodeStatus status, SkillTreeNodeDefinition node) {
        return switch (status) {
            case HIDDEN -> Component.literal("Requirement: hidden");
            case LOCKED_LEVEL -> Component.literal("Requires Discipline Level " + node.requiredRank());
            case LOCKED_POINTS -> Component.literal("Requires more Insight");
            case LOCKED_PARENT -> Component.literal("Requires connected parent unlock");
            case AVAILABLE -> Component.literal("Available: click to inscribe");
            case INSCRIBED -> Component.literal("Inscribed");
        };
    }

    private static ChatFormatting statusStyle(SkillNodeStatus status) {
        return switch (status) {
            case AVAILABLE -> ChatFormatting.GREEN;
            case INSCRIBED -> ChatFormatting.YELLOW;
            case LOCKED_POINTS -> ChatFormatting.GOLD;
            case HIDDEN, LOCKED_LEVEL, LOCKED_PARENT -> ChatFormatting.RED;
        };
    }

    private static String typeLabel(String type) {
        return switch (type) {
            case "core" -> "Core";
            case "technique" -> "Technique";
            case "manifestation" -> "Manifestation";
            case "axiom" -> "Axiom";
            default -> "Trait";
        };
    }
}
