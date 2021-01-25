/*
 * Copyright (c) 2020, Wild Adventure
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 * 4. Redistribution of this software in source or binary forms shall be free
 *    of all charges or fees to the recipient of this software.
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
package com.gmail.filoghost.antiafk;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import net.md_5.bungee.api.chat.TextComponent;
import net.tecnocraft.utils.utils.CommandFramework;
import net.tecnocraft.utils.utils.TextComponentBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

import net.cubespace.yamler.YamlerConfigurationException;

public class AntiAFK extends JavaPlugin implements Listener {

    private static Map<Player, Timestamp> lastDirectionChange = new HashMap<>();
    private static long kickTimeoutMillis;
    private static long suffucationSeconds;

    @Override
    public void onEnable() {
        try {
            new MainConfig(this).init();
        } catch (YamlerConfigurationException e) {
            e.printStackTrace();
            getLogger().severe("Impossibile leggere config.yml!");
        }

        if (MainConfig.kickTimeoutMinutes <= 0) {
            getLogger().info("AntiAFK disabilitato.");
            return;
        }

        kickTimeoutMillis = TimeUnit.MINUTES.toMillis(MainConfig.kickTimeoutMinutes);
        suffucationSeconds = TimeUnit.SECONDS.toMillis(MainConfig.suffucationSeconds);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long now = System.currentTimeMillis();
            List<Player> playersToKick = null;

            for (Entry<Player, Timestamp> entry : lastDirectionChange.entrySet()) {
                long timeSinceDirectionChange = now - entry.getValue().get();

                if (timeSinceDirectionChange > kickTimeoutMillis) {
                    if (playersToKick == null)
                        playersToKick = new ArrayList<>();
                    playersToKick.add(entry.getKey());
                }
            }

            if (playersToKick != null)
                for (Player player : playersToKick) {
                    if (player.getVehicle() != null)
                        if (player.getVehicle() instanceof ArmorStand)
                            if (((ArmorStand) player.getVehicle()).getName().startsWith("VP"))
                                continue;

                    player.kickPlayer(MainConfig.kickMessage);
                }

        }, 20L, 20L);

        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("lastMove").setExecutor(this);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        if (event.getPlayer().hasPermission("antiafk.bypass")) return;
        lastDirectionChange.put(event.getPlayer(), new Timestamp(System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        lastDirectionChange.remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMove(PlayerMoveEvent event) {
        if (event.getPlayer().hasPermission("antiafk.bypass")) return;
        if (isDifferentLookDirection(event.getFrom(), event.getTo())) {
            Timestamp timestamp = lastDirectionChange.get(event.getPlayer());
            if (timestamp != null) timestamp.set(System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerConsumeItem(PlayerItemConsumeEvent event) {
        if (event.getPlayer().hasPermission("antiafk.bypass")) return;
        Timestamp timestamp = lastDirectionChange.get(event.getPlayer());
        if (timestamp != null) timestamp.set(System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBookEvent(PlayerEditBookEvent event) {
        if (event.getPlayer().hasPermission("antiafk.bypass")) return;
        Timestamp timestamp = lastDirectionChange.get(event.getPlayer());
        if (timestamp != null) timestamp.set(System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onAFKSuffucation(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (player.hasPermission("antiafk.bypass")) return;
            if (event.getCause().equals(EntityDamageEvent.DamageCause.DROWNING))
                if (lastDirectionChange.get(player) != null)
                    if (System.currentTimeMillis() - lastDirectionChange.get(player).get() > suffucationSeconds) {
                        event.setDamage(0);
                        for (Player staffer : Bukkit.getOnlinePlayers())
                            if (staffer.hasPermission("tecnoroleplay.admin"))
                                staffer.spigot().sendMessage(
                                        new TextComponent(ChatColor.GOLD + "[AntiAFK] " + ChatColor.YELLOW + player.getName() + " sta soffocando mentre è AFK! "),
                                        new TextComponentBuilder().setText("[TP]").setColor(net.md_5.bungee.api.ChatColor.DARK_GRAY).setHover(ChatColor.GRAY + "Clicca per teletrasportarti.").setCommand("/tp " + player.getLocation().getBlockX() + " " + player.getLocation().getBlockY() + " " + player.getLocation().getBlockZ()).build()
                                );
                    }
        }
    }

    private boolean isDifferentLookDirection(Location from, Location to) {
        return from.getYaw() != to.getYaw() || from.getPitch() != from.getPitch();
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        CommandFramework.Validator.Permission(sender, "tecnoroleplay.admin");

        if (args.length != 1) return false;

        Player player = Bukkit.getPlayer(args[0]);

        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Questo giocatore non è online!");
            return true;
        }

        Timestamp timestamp = lastDirectionChange.get(player);

        if (timestamp == null) {
            sender.sendMessage(ChatColor.RED + "Non è stato rilevato l'ultimo movimento di " + player.getName() + "!");
            return true;
        }

        sender.sendMessage(ChatColor.DARK_AQUA + "Ultimo movimento di " + player.getName() + ": " + ChatColor.AQUA + millisToDate(timestamp.get()));
        return true;
    }

    public static String millisToDate(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        Date date = new Date(millis);
        return sdf.format(date);
    }

}
