package com.chillzone.moderation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

import java.net.URI;
import java.util.*;

public final class ChillZoneModeration implements ModInitializer {
    private static ModerationConfig config;
    private static ModerationStore store;
    private static final Map<UUID, FrozenAnchor> FROZEN_ANCHORS = new HashMap<>();

    private record FrozenAnchor(ServerLevel level, double x, double y, double z, float yaw, float pitch) {}

    private record Preset(String id, String display, String duration) {}
    private record HackType(String id, String display, String addedDuration) {}

    // Preset punishments. "hacking" and "other" are handled as special branches below.
    private static final List<Preset> PRESETS = List.of(
        new Preset("xray", "X-Ray", "3d"),
        new Preset("duping", "Duping / Item Exploits", "14d"),
        new Preset("severe_exploiting", "Severe Exploiting", "14d"),
        new Preset("major_griefing", "Major Griefing", "2d"),
        new Preset("minor_griefing", "Minor Griefing", "12h"),
        new Preset("spawn_killing", "Spawn Killing", "5h"),
        new Preset("event_disruption", "Event Disruption", "5d"),
        new Preset("harassment", "Harassment", "1d"),
        new Preset("ban_evasion", "Ban Evasion", "14d")
    );

    // Hacking always starts at 7 days. Every selected hack subtype adds its own time.
    // Current agreed subtype increment is +5 days each.
    private static final List<HackType> HACK_TYPES = List.of(
        new HackType("flying", "Fly Hacks", "5d"),
        new HackType("speed_movement", "Speed / Movement Hacks", "5d"),
        new HackType("combat", "Combat Hacks", "5d"),
        new HackType("phase_noclip", "Phase / NoClip", "5d"),
        new HackType("jesus_mode", "Jesus Mode (Walking on Water)", "5d"),
        new HackType("duping", "Duping", "5d"),
        new HackType("xray", "X-Ray", "5d"),
        new HackType("other", "Other Hack", "5d")
    );

