package lol.hub.autosort;

import lol.hub.testutils.Area;
import lol.hub.testutils.Bot;
import lol.hub.testutils.Container;
import lol.hub.testutils.Pos;
import lol.hub.testutils.TestServer;
import lol.hub.testutils.TestServerExtension;
import lol.hub.testutils.Waiting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(TestServerExtension.class)
class SortTest {

    // Out of AutoSort's order, with dirt split over two stacks.
    private static final String ITEMS = "[{Slot:0b,id:\"minecraft:dirt\",count:10},"
            + "{Slot:3b,id:\"minecraft:cobblestone\",count:5},"
            + "{Slot:7b,id:\"minecraft:stick\",count:4},"
            + "{Slot:12b,id:\"minecraft:diamond\",count:2},"
            + "{Slot:20b,id:\"minecraft:dirt\",count:20}]";

    // Sorted maps, so a failure prints both sides in slot order.
    private static final Map<Integer, String> PLACED = new TreeMap<>(Map.of(
            0, "minecraft:dirt x10",
            3, "minecraft:cobblestone x5",
            7, "minecraft:stick x4",
            12, "minecraft:diamond x2",
            20, "minecraft:dirt x20"));

    // AutoSort orders by Registry.MATERIAL, where diamond and stick come before cobblestone and dirt.
    private static final Map<Integer, String> SORTED = new TreeMap<>(Map.of(
            0, "minecraft:diamond x2",
            1, "minecraft:stick x4",
            2, "minecraft:cobblestone x5",
            3, "minecraft:dirt x30"));

    private static final Map<Integer, String> SORTED_WITH_DIAMOND = new TreeMap<>(Map.of(
            0, "minecraft:diamond x3",
            1, "minecraft:stick x4",
            2, "minecraft:cobblestone x5",
            3, "minecraft:dirt x30"));

    // Swords don't stack, so their order survives the merge. Placed out of order, and not reversed,
    // so a sort that keeps the placed order fails too.
    private static final String SWORDS = "[{Slot:0b,id:\"minecraft:iron_sword\",count:1,components:{\"minecraft:damage\":3}},"
            + "{Slot:4b,id:\"minecraft:iron_sword\",count:1,components:{\"minecraft:damage\":2}},"
            + "{Slot:8b,id:\"minecraft:iron_sword\",count:1},"
            + "{Slot:13b,id:\"minecraft:iron_sword\",count:1,components:{\"minecraft:damage\":1}}]";

    // AutoSort compares the metadata as text: {"minecraft:damage":1} and so on, {} without any. '}' sorts
    // after '"', so the plain sword comes last. Single-digit damage keeps text and number order the same.
    private static final Map<Integer, String> SWORDS_SORTED = new TreeMap<>(Map.of(
            0, "minecraft:iron_sword damage 1",
            1, "minecraft:iron_sword damage 2",
            2, "minecraft:iron_sword damage 3",
            3, "minecraft:iron_sword damage 0"));

    @Test
    void sortingOffLeavesTheChestAsPlaced(TestServer server, Area area) {
        Pos chest = placeChest(server, area);
        Bot bot = area.bot();

        bot.open(chest);
        bot.close();

        Waiting.holdsFor(Duration.ofSeconds(3), () -> layout(server.container(chest)), PLACED::equals);
    }

    @Test
    void openingSortsAndMergesTheChest(TestServer server, Area area) {
        Pos chest = placeChest(server, area);
        Bot bot = area.bot();
        assertTrue(bot.run("autosort"), "autosort command");

        bot.open(chest);

        assertEquals(SORTED, layout(server.container(chest)));
        bot.close();
    }

    @Test
    void closingSortsWhatChangedWhileOpen(TestServer server, Area area) {
        Pos chest = placeChest(server, area);
        Bot bot = area.bot();
        assertTrue(bot.run("autosort"), "autosort command");
        bot.open(chest);
        server.command("item replace block " + chest + " container.26 with minecraft:diamond 1");
        assertEquals("minecraft:diamond x1", layout(server.container(chest)).get(26), "diamond added while open");

        bot.close();

        assertEquals(SORTED_WITH_DIAMOND, layout(server.container(chest)));
    }

    @Test
    void openingOrdersOneItemTypeByItsMetadata(TestServer server, Area area) {
        Pos chest = area.at(6, 1, 6);
        server.command("setblock " + chest + " chest{Items:" + SWORDS + "}");
        assertEquals(4, server.container(chest).slots().size(), "swords before the bot opens the chest");
        Bot bot = area.bot();
        assertTrue(bot.run("autosort"), "autosort command");

        bot.open(chest);

        Map<Integer, String> swords = new TreeMap<>();
        server.container(chest).slots().forEach((slot, item) -> swords.put(slot, item.id() + " damage " + item.damage()));
        assertEquals(SWORDS_SORTED, swords);
        bot.close();
    }

    private static Pos placeChest(TestServer server, Area area) {
        Pos chest = area.at(6, 1, 6);
        server.command("setblock " + chest + " chest{Items:" + ITEMS + "}");
        assertEquals(PLACED, layout(server.container(chest)), "chest before the bot opens it");
        return chest;
    }

    private static Map<Integer, String> layout(Container container) {
        Map<Integer, String> layout = new TreeMap<>();
        container.slots().forEach((slot, item) -> layout.put(slot, item.id() + " x" + item.count()));
        return layout;
    }
}
