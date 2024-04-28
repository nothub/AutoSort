package lol.hub.autosort;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.*;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

@SuppressWarnings("unused")
public final class Plugin extends JavaPlugin implements Listener {

    private static final Path configActives = Path.of("plugins", "autosort", "active.txt");

    private final Set<UUID> actives = ConcurrentHashMap.newKeySet();

    private final Map<Material, Integer> materialOrder = new HashMap<>();

    private final Set<InventoryType> inventories = Set.of(
            InventoryType.CHEST,
            InventoryType.BARREL,
            InventoryType.ENDER_CHEST,
            InventoryType.SHULKER_BOX
    );

    private final Comparator<Object> comparator = Comparator
            .comparing(
                // resource namespace
                itemStack -> ((ItemStack) itemStack).getType().getKey().getNamespace())
            .thenComparing(
                // registry order (material)
                itemStack -> materialOrder.getOrDefault(
                        ((ItemStack) itemStack).getType(), 0))
            .thenComparing(
                // resource key
                itemStack -> ((ItemStack) itemStack).getType().getKey().getKey())
            .thenComparing(
                // meta data
                itemStack -> ((ItemStack) itemStack).getItemMeta().getAsString())
            .thenComparing(
                // stack size
                itemStack -> ((ItemStack) itemStack).getAmount()
            );

    private final CommandExecutor toggle = (sender, command, label, args) -> {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is not available for non-players.");
            return true;
        }

        UUID uuid = player.getUniqueId();
        var message = text("Sorting ");
        if (actives.contains(uuid)) {
            actives.remove(uuid);
            message = message.append(text("disabled").color(RED));
        } else {
            actives.add(uuid);
            message = message.append(text("enabled").color(GREEN))
                    .append(text(" for: " + inventories.stream()
                            .map(InventoryType::defaultTitle)
                            .map(comp -> PlainTextComponentSerializer.plainText().serialize(comp))
                            .sorted()
                            .collect(Collectors.joining(", "))));
        }
        player.sendMessage(message);

        saveActives();

        return true;
    };

    @Override
    public void onEnable() {

        // Index material registry order
        int n = 0;
        for (Material material : Registry.MATERIAL) {
            materialOrder.put(material, n++);
        }

        PluginCommand cmd = getCommand("autosort");
        cmd.setExecutor(toggle);
        cmd.setTabCompleter((sender, command, label, args) -> Collections.emptyList());

        loadActives();

        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        actives.clear();
        materialOrder.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent ev) {
        sort(ev.getInventory(), ev.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent ev) {
        sort(ev.getInventory(), ev.getPlayer());
    }

    private void sort(Inventory inventory, HumanEntity player) {
        if (!inventories.contains(inventory.getType())) return;
        if (!actives.contains(player.getUniqueId())) return;

        boolean hasViewers = inventory.getViewers().stream()
                .anyMatch(viewer -> !viewer.getUniqueId().equals(player.getUniqueId()));
        if (hasViewers) return;

        List<ItemStack> items = new ArrayList<>(Arrays.stream(inventory.getContents())
                .filter(Objects::nonNull)
                .filter(itemStack -> !itemStack.isEmpty())
                .toList()
        );

        items.sort(comparator);

        inventory.clear();

        for (ItemStack item : items) {
            // this will also compress/merge stacks
            inventory.addItem(item);
        }
    }

    private void loadActives() {
        try {
            if (configActives.toFile().exists()) {
                var lines = Files.readAllLines(configActives);
                for (String line : lines) {
                    if (line.isBlank()) continue;
                    actives.add(UUID.fromString(line));
                }
            }
        } catch (Exception ex) {
            getLogger().warning(ex.toString());
        }
    }

    private void saveActives() {
        configActives.getParent().toFile().mkdirs();
        try {
            Files.writeString(configActives, actives.stream()
                            .map(UUID::toString)
                            .collect(Collectors.joining(System.lineSeparator())),
                    WRITE, CREATE, TRUNCATE_EXISTING);
        } catch (Exception ex) {
            getLogger().warning(ex.toString());
        }
    }
}
