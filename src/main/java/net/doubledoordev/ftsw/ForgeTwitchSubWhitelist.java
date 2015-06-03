/*
 * Copyright (c) 2014, DoubleDoorDevelopment
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the project nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package net.doubledoordev.ftsw;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.lwjgl.Sys;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author Dries007
 */
@Mod(modid = ForgeTwitchSubWhitelist.MODID)
public class ForgeTwitchSubWhitelist
{
    public static final String MODID = "ForgeTwitchSubWhitelist";
    // check that a user is subbed
    public static final String CHECK_TWITCH_URL = "https://api.twitch.tv/kraken/channels/$CHANNEL$/subscriptions/$USER$?oauth_token=$TOKEN$";
    // verify that the token works
    public static final String VERIFY_TWITCH_URL = "https://api.twitch.tv/kraken/channels/$CHANNEL$/subscriptions?limit=1&oauth_token=$TOKEN$";
    public static final String GET_TWITCH_NAME_URL = "http://doubledoordev.net/getTwitchName.php?uuid=$UUID$";
    public static final String NOT_LINKED = "You need to link your MC and Twitch accounts! Go to http://www.doubledoordev.net/?p=twitch";

    public static final Gson GSON = new Gson();
    public static final Map<String, Long> SUB_END_DATE_MAP = new HashMap<>();

    private Configuration configuration;

    private String kickMsg = "You must be subscribed to $CHANNEL$ to join this server. http://www.twitch.tv/$CHANNEL$";
    private String twitchToken;
    private String channel;
    private File jsonFile;

    @Mod.EventHandler
    public void init(FMLPreInitializationEvent event) throws IOException
    {
        configuration = new Configuration(event.getSuggestedConfigurationFile());
        syncConfig();

        FMLCommonHandler.instance().bus().register(this);

        jsonFile = new File(event.getModConfigurationDirectory(), MODID.concat(".json"));
        if (jsonFile.exists())
        {
            SUB_END_DATE_MAP.putAll(GSON.<Map<? extends String, ? extends Long>>fromJson(FileUtils.readFileToString(jsonFile), new TypeToken<Map<String, Long>>()
            {
            }.getType()));
        }

        if (Strings.isNullOrEmpty(twitchToken) || Strings.isNullOrEmpty(channel))
        {
            RuntimeException e = new RuntimeException("\n\nYour TwitchToken or Channel are missing from the " + MODID + " config.\n\n");
            e.setStackTrace(new StackTraceElement[0]);
            throw e;
        }

        event.getModLog().info("Trying out the channel & twitchtoken. This could take a couple of seconds.");
        try
        {
            //noinspection ResultOfMethodCallIgnored
            IOUtils.toString(new URL(VERIFY_TWITCH_URL.replace("$CHANNEL$", channel).replace("$TOKEN$", twitchToken)));
        }
        catch (IOException ex)
        {
            RuntimeException e = new RuntimeException("\n\nYour TwitchToken or Channel are wrong or expired, or you are not partnered. Update them in the " + MODID + " config.\n\nDO NOT POST THIS LOG ANYWHERE ONLINE WITHOUT REMOVING THE URL IN THE LINE BELOW!\n", ex);
            e.setStackTrace(new StackTraceElement[0]);
            throw e;
        }
    }

    @NetworkCheckHandler
    public boolean networkCheckHandler(Map<String, String> map, Side side)
    {
        return true;
    }

    @Mod.EventHandler
    public void init(FMLServerStartingEvent event)
    {
        if (event.getSide().isServer()) return;
        event.registerServerCommand(new InfoCommand());
    }

    @SubscribeEvent
    public void joinEvent(final FMLNetworkEvent.ServerConnectionFromClientEvent event)
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                if (event.isLocal) return;
                ServerConfigurationManager scm = FMLCommonHandler.instance().getMinecraftServerInstance().getConfigurationManager();
                scm.setWhiteListEnabled(true);

                try
                {
                    NetHandlerPlayServer handler = ((NetHandlerPlayServer) event.handler);

                    if (scm.func_152607_e(handler.playerEntity.getGameProfile())) return; // op or whitelisted

                    String twitchName = lookupUsername(handler.playerEntity.getGameProfile().getId());
                    if (Strings.isNullOrEmpty(twitchName))
                    {
                        handler.kickPlayerFromServer(NOT_LINKED);
                        return;
                    }
                    System.out.println(twitchName);
                    if (!SUB_END_DATE_MAP.containsKey(twitchName) || SUB_END_DATE_MAP.get(twitchName) - System.currentTimeMillis() > 0) // Not yet known or cache expired
                    {
                        try
                        {
                            //noinspection ResultOfMethodCallIgnored
                            IOUtils.toString(new URL(CHECK_TWITCH_URL.replace("$CHANNEL$", channel).replace("$TOKEN$", twitchToken).replace("$USER$", twitchName))); // this is what fails if not subbed
                            SUB_END_DATE_MAP.put(twitchName, System.currentTimeMillis() + (1000 * 60 * 60 * 24)); // 24h cache period
                            FileUtils.writeStringToFile(jsonFile, GSON.toJson(SUB_END_DATE_MAP));
                        }
                        catch (IOException e)
                        {
                            handler.kickPlayerFromServer(kickMsg.replace("$CHANNEL$", channel));
                        }
                    }
                }
                finally
                {
                    scm.setWhiteListEnabled(false);
                }
            }
        }).start();
    }

    public void syncConfig()
    {
        configuration.addCustomCategoryComment(MODID, "This information is required for server side operation.");

        twitchToken = configuration.getString("twitchToken", MODID, "", "Get it from http://www.doubledoordev.net/?p=twitch");
        channel = configuration.getString("channel", MODID, "", "");
        kickMsg = configuration.getString("kickMsg", MODID, kickMsg, "You can use $CHANNEL$ to insert the channel name.");

        if (configuration.hasChanged()) configuration.save();
    }

    public static String lookupUsername(UUID uuid)
    {
        String twitchName = null;
        try
        {
            twitchName = IOUtils.toString(new URL(GET_TWITCH_NAME_URL.replace("$UUID$", uuid.toString())));
            int firstLineEnd = twitchName.indexOf('\n');
            if (firstLineEnd != -1) twitchName = twitchName.substring(0, firstLineEnd);
            twitchName = twitchName.trim();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return twitchName;
    }
}
