package com.chillzone.moderation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.util.*;

public final class ChillZoneModeration implements ModInitializer {
    private static ModerationConfig config;
    private static ModerationStore store;

    private record Preset(String id, String display, String duration) {}
    private static final List<Preset> PRESETS = List.of(
        new Preset("flying", "Flying / Movement Hacks", "3d"),
        new Preset("xray", "X-Ray", "7d"),
        new Preset("combat", "Combat Hacks", "7d"),
        new Preset("godmode", "God Mode / Invincibility", "7d"),
        new Preset("duping", "Duping / Item Exploits", "14d"),
        new Preset("major_griefing", "Major Griefing", "7d"),
        new Preset("minor_griefing", "Minor Griefing", "1d"),
        new Preset("event_disruption", "Event Disruption", "3d"),
        new Preset("harassment", "Harassment", "3d"),
        new Preset("chat_spam", "Chat / Spam Abuse", "12h"),
        new Preset("ban_evasion", "Ban Evasion", "14d"),
        new Preset("severe_exploiting", "Severe Exploiting", "14d")
    );
    private static final List<String> HACK_TYPES = List.of("flying", "xray", "combat", "phase_noclip", "godmode", "speed_movement", "duping", "other");

    private static final SuggestionProvider<CommandSourceStack> KNOWN_PLAYERS = (ctx, b) -> {
        for (PlayerResolver.CachedPlayer p : PlayerResolver.knownPlayers(ctx.getSource().getServer())) b.suggest(p.name());
        return b.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> BANNED_PLAYERS = (ctx, b) -> {
        long now = System.currentTimeMillis();
        for (PunishmentRecord r : store.all()) if (r.ban != null && (r.ban.expiresAt == 0 || r.ban.expiresAt > now) && r.lastKnownName != null) b.suggest(r.lastKnownName);
        return b.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> OFFENCES = (ctx, b) -> {
        b.suggest("hacking", Component.literal("[7 Days] - choose hack type(s) next"));
        for (Preset p : PRESETS) b.suggest(p.id(), Component.literal("[" + prettyDuration(p.duration()) + "] " + p.display()));
        b.suggest("other", Component.literal("[Custom] duration + reason"));
        return b.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> HACKS = (ctx, b) -> {
        for (String h : HACK_TYPES) b.suggest(h);
        b.suggest("flying,xray"); b.suggest("flying,phase_noclip,godmode");
        return b.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> BAN_REASONS = (ctx, b) -> {
        for (String s : List.of("Hacking", "Flying / Movement Hacks", "X-Ray", "Combat Hacks", "God Mode / Invincibility", "Duping / Item Exploits", "Major Griefing", "Event Disruption", "Harassment", "Chat / Spam Abuse", "Ban Evasion", "Severe Exploiting", "Other")) b.suggest(s);
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
                        .then(Commands.argument("details", StringArgumentType.greedyString()).suggests(HACKS)
                            .executes(ctx -> tempbanWithDetails(ctx.getSource(), StringArgumentType.getString(ctx,"player"), StringArgumentType.getString(ctx,"offence"), StringArgumentType.getString(ctx,"details")))))));

            registerBan(dispatcher, "czban");
            registerBan(dispatcher, "ban");
            registerUnban(dispatcher, "czunban");
            registerUnban(dispatcher, "unban");

            dispatcher.register(Commands.literal("punishments").requires(s -> Permissions.has(s, Permissions.WARNINGS))
                .executes(ctx -> showPunishments(ctx.getSource(), null))
                .then(Commands.argument("player", StringArgumentType.word()).suggests(KNOWN_PLAYERS).executes(ctx -> showPunishments(ctx.getSource(), StringArgumentType.getString(ctx,"player")))));
        });

        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{ServerPlayer p=handler.getPlayer();var rec=store.get(p.getUUID());if(rec==null||rec.ban==null)return;if(rec.ban.expiresAt>0&&System.currentTimeMillis()>=rec.ban.expiresAt){rec.ban=null;store.save();return;}server.execute(()->p.connection.disconnect(banMessage(rec)));});
    }

    private static void registerBan(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> d,String root){ d.register(Commands.literal(root).requires(s->Permissions.has(s,Permissions.BAN)).then(Commands.argument("player",StringArgumentType.word()).suggests(KNOWN_PLAYERS).then(Commands.argument("reason",StringArgumentType.greedyString()).suggests(BAN_REASONS).executes(ctx->{ServerPlayer staff=ctx.getSource().getPlayerOrException();var t=resolve(ctx.getSource().getServer(),StringArgumentType.getString(ctx,"player"),ctx.getSource());if(t==null)return 0;String reason=StringArgumentType.getString(ctx,"reason");var r=store.getOrCreate(t.uuid(),t.name());var b=new PunishmentRecord.BanEntry();b.reason=reason;b.staff=staff.getGameProfile().name();b.issuedAt=System.currentTimeMillis();b.expiresAt=0;r.ban=b;store.save();if(t.onlinePlayer()!=null)t.onlinePlayer().connection.disconnect(banMessage(r));ctx.getSource().sendSuccess(()->Component.literal("Permanently banned "+t.name()+". Reason: "+reason),false);return 1;})))); }
    private static void registerUnban(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> d,String root){d.register(Commands.literal(root).requires(s->Permissions.has(s,Permissions.UNBAN)).then(Commands.argument("player",StringArgumentType.word()).suggests(BANNED_PLAYERS).executes(ctx->{var t=resolve(ctx.getSource().getServer(),StringArgumentType.getString(ctx,"player"),ctx.getSource());if(t==null)return 0;var r=store.get(t.uuid());if(r!=null){r.ban=null;store.save();}ctx.getSource().sendSuccess(()->Component.literal("Unbanned "+t.name()+"."),false);return 1;})));}

    private static int presetTempban(CommandSourceStack src,String player,String offence,String details){
        if(offence.equalsIgnoreCase("hacking")){src.sendFailure(Component.literal("Hacking requires hack type(s), e.g. /tempban "+player+" hacking flying,phase_noclip,godmode"));return 0;}
        if(offence.equalsIgnoreCase("other")){src.sendFailure(Component.literal("Custom format: /tempban "+player+" other <duration> <reason>  Example: /tempban "+player+" other 5d Repeated rule violations"));return 0;}
        Preset p=PRESETS.stream().filter(x->x.id().equalsIgnoreCase(offence)).findFirst().orElse(null);if(p==null){src.sendFailure(Component.literal("Unknown offence. Choose a suggested offence or 'other'."));return 0;}
        return issueTempban(src,player,p.duration(),p.display());
    }
    private static int tempbanWithDetails(CommandSourceStack src,String player,String offence,String details){
        if(offence.equalsIgnoreCase("hacking")){String cleaned=details.replace(',', ' ').trim();if(cleaned.isEmpty()){src.sendFailure(Component.literal("Specify at least one hack type."));return 0;}return issueTempban(src,player,"7d","Hacking - "+cleaned.replace('_',' '));}
        if(offence.equalsIgnoreCase("other")){int sp=details.indexOf(' ');if(sp<1){src.sendFailure(Component.literal("Use: /tempban "+player+" other <duration> <reason>"));return 0;}String dur=details.substring(0,sp);String reason=details.substring(sp+1).trim();if(reason.isEmpty()){src.sendFailure(Component.literal("Please provide a custom reason."));return 0;}return issueTempban(src,player,dur,reason);}
        return presetTempban(src,player,offence,details);
    }
    private static int issueTempban(CommandSourceStack src,String player,String durationText,String reason){
        ServerPlayer staff;try{staff=src.getPlayerOrException();}catch(Exception e){src.sendFailure(Component.literal("This command must be run by a player moderator."));return 0;}
        var t=resolve(src.getServer(),player,src);if(t==null)return 0;long duration=TimeParser.parseMillis(durationText);if(duration<=0){src.sendFailure(Component.literal("Invalid duration. Use 30m, 12h, 2d, 1w, etc."));return 0;}
        var r=store.getOrCreate(t.uuid(),t.name());var b=new PunishmentRecord.BanEntry();b.reason=reason;b.staff=staff.getGameProfile().name();b.issuedAt=System.currentTimeMillis();b.expiresAt=b.issuedAt+duration;r.ban=b;store.save();if(t.onlinePlayer()!=null)t.onlinePlayer().connection.disconnect(banMessage(r));src.sendSuccess(()->Component.literal("Temporarily banned "+t.name()+" for "+prettyDuration(durationText)+". Reason: "+reason),false);return 1;
    }
    private static int showPunishments(CommandSourceStack src,String player){long now=System.currentTimeMillis();if(player!=null){var t=resolve(src.getServer(),player,src);if(t==null)return 0;var r=store.get(t.uuid());showRecord(src,r,t.name(),now);return 1;}int count=0;src.sendSuccess(()->Component.literal("---- Active Punishments ----"),false);for(var r:store.all()){if(r.ban==null)continue;if(r.ban.expiresAt>0&&r.ban.expiresAt<=now){r.ban=null;store.save();continue;}showRecord(src,r,r.lastKnownName==null?r.uuid.toString():r.lastKnownName,now);count++;}if(count==0)src.sendSuccess(()->Component.literal("No active bans or temporary bans."),false);return 1;}
    private static void showRecord(CommandSourceStack src,PunishmentRecord r,String name,long now){if(r==null||r.ban==null){src.sendSuccess(()->Component.literal(name+" has no active ban."),false);return;}var b=r.ban;if(b.expiresAt>0&&b.expiresAt<=now){b=null;r.ban=null;store.save();src.sendSuccess(()->Component.literal(name+" has no active ban."),false);return;}String type=b.expiresAt==0?"PERMANENT BAN":"TEMP BAN";String time=b.expiresAt==0?"Permanent":TimeParser.formatRemaining(b.expiresAt-now);String line=name+" | "+type+" | Reason: "+b.reason+" | Time Left: "+time+" | By: "+b.staff;src.sendSuccess(()->Component.literal(line),false);}
    private static String prettyDuration(String d){if(d==null||d.length()<2)return d;String n=d.substring(0,d.length()-1);return n+switch(Character.toLowerCase(d.charAt(d.length()-1))){case 'm'->" Minutes";case 'h'->" Hours";case 'd'->" Days";case 'w'->" Weeks";default->"";};}
    private static PlayerResolver.ResolvedPlayer resolve(MinecraftServer server,String name,CommandSourceStack src){var t=PlayerResolver.resolve(server,name);if(t==null)src.sendFailure(Component.literal("Player not found. They must have joined Chill Zone SMP at least once."));return t;}
    private static Component banMessage(PunishmentRecord rec){var ban=rec.ban;boolean temporary=ban.expiresAt>0;MutableComponent root=Component.literal("You are "+(temporary?"temporarily banned":"banned")+" from "+config.serverName+".\n\nReason:\n"+ban.reason+"\n");if(temporary)root.append(Component.literal("\nTime Remaining:\n"+TimeParser.formatRemaining(ban.expiresAt-System.currentTimeMillis())+"\n"));root.append(Component.literal("\nAppeal:\n"));MutableComponent discord=Component.literal(config.discordInvite);try{discord=discord.withStyle(st->st.withUnderlined(true).withClickEvent(new ClickEvent.OpenUrl(URI.create(config.discordInvite))));}catch(Exception ignored){}root.append(discord);root.append(Component.literal("\n\n"+config.appealMessage));return root;}
}
