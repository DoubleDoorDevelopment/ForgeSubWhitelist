/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Dries K. Aka Dries007
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.doubledoordev.fsw;

import com.google.common.base.Joiner;
import com.mojang.authlib.GameProfile;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.NetworkCheckHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.UUID;

/**
 * @author Dries007
 */
@Mod(modid = ForgeSubWhitelist.MODID, name = ForgeSubWhitelist.MODNAME)
public class ForgeSubWhitelist
{
    @SuppressWarnings("WeakerAccess")
    public static final String MODID = "forgesubwhitelist";
    public static final String MODNAME = "ForgeSubWhitelist";

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String BASE_URL = "http://doubledoordev.net/isAuthorized.php?token=$TOKEN$";

    private static final CachedSet<UUID> CACHE = new CachedSet<>(86400000); // 24 hours

    private static Configuration configuration;

    private static String[] kickMsg = new String[]{"You must be subscribed to join this server.", "Make sure your accounts are linked: http://doubledoordev.net/?p=linking"};
    private static Logger logger;
    private static boolean closed = false;
    private static String closed_msg = "Sorry, the server isn't open yet.";
    private static Streamer[] streamers;

    // This mod is SideOnly SERVER, so who cares for multiple server instances...
    private static MinecraftServer server;

    @Mod.EventHandler
    public void init(FMLPreInitializationEvent event) throws IOException
    {
        logger = event.getModLog();
        configuration = new Configuration(event.getSuggestedConfigurationFile());
        syncConfig();

        MinecraftForge.EVENT_BUS.register(this);
    }

    @NetworkCheckHandler
    public boolean networkCheckHandler(Map<String, String> map, Side side)
    {
        return true;
    }

    @SubscribeEvent
    public void joinEvent(final FMLNetworkEvent.ServerConnectionFromClientEvent event)
    {
        if (event.isLocal()) return;
        new Thread(new ForgeSubWhitelist.Checker(((NetHandlerPlayServer) event.getHandler()).player.getGameProfile())).start();
    }

    @Mod.EventHandler
    public void serverStart(FMLServerStartingEvent event) throws IOException
    {
        server = event.getServer();
        event.registerServerCommand(new CommandBase()
        {
            @Override
            public String getName()
            {
                return "closed";
            }

            @Override
            public String getUsage(ICommandSender sender)
            {
                return "/closed [true|false]";
            }

            @Override
            public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException
            {
                if (args.length == 1)
                {
                    closed = parseBoolean(args[0]);
                    configuration.get(MODID, "closed", closed).set(closed);
                    if (configuration.hasChanged()) configuration.save();
                }
                sender.sendMessage(new TextComponentString("The server is currently " + (closed ? "closed" : "open" + ".")));
            }
        });
    }

    public static class Streamer
    {
        public final String token;
        public final String redactedToken;
        public final boolean twitch;
        public final boolean beam;
        public final int gamewisp;
        public final String baseUrl;
        public final String fullUrl;

        public Streamer(String token, boolean twitch, boolean beam, int gamewisp)
        {
            this.token = token;
            this.twitch = twitch;
            this.beam = beam;
            this.gamewisp = gamewisp;
            this.baseUrl = BASE_URL.replace("$TOKEN$", token);
            StringBuilder sb = new StringBuilder(baseUrl);
            if (twitch) sb.append("&twitch=true");
            if (beam) sb.append("&beam=true");
            if (gamewisp != -1) sb.append("&gamewisp=").append(gamewisp);
            sb.append("&uuid=");
            this.fullUrl = sb.toString();
            sb = new StringBuilder(token);
            for (int i = token.length() / 2; i < token.length(); i++) sb.setCharAt(i, '*');
            redactedToken = sb.toString();
        }

        @Override
        public String toString()
        {
            return token +
                    ' ' + twitch +
                    ' ' + beam +
                    ' ' + gamewisp;
        }
    }

