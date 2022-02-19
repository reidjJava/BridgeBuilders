package me.reidj.bridgebuilders.donate.impl

import dev.implario.bukkit.item.item
import me.reidj.bridgebuilders.donate.DonatePosition
import me.reidj.bridgebuilders.donate.MoneyFormatter
import me.reidj.bridgebuilders.donate.Rare
import me.reidj.bridgebuilders.user.User
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

enum class NameTag(private val title: String, private val price: Int, private val rare: Rare) : DonatePosition {
    NONE("Отсутствует", 0, Rare.COMMON),
    BUILDER("Строитель", 128, Rare.COMMON),
    HARD_WORKER("Работяга", 480, Rare.COMMON),
    PAVEMENT("Мостовой", 640, Rare.COMMON),
    MASON("Каменщик", 1280, Rare.RARE),
    RALPH("Ральф", 1280, Rare.RARE),
    DESIGNER("Дизайнер", 1600, Rare.RARE),
    DROGBAR("Дрогбар", 1600, Rare.RARE),
    CHIROPRACTOR("Костоправ", 2048, Rare.EPIC),
    MUSKETEER("Мушкетёр", 2048, Rare.EPIC),
    ARCHITECT("Архитектор", 4096, Rare.LEGENDARY),
    ANARCHIST("Анархист", 4864, Rare.LEGENDARY);

    override fun getTitle(): String {
        return title
    }

    override fun getPrice(): Int {
        return price
    }

    override fun getRare(): Rare {
        return rare
    }

    override fun getIcon(): ItemStack {
        return item {
            type = Material.CLAY_BALL
            nbt("other", "pets1")
            text(rare.with("префикс $title") + "\n\n§fРедкость: ${rare.getColored()}\n§fСтоимость: ${MoneyFormatter.texted(price)}")
        }
    }

    override fun give(user: User) {
        user.stat.activeNameTag = this
        user.stat.donate.add(this)
    }

    override fun isActive(user: User): Boolean {
        return user.stat.activeNameTag == this
    }

    override fun getName(): String {
        return name
    }

}