package org.iansaididontcare.etymonica.item.custom;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.iansaididontcare.etymonica.registry.experience_tree.ExperienceTreeData;

import java.util.List;
import java.util.function.Consumer;

public class ExperienceTreeItem extends Item {
    private static final String KEY_EXPERIENCE = "Experience";

    public ExperienceTreeItem(Properties properties) {
        super(properties);
    }

    public static int getExperience(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            return tag.getIntOr(KEY_EXPERIENCE, 0);
        }
        return 0;
    }

    public static void setExperience(ItemStack stack, int experience) {
        CompoundTag tag = new CompoundTag();
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            tag = data.copyTag();
        }
        tag.putInt(KEY_EXPERIENCE, experience);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        
        // Update CustomModelData for the new model system in 1.21.4
        updateModelData(stack, experience);
    }

    private static void updateModelData(ItemStack stack, int experience) {
        int age = getAge(experience);
        String stage = getStage(age);
        
        // Store both:
        // floats[0] = age (for numeric dispatch)
        // strings[0] = stage name (for select dispatch)
        CustomModelData modelData = new CustomModelData(List.of((float) age), List.of(), List.of(stage), List.of());
        stack.set(DataComponents.CUSTOM_MODEL_DATA, modelData);
    }

    public static int getAge(int totalXP) {
        if (totalXP <= 0) return 0;
        
        // Minecraft's total XP to Level formulas
        if (totalXP < 352) {
            return (int) (Math.sqrt(totalXP + 9) - 3);
        } else if (totalXP < 1507) {
            return (int) (8.1 + Math.sqrt(0.4 * (totalXP - 195.975)));
        } else {
            return (int) (18.055555555555557 + Math.sqrt(0.2222222222222222 * (totalXP - 752.9861111111111)));
        }
    }

    public static String getStage(int age) {
        List<ExperienceTreeData.ExperienceTreeStage> stages = ExperienceTreeData.getStages();
        if (stages.isEmpty()) {
            if (age < 1) return "seed";
            if (age < 15) return "sprout";
            if (age < 30) return "sapling";
            if (age < 50) return "young_tree";
            if (age < 100) return "tree";
            return "ancient_tree";
        }

        String lastMatch = stages.get(0).stageName();
        for (ExperienceTreeData.ExperienceTreeStage stage : stages) {
            if (age >= stage.threshold()) {
                lastMatch = stage.stageName();
            } else {
                break;
            }
        }
        return lastMatch;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            if (player.totalExperience > 0) {
                int amount = player.isCrouching() ? player.totalExperience : 1;
                int currentXp = getExperience(stack);
                setExperience(stack, currentXp + amount);
                
                if (player.isCrouching()) {
                    player.giveExperiencePoints(-player.totalExperience);
                } else {
                    player.giveExperiencePoints(-1);
                }
                
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public Component getName(ItemStack stack) {
        int experience = getExperience(stack);
        int age = getAge(experience);
        String stage = getStage(age);
        
        return Component.translatable("item.etymonica.experience_tree." + stage);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            TooltipDisplay tooltipDisplay,
            Consumer<Component> tooltipAdder,
            TooltipFlag flag
    ) {
        int experience = getExperience(stack);
        int age = getAge(experience);

        tooltipAdder.accept(Component.translatable("tooltip.etymonica.experience_tree.experience", experience)
                .withStyle(ChatFormatting.GREEN));
        tooltipAdder.accept(Component.translatable("tooltip.etymonica.experience_tree.age", age)
                .withStyle(ChatFormatting.GOLD));
        
        super.appendHoverText(stack, context, tooltipDisplay, tooltipAdder, flag);
    }
}