    private void syncConfig()
    {
        String[] old = null;
        if (configuration.hasKey(MODID, "apiToken"))
        {
            logger.info("Converting old config to new format.");
            old = new String[]{ new Streamer(
                    configuration.get(MODID, "apiToken", "").getString(),
                    configuration.get(MODID, "twitch", false).getBoolean(),
                    configuration.get(MODID, "beam", false).getBoolean(),
                    configuration.get(MODID, "gamewisp", -1).getInt()
            ).toString()};
            configuration.removeCategory(configuration.getCategory(MODID));
        }
        configuration.addCustomCategoryComment(MODID, "This information is required for server side operation.");

        Property p = configuration.get(MODID, "tokens", new String[0],
            "This new format allows you to have a server run by multiple people. Being subscribed to one of them is enough to get on.\n" +
            "Syntax: (remove quotes, 1 per line)\n" +
            "    '<apitoken> <twitch> <beam> <gamewisp>'\n" +
            "Services:\n" +
            "    <apitoken> is token you get from http://doubledoordev.net/?p=linking\n" +
            "    <twitch> is true or false. True means let subs from twitch on.\n" +
            "    <beam> is true or false. True means let subs from beam on.\n" +
            "    <gamewisp> is a number. -1 means ignore gamewisp subs. Any other number is used as the mimimum gamewisp tear for this server.\n" +
            "Examples:\n" +
            "    'TOKEN true false 1'   to allow twitch and tear 1 and above on gamewisp, but ignore beam.\n" +
            "    'TOKEN true false -1'  to only allow twitch.");
        if (old != null) p.set(old);
        String[] lines = p.getStringList();
        streamers = new Streamer[lines.length];
        for (int i = 0; i < lines.length; i++)
        {
            String[] split = lines[i].split("\\s+");
            String token = split[0];
            if (split.length > 4) throw new RuntimeException("Too many parts in the config string: " + lines[i]);
            boolean twitch = split.length > 1 && Boolean.parseBoolean(split[1]);
            boolean beam = split.length > 2 && Boolean.parseBoolean(split[2]);
            int gamewisp = split.length > 3 ? Integer.parseInt(split[3]) : -1;
            streamers[i] = new Streamer(token, twitch, beam, gamewisp);
        }

        kickMsg = configuration.getStringList("kickMsg", MODID, kickMsg, "Please put a nice message here. Newline allowed. Its recommended to link to a document explain the auth process and/or your channel. Remember that you cannot click links, so keep it short.");
        closed = configuration.getBoolean("closed", MODID, closed, "Used for not-yet-public state. Enable ingame with /closed <true|false>.");
        closed_msg = configuration.getString("closed_msg", MODID, closed_msg, "The message when the server is closed.");

        if (configuration.hasChanged()) configuration.save();

        logger.info("Trying out the API token. This could take a couple of seconds.");
        try
        {
            for (Streamer s : streamers)
            {
                //noinspection ResultOfMethodCallIgnored
                IOUtils.toString(new URL(s.baseUrl), UTF8);
            }
        }
        catch (IOException ex)
        {
            RuntimeException e = new RuntimeException("\n\nYour API token is wrong. Update them in the " + MODID + " config.\n\nDO NOT POST THIS LOG ANYWHERE ONLINE WITHOUT REMOVING THE BASE_URL IN THE LINE BELOW!\n", ex);
            e.setStackTrace(new StackTraceElement[0]);
            throw e;
        }

        logger.info("Configuration:");
        for (Streamer s : streamers) logger.info("Token (redacted): {} Twitch: {} Beam: {} Gamewisp: {}", s.redactedToken, s.twitch, s.beam, s.gamewisp);
        if (streamers.length == 0) logger.warn("YOU DON NOT HAVE ANY TOKENES CONFIGURED. YOU WILL NOT BE ABLE TO LOG ON WITHOUT WHITELISTING!");
    }

    private static class Checker implements Runnable
    {
        private final GameProfile gameProfile;

        private Checker(GameProfile gameProfile)
        {
            this.gameProfile = gameProfile;
        }

        @Override
        public void run()
        {
            UUID uuid = gameProfile.getId();
            PlayerList scm = server.getPlayerList();
            if (scm.canSendCommands(gameProfile) || scm.getWhitelistedPlayers().isWhitelisted(gameProfile))
            {
                logger.info("Letting {} join, manual or op.", gameProfile.getName());
                return;
            }
            if (closed) kick(scm.getPlayerByUUID(gameProfile.getId()));
            if (CACHE.contains(uuid)) return;

            for (Streamer s : streamers)
            {
                try
                {
                    if (Boolean.parseBoolean(IOUtils.toString(new URL(s.fullUrl + uuid.toString()), UTF8)))
                    {
                        logger.info("Letting {} join, authorized by {} (token redacted)", gameProfile.getName(), s.redactedToken);
                        CACHE.add(uuid);
                        return;
                    }
                }
                catch (IOException ignored)
                {
                }
            }
            kick(scm.getPlayerByUUID(gameProfile.getId()));
        }

        private void kick(final EntityPlayerMP playerMP)
        {
            if (playerMP == null) return;
            logger.info("Kicking {} because {}.", playerMP.getName(), closed ? "Closed" : "Not authenticated");
            server.addScheduledTask(() -> playerMP.connection.disconnect(new TextComponentString(closed ? closed_msg : Joiner.on('\n').join(kickMsg))));
        }
    }
}
