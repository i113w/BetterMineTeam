package com.i113w.better_mine_team.command;

import com.i113w.better_mine_team.BetterMineTeam;
import com.i113w.better_mine_team.common.network.OpenTeamGuiPayload;
import com.i113w.better_mine_team.common.team.TeamDataStorage;
import com.i113w.better_mine_team.common.team.TeamPermissions;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.TeamArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@EventBusSubscriber(modid = BetterMineTeam.MODID)
public class ManageTeamCommand {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("mngteam")
                        .requires(source -> true)

                        // --- Menu ---
                        .then(Commands.literal("menu")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    PacketDistributor.sendToPlayer(player, new OpenTeamGuiPayload());
                                    return 1;
                                })
                        )

                        // --- Set (Requires OP Level 4) ---
                        .then(Commands.literal("set")
                                .requires(source -> source.hasPermission(4))

                                // 1. Set Captain
                                .then(Commands.literal("captain")
                                        .then(Commands.argument("target", EntityArgument.player())
                                                // 情况A: 不指定队伍 -> 设为当前队伍队长
                                                .executes(context -> executeSetCaptain(
                                                        context,
                                                        EntityArgument.getPlayer(context, "target"),
                                                        null
                                                ))
                                                // 情况B: 指定队伍 -> 设为该队伍队长
                                                .then(Commands.argument("team", TeamArgument.team())
                                                        .executes(context -> executeSetCaptain(
                                                                context,
                                                                EntityArgument.getPlayer(context, "target"),
                                                                TeamArgument.getTeam(context, "team")
                                                        ))
                                                )
                                        )
                                )

                                // 2. Set TeamsLord
                                .then(Commands.literal("teamslord")
                                        .executes(context -> executeSetTeamsLord(
                                                context,
                                                context.getSource().getPlayerOrException(),
                                                true
                                        ))
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .executes(context -> executeSetTeamsLord(
                                                        context,
                                                        EntityArgument.getPlayer(context, "target"),
                                                        true
                                                ))
                                                .then(Commands.argument("active", BoolArgumentType.bool())
                                                        .executes(context -> executeSetTeamsLord(
                                                                context,
                                                                EntityArgument.getPlayer(context, "target"),
                                                                BoolArgumentType.getBool(context, "active")
                                                        ))
                                                )
                                        )
                                )
                        )

                        // --- Get ---
                        .then(Commands.literal("get")
                                .executes(context -> {
                                    Entity entity = context.getSource().getEntity();
                                    if (entity instanceof ServerPlayer player) {
                                        return executeGet(context, player, true, false);
                                    } else {
                                        context.getSource().sendFailure(Component.translatable("message.better_mine_team.error.not_player"));
                                        return 0;
                                    }
                                })
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(context -> executeGet(context, EntityArgument.getPlayer(context, "target"), true, false))
                                        .then(Commands.argument("showMembers", BoolArgumentType.bool())
                                                .executes(context -> executeGet(context, EntityArgument.getPlayer(context, "target"), BoolArgumentType.getBool(context, "showMembers"), false))
                                                .then(Commands.argument("showAllEntities", BoolArgumentType.bool())
                                                        .executes(context -> executeGet(context, EntityArgument.getPlayer(context, "target"), BoolArgumentType.getBool(context, "showMembers"), BoolArgumentType.getBool(context, "showAllEntities")))
                                                )
                                        )
                                )
                        )
        );
    }

    // --- 逻辑实现: Set Captain ---
    private static int executeSetCaptain(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer, PlayerTeam specificTeam) {
        PlayerTeam teamToSet;

        if (specificTeam != null) {
            teamToSet = specificTeam;
        } else {
            Scoreboard scoreboard = context.getSource().getServer().getScoreboard();
            teamToSet = scoreboard.getPlayersTeam(targetPlayer.getScoreboardName());

            if (teamToSet == null) {
                context.getSource().sendFailure(
                        Component.translatable("message.better_mine_team.error.no_team_specified", targetPlayer.getDisplayName())
                );
                BetterMineTeam.debug("CMD: SetCaptain Failed: Player {} is not in any team.", targetPlayer.getName().getString());
                return 0;
            }
        }

        BetterMineTeam.debug("CMD: Executing SetCaptain: Player={}, Team={}, UUID={}",
                targetPlayer.getName().getString(), teamToSet.getName(), targetPlayer.getUUID());

        // 设置数据 (使用 Overworld 存储)
        TeamDataStorage storage = TeamDataStorage.get(context.getSource().getServer().overworld());
        storage.setCaptain(teamToSet.getName(), targetPlayer.getUUID());

        Component teamNameComp = teamToSet.getDisplayName().copy().withStyle(teamToSet.getColor());
        context.getSource().sendSuccess(() ->
                        Component.translatable("message.better_mine_team.captain_set", targetPlayer.getDisplayName(), teamNameComp),
                true
        );

        return 1;
    }

    // --- 逻辑实现: Set TeamsLord ---
    private static int executeSetTeamsLord(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer, boolean active) {
        TeamPermissions.setOverridePermission(targetPlayer, active);
        if (active) {
            context.getSource().sendSuccess(() ->
                            Component.translatable("message.better_mine_team.teamslord.granted", targetPlayer.getDisplayName()).withStyle(ChatFormatting.GOLD),
                    true
            );
        } else {
            context.getSource().sendSuccess(() ->
                            Component.translatable("message.better_mine_team.teamslord.revoked", targetPlayer.getDisplayName()).withStyle(ChatFormatting.GRAY),
                    true
            );
        }
        return 1;
    }

    // --- 逻辑实现: Get ---
    private static int executeGet(CommandContext<CommandSourceStack> context, ServerPlayer targetPlayer, boolean showMembers, boolean showAllEntities) {
        Scoreboard scoreboard = context.getSource().getServer().getScoreboard();
        PlayerTeam team = scoreboard.getPlayersTeam(targetPlayer.getScoreboardName());

        if (team == null) {
            context.getSource().sendFailure(
                    Component.translatable("message.better_mine_team.no_team", targetPlayer.getDisplayName())
            );
            return 0;
        }

        Component teamName = team.getDisplayName().copy().withStyle(team.getColor());
        int memberCount = team.getPlayers().size(); // 获取队员数
        context.getSource().sendSuccess(() ->
                        Component.translatable("message.better_mine_team.in_team",
                                        targetPlayer.getDisplayName(),
                                        teamName)
                                .append(Component.literal(" [" + memberCount + " members]").withStyle(ChatFormatting.GRAY)), // 追加显示
                false
        );

        // 队长信息 (使用 Overworld 存储)
        TeamDataStorage storage = TeamDataStorage.get(context.getSource().getServer().overworld());
        UUID captainUUID = storage.getCaptain(team.getName());

        if (captainUUID != null) {
            String captainName = resolvePlayerName(context.getSource().getServer(), captainUUID);
            context.getSource().sendSuccess(() ->
                            Component.translatable("message.better_mine_team.captain_info", captainName).withStyle(ChatFormatting.GOLD),
                    false
            );
        } else {
            context.getSource().sendSuccess(() ->
                            Component.literal("Team Captain: [None]").withStyle(ChatFormatting.GRAY),
                    false
            );
        }

        if (!showMembers) return 1;

        Collection<String> members = team.getPlayers();
        List<String> displayMembers = new ArrayList<>();

        for (String memberName : members) {
            if (memberName.equals(targetPlayer.getScoreboardName())) continue;
            if (showAllEntities || !UUID_PATTERN.matcher(memberName).matches()) {
                displayMembers.add(memberName);
            }
        }

        if (displayMembers.isEmpty()) {
            context.getSource().sendSuccess(() ->
                            Component.translatable("message.better_mine_team.no_other_members").withStyle(ChatFormatting.GRAY),
                    false
            );
        } else {
            String memberListStr = String.join(", ", displayMembers);
            context.getSource().sendSuccess(() ->
                            Component.translatable("message.better_mine_team.other_members", memberListStr).withStyle(ChatFormatting.GRAY),
                    false
            );
        }

        return 1;
    }

    private static String resolvePlayerName(MinecraftServer server, UUID uuid) {
        String name = uuid.toString();
        GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            Optional<GameProfile> profile = cache.get(uuid);
            if (profile.isPresent()) {
                name = profile.get().getName();
            }
        }
        return name;
    }
}