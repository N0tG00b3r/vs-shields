package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.item.FrequencyIDCardItem;
import com.mechanicalskies.vsshields.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Minimal client-only screen for programming a FrequencyIDCard.
 * Opened via Shift+Right-Click on the card item.
 */
public class FrequencyIDCardScreen extends Screen {
    private static final int BG_COLOR     = 0xCC0A0A1A;
    private static final int BORDER_COLOR = 0xFF3A3A8A;

    private EditBox codeBox;
    private final ItemStack cardStack;
    private final InteractionHand hand;

    private FrequencyIDCardScreen(ItemStack cardStack, InteractionHand hand) {
        super(Component.translatable("gui.vs_shields.frequency_id_card.title"));
        this.cardStack = cardStack;
        this.hand = hand;
    }

    public static void open(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        InteractionHand hand = mc.player.getMainHandItem().equals(stack)
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        mc.setScreen(new FrequencyIDCardScreen(stack, hand));
    }

    @Override
    protected void init() {
        super.init();
        int midX = this.width / 2;
        int midY = this.height / 2;

        codeBox = new EditBox(font, midX - 50, midY - 10, 100, 18,
                Component.translatable("gui.vs_shields.frequency_id_card.code_label"));
        codeBox.setMaxLength(8);
        codeBox.setValue(FrequencyIDCardItem.getCode(cardStack));
        codeBox.setFilter(s -> s.matches("[a-zA-Z0-9]*"));
        codeBox.setFocused(true);
        addRenderableWidget(codeBox);

        // Save
        addRenderableWidget(Button.builder(
                Component.translatable("gui.vs_shields.frequency_id_card.save"),
                btn -> saveAndClose())
                .bounds(midX - 52, midY + 12, 50, 18)
                .build());

        // Cancel
        addRenderableWidget(Button.builder(
                Component.translatable("gui.vs_shields.frequency_id_card.cancel"),
                btn -> this.onClose())
                .bounds(midX + 2, midY + 12, 50, 18)
                .build());
    }

    private void saveAndClose() {
        String code = codeBox.getValue().replaceAll("[^a-zA-Z0-9]", "");
        if (code.length() > 8) code = code.substring(0, 8);
        ModNetwork.sendCardProgramToServer(hand, code);
        this.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter
            saveAndClose();
            return true;
        }
        if (keyCode == 256) { // Escape
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int midX = this.width / 2;
        int midY = this.height / 2;
        int boxW = 120;
        int boxH = 60;
        g.fill(midX - boxW/2, midY - boxH/2 - 10, midX + boxW/2, midY + boxH/2 + 10, BG_COLOR);
        g.renderOutline(midX - boxW/2, midY - boxH/2 - 10, boxW, boxH + 20, BORDER_COLOR);
        g.drawCenteredString(font, title, midX, midY - boxH/2 - 4, 0xFFCCCCFF);
        g.drawString(font,
                Component.translatable("gui.vs_shields.frequency_id_card.code_label"),
                midX - 50, midY - 22, 0xFF88AAFF, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
