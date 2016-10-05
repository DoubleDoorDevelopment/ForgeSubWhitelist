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
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.NetworkCheckHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Dries007
 */
@Mod(modid = ForgeSubWhitelist.MODID)
public class ForgeSubWhitelist
{
    @SuppressWarnings("WeakerAccess")
    public static final String MODID = "ForgeSubWhitelist";

    private static final String BASE_URL = "http://doubledoordev.net/isAuthorized.php?token=$TOKEN$";

    private static final CachedSet<UUID> CACHE = new CachedSet<>(86400000); // 24 hours
    private static final Queue<String> TO_KICK = new ConcurrentLinkedQueue<>();

    private static Configuration configuration;

    private static String[] kickMsg = new String[]{"You must be subscribed to join this server.", "Make sure your accounts are linked: http://doubledoordev.net/?p=linking"};
    private static String apiToken;
    private static boolean twitch = true;
    private static boolean beam = true;
    private static int gamewisp = -1;
    private static Logger logger;
    private static String url;
    private static boolean closed = false;
    private static String closed_msg = "Sorry, the server isn't open yet.";

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
        new Thread(new ForgeSubWhitelist.Checker(((NetHandlerPlayServer) event.getHandler()).playerEntity.getGameProfile())).start();
    }

    @Mod.EventHandler
    public void serverStart(FMLServerStartingEvent event) throws IOException
    {
        event.registerServerCommand(new CommandBase()
        {
            @Override
            public String getCommandName()
            {
                return "closed";
            }

            @Override
            public String getCommandUsage(ICommandSender sender)
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
                sender.addChatMessage(new TextComponentString("The server is currently " + (closed ? "closed" : "open" + ".")));
            }
        });
    }

    @SubscribeEvent
    public void tickEvent(TickEvent.ServerTickEvent event)
    {
        while (!TO_KICK.isEmpty())
        {
            EntityPlayerMP player = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUsername(TO_KICK.poll());
            if (player == null) continue;
            player.connection.kickPlayerFromServer(closed ? closed_msg : Joiner.on('\n').join(kickMsg));
        }
    }

    private void syncConfig()
    {
        configuration.addCustomCategoryComment(MODID, "This information is required for server side operation.");

        apiToken = configuration.getString("apiToken", MODID, "", "Get it from http://doubledoordev.net/?p=linking");
        kickMsg = configuration.getStringList("kickMsg", MODID, kickMsg, "Please put a nice message here. Newline allowed. Its recommended to link to a document explain the auth process and/or your channel. Remember that you cannot click links, so keep it short.");
        twitch = configuration.getBoolean("twitch", MODID, twitch, "If true anyone who is subbed on twitch will be able to join this server.");
        beam = configuration.getBoolean("beam", MODID, beam, "If true anyone who is subbed on beam will be able to join this server.");
        gamewisp = configuration.getInt("gamewisp", MODID, gamewisp, -1, Integer.MAX_VALUE, "If -1, use ignore. Put in the tier at which subs get access to this server. (Includes all above). Your first tier is 1, second is 2, ...");

        closed = configuration.getBoolean("closed", MODID, closed, "Used for not-yet-public state. Enable ingame with /closed <true|false>.");
        closed_msg = configuration.getString("closed_msg", MODID, closed_msg, "The message when the server is closed.");

        if (configuration.hasChanged()) configuration.save();

        logger.info("Trying out the API token. This could take a couple of seconds.");
        try
        {
            //noinspection ResultOfMethodCallIgnored
            IOUtils.toString(new URL(BASE_URL.replace("$TOKEN$", apiToken)));
        }
        catch (IOException ex)
        {
            RuntimeException e = new RuntimeException("\n\nYour API token is wrong. Update them in the " + MODID + " config.\n\nDO NOT POST THIS LOG ANYWHERE ONLINE WITHOUT REMOVING THE BASE_URL IN THE LINE BELOW!\n", ex);
            e.setStackTrace(new StackTraceElement[0]);
            throw e;
        }
        url = BASE_URL + "&uuid=$UUID$";
        if (twitch) url += "&twitch=true";
        if (beam) url += "&beam=true";
        if (gamewisp != -1) url += "&gamewisp=" + gamewisp;
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
            PlayerList scm = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList();
            if (scm.canSendCommands(gameProfile) || scm.getWhitelistedPlayers().isWhitelisted(gameProfile))
            {
                logger.info("Letting {} join, manual or op.", uuid);
                return;
            }
            if (closed)
            {
                sleep();
                TO_KICK.add(gameProfile.getName());
            }

            if (CACHE.contains(uuid)) return;

            try
            {
                String out = IOUtils.toString(new URL(url.replace("$TOKEN$", apiToken).replace("$UUID$", uuid.toString())));
                if (Boolean.parseBoolean(out))
                {
                    logger.info("Letting {} join, authorized online.", uuid);
                    CACHE.add(uuid);
                    return;
                }
            }
            catch (IOException ignored)
            {
                // 500 or something, we don't care. You ain't getting on.
            }
            logger.info("Adding {} to kick list.", uuid);
            sleep();
            TO_KICK.add(gameProfile.getName());
        }

        private void sleep()
        {
            try
            {
                Thread.sleep(1000);
            }
            catch (InterruptedException ignored)
            {

            }
        }
    }
}
