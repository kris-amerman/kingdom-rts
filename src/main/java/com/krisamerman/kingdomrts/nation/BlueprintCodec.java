package com.krisamerman.kingdomrts.nation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.krisamerman.kingdomrts.building.Blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

// How a building's blueprint is sent to a client for its preview. Only the blocks to draw are sent:
// air (spots the building keeps clear) is left out, since there's nothing to show there.
//
// Compact, so the biggest allowed building (Blueprint.MAX_BLOCKS) fits easily in one packet: a
// palette of the block states used, by their network ids (the numbers vanilla also uses for blocks
// in chunk packets), then each block as its position inside the template's box, packed into one
// number (a long, so even a huge template's box can't overflow it), and its palette index.
public final class BlueprintCodec {
    public static final StreamCodec<FriendlyByteBuf, Blueprint> STREAM_CODEC = StreamCodec.of(BlueprintCodec::write, BlueprintCodec::read);

    private BlueprintCodec() {
    }

    // The blueprint without its air: what a client is sent.
    public static Blueprint withoutAir(Blueprint blueprint) {
        return new Blueprint(blueprint.size(), blueprint.parts().stream().filter(part -> !part.state().isAir()).toList());
    }

    private static void write(FriendlyByteBuf buf, Blueprint blueprint) {
        Vec3i size = blueprint.size();
        buf.writeVarInt(size.getX());
        buf.writeVarInt(size.getY());
        buf.writeVarInt(size.getZ());
        Map<BlockState, Integer> palette = new HashMap<>();
        List<BlockState> states = new ArrayList<>();
        List<Blueprint.Part> parts = withoutAir(blueprint).parts();
        for (Blueprint.Part part : parts) {
            palette.computeIfAbsent(part.state(), state -> {
                states.add(state);
                return states.size() - 1;
            });
        }
        buf.writeCollection(states, (b, state) -> b.writeVarInt(Block.getId(state)));
        buf.writeVarInt(parts.size());
        for (Blueprint.Part part : parts) {
            BlockPos pos = part.pos();
            buf.writeVarLong(((long) pos.getY() * size.getZ() + pos.getZ()) * size.getX() + pos.getX());
            buf.writeVarInt(palette.get(part.state()));
        }
    }

    private static Blueprint read(FriendlyByteBuf buf) {
        Vec3i size = new Vec3i(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        List<BlockState> states = buf.readList(b -> Block.stateById(b.readVarInt()));
        int count = buf.readVarInt();
        if (count < 0 || count > Blueprint.MAX_BLOCKS || size.getX() <= 0 || size.getZ() <= 0) {
            throw new IllegalArgumentException("Bad blueprint: " + count + " blocks in " + size);
        }
        List<Blueprint.Part> parts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long index = buf.readVarLong();
            int x = (int) (index % size.getX());
            int z = (int) (index / size.getX() % size.getZ());
            int y = (int) (index / size.getX() / size.getZ());
            parts.add(new Blueprint.Part(new BlockPos(x, y, z), states.get(buf.readVarInt())));
        }
        return new Blueprint(size, parts);
    }
}
