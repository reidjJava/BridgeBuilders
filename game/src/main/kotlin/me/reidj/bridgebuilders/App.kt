package me.reidj.bridgebuilders

import clepto.bukkit.B
import dev.implario.bukkit.platform.Platforms
import dev.implario.platform.impl.darkpaper.PlatformDarkPaper
import implario.ListUtils
import me.func.mod.Anime
import me.func.mod.Banners
import me.func.mod.Kit
import me.reidj.bridgebuilders.content.Lootbox
import me.reidj.bridgebuilders.data.BlockPlan
import me.reidj.bridgebuilders.data.Bridge
import me.reidj.bridgebuilders.data.Team
import me.reidj.bridgebuilders.listener.ConnectionHandler
import me.reidj.bridgebuilders.listener.DamageListener
import me.reidj.bridgebuilders.listener.DefaultListener
import me.reidj.bridgebuilders.listener.GlobalListeners
import me.reidj.bridgebuilders.map.MapType
import me.reidj.bridgebuilders.mod.ModHelper
import me.reidj.bridgebuilders.top.TopManager
import me.reidj.bridgebuilders.user.User
import me.reidj.bridgebuilders.util.ArrowEffect
import me.reidj.bridgebuilders.util.MapLoader
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import ru.cristalix.core.datasync.EntityDataParameters
import ru.cristalix.core.formatting.Color
import ru.cristalix.core.realm.RealmId
import java.util.*
import java.util.stream.Collectors

const val GAMES_STREAK_RESTART = 6

lateinit var app: App

val map = MapLoader.load(MapType.AQUAMARINE.data.title)
val LOBBY_SERVER: RealmId = RealmId.of("TEST-56")
var activeStatus = Status.STARTING
var games = 0

val teams = map.getLabels("team").map {
    val data = it.tag.split(" ")
    val team = data[0]
    Team(
        mutableListOf(),
        Color.valueOf(data.first().uppercase()),
        it,
        map.getLabel("$team-teleport"),
        data[3].toFloat(),
        data[4].toFloat(),
        false,
        mutableMapOf(),
        Bridge(
            Vector(data[1].toInt(), 0, data[2].toInt()),
            map.getLabel("$team-x"),
            map.getLabel("$team-z"),
        ),
        mutableMapOf()
    )
}

class App : JavaPlugin() {

    override fun onEnable() {
        B.plugin = this
        app = this
        Platforms.set(PlatformDarkPaper())
        EntityDataParameters.register()

        Anime.include(Kit.EXPERIMENTAL, Kit.STANDARD, Kit.NPC)

        BridgeBuildersInstance(this, { getUser(it) }, { getUser(it) }, map, 16)
        realm.readableName = "BridgeBuilders ${realm.realmId.id}"
        realm.lobbyFallback = LOBBY_SERVER

        teams.forEach { team -> BlockPlan.values().forEach { team.collected[it] = 0 } }

        // Регистрация обработчиков событий
        B.events(
            GlobalListeners,
            ConnectionHandler,
            DefaultListener,
            DamageListener,
            Lootbox
        )

        // Создаю полигон
        teams.forEach { team ->
            me.func.mod.Glow.addPlace(
                me.func.protocol.GlowColor.GREEN,
                team.teleport.x + 0.5,
                team.teleport.y,
                team.teleport.z + 0.5
            ) { player ->
                if (!team.isActiveTeleport)
                    return@addPlace
                val playerTeam = teams.filter { team -> team.players.contains(player.uniqueId) }
                if (player.location.distanceSquared(playerTeam[0].teleport) < 4 * 4) {
                    val enemyTeam = ListUtils.random(teams.stream()
                        .filter { enemy -> !enemy.players.contains(player.uniqueId) }
                        .collect(Collectors.toList()))
                    player.teleport(enemyTeam.spawn)
                    enemyTeam.players.map { uuid -> Bukkit.getPlayer(uuid) }.forEach { enemy ->
                        enemy.playSound(
                            player.location,
                            Sound.ENTITY_ENDERDRAGON_GROWL,
                            1f,
                            1f
                        )
                    }
                } else {
                    player.teleport(playerTeam[0].spawn)
                }
                team.isActiveTeleport = false
                B.postpone(20 * 180) {
                    team.isActiveTeleport = true
                    team.players.map { uuid -> getByUuid(uuid) }.forEach { user ->
                        ModHelper.notification(
                            user,
                            "Телепорт на чужие базы теперь §aдоступен"
                        )
                        user.player!!.playSound(
                            user.player!!.location,
                            Sound.BLOCK_PORTAL_AMBIENT,
                            1.5f,
                            1.5f
                        )
                    }
                }
            }
        }

        // Создание баннера
        Banners.new {
            x = 6.0
            y = 95.0
            z = -1.2
            opacity = 0.0
            content = "Сломай меня"
            height = 10
            weight = 10
            watchingOnPlayer = true
        }

        // Рисую эффект выстрела
        ArrowEffect().arrowEffect(this)

        // Запуск игрового таймера
        timer = Timer()
        timer.runTaskTimer(this, 10, 1)

        TopManager()

        teams.forEach { generateBridge(it) }
    }

