package com.krisamerman.kingdomrts.unit;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

// How long a unit takes to break a block: the same as a player standing on the ground holding the
// same tool (vanilla's Player.getDigSpeed and Block.getDestroyProgress). Each tick a player adds
// speed / hardness / 30 to the block's break progress (/ 100 if the block needs a tool and this
// isn't one); the block breaks when progress reaches 1.
public final class BreakTime {
    // Returned for blocks that can't be broken (bedrock and the like).
    public static final int NEVER = Integer.MAX_VALUE;

    private BreakTime() {
    }

    // Pure: ticks to break a block of this hardness with a tool of this speed (1 = bare hand).
    // canHarvest is false when the block needs the right tool and this isn't it. miningEfficiency
    // (the Efficiency enchantment) only applies to tools faster than a hand; breakSpeed multiplies.
    public static int ticks(float hardness, float toolSpeed, boolean canHarvest, float miningEfficiency, float breakSpeed) {
        if (hardness < 0) {
            return NEVER;
        }
        float speed = toolSpeed;
        if (speed > 1.0f) {
            speed += miningEfficiency;
        }
        speed *= breakSpeed;
        if (hardness == 0) {
            return 1;
        }
        float progressPerTick = speed / hardness / (canHarvest ? 30.0f : 100.0f);
        if (progressPerTick <= 0) {
            return NEVER;
        }
        return Math.max(1, (int) Math.ceil(1.0f / progressPerTick));
    }

    // Ticks for this unit to break the block at pos with tool (ItemStack.EMPTY for a bare hand).
    public static int ticks(LivingEntity breaker, ItemStack tool, BlockState state, BlockGetter level, BlockPos pos) {
        boolean canHarvest = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
        float toolSpeed = tool.isEmpty() ? 1.0f : tool.getDestroySpeed(state);
        return ticks(state.getDestroySpeed(level, pos), toolSpeed, canHarvest,
                (float) breaker.getAttributeValue(Attributes.MINING_EFFICIENCY),
                (float) breaker.getAttributeValue(Attributes.BLOCK_BREAK_SPEED));
    }
}
