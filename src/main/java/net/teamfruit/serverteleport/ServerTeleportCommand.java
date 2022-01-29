package net.teamfruit.serverteleport;

import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ServerTeleportCommand implements SimpleCommand {
  private final ProxyServer server;

  private final String langPrefix;
  private final String langUsage;
  private final String langNoServer;
  private final String langNoPermission;
  private final String langPlayerNum;
  private final String langPlayerName;
  private final String langSuccess;

  public ServerTeleportCommand(ProxyServer server, Toml toml) {
    this.server = server;

    // Localization
    Toml lang = toml.getTable("lang");
    this.langPrefix = lang.getString("prefix");
    this.langUsage = lang.getString("usage");
    this.langNoServer = lang.getString("noserver");
    this.langNoPermission = lang.getString("nopermission");
    this.langPlayerNum = lang.getString("player-num");
    this.langPlayerName = lang.getString("player-name");
    this.langSuccess = lang.getString("success");
  }


  @Override
  public void execute(final Invocation invocation) {
    CommandSource player = invocation.source();
    String[] args = invocation.arguments();

    // Permission Validation
    if (!player.hasPermission("servertp")) {
      player.sendMessage(Component.text(langPrefix + " " + langNoPermission));
      return;
    }

    // Argument Validation
    if (args.length < 2) {
      player.sendMessage(Component.text(langPrefix + " " + langUsage));
      return;
    }
    String srcArg = args[0];
    String dstArg = args[1];

    Optional<RegisteredServer> registeredServer = this.validateDestServer(player, dstArg);
    if (!registeredServer.isPresent()) {
      return;
    }
    List<Player> targetPlayers = this.getPlayerList(srcArg, registeredServer);

    this.sendRedirectNotice(player, dstArg, targetPlayers);

    // Run Redirect
    targetPlayers.forEach(p -> p.createConnectionRequest(registeredServer.get()).fireAndForget());
  }

  private Optional<RegisteredServer> validateDestServer(CommandSource player, String dstArg) {
    Optional<RegisteredServer> dstOptional;
    if (dstArg.startsWith("#")) {
      dstOptional = this.server.getServer(dstArg.substring(1));
    } else {
      dstOptional = this.server.getPlayer(dstArg).flatMap(Player::getCurrentServer).map(ServerConnection::getServer);
    }
    if (!dstOptional.isPresent()) {
      player.sendMessage(Component.text(
        langPrefix + " " + langNoServer
      ));
      return Optional.empty();
    }
    return dstOptional;
  }

  private List<Player> getPlayerList(String srcArg, Optional<RegisteredServer> dstOptional) {
    Collection<Player> players;
    if (srcArg.startsWith("#")) {
      players = this.server.getServer(srcArg.substring(1)).map(RegisteredServer::getPlayersConnected).orElseGet(Collections::emptyList);
    } else if ("@a".equals(srcArg)) {
      players = this.server.getAllPlayers();
    } else {
      players = this.server.getPlayer(srcArg).map(Arrays::asList).orElseGet(Collections::emptyList);
    }
    return players
      .stream()
      .filter(p -> !dstOptional.equals(p.getCurrentServer().map(ServerConnection::getServer)))
      .collect(Collectors.toList());
  }

  private void sendRedirectNotice(CommandSource player, String dstArg, List<Player> targetPlayers) {
    player.sendMessage(Component.text(
      langPrefix
        + " "
        + String.format(langSuccess,
        dstArg,
        targetPlayers.size() == 1
          ? String.format(langPlayerName, targetPlayers.get(0).getUsername())
          : String.format(langPlayerNum, targetPlayers.size())
      )
    ));
  }


  private List<String> candidate(String arg, List<String> candidates) {
    if (arg.isEmpty())
      return candidates;
    if (candidates.contains(arg))
      return Arrays.asList(arg);
    return candidates.stream().filter(e -> e.startsWith(arg)).collect(Collectors.toList());
  }

  @Override
  public List<String> suggest(final Invocation invocation) {
    CommandSource player = invocation.source();
    String[] args = invocation.arguments();

    // Permission Validation
    if (!player.hasPermission("servertp"))
      return Collections.emptyList();

    // Source Suggestion
    if (args.length == 1)
      return candidate(args[0],
        Stream.of(
            Stream.of("@a"),
            this.server.getAllServers().stream().map(RegisteredServer::getServerInfo)
              .map(ServerInfo::getName).map(e -> "#" + e),
            this.server.getAllPlayers().stream().map(Player::getUsername)
          )
          .flatMap(Function.identity())
          .collect(Collectors.toList())
      );

    // Destination Suggestion
    if (args.length == 2)
      return candidate(args[1],
        Stream.of(
            this.server.getAllServers().stream().map(RegisteredServer::getServerInfo)
              .map(ServerInfo::getName).map(e -> "#" + e),
            this.server.getAllPlayers().stream().map(Player::getUsername)
          )
          .flatMap(Function.identity())
          .collect(Collectors.toList())
      );

    return Collections.emptyList();
  }
}
