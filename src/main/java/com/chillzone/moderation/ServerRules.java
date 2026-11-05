package com.chillzone.moderation;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Public Season 2 rules. Plain text for Java and Bedrock/Geyser compatibility. */
public final class ServerRules {
    private ServerRules() {}

    private record Section(String id, String title, String[] rules) {}
    private static final Section[] SECTIONS = {
        new Section("spawn", "1. SPAWN AND PLAYER PROTECTION", new String[] {
            "1. No spawn killing: do not harm a player at the spawn arrival point or while they are loading in or unable to move after arrival.",
            "2. No spawn exit camping: do not wait at or repeatedly attack players as they leave spawn, even from just outside protected blocks.",
            "3. No naked killing: a player wearing ZERO armour pieces is protected if they have not initiated or joined combat. A weapon in hand alone does not remove protection.",
            "4. If an unarmoured player intentionally damages, attacks, or actively assists in attacking you or your team, you may defend yourself with proportionate PvP.",
            "5. An unarmoured player does not lose protection merely by running away, collecting items, holding a weapon, or refusing a fight.",
            "6. No traps at spawn or arrival areas: this includes mobs, lava, TNT, crystals, holes, falling blocks, mace drops, elytra dives, wind charges and indirect attacks.",
            "7. No teleport traps: do not use /tpa, /home, portals, or invitations to lure players into immediate OR delayed unavoidable harm.",
            "8. No killing while loading: do not take advantage of players visibly frozen, falling through unloaded terrain, reconnecting, or unable to control their character.",
            "9. No newcomer targeting: do not repeatedly intercept someone who is just joining, respawning, or trying to establish basic equipment.",
            "10. No bypassing existing spawn or public-area protection through explosions, projectiles, mobs, vehicles, commands, or glitches."
        }),
        new Section("pvp", "2. PVP AND LIFESTEAL", new String[] {
            "11. Ordinary PvP, fair fights, alliances, being outnumbered, and rivalries are allowed outside protected situations.",
            "12. No combat logging or disconnecting to evade an active combat tag. Genuine crashes and connection faults will be investigated using available evidence.",
            "13. No bypassing combat restrictions with commands, dimensions, pearls, portals, mod bugs, or other unintended travel.",
            "14. No arranged or staged kills to farm hearts, loot, money, Shards, kill streaks, leaderboards, or other rewards.",
            "15. No repeated victim farming: farming one player for rewards or repeatedly denying their opportunity to play is prohibited even without their cooperation.",
            "16. No alternate accounts to scout, evade punishment, store prohibited goods, farm rewards, or manipulate PvP. Legitimate alternate accounts require Owner approval.",
            "17. No exploiting a player whose connection is visibly frozen or who is falling through unloaded chunks. A normal fight is not invalid merely because ping is high.",
            "18. No invulnerability, reach, damage, knockback, movement, or combat-timer exploits. Unintended mechanics used to defeat server restrictions are prohibited.",
            "19. No manipulating heart limits, revival cooldowns, revived-player status, or Lifesteal deaths by any unintended method.",
            "20. No abusing revival items or beacons for heart duplication, punishment evasion, or bypassing the server normal revival rules.",
            "21. At zero hearts, the installed Lifesteal system controls elimination and revival; do not bypass it by exploits or alternate accounts.",
            "22. No harassment through repeated attacks after someone has disengaged when the pattern prevents normal gameplay. Staff review the full pattern, not one kill alone."
        }),
        new Section("cheating", "3. CHEATING AND CLIENT MODIFICATIONS", new String[] {
            "23. No X-ray: prohibited methods include resource packs, ore finders, chunk analysis, cave finders, base finders, and abused rendering glitches.",
            "24. No Freecam or camera noclip, including features inside Tweakeroo, replay tools, utility mods, or hacked clients used to inspect hidden areas.",
            "25. No ESP, tracer, radar, wallhack, chest ESP, player ESP, entity ESP, or map overlays that reveal players or resources through obstacles.",
            "26. No seed cracking, world-seed reconstruction, structure prediction, or external map viewers used to reveal undiscovered locations or bases.",
            "27. No KillAura, triggerbots, aim assists, auto-totem, reach, flight, speed, anti-knockback, or movement hacks that provide unfair advantages.",
            "28. No hacked clients or prohibited functions even if they are renamed, hidden inside another mod, or enabled through scripts or resource packs.",
            "29. Purely cosmetic, accessibility, and FPS mods are allowed unless a specific feature gives prohibited scouting, combat, or automation advantages.",
            "30. No unapproved automatic clicking, fishing, farming, mining, fighting, or AFK movement scripts. Ordinary in-game farms without prohibited automation are allowed.",
            "31. No exploiting duplication, packet glitches, bedrock or chunk errors, or unintended interactions to gain access, items, information, or advantages.",
            "32. No possessing, selling, or transferring bug-created or otherwise illicit items or rewards, even when the visible item type is normally obtainable.",
            "33. Report discovered gameplay exploits privately before using or sharing them. Do not test a bug repeatedly on the live server without Owner permission.",
            "34. No bypassing anti-cheat, X-ray protection, moderation, identity checks, bans, or other server security by account, connection, or client manipulation.",
            "35. No sharing instructions, tools, or downloads intended to help others cheat on this server.",
            "36. An accusation alone does not prove cheating. Staff must review logs, clips, reproducible evidence, or other corroboration before serious sanctions."
        }),
        new Section("teams", "4. TEAMS, BASES AND ECONOMY", new String[] {
            "37. Raiding, ordinary stealing, betrayal, scams involving in-game items, and team rivalries are allowed outside protected areas unless another rule is broken.",
            "38. No destroying or altering spawn, protected public builds, or server event structures, including indirect damage and environmental abuse.",
            "39. No accessing a base through Freecam, X-ray, ESP, seed tools, unauthorized staff data, or collision and dimension glitches.",
            "40. No building inaccessible bases by abusing protected land, void or bedrock errors, restricted dimensions, or server protection bugs.",
            "41. Team members may access shared storage according to their normal team permissions. Insider theft is an in-game risk; exploiting permissions is not allowed.",
            "42. No team storage duplication, inventory desync, item destruction by glitches, or use of unauthorized admin viewers.",
            "43. No impersonating team owners, changing team roles by exploits, or using team bugs to access private member or owner actions.",
            "44. No Shard farming through AFK pools, automated movement, bots, macros, or repetitive inputs intended to deceive the activity system.",
            "45. No exploiting Shop prices, purchases, refunds, temporary effects, repair, XP recovery, or currencies for unintended rewards.",
            "46. No lag machines or excessive farms that materially disrupt the server. Staff may ask players to reduce active entities or redstone before disabling a build.",
            "47. No intentional server crashes, packet flooding, world-corruption attempts, or mass generation meant to degrade server performance.",
            "48. Real-money sales of in-game items, hearts, Shards, accounts, or services are prohibited unless specifically authorized by the Owner."
        }),
        new Section("chat", "5. CHAT AND PLAYER CONDUCT", new String[] {
            "49. No harassment, bullying, intimidation, or sustained personal targeting. A single serious threat or hateful slur can also violate the rules.",
            "50. No hateful or discriminatory language, symbols, coded slurs, or offensive content in chat, names, skins, signs, books, builds, or voice.",
            "51. No real-world threats, doxxing, sharing private information, phishing, or malicious links.",
            "52. No chat, private-message, team-chat, voice, book, sign, or build spam or advertising of other servers without Owner permission.",
            "53. No pretending to be staff, the server, a public figure, or another player to deceive or intimidate others.",
            "54. No real-money scams, account selling, account trading, or asking for account credentials.",
            "55. In-game trade scams and betrayal may be part of survival; real-world fraud, threats, hacking, and exploits never are.",
            "56. Players must follow Minecraft and platform account rules and use age-appropriate conduct in public spaces.",
            "57. No retaliation through harassment or cheating against someone who reports an incident."
        }),
        new Section("staff", "6. STAFF, REPORTS AND ENFORCEMENT", new String[] {
            "58. Staff and Owners are subject to these rules too; administrative powers must not be used to gain unfair survival advantages.",
            "59. Follow reasonable staff instructions related to active safety, moderation, or investigations; dispute them through the appeal process afterward.",
            "60. No evading warnings, bans, mutes, or restrictions with alternate accounts, changed identities, or other connections.",
            "61. No destroying, altering, or fabricating evidence, and no pressuring witnesses or retaliating against reporters.",
            "62. Clips, screenshots, chat logs, server logs, and direct observations may be used; staff should evaluate context and authenticity.",
            "63. Players may appeal through the designated support channel. Repeated spam appeals or harassment do not help an appeal.",
            "64. Warnings, item rollback, mutes, temporary bans, and permanent bans are possible; sanctions depend on severity, evidence and prior verified offences.",
            "65. For non-severe first offences, warnings or corrective action are preferred; cheating, threats, serious exploits, and repeated abuse may justify immediate bans.",
            "66. Rules also apply to indirect acts committed through teammates, mobs, machines, projectiles, alternate accounts, or another tool.",
            "67. New or unusual harmful conduct may be investigated under the nearest stated rule; staff must give a clear reason and cannot invent secret retroactive rules.",
            "68. Published rules can be updated with notice. Disputes are handled using the version in effect when the conduct occurred."
        })
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("rules")
            .executes(ctx -> showOverview(ctx.getSource()))
            .then(Commands.literal("all").executes(ctx -> showAll(ctx.getSource())));
        for (Section section : SECTIONS) {
            root.then(Commands.literal(section.id()).executes(ctx -> showSection(ctx.getSource(), section)));
        }
        dispatcher.register(root);
    }

