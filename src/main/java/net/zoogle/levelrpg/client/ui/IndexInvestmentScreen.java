package net.zoogle.levelrpg.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zoogle.levelrpg.client.data.ClientProfileCache;
import net.zoogle.levelrpg.net.payload.IndexDisciplineInvestRequestPayload;
import net.zoogle.levelrpg.net.payload.RequestProfileSyncPayload;
import net.zoogle.levelrpg.profile.ProgressionSkill;
import net.zoogle.levelrpg.profile.SkillState;
import net.zoogle.levelrpg.progression.DisciplineInvestmentCosts;
import net.zoogle.levelrpg.progression.DisciplineInvestmentProgression;
import net.zoogle.levelrpg.progression.SpecializationProgression;

import java.util.ArrayList;
import java.util.List;

/**
 * V1 The Index investment screen.
 *
 * <p>This is intentionally simple: read values from synced client cache and send
 * one invest request per click. Server remains authoritative.
 */
public class IndexInvestmentScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_START_Y = 56;
    private static final int INVEST_BUTTON_WIDTH = 54;
    private static final int INVEST_COST = DisciplineInvestmentCosts.fixedV1CostForNextDisciplineLevel();

    private final List<RowWidgets> rows = new ArrayList<>();

    public IndexInvestmentScreen() {
        super(Component.translatable("screen.levelrpg.index.title"));
    }

    @Override
    protected void init() {
        super.init();
        rows.clear();
        if (minecraft != null && minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(RequestProfileSyncPayload.INSTANCE);
        }

        int left = Math.max(10, (width - 340) / 2);
        int buttonX = left + 280;
        for (int i = 0; i < ProgressionSkill.values().length; i++) {
            ProgressionSkill skill = ProgressionSkill.values()[i];
            int rowY = ROW_START_Y + (i * ROW_HEIGHT);
            Button button = addRenderableWidget(Button.builder(
                    Component.translatable("screen.levelrpg.index.invest"),
                    b -> PacketDistributor.sendToServer(new IndexDisciplineInvestRequestPayload(skill.id()))
            ).bounds(buttonX, rowY - 2, INVEST_BUTTON_WIDTH, 20).build());
            rows.add(new RowWidgets(skill, button));
        }
    }

    @Override
    public void tick() {
        super.tick();
        updateButtonStates();
    }

    private void updateButtonStates() {
        int totalLevel = ClientProfileCache.totalInvestedLevelsAcrossSkills();
        int essence = ClientProfileCache.getEssence();
        for (RowWidgets row : rows) {
            SkillState state = ClientProfileCache.getSkillsView().getOrDefault(row.skillId(), new SkillState());
            int level = Math.max(0, state.level);
            int potentialCap = Math.max(0, state.rank);
            boolean blocked = false;
            if (totalLevel >= DisciplineInvestmentProgression.TOTAL_INVESTED_DISCIPLINE_LEVEL_CAP) {
                blocked = true;
            } else if (level >= potentialCap) {
                blocked = true;
            } else if (essence < INVEST_COST) {
                blocked = true;
            }
            row.button.active = !blocked;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int left = Math.max(10, (width - 340) / 2);
        int top = 20;
        guiGraphics.drawString(font, title, left, top, 0xD9C29A, false);

        int essence = ClientProfileCache.getEssence();
        int totalLevel = ClientProfileCache.totalInvestedLevelsAcrossSkills();
        int globalInsightEarned = SpecializationProgression.gainedInsightForTotalLevels(totalLevel)
                + Math.max(0, ClientProfileCache.getBonusSpecializationPoints());
        int globalInsightInscribed = ClientProfileCache.totalSpecializationSpentAcrossTrees();
        int globalInsightAvailable = Math.max(0, globalInsightEarned - globalInsightInscribed);
        guiGraphics.drawString(
                font,
                Component.translatable("screen.levelrpg.index.essence", essence),
                left,
                top + 14,
                0xFFFFFF,
                false
        );
        guiGraphics.drawString(
                font,
                Component.translatable("screen.levelrpg.index.global_insight_available", globalInsightAvailable),
                left,
                top + 28,
                0xC9E8FF,
                false
        );
        guiGraphics.drawString(
                font,
                Component.translatable("screen.levelrpg.index.global_insight_earned", globalInsightEarned),
                left + 170,
                top + 28,
                0xC9E8FF,
                false
        );
        guiGraphics.drawString(
                font,
                Component.translatable("screen.levelrpg.index.global_insight_inscribed", globalInsightInscribed),
                left,
                top + 40,
                0xC9E8FF,
                false
        );
        guiGraphics.drawString(
                font,
                Component.translatable(
                        "screen.levelrpg.index.total_level",
                        totalLevel,
                        DisciplineInvestmentProgression.TOTAL_INVESTED_DISCIPLINE_LEVEL_CAP
                ),
                left + 170,
                top + 14,
                0xFFFFFF,
                false
        );

        for (int i = 0; i < rows.size(); i++) {
            RowWidgets row = rows.get(i);
            SkillState state = ClientProfileCache.getSkillsView().getOrDefault(row.skillId(), new SkillState());
            int level = Math.max(0, state.level);
            int potentialCap = Math.max(0, state.rank);
            int rowY = ROW_START_Y + (i * ROW_HEIGHT);

            String leftText = row.skill.displayName()
                    + "  "
                    + Component.translatable("screen.levelrpg.index.level_short", level).getString()
                    + "  "
                    + Component.translatable("screen.levelrpg.index.potential_short", potentialCap).getString()
                    + "  "
                    + Component.translatable("screen.levelrpg.index.cost_short", INVEST_COST).getString();
            guiGraphics.drawString(font, leftText, left, rowY + 4, 0xE9E4D3, false);

            Component reason = blockedReason(totalLevel, level, potentialCap, essence);
            if (reason != null) {
                guiGraphics.drawString(font, reason, left + 5, rowY + 16, 0xFFAAAA, false);
            }
        }
    }

    private Component blockedReason(int totalLevel, int level, int potentialCap, int essence) {
        if (totalLevel >= DisciplineInvestmentProgression.TOTAL_INVESTED_DISCIPLINE_LEVEL_CAP) {
            return Component.translatable("screen.levelrpg.index.blocked.character_cap");
        }
        if (level >= potentialCap) {
            return Component.translatable("screen.levelrpg.index.blocked.potential_cap");
        }
        if (essence < INVEST_COST) {
            return Component.translatable("screen.levelrpg.index.blocked.not_enough_essence");
        }
        return null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record RowWidgets(ProgressionSkill skill, Button button) {
        ResourceLocation skillId() {
            return skill.id();
        }
    }
}

