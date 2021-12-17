package me.reidj.bridgebuilders

import me.reidj.bridgebuilders.data.DefaultKit
import org.bukkit.Bukkit
import org.bukkit.GameMode
import ru.cristalix.core.realm.RealmStatus.GAME_STARTED_CAN_JOIN
import ru.cristalix.core.realm.RealmStatus.GAME_STARTED_RESTRICTED

lateinit var winMessage: String
const val needPlayers: Int = 3
val kit = DefaultKit

enum class Status(val lastSecond: Int, val now: (Int) -> Int) {
    STARTING(5, { it ->
        // Если набор игроков начался, обновить статус реалма
        if (it == 40)
            realm.status = GAME_STARTED_CAN_JOIN

        val players = Bukkit.getOnlinePlayers()
        // Обновление шкалы онлайна
        players.forEach {
            me.reidj.bridgebuilders.mod.ModTransfer()
                .integer(slots)
                .integer(players.size)
                .boolean(true)
                .send("bridge:online", app.getUser(it))
        }
        var actualTime = it

        // Если время вышло и пора играть
        if (it / 20 == STARTING.lastSecond) {
            // Начать отсчет заново, так как мало игроков
            if (players.size + needPlayers < slots)
                actualTime = 1
            else {
                // Обновление статуса реалма, чтобы нельзя было войти
                realm.status = GAME_STARTED_RESTRICTED
                games++
                // Удаление игроков если они оффлайн
                teams.forEach {
                    it.players.removeIf { player ->
                        val craftPlayer = Bukkit.getPlayer(player)
                        craftPlayer == null || !craftPlayer.isOnline
                    }
                }
                // Заполение команд
                Bukkit.getOnlinePlayers().forEach { player ->
                    player.inventory.clear()
                    player.openInventory.topInventory.clear()
                    if (!teams.any { it.players.contains(player.uniqueId) })
                        teams.minByOrNull { it.players.size }!!.players.add(player.uniqueId)
                }
                // Телепортация игроков
                teams.forEach { team ->
                    team.players.forEach {
                        val player = Bukkit.getPlayer(it) ?: return@forEach
                        player.gameMode = GameMode.SURVIVAL
                        player.itemOnCursor = null
                        player.teleport(team.spawn)
                        player.inventory.armorContents = kit.armor
                        player.inventory.addItem(kit.sword, kit.pickaxe, kit.bread)
                        team.team!!.addEntry(player.name)

                        me.reidj.bridgebuilders.mod.ModTransfer()
                            .string(org.apache.commons.lang.RandomStringUtils.random(5))
                            .double(team.teleport.x)
                            .double(team.teleport.y)
                            .double(team.teleport.z)
                            .string("mcpatcher/cit/among_us/alert.png")
                            .send("func:marker-create", getByUuid(it))

                        team.requiredBlocks.entries.forEachIndexed { index, block ->
                            me.reidj.bridgebuilders.mod.ModTransfer()
                                .integer(index)
                                .integer(block.value.needTotal)
                                .integer(block.value.collected)
                                .string(block.value.title)
                                .integer(block.key)
                                .send("bridge:init", getByUuid(it))
                        }
                    }
                }
                // Список игроков
                val users = players.map { app.getUser(it) }
                users.forEach { user ->
                    // Отправить информацию о начале игры клиенту
                    me.reidj.bridgebuilders.mod.ModTransfer().send("bridge:start", user)
                }
                // Выдача активных ролей
                activeStatus = GAME
                actualTime + 1
            }
        }
        // Если набралось максимальное количество игроков, то сократить время ожидания до 10 секунд
        if (players.size == slots && it / 20 < STARTING.lastSecond - 10)
            actualTime = (STARTING.lastSecond - 10) * 20
        actualTime
    }),
    GAME(330, { time ->
        // Обновление шкалы времени
        if (time % 20 == 0) {
            Bukkit.getOnlinePlayers().forEach {
                me.reidj.bridgebuilders.mod.ModTransfer()
                    .integer(GAME.lastSecond)
                    .integer(time)
                    .boolean(false)
                    .send("bridge:online", app.getUser(it))
            }
            if (time == 180)
                me.reidj.bridgebuilders.mod.ModHelper.allNotification("Телепорт на чужие базы теперь §aдоступен")
        }
        // Проверка на победу
        if (me.reidj.bridgebuilders.util.WinUtil.check4win()) {
            activeStatus = END
        }
        time
    }),
    END(340, { time ->
        if (GAME.lastSecond * 20 + 10 == time) {
            // Выдача побед выжившим и выдача всем доп. игр
            Bukkit.getOnlinePlayers().forEach {
                val user = app.getUser(it)
                user.stat.games++
            }
        }
        teams.forEach {
            it.players.forEach { player ->
                try {
                    val find = Bukkit.getPlayer(player)
                    if (find != null)
                        it.team!!.removePlayer(find)
                } catch (ignored: Exception) {
                }
            }
            it.players.clear()
        }
        when {
            time == GAME.lastSecond * 20 + 20 * 10 -> {
                app.restart()
                -1
            }
            time < (END.lastSecond - 10) * 20 -> (END.lastSecond - 10) * 20
            else -> time
        }
    }),
}