package com.chillzone.moderation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;

/** Season 2 public rules; shared numbers for /rules and /czrules. */
public final class ServerRules {
    private ServerRules() {}
    private record Rule(String name, String detail) {}
    private record Section(String id, String title, Rule[] rules) {}
    private static final Section[] SECTIONS = {
        new Section("chat", "CHAT AND PLAYER CONDUCT", new Rule[] {
            new Rule("No Harassment", "Bullying, intimidation, and repeated personal targeting are prohibited."),
            new Rule("No Hateful or Discriminatory Language", "No hateful slurs, coded language, discriminatory symbols, or offensive content in chat, names, skins, books, signs, builds, or voice chat."),
            new Rule("No Real-World Threats", "No threats of real-world harm, doxing, sharing private information, or malicious links."),
            new Rule("No Promoting Other Servers", "Do not advertise other servers through private messages, team chat, voice chat, books, signs, builds, or spam without Owner permission."),
            new Rule("No Pretending to Be Staff", "Do not impersonate staff, public figures, or other players to deceive or intimidate."),
            new Rule("No Real-Money Scams", "No real-money fraud, selling or trading accounts, or requesting account login details."),
            new Rule("In-Game Scams Are Part of Survival", "In-game item trade scams and betrayal are allowed unless cheating, real-world fraud, harassment, or another rule is involved."),
            new Rule("Follow Platform Rules", "Follow Minecraft and platform account rules; keep public content age-appropriate."),
            new Rule("No Retaliation Against Reports", "Do not harass or cheat against players for reporting an incident."),
        }),
        new Section("cheating", "CHEATING AND CLIENT MODIFICATIONS", new Rule[] {
            new Rule("No X-Ray", "No X-ray resource packs, ore finders, cave/base finders, or rendering exploits used to reveal hidden information."),
            new Rule("No Freecam", "No Freecam or camera noclip used to scout areas players cannot normally see."),
            new Rule("No ESP", "No player, chest, entity, or resource ESP, tracers, radar, or wallhacks that reveal information through blocks."),
            new Rule("No Combat Hacks", "No KillAura, reach, aim assist, triggerbots, auto-totem exploits, or unfair combat assistance."),
            new Rule("No Hacked Clients", "No hacked clients, flight, speed, anti-knockback, or prohibited client features even when disguised as other mods."),
            new Rule("No Automated Gameplay", "No auto-clicking, auto-fishing, auto-farming, auto-mining, auto-fighting, or AFK movement scripts that play for you. Ordinary vanilla farms are allowed."),
            new Rule("No Exploit Duplication", "No duplication, packet, chunk, bedrock, or other bugs used to gain unfair items, access, or advantages. Rail, carpet, and string dupers are permitted exceptions."),
            new Rule("No Bypassing Server Security", "Do not bypass anti-cheat, X-ray protection, bans, identity checks, or moderation systems."),
            new Rule("No Sharing Cheats", "Do not distribute cheat tools, downloads, or instructions intended for cheating on the server."),
            new Rule("Evidence Is Required", "Accusations alone are not proof. Staff review logs, clips, or other reliable evidence before major punishments."),
        }),
        new Section("pvp", "PVP AND LIFESTEAL", new Rule[] {
            new Rule("Normal PvP Is Allowed", "Fair fights, alliances, outnumbering, betrayals, and rivalries are allowed outside protected situations."),
            new Rule("No Combat Logging", "Do not intentionally disconnect to escape combat. Genuine crashes may be investigated; video proof greatly improves the chance of restoring lost items or hearts."),
            new Rule("No Exploit-Based Combat Escapes", "Do not escape combat using hacked clients or bugs. Normal pearls, portals, dimension travel, and permitted commands are allowed."),
            new Rule("No Arranged Kill Farming", "No staged or arranged kills for hearts, loot, Shards, or leaderboard positions."),
            new Rule("No Repeated Victim Farming", "Do not repeatedly target the same player to farm rewards or deliberately prevent normal play."),
            new Rule("No Abusive Alternate Accounts", "No alternate accounts to evade punishment, scout unfairly, farm rewards, or manipulate PvP. Legitimate alts need Owner approval."),
            new Rule("No Exploiting Frozen Players", "Do not knowingly attack players frozen by loading, disconnection, or chunk problems. High ping alone does not invalidate a fair fight."),
            new Rule("No Combat Exploits", "No invulnerability, reach, damage, knockback, movement, or unintended combat-timer exploits."),
            new Rule("No Lifesteal Exploits", "Do not manipulate heart limits, revival cooldowns, zero-heart elimination, or heart transfers through bugs or alternate accounts."),
            new Rule("No Revival Duplication", "Do not duplicate or exploit revival items or beacons to avoid intended Lifesteal mechanics."),
            new Rule("No Targeted Harassment Through PvP", "Repeated attacks intended to stop a disengaged player from playing normally may be investigated; one ordinary kill is not automatically harassment."),
        }),
        new Section("spawn", "SPAWN AND PLAYER PROTECTION", new Rule[] {
            new Rule("No Spawn Killing", "Do not attack players arriving at spawn or before they finish loading and regain control."),
            new Rule("No Spawn Camping", "Do not wait at spawn to immediately attack arrivals. Allow about 90 seconds before initiating PvP against an armored player who remains there; mutually agreed fights may begin sooner. This never overrides no naked killing."),
            new Rule("No Naked Killing", "Do not attack a player wearing zero armor pieces unless they repeatedly attack you first. You may defend yourself if attacked."),
            new Rule("No Spawn Traps", "No traps at spawn or arrival points, including indirect hazards such as lava, TNT, mobs, or fall traps."),
            new Rule("No Immediate TP Traps", "Do not lure a player through /tpa, invitations, or similar teleports into a trap that kills or harms them immediately (roughly within 5-10 seconds). A later betrayal after they have had a real chance to leave is not automatically TP trapping."),
            new Rule("No Killing While Loading", "Do not exploit a visibly frozen, reconnecting, or unloaded player. If a crash causes a loss, submit proof such as a recording for staff review."),
            new Rule("No Repeated Newcomer Targeting", "Do not repeatedly intercept players just joining, respawning, or trying to establish basic equipment."),
            new Rule("No Bypassing Protected Areas", "Do not bypass spawn or public-build protection using explosions, mobs, projectiles, vehicles, commands, or glitches."),
        }),
        new Section("staff", "STAFF, REPORTS AND ENFORCEMENT", new Rule[] {
            new Rule("Rules Apply to Staff and Owners", "Staff must follow the rules and must not abuse administrative powers for unfair survival advantages."),
            new Rule("Follow Reasonable Staff Instructions", "Cooperate with legitimate moderation, safety instructions, and investigations. Disputes can be appealed afterward."),
            new Rule("No Punishment Evasion", "Do not evade warnings, bans, mutes, or restrictions using alts, new identities, or connections."),
            new Rule("Do Not Tamper With Evidence", "Do not destroy, alter, fabricate evidence, pressure witnesses, or retaliate against reporters."),
            new Rule("Staff Review Evidence", "Clips, screenshots, chat logs, server logs, and direct observations may be considered in context."),
            new Rule("Appeals Through Support", "Use official server support channels for appeals; do not spam or harass staff."),
            new Rule("Punishments Depend on Severity", "Warnings, corrective actions, rollbacks, mutes, temporary bans, and permanent bans are possible based on evidence and severity."),
            new Rule("Warnings for Minor First Offences", "A first minor offence usually receives a warning. Cheating, threats, serious exploiting, or repeated abuse can justify an immediate ban."),
            new Rule("Indirect Actions Still Count", "Rules apply when violations are carried out through teammates, mobs, machines, projectiles, alternate accounts, or tools."),
            new Rule("New Harmful Conduct Can Be Investigated", "Staff may apply the closest published rule with a clear explanation; secret retroactive rules are not allowed."),
            new Rule("Rules Can Be Updated", "Changes will be announced. Incidents are assessed using the rules active at the time."),
        }),
        new Section("team", "TEAMS, BASES AND ECONOMY", new Rule[] {
            new Rule("Normal Raiding and Betrayal Are Allowed", "Ordinary stealing, raiding, in-game item scams, team rivalries, and betrayal are allowed outside protected areas unless another rule is broken."),
            new Rule("Do Not Damage Protected Builds", "No destroying or altering spawn, public protected structures, or event builds by direct or indirect means."),
            new Rule("No Cheat-Based Base Access", "Do not scout or access bases using X-ray, Freecam, ESP, unauthorized staff information, or glitches."),
            new Rule("No Exploit-Based Inaccessible Bases", "Do not exploit protected terrain, void, bedrock, dimension restrictions, or protection bugs to make inaccessible bases."),
            new Rule("Team Storage and Betrayal", "Team members may use storage they normally can access. Insider theft and betrayal are allowed; exploiting permissions or bugs is not."),
            new Rule("No Storage Exploits", "No team-storage duplication, inventory desync exploits, item-destruction bugs, or unauthorized admin-viewer access."),
            new Rule("No Team Permission Exploits", "No impersonating team owners or exploiting bugs to change team roles or access restricted team actions."),
            new Rule("No AFK Shard Farming", "No bots, macros, AFK movement scripts, or repetitive automation intended to farm Shards."),
            new Rule("No Shop Exploits", "No abusing prices, purchases, refunds, repairs, XP recovery, effects, or currencies for unintended rewards."),
            new Rule("No Lag Machines", "Do not deliberately build machines that seriously lag the server. Rail dupers, carpet dupers, and string dupers are allowed if they do not cause harmful lag. Staff may request reductions in entities or redstone first."),
            new Rule("No Deliberate Server Damage", "No intentional crashes, packet flooding, world corruption, or mass generation intended to harm performance."),
            new Rule("No Real-Money Item Sales", "Selling items, hearts, Shards, accounts, or in-game services for real money is prohibited without explicit Owner authorization."),
        }),
    };

