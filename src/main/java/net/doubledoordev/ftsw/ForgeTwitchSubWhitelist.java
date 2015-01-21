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
import net.minecraftforge.common.config.Configuration;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

/**
 * @author Dries007
 */
@Mod(modid = ForgeTwitchSubWhitelist.MODID)
public class ForgeTwitchSubWhitelist
{
    public static final String MODID = "ForgeTwitchSubWhitelist";
    public static final String CHECK_TWITCH_URL = "http://www.twitch.tv/api/channels/%s/subscriptions/%s?oauth_token=%s";
    public static final String GET_TWITCH_NAME_URL = "http://doubledoordev.net/getTwitchName.php?uuid=%s";

    @Mod.Instance(MODID)
    public static ForgeTwitchSubWhitelist instance;
    private Configuration configuration;

    private String twitchToken;
    private String channel;

    @Mod.EventHandler
    public void init(FMLPreInitializationEvent event)
    {
        FMLCommonHandler.instance().bus().register(this);

        configuration = new Configuration(event.getSuggestedConfigurationFile());

        syncConfig();
    }

    @NetworkCheckHandler
    public boolean networkCheckHandler(Map<String, String> map, Side side)
    {
        return true;
    }

    @Mod.EventHandler
    public void init(FMLServerStartingEvent event)
    {
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

                NetHandlerPlayServer handler = ((NetHandlerPlayServer) event.handler);

                if (scm.func_152607_e(handler.playerEntity.getGameProfile())) return; // op or whitelisted

                String twitchName;
                try
                {
                    twitchName = IOUtils.toString(new URL(String.format(GET_TWITCH_NAME_URL, handler.playerEntity.getUniqueID().toString()))).trim();
                }
                catch (IOException e)
                {
                    twitchName = handler.playerEntity.getCommandSenderName();
                }
                if (Strings.isNullOrEmpty(twitchName)) handler.kickPlayerFromServer("You need to link you MC and Twitch on:\nhttp://www.doubledoordev.net/?p=twitch");
                try
                {
                    //noinspection ResultOfMethodCallIgnored
                    IOUtils.toString(new URL(String.format(CHECK_TWITCH_URL, channel, twitchName, twitchToken)));
                }
                catch (IOException e)
                {
                    handler.kickPlayerFromServer(String.format("You must be subscribed to %s to join this server.", channel));
                }

                scm.setWhiteListEnabled(false);
            }
        }).start();
    }

    public void syncConfig()
    {
        twitchToken = configuration.getString("twitchToken", MODID, "", "Get it from http://www.doubledoordev.net/?p=twitch");
        channel = configuration.getString("channel", MODID, "", "Cap sensitive!");

        if (configuration.hasChanged()) configuration.save();
    }
}
