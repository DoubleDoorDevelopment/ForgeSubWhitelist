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
import com.mojang.authlib.GameProfile;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 * @author Dries007
 */
public class InfoCommand extends CommandBase
{
    @Override
    public String getCommandName()
    {
        return "ftsw";
    }

    @Override
    public String getCommandUsage(ICommandSender sender)
    {
        return "/ftsw [player]";
    }

    @Override
    public void processCommand(final ICommandSender sender, final String[] args)
    {
        if (args.length == 0)
        {
            sender.addChatMessage(new ChatComponentText(ForgeTwitchSubWhitelist.MODID).setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA)));
            sender.addChatMessage(new ChatComponentText("Use this command to find out who a mc account belongs too."));
        }
        else
        {
            new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    for (String arg : args)
                    {
                        GameProfile profile = MinecraftServer.getServer().func_152358_ax().func_152655_a(arg);
                        String twitchName = null;
                        try
                        {
                            twitchName = IOUtils.toString(new URL(String.format(ForgeTwitchSubWhitelist.GET_TWITCH_NAME_URL, profile.getId().toString()))).trim();
                        }
                        catch (IOException e)
                        {

                        }
                        if (Strings.isNullOrEmpty(twitchName)) twitchName = "???";
                        sender.addChatMessage(new ChatComponentText(arg).appendText(" -> ").appendSibling(new ChatComponentText(twitchName).setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GOLD))));
                    }
                }
            }).start();
        }
    }

    @Override
    public List addTabCompletionOptions(ICommandSender p_71516_1_, String[] p_71516_2_)
    {
        return getListOfStringsMatchingLastWord(p_71516_2_, MinecraftServer.getServer().getConfigurationManager().getAvailablePlayerDat());
    }
}
