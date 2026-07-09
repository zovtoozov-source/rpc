package tech.onetap.util.commands.defaults;

import net.minecraft.client.MinecraftClient;
import tech.onetap.Onetap;
import tech.onetap.util.chat.ChatUtil;
import tech.onetap.util.commands.api.Command;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.exception.CommandException;
import tech.onetap.util.waypoint.Waypoint;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class WayCommand extends Command {
    public WayCommand() { super("way"); }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (!args.hasAny()) {
            var waypoints = Onetap.getInstance().getWaypointManager().getWaypoints();
            if (waypoints.isEmpty()) {
                ChatUtil.send("Список вейпоинтов пуст");
            } else {
                ChatUtil.send("Вейпоинты (" + waypoints.size() + "):");
                for (Waypoint w : waypoints) {
                    ChatUtil.send("  " + w.getName() + " -> " + (int) w.getX() + ", " + (int) w.getY() + ", " + (int) w.getZ());
                }
            }
            return;
        }

        String sub = args.getString();
        var mgr = Onetap.getInstance().getWaypointManager();

        switch (sub) {
            case "add" -> {
                String name = args.getString();
                var player = MinecraftClient.getInstance().player;
                int x, z, y;
                if (args.hasAny()) {
                    x = (int) Double.parseDouble(args.getString());
                    z = (int) Double.parseDouble(args.getString());
                    y = player != null ? (int) player.getY() : 64;
                } else {
                    x = (int) player.getX();
                    y = (int) player.getY();
                    z = (int) player.getZ();
                }
                mgr.add(new Waypoint(name, x, y, z));
                ChatUtil.send("Вейпоинт \"" + name + "\" добавлен (" + x + ", " + y + ", " + z + ")");
            }
            case "del" -> {
                String name = args.getString();
                if (mgr.remove(name)) {
                    ChatUtil.send("Вейпоинт \"" + name + "\" удалён");
                } else {
                    ChatUtil.send("Вейпоинт \"" + name + "\" не найден");
                }
            }
            case "clear" -> {
                mgr.clear();
                ChatUtil.send("Все вейпоинты удалены");
            }
            case "list" -> {
                var waypoints = mgr.getWaypoints();
                if (waypoints.isEmpty()) {
                    ChatUtil.send("Список вейпоинтов пуст");
                } else {
                    ChatUtil.send("Вейпоинты (" + waypoints.size() + "):");
                    for (Waypoint w : waypoints) {
                        ChatUtil.send("  " + w.getName() + " -> " + (int) w.getX() + ", " + (int) w.getY() + ", " + (int) w.getZ());
                    }
                }
            }
        }
    }

    @Override public String getShortDesc() { return "Управление вейпоинтами"; }
    @Override public List<String> getLongDesc() {
        return Arrays.asList(
                "Управление .way метками",
                "",
                "Использование:",
                ".way add <name> [x] [z] - Добавить вейпоинт (без кордов — текущая позиция)",
                ".way del <name> - Удалить вейпоинт",
                ".way clear - Очистить все",
                ".way list - Показать список"
        );
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.of("add", "del", "clear", "list");
    }
}
