package com.krisamerman.kingdomrts.unit;

import java.util.UUID;

import javax.annotation.Nullable;

import com.krisamerman.kingdomrts.faction.FactionData;
import com.krisamerman.kingdomrts.settlement.Settlement;
import com.krisamerman.kingdomrts.settlement.SettlementData;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;

// What the unit info panel shows about a unit, built on the server and sent to the viewer when the
// panel opens. Everyone gets the basics; only members of the unit's own faction get the details
// (and the inventory, see UnitMenu). Because the server leaves the details out for anyone else,
// they never reach that player's client: hiding them only in the screen would leak them to a
// modified client.
public record UnitInfo(String name, String factionName, String occupation, float maxHealth,
        double attackDamage, double attackRange, double movementSpeed, @Nullable Details details) {

    // What only the unit's own faction may see. Settlement is "" if it belongs to none.
    public record Details(String settlementName, @Nullable BlockPos home, @Nullable BlockPos post) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(settlementName);
            buf.writeNullable(home, BlockPos.STREAM_CODEC);
            buf.writeNullable(post, BlockPos.STREAM_CODEC);
        }

        static Details read(FriendlyByteBuf buf) {
            return new Details(buf.readUtf(), buf.readNullable(BlockPos.STREAM_CODEC), buf.readNullable(BlockPos.STREAM_CODEC));
        }
    }

    public enum Access { FULL, LIMITED }

    // Who sees what: the full view only for a member of the faction that owns the unit. Nobody
    // gets it for a factionless unit, since no one owns it. (Allied access is still open.)
    public static Access accessFor(@Nullable UUID viewerFaction, @Nullable UUID unitFaction) {
        return viewerFaction != null && viewerFaction.equals(unitFaction) ? Access.FULL : Access.LIMITED;
    }

    // Server: what a viewer with this access level gets to know about the unit.
    public static UnitInfo of(ServerLevel level, Unit unit, Access access) {
        String factionName = unit.getFactionId() != null ? FactionData.get(level).nameOf(unit.getFactionId()) : "None";
        String occupation = unit.getOccupationId() != null ? unit.getOccupationId().toString() : "None";
        Details details = null;
        if (access == Access.FULL) {
            Settlement settlement = SettlementData.get(level).byId(unit.getSettlementId());
            details = new Details(settlement != null ? settlement.name() : "", unit.getHome(), unit.getPost());
        }
        return new UnitInfo(unit.getName().getString(), factionName, occupation, unit.getMaxHealth(),
                unit.getAttributeValue(Attributes.ATTACK_DAMAGE), unit.getAttackRange(),
                unit.getAttributeValue(Attributes.MOVEMENT_SPEED), details);
    }

    public boolean isFull() {
        return details != null;
    }

    void write(FriendlyByteBuf buf) {
        buf.writeUtf(name);
        buf.writeUtf(factionName);
        buf.writeUtf(occupation);
        buf.writeFloat(maxHealth);
        buf.writeDouble(attackDamage);
        buf.writeDouble(attackRange);
        buf.writeDouble(movementSpeed);
        buf.writeNullable(details, (b, d) -> d.write(b));
    }

    static UnitInfo read(FriendlyByteBuf buf) {
        return new UnitInfo(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readFloat(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readNullable(Details::read));
    }
}
