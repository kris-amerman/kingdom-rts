package com.krisamerman.kingdomrts.nation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.world.level.ChunkPos;

class BorderLinesTest {
    private static final double EPSILON = 1e-9;
    private static final BorderLines.Ground FLAT = (x, z) -> 64;

    @Test
    void oneChunkIsBorderedAllRound() {
        List<BorderLines.Edge> edges = BorderLines.edges(Set.of(ChunkPos.asLong(0, 0)));
        assertEquals(4, edges.size());
        assertEquals(64, BorderLines.pieces(Set.of(ChunkPos.asLong(0, 0)), FLAT).size(), "16 pieces a side");
    }

    @Test
    void chunksOfTheSameClaimHaveNoBorderBetweenThem() {
        // Three by three: only the outside, 3 chunks a side.
        Set<Long> square = square(-1, -1, 1, 1);
        List<BorderLines.Edge> edges = BorderLines.edges(square);
        assertEquals(12, edges.size());
        assertTrue(edges.stream().noneMatch(edge -> edge.chunkX() == 0 && edge.chunkZ() == 0), "The middle chunk has no border");
    }

    @Test
    void theBorderIsDrawnJustInsideTheClaimAndMeetsAtTheCorners() {
        List<BorderLines.Piece> pieces = BorderLines.pieces(Set.of(ChunkPos.asLong(0, 0)), FLAT);
        // The north side runs along z = 0.1, from x = 0.1 to 15.9: in from both corners.
        List<BorderLines.Piece> north = pieces.stream().filter(piece -> piece.z1() == BorderLines.INSET && piece.z2() == BorderLines.INSET).toList();
        assertEquals(16, north.size());
        assertEquals(BorderLines.INSET, north.stream().mapToDouble(BorderLines.Piece::x1).min().orElseThrow(), EPSILON);
        assertEquals(16 - BorderLines.INSET, north.stream().mapToDouble(BorderLines.Piece::x2).max().orElseThrow(), EPSILON);
        // The west side starts where the north side does.
        assertTrue(pieces.stream().anyMatch(piece -> piece.x1() == BorderLines.INSET && Math.abs(piece.z1() - BorderLines.INSET) < EPSILON));
    }

    @Test
    void aStraightBorderRunsOnAcrossChunksAndAnInsideCornerReachesOut() {
        // An L: (0,0), (1,0) and (0,1). The north side runs straight from chunk 0 into chunk 1.
        Set<Long> l = Set.of(ChunkPos.asLong(0, 0), ChunkPos.asLong(1, 0), ChunkPos.asLong(0, 1));
        List<BorderLines.Piece> pieces = BorderLines.pieces(l, FLAT);
        assertTrue(pieces.stream().anyMatch(piece -> piece.z1() == BorderLines.INSET && piece.x1() == 15 && piece.x2() == 16), "Straight on at x = 16");
        // The inside corner is at (16, 16). Chunk (1,0)'s south side (along z = 15.9) and chunk
        // (0,1)'s east side (along x = 15.9) both reach past it to meet at (15.9, 15.9).
        double inside = 16 - BorderLines.INSET;
        double southStart = pieces.stream().filter(piece -> Math.abs(piece.z1() - inside) < EPSILON && piece.x2() > 16)
                .mapToDouble(BorderLines.Piece::x1).min().orElseThrow();
        double eastStart = pieces.stream().filter(piece -> Math.abs(piece.x1() - inside) < EPSILON && piece.z2() > 16)
                .mapToDouble(BorderLines.Piece::z1).min().orElseThrow();
        assertEquals(inside, southStart, EPSILON);
        assertEquals(inside, eastStart, EPSILON);
        assertEquals(8, BorderLines.edges(l).size(), "An L has eight sides of chunk on its border");
    }

    @Test
    void eachPieceFollowsTheGroundOnTheClaimsSide() {
        // The ground rises one block per block east.
        List<BorderLines.Piece> pieces = BorderLines.pieces(Set.of(ChunkPos.asLong(0, 0)), (x, z) -> 64 + x);
        BorderLines.Piece east = pieces.stream().filter(piece -> piece.x1() == 16 - BorderLines.INSET).findFirst().orElseThrow();
        assertEquals(64 + 15, east.y(), "The east side is on the claim's last column");
        assertTrue(pieces.stream().filter(piece -> piece.z1() == BorderLines.INSET).allMatch(piece -> piece.y() == 64 + (int) Math.floor(piece.x1())));
    }

    @Test
    void eachPieceKnowsTheColumnItFollows() {
        // The ground rises one block per block east and two per block south: each piece's height
        // is its column's.
        BorderLines.Ground slope = (x, z) -> 64 + x + 2 * z;
        Set<Long> l = Set.of(ChunkPos.asLong(0, 0), ChunkPos.asLong(1, 0), ChunkPos.asLong(0, 1));
        for (BorderLines.Piece piece : BorderLines.pieces(l, slope)) {
            assertEquals(slope.heightAt(piece.columnX(), piece.columnZ()), piece.y(), "Piece " + piece);
            assertTrue(l.contains(ChunkPos.asLong(piece.columnX() >> 4, piece.columnZ() >> 4)), "Its column is in the claim: " + piece);
        }
    }

    @Test
    void bordersAreLaidOnAHeightMapClientsHave() {
        assertTrue(BorderLines.GROUND.sendToClient(), BorderLines.GROUND + " is empty on a client");
    }

    private static Set<Long> square(int minX, int minZ, int maxX, int maxZ) {
        Set<Long> chunks = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                chunks.add(ChunkPos.asLong(x, z));
            }
        }
        return chunks;
    }
}