    private static final SuggestionProvider<CommandSourceStack> KNOWN_PLAYERS = (ctx, b) -> {
        for (PlayerResolver.CachedPlayer p : PlayerResolver.knownPlayers(ctx.getSource().getServer())) b.suggest(p.name());
        return b.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> BANNED_PLAYERS = (ctx, b) -> {
        long now = System.currentTimeMillis();
        for (PunishmentRecord r : store.all()) if (r.ban != null && (r.ban.expiresAt == 0 || r.ban.expiresAt > now) && r.lastKnownName != null) b.suggest(r.lastKnownName);
        return b.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> MUTED_PLAYERS = (ctx, b) -> {
        long now = System.currentTimeMillis();
        for (PunishmentRecord r : store.all()) if (r.mute != null && r.mute.expiresAt > now && r.lastKnownName != null) b.suggest(r.lastKnownName);
        return b.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> MUTE_OFFENCES = (ctx, b) -> {
        b.suggest("chat_spam", Component.literal("[12 Hours] Chat / Spam Abuse"));
        b.suggest("other", Component.literal("[Custom: 1 minute to 30 years]"));
        return b.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> OFFENCES = (ctx, b) -> {
        b.suggest("hacking", Component.literal("[7 Days base] + selected hack time"));
        for (Preset p : PRESETS) b.suggest(p.id(), Component.literal("[" + prettyDuration(p.duration()) + "] " + p.display()));
        b.suggest("other", Component.literal("[Custom: 1 minute to 30 years]"));
        return b.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> CONDITIONAL_DETAILS = (ctx, b) -> {
        String offence;
        try { offence = StringArgumentType.getString(ctx, "offence"); } catch (Exception e) { return b.buildFuture(); }
        if (offence.equalsIgnoreCase("hacking")) {
            for (HackType h : HACK_TYPES) b.suggest(h.id(), Component.literal("[+" + prettyDuration(h.addedDuration()) + "] " + h.display()));
            b.suggest("flying,speed_movement");
            b.suggest("flying,speed_movement,phase_noclip");
            b.suggest("flying,speed_movement,jesus_mode");
            b.suggest("flying,speed_movement,phase_noclip,jesus_mode");
            b.suggest("flying,phase_noclip,jesus_mode");
        } else if (offence.equalsIgnoreCase("other")) {
            // Examples only; any custom duration from 1 minute through 30 years is accepted.
            b.suggest("1m", Component.literal("minimum; then type your custom reason"));
            b.suggest("1h", Component.literal("example; then type your custom reason"));
            b.suggest("1d", Component.literal("example; then type your custom reason"));
            b.suggest("1y", Component.literal("example; then type your custom reason"));
        }
        return b.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> BAN_REASONS = (ctx, b) -> {
        for (String s : List.of("Hacking", "Flying / Movement Hacks", "X-Ray", "Combat Hacks", "Jesus Mode (Walking on Water)", "Duping / Item Exploits", "Major Griefing", "Minor Griefing", "Spawn Killing", "Event Disruption", "Harassment", "Ban Evasion", "Severe Exploiting", "Other")) b.suggest(s);
        return b.buildFuture();
    };

    @Override public void onInitialize() {
        config = ModerationConfig.load();
        store = ModerationStore.load();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("warn").requires(s -> Permissions.has(s, Permissions.WARN))
                .then(Commands.argument("player", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                    .then(Commands.argument("reason", StringArgumentType.greedyString()).executes(ctx -> {
                        ServerPlayer staff = ctx.getSource().getPlayerOrException();
                        var target = resolve(ctx.getSource().getServer(), StringArgumentType.getString(ctx,"player"), ctx.getSource()); if (target == null) return 0;
                        String reason = StringArgumentType.getString(ctx,"reason"); var rec = store.getOrCreate(target.uuid(), target.name());
                        var e = new PunishmentRecord.WarningEntry(); e.reason=reason; e.staff=staff.getGameProfile().name(); e.issuedAt=System.currentTimeMillis(); rec.warnings.add(e); store.save();
                        if (target.onlinePlayer()!=null) target.onlinePlayer().sendSystemMessage(Component.literal("You have received a warning. Reason: "+reason));
                        ctx.getSource().sendSuccess(() -> Component.literal("Warned "+target.name()+"."), false); return 1;
                    }))));

            dispatcher.register(Commands.literal("warnings").requires(s -> Permissions.has(s, Permissions.WARNINGS))
                .then(Commands.argument("player", StringArgumentType.word()).suggests(KNOWN_PLAYERS).executes(ctx -> {
                    var target=resolve(ctx.getSource().getServer(),StringArgumentType.getString(ctx,"player"),ctx.getSource()); if(target==null)return 0;
                    var rec=store.get(target.uuid()); if(rec==null||rec.warnings.isEmpty()){ctx.getSource().sendSuccess(()->Component.literal(target.name()+" has no warnings."),false);return 1;}
                    ctx.getSource().sendSuccess(()->Component.literal("Warnings for "+target.name()+":"),false);
                    for(int i=0;i<rec.warnings.size();i++){var w=rec.warnings.get(i);int n=i+1;ctx.getSource().sendSuccess(()->Component.literal("#"+n+" - "+w.reason+" (by "+w.staff+")"),false);} return 1;
                })));

            dispatcher.register(Commands.literal("discord").executes(ctx -> { MutableComponent intro=Component.literal("Join the Chill Zone SMP Discord: "); MutableComponent link=Component.literal(config.discordInvite); try{link=link.withStyle(st->st.withUnderlined(true).withClickEvent(new ClickEvent.OpenUrl(URI.create(config.discordInvite))));}catch(Exception ignored){} MutableComponent msg=intro.append(link);ctx.getSource().sendSuccess(()->msg,false);return 1;}));

            dispatcher.register(Commands.literal("tempban").requires(s -> Permissions.has(s, Permissions.TEMPBAN))
                .then(Commands.argument("player", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                    .then(Commands.argument("offence", StringArgumentType.word()).suggests(OFFENCES)
                        .executes(ctx -> presetTempban(ctx.getSource(), StringArgumentType.getString(ctx,"player"), StringArgumentType.getString(ctx,"offence"), null))
                        .then(Commands.argument("details", StringArgumentType.greedyString()).suggests(CONDITIONAL_DETAILS)
                            .executes(ctx -> tempbanWithDetails(ctx.getSource(), StringArgumentType.getString(ctx,"player"), StringArgumentType.getString(ctx,"offence"), StringArgumentType.getString(ctx,"details")))))));

            dispatcher.register(Commands.literal("mute").requires(src -> Permissions.has(src, Permissions.MUTE))
                .then(Commands.argument("player", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                    .then(Commands.argument("offence", StringArgumentType.word()).suggests(MUTE_OFFENCES)
                        .executes(ctx -> mutePreset(ctx.getSource(), StringArgumentType.getString(ctx,"player"), StringArgumentType.getString(ctx,"offence")))
                        .then(Commands.argument("details", StringArgumentType.greedyString())
                            .executes(ctx -> muteWithDetails(ctx.getSource(), StringArgumentType.getString(ctx,"player"), StringArgumentType.getString(ctx,"offence"), StringArgumentType.getString(ctx,"details")))))));
            dispatcher.register(Commands.literal("unmute").requires(src -> Permissions.has(src, Permissions.MUTE))
                .then(Commands.argument("player", StringArgumentType.word()).suggests(MUTED_PLAYERS).executes(ctx -> unmute(ctx.getSource(), StringArgumentType.getString(ctx,"player")))));

            dispatcher.register(Commands.literal("freeze").requires(src -> Permissions.has(src, Permissions.FREEZE))
                .then(Commands.argument("player", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                    .executes(ctx -> freezePlayer(ctx.getSource(), StringArgumentType.getString(ctx, "player")))));
            dispatcher.register(Commands.literal("unfreeze").requires(src -> Permissions.has(src, Permissions.FREEZE))
                .then(Commands.argument("player", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                    .executes(ctx -> unfreezePlayer(ctx.getSource(), StringArgumentType.getString(ctx, "player")))));

            registerBan(dispatcher, "czban");
            registerBan(dispatcher, "ban");
            registerUnban(dispatcher, "czunban");
            registerUnban(dispatcher, "unban");
            registerUntempban(dispatcher);

            dispatcher.register(Commands.literal("punishments").requires(s -> Permissions.has(s, Permissions.WARNINGS))
                .executes(ctx -> showPunishments(ctx.getSource(), null))
                .then(Commands.argument("player", StringArgumentType.word()).suggests(KNOWN_PLAYERS).executes(ctx -> showPunishments(ctx.getSource(), StringArgumentType.getString(ctx,"player")))));
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer p = handler.getPlayer();
            var rec = store.get(p.getUUID());
            if (rec != null && rec.ban != null) {
                if (rec.ban.expiresAt > 0 && System.currentTimeMillis() >= rec.ban.expiresAt) {
                    rec.ban = null;
                    store.save();
                } else {
                    server.execute(() -> p.connection.disconnect(banMessage(rec)));
                    return;
                }
            }
            if (rec != null && rec.frozen) {
                server.execute(() -> {
                    anchorFrozenPlayer(p);
                    p.sendSystemMessage(Component.literal("You are frozen by staff."));
                });
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> FROZEN_ANCHORS.remove(handler.getPlayer().getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(ChillZoneModeration::enforceFrozenPlayers);

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, boundChatType) -> {
            var rec = store.get(sender.getUUID());
            if (rec == null || rec.mute == null) return true;
            long now = System.currentTimeMillis();
            if (rec.mute.expiresAt <= now) { rec.mute = null; store.save(); return true; }
            sender.sendSystemMessage(Component.literal("You are currently muted. Reason: " + rec.mute.reason + " | Time Remaining: " + TimeParser.formatRemaining(rec.mute.expiresAt - now)));
            return false;
        });
    }

    private static int freezePlayer(CommandSourceStack src, String playerName) {
        var target = resolve(src.getServer(), playerName, src);
        if (target == null) return 0;
        var rec = store.getOrCreate(target.uuid(), target.name());
        rec.frozen = true;
        store.save();
        if (target.onlinePlayer() != null) {
            anchorFrozenPlayer(target.onlinePlayer());
            target.onlinePlayer().setDeltaMovement(0.0, 0.0, 0.0);
            target.onlinePlayer().sendSystemMessage(Component.literal("You have been frozen by staff."));
        }
        src.sendSuccess(() -> Component.literal("Frozen " + target.name() + "."), false);
        return 1;
    }

    private static int unfreezePlayer(CommandSourceStack src, String playerName) {
        var target = resolve(src.getServer(), playerName, src);
        if (target == null) return 0;
        var rec = store.getOrCreate(target.uuid(), target.name());
        rec.frozen = false;
        store.save();
        FROZEN_ANCHORS.remove(target.uuid());
        if (target.onlinePlayer() != null) {
            target.onlinePlayer().sendSystemMessage(Component.literal("You have been unfrozen by staff."));
        }
        src.sendSuccess(() -> Component.literal("Unfrozen " + target.name() + "."), false);
        return 1;
    }

    private static void anchorFrozenPlayer(ServerPlayer player) {
        FROZEN_ANCHORS.put(player.getUUID(), new FrozenAnchor(
            (ServerLevel) player.level(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()
        ));
    }

    private static void enforceFrozenPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PunishmentRecord rec = store.get(player.getUUID());
            if (rec == null || !rec.frozen) {
                FROZEN_ANCHORS.remove(player.getUUID());
                continue;
            }
            FrozenAnchor anchor = FROZEN_ANCHORS.get(player.getUUID());
            if (anchor == null) {
                anchorFrozenPlayer(player);
                anchor = FROZEN_ANCHORS.get(player.getUUID());
            }
            player.setDeltaMovement(0.0, 0.0, 0.0);
            double dx = player.getX() - anchor.x();
            double dy = player.getY() - anchor.y();
            double dz = player.getZ() - anchor.z();
            if (player.level() != anchor.level() || dx * dx + dy * dy + dz * dz > 0.0001) {
                player.teleportTo(
                    anchor.level(), anchor.x(), anchor.y(), anchor.z(),
                    Set.of(), anchor.yaw(), anchor.pitch(), false
                );
            }
        }
    }

    private static void registerBan(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> d,String root){ d.register(Commands.literal(root).requires(s->Permissions.has(s,Permissions.BAN)).then(Commands.argument("player",StringArgumentType.word()).suggests(KNOWN_PLAYERS).then(Commands.argument("reason",StringArgumentType.greedyString()).suggests(BAN_REASONS).executes(ctx->{ServerPlayer staff=ctx.getSource().getPlayerOrException();var t=resolve(ctx.getSource().getServer(),StringArgumentType.getString(ctx,"player"),ctx.getSource());if(t==null)return 0;String reason=StringArgumentType.getString(ctx,"reason");var r=store.getOrCreate(t.uuid(),t.name());var b=new PunishmentRecord.BanEntry();b.reason=reason;b.staff=staff.getGameProfile().name();b.issuedAt=System.currentTimeMillis();b.expiresAt=0;r.ban=b;store.save();if(t.onlinePlayer()!=null)t.onlinePlayer().connection.disconnect(banMessage(r));ctx.getSource().sendSuccess(()->Component.literal("Permanently banned "+t.name()+". Reason: "+reason),false);return 1;})))); }
    private static void registerUnban(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> d,String root){d.register(Commands.literal(root).requires(s->Permissions.has(s,Permissions.UNBAN)).then(Commands.argument("player",StringArgumentType.word()).suggests(BANNED_PLAYERS).executes(ctx->{var t=resolve(ctx.getSource().getServer(),StringArgumentType.getString(ctx,"player"),ctx.getSource());if(t==null)return 0;var r=store.get(t.uuid());if(r!=null){r.ban=null;store.save();}ctx.getSource().sendSuccess(()->Component.literal("Unbanned "+t.name()+"."),false);return 1;})));}
    private static void registerUntempban(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> d){d.register(Commands.literal("untempban").requires(s->Permissions.has(s,Permissions.UNBAN)).then(Commands.argument("player",StringArgumentType.word()).suggests(BANNED_PLAYERS).executes(ctx->{var t=resolve(ctx.getSource().getServer(),StringArgumentType.getString(ctx,"player"),ctx.getSource());if(t==null)return 0;var r=store.get(t.uuid());if(r==null||r.ban==null){ctx.getSource().sendFailure(Component.literal(t.name()+" is not currently banned."));return 0;}if(r.ban.expiresAt==0){ctx.getSource().sendFailure(Component.literal(t.name()+" has a permanent ban. Use /unban instead."));return 0;}r.ban=null;store.save();ctx.getSource().sendSuccess(()->Component.literal("Removed temporary ban for "+t.name()+"."),false);return 1;})));}

    private static int mutePreset(CommandSourceStack src, String player, String offence) {
        if (offence.equalsIgnoreCase("chat_spam")) return issueMute(src, player, TimeParser.parseMillis("12h"), "Chat / Spam Abuse");
        if (offence.equalsIgnoreCase("other")) { src.sendFailure(Component.literal("Use: /mute " + player + " other <duration> <reason>  (1m minimum, 30y maximum)")); return 0; }
        src.sendFailure(Component.literal("Unknown mute offence. Choose chat_spam or other.")); return 0;
    }
    private static int muteWithDetails(CommandSourceStack src, String player, String offence, String details) {
        if (!offence.equalsIgnoreCase("other")) { src.sendFailure(Component.literal("That mute preset is complete after the offence. Extra details are only used with 'other'.")); return 0; }
        int sp = details.indexOf(' ');
        if (sp < 1) { src.sendFailure(Component.literal("Use: /mute " + player + " other <duration> <reason>")); return 0; }
        String dur = details.substring(0, sp).trim(); String reason = details.substring(sp + 1).trim();
        long ms = TimeParser.parseMillis(dur), min = 60_000L, max = TimeParser.parseMillis("30y");
        if (reason.isEmpty()) { src.sendFailure(Component.literal("Please provide a custom mute reason.")); return 0; }
        if (ms < min || ms > max) { src.sendFailure(Component.literal("Custom mutes must be between 1 minute and 30 years.")); return 0; }
        return issueMute(src, player, ms, reason);
    }
    private static int issueMute(CommandSourceStack src, String player, long duration, String reason) {
        ServerPlayer staff; try { staff = src.getPlayerOrException(); } catch (Exception e) { src.sendFailure(Component.literal("This command must be run by a player moderator.")); return 0; }
        var t = resolve(src.getServer(), player, src); if (t == null) return 0;
        var r = store.getOrCreate(t.uuid(), t.name()); var m = new PunishmentRecord.MuteEntry();
        m.reason = reason; m.staff = staff.getGameProfile().name(); m.issuedAt = System.currentTimeMillis(); m.expiresAt = m.issuedAt + duration; r.mute = m; store.save();
        if (t.onlinePlayer() != null) t.onlinePlayer().sendSystemMessage(Component.literal("You have been muted for " + TimeParser.formatDuration(duration) + ". Reason: " + reason));
        src.sendSuccess(() -> Component.literal("Muted " + t.name() + " for " + TimeParser.formatDuration(duration) + ". Reason: " + reason), false); return 1;
    }
    private static int unmute(CommandSourceStack src, String player) {
        var t = resolve(src.getServer(), player, src); if (t == null) return 0; var r = store.get(t.uuid());
        if (r == null || r.mute == null || r.mute.expiresAt <= System.currentTimeMillis()) { if (r != null && r.mute != null) { r.mute = null; store.save(); } src.sendFailure(Component.literal(t.name() + " is not currently muted.")); return 0; }
        r.mute = null; store.save(); if (t.onlinePlayer() != null) t.onlinePlayer().sendSystemMessage(Component.literal("Your mute has been removed."));
        src.sendSuccess(() -> Component.literal("Unmuted " + t.name() + "."), false); return 1;
    }

    private static int presetTempban(CommandSourceStack src,String player,String offence,String details){
        if(offence.equalsIgnoreCase("hacking")) return issueTempban(src,player,"7d","Hacking");
        if(offence.equalsIgnoreCase("other")){src.sendFailure(Component.literal("Custom format: /tempban "+player+" other <duration> <reason>  (1m minimum, 30y maximum)"));return 0;}
        Preset p=PRESETS.stream().filter(x->x.id().equalsIgnoreCase(offence)).findFirst().orElse(null);if(p==null){src.sendFailure(Component.literal("Unknown offence. Choose a suggested offence or 'other'."));return 0;}
        return issueTempban(src,player,p.duration(),p.display());
    }
    private static int tempbanWithDetails(CommandSourceStack src,String player,String offence,String details){
        if(offence.equalsIgnoreCase("hacking")){
            String cleaned=details.trim();
            if(cleaned.isEmpty()) return issueTempban(src,player,"7d","Hacking");
            String[] ids=cleaned.split("[,\\s]+");
            LinkedHashSet<String> unique=new LinkedHashSet<>();
            List<String> names=new ArrayList<>();
            long total=TimeParser.parseMillis("7d");
            for(String raw:ids){
                if(raw.isBlank()) continue;
                String id=raw.toLowerCase(Locale.ROOT);
                if(!unique.add(id)) continue;
                HackType h=HACK_TYPES.stream().filter(x->x.id().equalsIgnoreCase(id)).findFirst().orElse(null);
                if(h==null){src.sendFailure(Component.literal("Unknown hack type: "+raw+". Choose one of the hacking suggestions."));return 0;}
                names.add(h.display()); total += TimeParser.parseMillis(h.addedDuration());
            }
            if(names.isEmpty()) return issueTempban(src,player,"7d","Hacking");
            return issueTempbanMillis(src,player,total,"Hacking - "+String.join(", ",names));
        }
        if(offence.equalsIgnoreCase("other")){
            int sp=details.indexOf(' ');if(sp<1){src.sendFailure(Component.literal("Use: /tempban "+player+" other <duration> <reason>"));return 0;}
            String dur=details.substring(0,sp).trim();String reason=details.substring(sp+1).trim();
            if(reason.isEmpty()){src.sendFailure(Component.literal("Please provide a custom reason."));return 0;}
            long ms=TimeParser.parseMillis(dur);
            long min=60_000L,max=TimeParser.parseMillis("30y");
            if(ms<min||ms>max){src.sendFailure(Component.literal("Custom temp bans must be between 1 minute and 30 years."));return 0;}
            return issueTempbanMillis(src,player,ms,reason);
        }
        src.sendFailure(Component.literal("That preset is complete after the offence. Extra hack/custom details are only available for 'hacking' or 'other'."));
        return 0;
    }
    private static int issueTempban(CommandSourceStack src,String player,String durationText,String reason){
        long duration=TimeParser.parseMillis(durationText);
        if(duration<=0){src.sendFailure(Component.literal("Invalid duration. Examples: 1m, 12h, 2d, 1w, 6mo, 1y."));return 0;}
        return issueTempbanMillis(src,player,duration,reason);
    }
    private static int issueTempbanMillis(CommandSourceStack src,String player,long duration,String reason){
        ServerPlayer staff;try{staff=src.getPlayerOrException();}catch(Exception e){src.sendFailure(Component.literal("This command must be run by a player moderator."));return 0;}
        var t=resolve(src.getServer(),player,src);if(t==null)return 0;
        var r=store.getOrCreate(t.uuid(),t.name());var b=new PunishmentRecord.BanEntry();b.reason=reason;b.staff=staff.getGameProfile().name();b.issuedAt=System.currentTimeMillis();b.expiresAt=b.issuedAt+duration;r.ban=b;store.save();
        if(t.onlinePlayer()!=null)t.onlinePlayer().connection.disconnect(banMessage(r));
        String shown=TimeParser.formatDuration(duration);
        src.sendSuccess(()->Component.literal("Temporarily banned "+t.name()+" for "+shown+". Reason: "+reason),false);return 1;
    }
    private static int showPunishments(CommandSourceStack src,String player){long now=System.currentTimeMillis();if(player!=null){var t=resolve(src.getServer(),player,src);if(t==null)return 0;var r=store.get(t.uuid());showRecord(src,r,t.name(),now);return 1;}int count=0;src.sendSuccess(()->Component.literal("---- Active Punishments ----"),false);for(var r:store.all()){if(r.ban==null)continue;if(r.ban.expiresAt>0&&r.ban.expiresAt<=now){r.ban=null;store.save();continue;}showRecord(src,r,r.lastKnownName==null?r.uuid.toString():r.lastKnownName,now);count++;}if(count==0)src.sendSuccess(()->Component.literal("No active bans or temporary bans."),false);return 1;}
    private static void showRecord(CommandSourceStack src,PunishmentRecord r,String name,long now){if(r==null||r.ban==null){src.sendSuccess(()->Component.literal(name+" has no active ban."),false);return;}var b=r.ban;if(b.expiresAt>0&&b.expiresAt<=now){b=null;r.ban=null;store.save();src.sendSuccess(()->Component.literal(name+" has no active ban."),false);return;}String type=b.expiresAt==0?"PERMANENT BAN":"TEMP BAN";String time=b.expiresAt==0?"Permanent":TimeParser.formatRemaining(b.expiresAt-now);String line=name+" | "+type+" | Reason: "+b.reason+" | Time Left: "+time+" | By: "+b.staff;src.sendSuccess(()->Component.literal(line),false);}
    private static String prettyDuration(String d){if(d==null||d.length()<2)return d;String n=d.substring(0,d.length()-1);return n+switch(Character.toLowerCase(d.charAt(d.length()-1))){case 'm'->" Minutes";case 'h'->" Hours";case 'd'->" Days";case 'w'->" Weeks";default->"";};}
    private static PlayerResolver.ResolvedPlayer resolve(MinecraftServer server,String name,CommandSourceStack src){var t=PlayerResolver.resolve(server,name);if(t==null)src.sendFailure(Component.literal("Player not found. They must have joined Chill Zone SMP at least once."));return t;}
    /**
     * Login-gate blacklist lookup used before a ServerPlayer is created.
     * Active permanent bans return a disconnect message. Active temporary bans do the same.
     * Expired temporary bans are cleared immediately and no longer block login.
     */
    public static Component blacklistMessage(UUID uuid) {
        if (store == null || uuid == null) return null;
        PunishmentRecord rec = store.get(uuid);
        if (rec == null || rec.ban == null) return null;
        long now = System.currentTimeMillis();
        if (rec.ban.expiresAt > 0 && now >= rec.ban.expiresAt) {
            rec.ban = null;
            store.save();
            return null;
        }
        return banMessage(rec);
    }

    private static Component banMessage(PunishmentRecord rec){var ban=rec.ban;boolean temporary=ban.expiresAt>0;MutableComponent root=Component.literal("You are "+(temporary?"temporarily banned":"banned")+" from "+config.serverName+".\n\nReason:\n"+ban.reason+"\n");if(temporary)root.append(Component.literal("\nTime Remaining:\n"+TimeParser.formatRemaining(ban.expiresAt-System.currentTimeMillis())+"\n"));root.append(Component.literal("\nPunished By:\n"+ban.staff+"\n"));root.append(Component.literal("\nAppeal:\n"));MutableComponent discord=Component.literal(config.discordInvite);try{discord=discord.withStyle(st->st.withUnderlined(true).withClickEvent(new ClickEvent.OpenUrl(URI.create(config.discordInvite))));}catch(Exception ignored){}root.append(discord);root.append(Component.literal("\n\n"+config.appealMessage));return root;}
}
