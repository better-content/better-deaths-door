package com.bettercontent.betterdeathsdoor.records;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LifeRecordsDataTest {
    @Test void restartPreservesCurrentLifeBestsReachedIdsAndBoundedLatest() {
        UUID player = UUID.randomUUID(); LifeRecordsData data = new LifeRecordsData();
        LifeRecord r = data.getOrCreate(player, false); r.level(30); r.add(LifeMetric.HP_LOST, 123); LifeRecap first = r.freeze();
        r.nextLife(); r.add(LifeMetric.MOB_KILLS, 7); UUID current = r.lifeId();
        LifeRecordsData loaded = LifeRecordsData.load(data.save(new CompoundTag()));
        LifeRecord restored = loaded.find(player);
        assertEquals(current, restored.lifeId()); assertEquals(first, restored.latest()); assertEquals(3, restored.reachedTiers[1]);
        LifeRecap next = restored.freeze(); assertEquals(7, next.value(LifeMetric.MOB_KILLS)); assertEquals(30, next.previousBests().get(1));
        CompoundTag saved = loaded.save(new CompoundTag());
        assertEquals(1, saved.getList("players", Tag.TAG_COMPOUND).size());
        assertFalse(saved.toString().contains("history"));
    }
    @Test void milestonesPersistSeparatelyAndNeverAwardAgain() {
        LifeRecord r = new LifeRecord(false); r.level(10); r.freeze(); assertEquals(1, r.reachedTiers[1]);
        r.nextLife(); r.level(10); LifeRecap repeated = r.freeze();
        assertFalse(repeated.cards().stream().anyMatch(c -> c.badges().contains(LifeRecap.Badge.MILESTONE)));
        r.nextLife(); r.level(30); LifeRecap higher = r.freeze();
        assertEquals(3, higher.cards().get(0).milestoneTier()); assertEquals(3, r.reachedTiers[1]);
        LifeRecord partial = new LifeRecord(true); partial.level(30); partial.freeze(); assertEquals(0, partial.reachedTiers[1]);
    }
    @Test void unknownSchemaAndNanResetOnlyRecords() {
        UUID player = UUID.randomUUID(); LifeRecordsData data = new LifeRecordsData(); data.getOrCreate(player, false).level(10);
        CompoundTag saved = data.save(new CompoundTag()); saved.putInt("schema", 999);
        assertNull(LifeRecordsData.load(saved).find(player));
        saved = data.save(new CompoundTag());
        CompoundTag entry = saved.getList("players", Tag.TAG_COMPOUND).getCompound(0);
        ListTag values = entry.getList("values", Tag.TAG_DOUBLE); values.set(0, DoubleTag.valueOf(Double.NaN));
        assertNull(LifeRecordsData.load(saved).find(player));
        assertNotNull(data.find(player), "Reset must not mutate source/native player/body state");
    }
    @Test void frozenRecapRejectsOversizeAndBadTier() {
        LifeRecord r = new LifeRecord(false); r.level(10); CompoundTag recap = LifeRecordsData.writeRecap(r.freeze());
        ListTag cards = recap.getList("cards", Tag.TAG_COMPOUND); CompoundTag card = cards.getCompound(0); card.putInt("tier", 4);
        assertThrows(IllegalArgumentException.class, () -> LifeRecordsData.readRecap(recap));
        card.putInt("tier", 1); cards.add(card.copy()); cards.add(card.copy()); cards.add(card.copy());
        assertThrows(IllegalArgumentException.class, () -> LifeRecordsData.readRecap(recap));
    }
}
