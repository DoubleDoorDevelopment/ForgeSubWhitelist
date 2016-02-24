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
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraftforge.common.config.Configuration;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Dries007
 */
@Mod(modid = ForgeSubWhitelist.MODID)
public class ForgeSubWhitelist
{
    public static final String MODID = "ForgeSubWhitelist";

    public static final String URL = "http://doubledoordev.net/isAuthorized.php?token=$TOKEN$";

    private static final Queue<String> TO_KICK = new ConcurrentLinkedQueue<>();
    private static final Map<UUID, Long> CACHE_MAP = new ConcurrentHashMap<>();

    private static Configuration configuration;

    private static String[] kickMsg = new String[]{"You must be subscribed to join this server.", "Make sure your accounts are linked: http://doubledoordev.net/?p=linking"};
    private static String apiToken;
    private static boolean twitch = true;
    private static boolean beam = true;
    private static int gamewisp = -1;
    private static Logger logger;
    private static String url;

    @Mod.EventHandler
    public void init(FMLPreInitializationEvent event) throws IOException
    {
        logger = event.getModLog();
        configuration = new Configuration(event.getSuggestedConfigurationFile());
        syncConfig();

        FMLCommonHandler.instance().bus().register(this);
    }

    @NetworkCheckHandler
    public boolean networkCheckHandler(Map<String, String> map, Side side)
    {
        return true;
    }

    @SubscribeEvent
    public void joinEvent(final FMLNetworkEvent.ServerConnectionFromClientEvent event)
    {
        if (event.isLocal) return;
        new Thread(new ForgeSubWhitelist.Checker(((NetHandlerPlayServer) event.handler).playerEntity.getGameProfile())).start();
    }

    @SubscribeEvent
    public void tickEvent(TickEvent.ServerTickEvent event)
    {
        String kick = TO_KICK.poll();
        while (kick != null)
        {
            EntityPlayerMP player = FMLCommonHandler.instance().getMinecraftServerInstance().getConfigurationManager().func_152612_a(kick);
            player.playerNetServerHandler.kickPlayerFromServer(Joiner.on('\n').join(kickMsg));

            kick = TO_KICK.poll();
        }
    }

    public void syncConfig()
    {
        configuration.addCustomCategoryComment(MODID, "This information is required for server side operation.");

        apiToken = configuration.getString("apiToken", MODID, "", "Get it from http://doubledoordev.net/?p=linking");
        kickMsg = configuration.getStringList("kickMsg", MODID, kickMsg, "Please put a nice message here. Newline allowed. Its recommended to link to a document explain the auth process and/or your channel. Remember that you cannot click links, so keep it short.");
        twitch = configuration.getBoolean("twitch", MODID, twitch, "If true anyone who is subbed on twitch will be able to join this server.");
        beam = configuration.getBoolean("beam", MODID, beam, "If true anyone who is subbed on beam will be able to join this server.");
        gamewisp = configuration.getInt("gamewisp", MODID, gamewisp, -1, Integer.MAX_VALUE, "If -1, use ignore. Put in the tier at which subs get access to this server. (Includes all above). Your first tier is 1, second is 2, ...");

        if (configuration.hasChanged()) configuration.save();

        logger.info("Trying out the API token. This could take a couple of seconds.");
        try
        {
            //noinspection ResultOfMethodCallIgnored
            IOUtils.toString(new URL(URL.replace("$TOKEN$", apiToken)));
        }
        catch (IOException ex)
        {
            RuntimeException e = new RuntimeException("\n\nYour API token is wrong. Update them in the " + MODID + " config.\n\nDO NOT POST THIS LOG ANYWHERE ONLINE WITHOUT REMOVING THE URL IN THE LINE BELOW!\n", ex);
            e.setStackTrace(new StackTraceElement[0]);
            throw e;
        }
        url = URL + "&uuid=$UUID$";
        if (twitch) url += "&twitch=$TWITCH$";
        if (beam) url += "&beam=$BEAM$";
        if (gamewisp != -1) url += "&twitch=$TWITCH$";
    }

    public static class Checker implements Runnable
    {
        private final GameProfile gameProfile;

        public Checker(GameProfile gameProfile)
        {
            this.gameProfile = gameProfile;
        }

        @Override
        public void run()
        {
            UUID uuid = gameProfile.getId();
            ServerConfigurationManager scm = FMLCommonHandler.instance().getMinecraftServerInstance().getConfigurationManager();
            scm.setWhiteListEnabled(true);
            boolean b = scm.func_152607_e(gameProfile);
            scm.setWhiteListEnabled(false);
            if (b) // op or whitelisted manually
            {
                logger.info("Letting {} join, manual or op.", uuid);
                return;
            }
            if (CACHE_MAP.containsKey(uuid) && CACHE_MAP.get(uuid) - System.currentTimeMillis() < 0)
            {
                CACHE_MAP.remove(uuid);
                return;
            }
            try
            {
                String out = IOUtils.toString(new URL(url
                        .replace("$TOKEN$", apiToken)
                        .replace("$TWITCH$", String.valueOf(twitch))
                        .replace("$BEAM$", String.valueOf(beam))
                        .replace("$UUID$", uuid.toString())
                        .replace("$GAMEWISP$", String.valueOf(gamewisp))));
                if (Boolean.parseBoolean(out))
                {
                    logger.info("Letting {} join, authorized online.", uuid);
                    CACHE_MAP.put(uuid, System.currentTimeMillis() + (1000 * 60 * 60 * 24)); // 24h cache period
                    return;
                }
            }
            catch (IOException ignored)
            {
                // 500 or something, we don't care. You ain't getting on.
            }
            logger.info("Adding {} to kick list.", uuid);
            TO_KICK.add(gameProfile.getName());
        }
    }
}
