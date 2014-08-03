package com.serendipitymc.refund.refund;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.serendipitymc.refund.managers.RefundHandler;

public class RefundListener implements Listener{

	private refund plugin;

    public RefundListener(refund instance) {
        plugin = instance;
    }

    @EventHandler
    public void onLogin(PlayerJoinEvent event) {
        final Player p = event.getPlayer();
        if (p.hasPermission("refund.execute")) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin,
                    new Runnable() {
                public void run() {
                	try {
                    RefundHandler rh = new RefundHandler();
                    int executableRefunds = rh.getExecutableAmount();
                    if (executableRefunds > 0) {
                        p.sendMessage(ChatColor.GOLD + "[SSRefund]" + ChatColor.LIGHT_PURPLE + " There is " + ChatColor.RED + Integer.toString(executableRefunds) + ChatColor.LIGHT_PURPLE + " refunds ready to be executed");
                    }
                	} catch (Exception e) {
                		p.sendMessage(ChatColor.GOLD + "[SSRefund] " + ChatColor.LIGHT_PURPLE + "Something went wrong. Please report error ssr201");
                	}

                }
            }, 60L);
        }
    }
	
	
}
