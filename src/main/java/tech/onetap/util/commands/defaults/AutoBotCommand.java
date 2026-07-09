package tech.onetap.util.commands.defaults;

import tech.onetap.module.list.misc.AutoAncientBot;
import tech.onetap.util.base.Instance;
import tech.onetap.util.commands.api.Command;
import tech.onetap.util.commands.api.argument.IArgConsumer;
import tech.onetap.util.commands.api.exception.CommandException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class AutoBotCommand extends Command {

    public AutoBotCommand() {
        super("autobot");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMin(2);
        String sub = args.getString();
        String name = args.getString();
        AutoAncientBot bot = Instance.get(AutoAncientBot.class);
        if (sub.equalsIgnoreCase("sklad")) {
            bot.setSkladHome(name);
            logDirect("§a[AutoAncientBot] Sklad home set to: " + name);
        } else if (sub.equalsIgnoreCase("ad")) {
            bot.setAdHome(name);
            logDirect("§a[AutoAncientBot] AD home set to: " + name);
        } else {
            logDirect("§cUnknown subcommand. Use sklad or ad.");
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            return Stream.of("sklad", "ad");
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Configure AutoAncientBot storage homes";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
            "Usage:",
            "  .autobot sklad <name>  - set sklad home name",
            "  .autobot ad <name>    - set AD home name"
        );
    }
}
