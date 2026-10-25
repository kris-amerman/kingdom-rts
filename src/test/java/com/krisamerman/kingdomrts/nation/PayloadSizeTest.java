package com.krisamerman.kingdomrts.nation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.krisamerman.kingdomrts.building.Blueprint;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

// The nation interface's biggest payloads stay within their size budgets (docs/perf.md, Interface
// budget), so a codec change that bloats packets fails here rather than in a player's game.
class PayloadSizeTest {
    private static final int KB = 1024;
    private static final ResourceLocation SHED = ResourceLocation.parse("kingdomrts:storage_shed");
    private static final List<BlockState> STATES = List.of(Blocks.OAK_PLANKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.OAK_LOG.defaultBlockState(), Blocks.GLASS.defaultBlockState(), Blocks.OAK_STAIRS.defaultBlockState(),
            Blocks.TORCH.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(), Blocks.OAK_SLAB.defaultBlockState());

    // A box of the given size, every block solid (cycling through a few kinds), or air except for one
    // block.
    private static Blueprint box(int side, boolean solid) {
        List<Blueprint.Part> parts = new ArrayList<>();
        int i = 0;
        for (int y = 0; y < side; y++) {
            for (int z = 0; z < side; z++) {
                for (int x = 0; x < side; x++) {
                    BlockState state = solid || i == 0 ? STATES.get(i % STATES.size()) : Blocks.AIR.defaultBlockState();
                    parts.add(new Blueprint.Part(new BlockPos(x, y, z), state));
                    i++;
                }
            }
        }
        return new Blueprint(new Vec3i(side, side, side), parts);
    }

    private static <T> int encodedSize(StreamCodec<FriendlyByteBuf, T> codec, T value) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(buf, value);
        return buf.readableBytes();
    }

    @Test
    void theBiggestAllowedBuildingFitsItsBudget() {
        Blueprint biggest = box(32, true);
        assertTrue(biggest.parts().size() == Blueprint.MAX_BLOCKS, "32^3 is the most blocks a building may have");
        int size = encodedSize(BlueprintCodec.STREAM_CODEC, biggest);
        assertTrue(size <= 160 * KB, "The biggest building's blueprint is " + size / KB + " KB, over its 160 KB budget");
    }

    @Test
    void airIsNotSent() {
        int size = encodedSize(BlueprintCodec.STREAM_CODEC, box(32, false));
        assertTrue(size <= KB, "A building that's all air but one block should be tiny, but is " + size + " bytes");
    }

    @Test
    void theMostUnfinishedBuildingsSentFitTheirBudget() {
        List<NationPayloads.SiteView> sites = new ArrayList<>();
        for (int i = 0; i < NationRequests.MAX_SITES; i++) {
            sites.add(new NationPayloads.SiteView(SHED, new BlockPos(1000 + 6 * i, 64, -2000), Rotation.values()[i % 4]));
        }
        // One kind of building, the size of a small house (6x6x6, solid).
        NationPayloads.ShowSites payload = new NationPayloads.ShowSites(sites, Map.of(SHED, box(6, true)));
        int size = encodedSize(NationPayloads.ShowSites.STREAM_CODEC, payload);
        assertTrue(size <= 64 * KB, "The most unfinished buildings sent at once take " + size / KB + " KB, over their 64 KB budget");
    }

    @Test
    void theMostBordersSentFitTheirBudget() {
        // As many settlements as are sent, sharing out the most chunks, with long names.
        List<NationPayloads.BorderView> borders = new ArrayList<>();
        int perSettlement = BorderViewers.MAX_CHUNKS / BorderViewers.MAX_SETTLEMENTS;
        for (int i = 0; i < BorderViewers.MAX_SETTLEMENTS; i++) {
            long[] chunks = new long[perSettlement];
            for (int c = 0; c < perSettlement; c++) {
                chunks[c] = ChunkPos.asLong(100_000 + i * 8 + c % 8, -100_000 + c / 8);
            }
            borders.add(new NationPayloads.BorderView("Settlement number " + i, "A faction with a long name", 0xFF8800, i == 0, chunks));
        }
        NationPayloads.ShowBorders payload = new NationPayloads.ShowBorders(ResourceLocation.parse("minecraft:overworld"), borders);
        int size = encodedSize(NationPayloads.ShowBorders.STREAM_CODEC, payload);
        assertTrue(size <= 16 * KB, "The most borders sent at once take " + size / KB + " KB, over their 16 KB budget");
    }
}
