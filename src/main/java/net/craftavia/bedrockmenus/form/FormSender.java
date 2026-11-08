package net.craftavia.bedrockmenus.form;

import org.bukkit.entity.Player;

/** Delivers a form to a player. Answer callbacks may arrive on any thread. */
public interface FormSender {
    void simple(Player player, SimpleSpec spec);
}