    private static int countRules() {
        int count = 0;
        for (Section section : SECTIONS) count += section.rules().length;
        return count;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerNamed(dispatcher, "rules");
        registerNamed(dispatcher, "czrules");
    }

    private static void registerNamed(CommandDispatcher<CommandSourceStack> dispatcher, String name) {
        var root = Commands.literal(name)
            .executes(ctx -> showOverview(ctx.getSource()))
            .then(Commands.literal("all").executes(ctx -> showAll(ctx.getSource())))
            .then(Commands.argument("number", IntegerArgumentType.integer(1))
                .executes(ctx -> showNumber(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "number"))));
        for (Section section : SECTIONS) {
            root.then(Commands.literal(section.id()).executes(ctx -> showSection(ctx.getSource(), section)));
        }
        dispatcher.register(root);
    }

    private static int showOverview(CommandSourceStack source) {
        title(source, "CHILL ZONE SMP - RULES (" + countRules() + ")");
        gray(source, "Choose a section, /rules all, or type /rules <number> to view one rule.");
        int start = 1;
        for (Section section : SECTIONS) {
            int end = start + section.rules().length - 1;
            final int first = start;
            final int last = end;
            var link = Component.literal("/rules " + section.id() + " - " + section.title())
                .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Rules " + first + " to " + last)))));
            send(source, link);
            start = end + 1;
        }
        send(source, Component.literal("/rules all - ALL SERVER RULES")
            .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Rules 1 to " + countRules())))));
        gray(source, "Use /czrules if another mod overrides /rules.");
        printEnforcement(source);
        return 1;
    }

    private static int showAll(CommandSourceStack source) {
        title(source, "CHILL ZONE SMP - ALL SERVER RULES");
        int number = 1;
        for (Section section : SECTIONS) number = printSection(source, section, number);
        printEnforcement(source);
        return 1;
    }

    private static int showSection(CommandSourceStack source, Section wanted) {
        int start = 1;
        for (Section section : SECTIONS) {
            if (section == wanted) {
                printSection(source, section, start);
                printEnforcement(source);
                return 1;
            }
            start += section.rules().length;
        }
        return 0;
    }

    private static int showNumber(CommandSourceStack source, int requested) {
        int number = 1;
        for (Section section : SECTIONS) {
            for (Rule rule : section.rules()) {
                if (number == requested) {
                    title(source, section.title());
                    printRule(source, number, rule);
                    printEnforcement(source);
                    return 1;
                }
                number++;
            }
        }
        gray(source, "Unknown rule number. Valid numbers: 1 to " + countRules() + ".");
        return 0;
    }

    private static int printSection(CommandSourceStack source, Section section, int number) {
        int end = number + section.rules().length - 1;
        title(source, section.title() + " (Rules " + number + "-" + end + ")");
        for (Rule rule : section.rules()) printRule(source, number++, rule);
        return number;
    }

    private static void printRule(CommandSourceStack source, int number, Rule rule) {
        send(source, Component.literal(number + ". " + rule.name()).withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" - " + rule.detail()).withStyle(ChatFormatting.GRAY)));
    }

    private static void printEnforcement(CommandSourceStack source) {
        title(source, "ENFORCEMENT");
        gray(source, "Staff may issue warnings, corrections, rollbacks, mutes, temporary bans, or permanent bans based on evidence and severity. Punishments are not automatic.");
        gray(source, "Minor first offences usually receive a warning; serious or repeated violations may result in bans. Appeals use official support channels.");
        gray(source, "If a genuine disconnect during combat causes a loss, provide evidence. Video proof greatly improves the chance of recovering lost items or hearts; restoration is not guaranteed.");
    }

    private static void title(CommandSourceStack source, String message) {
        send(source, Component.literal(message).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
    }
    private static void gray(CommandSourceStack source, String message) {
        send(source, Component.literal(message).withStyle(ChatFormatting.GRAY));
    }
    private static void send(CommandSourceStack source, Component message) {
        if (source.getEntity() instanceof ServerPlayer player) player.sendSystemMessage(message);
        else source.sendSuccess(() -> message, false);
    }
}
