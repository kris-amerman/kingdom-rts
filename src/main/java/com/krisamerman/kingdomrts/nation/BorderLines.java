package com.krisamerman.kingdomrts.nation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

// Where a settlement's border runs, for the borders view: the sides of its claimed chunks that face
// a chunk it doesn't claim, cut into one piece per block so each piece can follow the ground.
// Pure, so it's unit tested; the client only adds the ground heights and draws the pieces.
public final class BorderLines {
    // How far inside its own claim a border is drawn, so where two claims touch, both borders show
    // side by side instead of fighting over the same spot.
    public static final double INSET = 0.1;
    // The ground the client lays borders on: the highest block that blocks movement, or water. It
    // must be a height map the server sends to clients: the others (such as the one that leaves out
    // leaves) are empty on a client, which put every border at the bottom of the world (S-036).
    // The client steps down through leaves itself, so borders don't run over treetops.
    public static final Heightmap.Types GROUND = Heightmap.Types.MOTION_BLOCKING;
    // The most leaves the client steps down through in one column.
    public static final int MAX_LEAVES = 32;

    public enum Side {
        NORTH(0, -1), SOUTH(0, 1), WEST(-1, 0), EAST(1, 0);

        final int dx;
        final int dz;

        Side(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }
    }

    // A side of a claimed chunk that faces an unclaimed one (or another settlement's).
    public record Edge(int chunkX, int chunkZ, Side side) {
    }

    // One block's length of border, on the ground at y (the first free block above the ground on
    // the claim's side), from (x1, z1) to (x2, z2) in world coordinates.
    public record Piece(double x1, double z1, double x2, double z2, int y) {
        // The block column the piece lies on, whose ground it follows: the middle of the piece is
        // always over it, as pieces are drawn inside the claim.
        public int columnX() {
            return (int) Math.floor((x1 + x2) / 2.0);
        }

        public int columnZ() {
            return (int) Math.floor((z1 + z2) / 2.0);
        }

        public Piece atHeight(int height) {
            return height == y ? this : new Piece(x1, z1, x2, z2, height);
        }
    }

    // How high the ground is: the first free block above it at a block column.
    @FunctionalInterface
    public interface Ground {
        int heightAt(int x, int z);
    }

    private BorderLines() {
    }

    // The border's edges, for a claim given as packed chunk positions (ChunkPos#toLong).
    public static List<Edge> edges(Set<Long> chunks) {
        List<Edge> edges = new ArrayList<>();
        for (long packed : chunks) {
            int x = ChunkPos.getX(packed);
            int z = ChunkPos.getZ(packed);
            for (Side side : Side.values()) {
                if (!chunks.contains(ChunkPos.asLong(x + side.dx, z + side.dz))) {
                    edges.add(new Edge(x, z, side));
                }
            }
        }
        return edges;
    }

    // The border's pieces, one per block along each edge, drawn INSET inside the claim. At a corner
    // the pieces are cut or stretched so the inset lines meet: an outside corner pulls the line in,
    // and an inside corner (where the border turns back into the claim) pushes it out.
    public static List<Piece> pieces(Set<Long> chunks, Ground ground) {
        List<Piece> pieces = new ArrayList<>();
        for (Edge edge : edges(chunks)) {
            int x0 = edge.chunkX() * 16;
            int z0 = edge.chunkZ() * 16;
            boolean alongX = edge.side() == Side.NORTH || edge.side() == Side.SOUTH;
            // The two sides at the edge's ends: for an edge along x, west and east; along z, north and south.
            Side startSide = alongX ? Side.WEST : Side.NORTH;
            Side endSide = alongX ? Side.EAST : Side.SOUTH;
            double start = cornerShift(chunks, edge, startSide);
            double end = -cornerShift(chunks, edge, endSide);
            for (int i = 0; i < 16; i++) {
                double from = i == 0 ? start : 0.0;
                double to = i == 15 ? 1.0 + end : 1.0;
                if (alongX) {
                    int z = edge.side() == Side.NORTH ? z0 : z0 + 15;
                    double lineZ = edge.side() == Side.NORTH ? z0 + INSET : z0 + 16 - INSET;
                    int x = x0 + i;
                    pieces.add(new Piece(x + from, lineZ, x + to, lineZ, ground.heightAt(x, z)));
                } else {
                    int x = edge.side() == Side.WEST ? x0 : x0 + 15;
                    double lineX = edge.side() == Side.WEST ? x0 + INSET : x0 + 16 - INSET;
                    int z = z0 + i;
                    pieces.add(new Piece(lineX, z + from, lineX, z + to, ground.heightAt(x, z)));
                }
            }
        }
        return pieces;
    }

    // How far to move an edge's end towards its middle (positive) or past its corner (negative) at
    // the corner on endSide: in by INSET at an outside corner, out by INSET at an inside one, and
    // not at all where the border runs straight on into the next chunk.
    private static double cornerShift(Set<Long> chunks, Edge edge, Side endSide) {
        boolean next = chunks.contains(ChunkPos.asLong(edge.chunkX() + endSide.dx, edge.chunkZ() + endSide.dz));
        if (!next) {
            return INSET; // the border turns the corner here, round the outside
        }
        boolean diagonal = chunks.contains(ChunkPos.asLong(edge.chunkX() + endSide.dx + edge.side().dx, edge.chunkZ() + endSide.dz + edge.side().dz));
        return diagonal ? -INSET : 0.0;
    }
}