    fun restart() {
        activeStatus = Status.STARTING
        Bukkit.getOnlinePlayers().forEach { it.kickPlayer("Выключение сервера.") }
        Bukkit.unloadWorld(map.name, false)

        // Полная перезагрузка если много игр наиграно
        if (games > GAMES_STREAK_RESTART)
            Bukkit.shutdown()
    }

    fun getUser(player: Player): User {
        return getUser(player.uniqueId)
    }

    fun getUser(uuid: UUID): User {
        return userManager.getUser(uuid)
    }

    fun addBlock(team: Team) {
        val toPlace = team.collected.filter {
            (team.bridge.blocks[it.key.material.id to it.key.blockData]
                ?: listOf()).size > it.key.needTotal - it.value
        }
        var nearest: Location? = null
        var data: Pair<Int, Byte>? = null
        team.bridge.blocks.filter { (key, _) ->
            toPlace.keys.any { it.material.id == key.first }
        }.forEach { (key, value) ->
            val tempNearest = value.minByOrNull { it.distanceSquared(team.spawn) }
            if (nearest == null || (tempNearest != null &&
                        tempNearest.distanceSquared(team.spawn) < nearest!!.distanceSquared(team.spawn))
            ) {
                nearest = tempNearest
                data = key
            }
        }
        if (nearest != null) {
            nearest?.block?.setTypeIdAndData(data!!.first, data!!.second, false)
            team.bridge.blocks[data]?.let {
                if (it.isEmpty())
                    team.bridge.blocks.remove(data)
                else
                    it.remove(nearest)
            }
        }
    }

    private fun generateBridge(team: Team): Bridge {
        val bridge = Bridge(team.bridge.toCenter, team.bridge.start, team.bridge.end, team.bridge.blocks)
        getBridge(team).forEach { current ->
            val currentBlock = current.block.type.id to current.block.data
            val blockList = bridge.blocks[currentBlock]
            if (blockList != null)
                blockList.add(current)
            else
                bridge.blocks[currentBlock] = mutableListOf(current)

            current.block.setTypeAndDataFast(0, 0)
        }
        return bridge
    }

    fun getBridge(team: Team): MutableList<Location> {
        val vector = team.bridge.toCenter
        val bridge = Bridge(vector, team.bridge.start, team.bridge.end, team.bridge.blocks)
        val length = 84
        val width = 16
        val height = 30
        val blockLocation = mutableListOf<Location>()

        repeat(length) { len ->
            repeat(width) { xOrZ ->
                repeat(height) { y ->
                    blockLocation.add(
                        Location(
                            map.world,
                            bridge.start.x + len * vector.x + xOrZ * vector.z,
                            bridge.start.y + y,
                            bridge.start.z + len * vector.z + xOrZ * vector.x,
                        )
                    )
                }
            }
        }
        return blockLocation
    }
}