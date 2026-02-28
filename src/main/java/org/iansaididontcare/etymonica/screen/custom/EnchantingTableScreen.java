package org.iansaididontcare.etymonica.screen.custom;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.iansaididontcare.etymonica.Etymonica;

public class EnchantingTableScreen extends AbstractContainerScreen<EnchantingTableMenu> {
    private static final Identifier GUI_TEXTURE =
            Identifier.fromNamespaceAndPath(Etymonica.MOD_ID, "textures/gui/enchanting_table/tier0.png");

    public EnchantingTableScreen(EnchantingTableMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    @Override
    protected void init() {
        super.init();

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        addRenderableWidget(Button.builder(Component.literal("Start"), btn -> {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, EnchantingTableMenu.BUTTON_START);
            btn.setFocused(false);
            this.setFocused(null);
        }).bounds(x + 28, y + 60, 50, 15).build());

        // Update button removed: shift-right-click the table now relinks + recomputes stats
    }

    @Override
    protected void renderBg(GuiGraphics gg, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        gg.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        super.render(gg, mouseX, mouseY, partialTick);

        final int x = 6;
        final int y = 6;
        final int line = 10;
        final int textColor = 0xFFFFFFFF;

        gg.drawString(this.font, Component.literal("Power: " + menu.getCurrentPower()), x, y + line * 0, textColor, true);
        gg.drawString(this.font, Component.literal(String.format("Speed: %.3f", menu.getCurrentSpeed())), x, y + line * 1, textColor, true);
        gg.drawString(this.font, Component.literal(String.format("Stability: %.3f", menu.getCurrentStability())), x, y + line * 2, textColor, true);
        gg.drawString(this.font, Component.literal(String.format("Efficiency: %.3f", menu.getCurrentEfficiency())), x, y + line * 3, textColor, true);

        if (menu.isRelinkInProgress()) {
            int total = Math.max(1, menu.getScanTotal());
            int done = Math.min(total, menu.getScanDone());
            int pct = (int) Math.round(done * 100.0 / total);

            gg.drawString(this.font,
                    Component.literal("Relinking: " + pct + "% (" + menu.getScanLinked() + " linked)"),
                    x, y + line * 5, textColor, true);
        }

        renderTooltip(gg, mouseX, mouseY);
    }
}