    private static int showOverview(CommandSourceStack source) {
        title(source, "CHILL ZONE SMP - SERVER RULES");
        normal(source, "Competitive Lifesteal survival. Normal PvP, raiding and rivalries are allowed; cheating, spawn abuse and harassment are not.");
        for (Section section : SECTIONS) {
            title(source, section.title());
            normal(source, "Use /rules " + section.id() + " to read this section.");
        }
        normal(source, "Use /rules all for all 68 rules. Rules apply to Java, Bedrock, teams and alternate accounts.");
        return 1;
    }

    private static int showAll(CommandSourceStack source) {
        title(source, "CHILL ZONE SMP - ALL SERVER RULES");
        for (Section section : SECTIONS) printSection(source, section);
        printEnforcement(source);
        return 1;
    }

    private static int showSection(CommandSourceStack source, Section section) {
        printSection(source, section);
        printEnforcement(source);
        return 1;
    }

    private static void printSection(CommandSourceStack source, Section section) {
        title(source, section.title());
        for (String line : section.rules()) normal(source, line);
    }

    private static void printEnforcement(CommandSourceStack source) {
        title(source, "ENFORCEMENT");
        normal(source, "Staff may issue warnings, corrections, item rollback, mutes, temporary bans or permanent bans based on evidence and severity. These are not automatic punishments.");
        normal(source, "For minor first offences a warning is usually preferred; serious or repeated violations may result in bans. Appeals go through the official support channel.");
    }

    private static void title(CommandSourceStack source, String message) {
        send(source, Component.literal(message).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
    }

    private static void normal(CommandSourceStack source, String message) {
        send(source, Component.literal(message).withStyle(ChatFormatting.AQUA));
    }

    /** Player-facing system messages deliberately do not depend on sendCommandFeedback gamerule. */
    private static void send(CommandSourceStack source, Component message) {
        if (source.getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(message);
        } else {
            source.sendSuccess(() -> message, false);
        }
    }
}
