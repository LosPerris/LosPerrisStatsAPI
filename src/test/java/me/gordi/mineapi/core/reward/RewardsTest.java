package me.gordi.mineapi.core.reward;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RewardsTest {

    @Test
    void parsesMoneyItemsAndCommands() {
        Rewards.Reward money = Rewards.parse("money: 250.5");
        assertEquals(Rewards.Kind.MONEY, money.kind());
        assertEquals(250.5, money.amount());

        Rewards.Reward item = Rewards.parse("item:diamond:3");
        assertEquals(Rewards.Kind.ITEM, item.kind());
        assertEquals("DIAMOND", item.value());
        assertEquals(3, item.amount());
        assertEquals("IRON_INGOT", Rewards.parse("item:minecraft:iron_ingot:2").value());

        Rewards.Reward cmd = Rewards.parse("/give {player} cake");
        assertEquals(Rewards.Kind.COMMAND, cmd.kind());
        assertEquals("give {player} cake", cmd.value());
        assertEquals("give %player% emerald 2", Rewards.parse("command: /give %player% emerald 2").value());
        assertNull(Rewards.parse("command:"));
    }

    @Test
    void rejectsInvalid() {
        assertNull(Rewards.parse(null));
        assertNull(Rewards.parse("   "));
        assertNull(Rewards.parse("money:-5"));
        assertNull(Rewards.parse("money:abc"));
        assertNull(Rewards.parse("item:not_a_block"));
        assertNull(Rewards.parse("item:air"));
        assertNull(Rewards.parse("item:stone:0"));
    }

    @Test
    void describeHidesCommands() {
        String d = Rewards.describe(List.of(Rewards.parse("money:500"), Rewards.parse("item:golden_apple:2"), Rewards.parse("say hi")));
        assertEquals("$500, 2x golden apple", d);
    }

    @Test
    void describeCanHideMoneyWhenThereIsNoEconomy() {
        List<Rewards.Reward> r = List.of(Rewards.parse("money:500"), Rewards.parse("item:DIAMOND:3"), Rewards.parse("say hi"));
        assertEquals("$500, 3x diamond", Rewards.describe(r));
        assertEquals("3x diamond", Rewards.describe(r, false));
    }
}
