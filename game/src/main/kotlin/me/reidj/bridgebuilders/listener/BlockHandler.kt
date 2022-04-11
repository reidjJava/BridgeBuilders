package me.reidj.bridgebuilders.listener

import clepto.bukkit.B
import me.reidj.bridgebuilders.app
import me.reidj.bridgebuilders.getByUuid
import me.reidj.bridgebuilders.mod.ModHelper
import me.reidj.bridgebuilders.teams
import me.reidj.bridgebuilders.util.WinUtil
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.ItemStack
import kotlin.math.min

object BlockHandler : Listener {

    @EventHandler
    fun BlockPlaceEvent.handle() {
        if (teams.all {
                block.location.distanceSquared(it.spawn) > 60 * 72 || app.getBridge(it).contains(block.location) ||
                        block.location.distanceSquared(it.spawn) < 4 * 4
            })
            isCancelled = true
    }

    @EventHandler
    fun BlockBreakEvent.handle() {
        val team = teams.filter { it.players.contains(player.uniqueId) }[0]
        when (val idAndData = block.typeId to block.data) {
            15 to 0.toByte() -> {
                //BattlePassUtil.update(player, BREAK, 1)
                team.breakBlocks[block.location] = idAndData
            }
            56 to 0.toByte() -> {
                //BattlePassUtil.update(player, BREAK, 1)
                team.breakBlocks[block.location] = idAndData
            }
            16 to 0.toByte() -> {
                //BattlePassUtil.update(player, BREAK, 1)
                team.breakBlocks[block.location] = idAndData
            }
            14 to block.data -> team.breakBlocks[block.location] = idAndData
            17 to block.data -> team.breakBlocks[block.location] = idAndData
            5 to block.data -> team.breakBlocks[block.location] = idAndData
            17 to block.data -> team.breakBlocks[block.location] = idAndData
            1 to 5.toByte() -> team.breakBlocks[block.location] = idAndData
            12 to 0.toByte() -> team.breakBlocks[block.location] = idAndData
        }
        if (app.getBridge(team).contains(block.location)) {
            isCancelled = true
            return
        } else if (block.type == Material.BEACON && app.getCountBlocksTeam(team)) {
            isCancelled = true
            return
        } else if (teams.any { block.location.distanceSquared(it.spawn) < 4 * 4 }) {
            isCancelled = true
            return
        }
        if (block.type == Material.BEACON && !app.getCountBlocksTeam(team)) {
            //BattlePassUtil.update(player, BREAK, 1)
            val winner = teams.filter { it.players.contains(player.uniqueId) }[0]
            ModHelper.allNotification("Победила команда ${winner.color.chatFormat + winner.color.teamName}")
            B.bc(" ")
            B.bc("§b―――――――――――――――――")
            B.bc("" + winner.color.chatFormat + winner.color.teamName + " §f победили!")
            B.bc(" ")
            B.bc("§e§lТОП ПРИНЕСЁННЫХ БЛОКОВ")
            winner.players.map { getByUuid(it) }.sortedBy { -it.collectedBlocks }
                .subList(0, min(3, winner.players.size))
                .forEachIndexed { index, user ->
                    B.bc(" §l${index + 1}. §e" + user.player?.name + " §с" + user.collectedBlocks + " блоков принесено")
                }
            B.bc("§b―――――――――――――――――")
            B.bc(" ")

            winner.players.map(getByUuid).forEach { user ->
                //BattlePassUtil.update(user.player!!, WIN, 1)
                WinUtil.end(user, team)
            }
            B.postpone(5 * 20) { app.restart() }
            return
        }
        if (block.type == Material.IRON_ORE) {
            block.type = Material.AIR
            player.inventory.addItem(ItemStack(Material.IRON_INGOT))
        } else if (block.type == Material.GOLD_ORE) {
            block.type = Material.AIR
            player.inventory.addItem(ItemStack(Material.GOLD_ORE))
        }
    }
}