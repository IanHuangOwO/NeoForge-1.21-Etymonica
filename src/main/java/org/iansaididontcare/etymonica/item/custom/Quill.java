package org.iansaididontcare.etymonica.item.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.iansaididontcare.etymonica.block.entity.AbstractEnchantingTableBlockEntity;
import org.iansaididontcare.etymonica.block.entity.AbstractInfusionAltarBlockEntity;
import org.iansaididontcare.etymonica.registry.enchantment.api.EnchantmentRarity;
import org.iansaididontcare.etymonica.registry.enchanting.EnchantingTableMessages;
import org.iansaididontcare.etymonica.registry.infusion.InfusionAltarMessages;
import org.iansaididontcare.etymonica.tag.ModBlockTags;

public class Quill extends Item {
    public Quill(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        BlockPos pos = ctx.getClickedPos();

        if (player == null) return InteractionResult.PASS;

        // Only on shift-right-click
        if (!player.isCrouching()) return InteractionResult.PASS;

        BlockEntity be = level.getBlockEntity(pos);
        
        // Handle Enchanting Tables
        if (level.getBlockState(pos).is(ModBlockTags.ENCHANTING_TABLES)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (be instanceof AbstractEnchantingTableBlockEntity table) {
                player.displayClientMessage(EnchantingTableMessages.action(table.requestStartEnchanting()), true);
                return InteractionResult.SUCCESS;
            }
        }

        // Handle Infusion Altars
        if (level.getBlockState(pos).is(ModBlockTags.INFUSION_ALTARS)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (be instanceof AbstractInfusionAltarBlockEntity altar) {
                var result = altar.attemptStartInfusion(player);
                player.displayClientMessage(InfusionAltarMessages.action(result), true);
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }
}
