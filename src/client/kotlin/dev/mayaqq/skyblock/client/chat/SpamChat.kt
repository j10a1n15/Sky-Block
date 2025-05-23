package dev.mayaqq.skyblock.client.chat

import dev.mayaqq.skyblock.client.config.Config
import dev.mayaqq.skyblock.client.config.categories.ChatConfig
import dev.mayaqq.skyblock.client.utils.pushPop
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Items
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.chat.ActionBarReceivedEvent
import tech.thatgravyboat.skyblockapi.api.events.chat.ChatReceivedEvent
import tech.thatgravyboat.skyblockapi.api.events.info.ActionBarWidget
import tech.thatgravyboat.skyblockapi.api.events.info.RenderActionBarWidgetEvent
import tech.thatgravyboat.skyblockapi.api.events.render.RenderHudEvent
import tech.thatgravyboat.skyblockapi.helpers.McClient
import tech.thatgravyboat.skyblockapi.utils.text.Text
import java.awt.Color
import java.util.*
import kotlin.math.sin

object SpamChat {

    private var messages = Collections.synchronizedList(ArrayList<Message>())
    private var previousRender = Date().time

    private var extraMessages = Config.extra.get().map(::Regex)
    private var extraUiMessages = Config.extraUi.get().map(::Regex)

    init {
        Config.extra.addListener { _, new ->
            extraMessages = new.mapNotNull {
                runCatching { Regex(it) }.getOrNull()
            }
        }
        Config.extraUi.addListener { _, new ->
            extraUiMessages = new.mapNotNull {
                runCatching { Regex(it) }.getOrNull()
            }
        }
    }

    @Subscription
    fun onHudRender(event: RenderHudEvent) {
        if (!Config.enabled) return
        if (McClient.options.hideGui) return

        val font = McClient.self.font
        val now = System.currentTimeMillis()
        val timePassed = now - previousRender

        val animDiv = timePassed.toDouble() / 1000.0
        previousRender = now

        messages.reversed().withIndex().forEach { (index, message) ->
            val messageWidth = font.width(message.component)
            message.height += (index * 10 - message.height) * (animDiv * 5)
            var animOnOff = 0.0
            if (message.time < 500) {
                animOnOff = 1 - message.time / 500.0
            }
            if (message.time > 3500) {
                animOnOff = (message.time - 3500) / 500.0
            }

            animOnOff *= 90.0
            animOnOff += 90.0
            animOnOff *= Math.PI / 180
            animOnOff = sin(animOnOff)
            animOnOff *= -1.0
            animOnOff += 1.0
            val x = -animOnOff * (messageWidth + 30)
            val y = message.height

            // render
            event.graphics.pushPop {
                translate(
                    event.graphics.guiWidth().toFloat() - (x + messageWidth).toFloat(),
                    event.graphics.guiHeight().toFloat() - y.toFloat(),
                    0.0F,
                )

                event.graphics.drawStringWithBackdrop(
                    font,
                    message.component,
                    0,
                    -50,
                    0,
                    0,
                )
            }
            message.time += timePassed
        }

        messages.removeIf { it.time > 4000 }
    }

    @Subscription
    fun onChatReceived(event: ChatReceivedEvent.Pre) {
        if (!Config.enabled) return

        if (extraMessages.isNotEmpty()) {
            for (regex in extraMessages) {
                if (regex.find(event.text) != null) {
                    event.cancel()
                    return
                }
            }
        }

        if (extraUiMessages.isNotEmpty()) {
            for (regex in extraUiMessages) {
                if (regex.find(event.text) != null) {
                    messages.add(Message(event.component, 0, 0.0))
                    event.cancel()
                    return
                }
            }
        }

        SpamMessage.entries.forEach { message ->
            val regex = message.regex
            val option = message.option()
            if (regex.find(event.text) == null) return@forEach
            when (option) {
                HidingOption.SEPARATE -> {
                    separate(event.component)
                    event.cancel()
                    return
                }

                HidingOption.TOAST -> {
                    McClient.self.toastManager.addToast(
                        SkyBlockToast(
                            Items.GRASS_BLOCK.defaultInstance,
                            Text.of("Hewwo").withColor(Color.red.rgb),
                            Text.of("Person joined!!"),
                        ),
                    )
                    event.cancel()
                    return
                }

                HidingOption.HIDE -> {
                    event.cancel()
                    return
                }

                else -> {
                    return@forEach
                }
            }
        }
    }

    private var abilityUses = 0
    private var lastAbilityUsed = ""
    private val abilityRegex = Regex("§.-?(?<amount>[\\d,]+) Mana \\(§.(?<ability>[^)]+)§.\\)")

    @Subscription
    fun onActionBarWidget(event: ActionBarReceivedEvent.Pre) {

        if (!Config.enabled) return
        if (!Config.abilityUse) return

        event.coloredText.split("     ").forEach { message ->
            if (abilityRegex.matches(message)) {
                if (lastAbilityUsed != message || abilityUses % 3 == 0) {
                    lastAbilityUsed = message
                    abilityUses = 1
                    messages.add(Message(Component.literal(lastAbilityUsed), 0, 0.0))
                } else abilityUses++
            }
        }
    }

    @Subscription
    fun onWidgetRender(event: RenderActionBarWidgetEvent) {
        if (Config.abilityUse && event.widget == ActionBarWidget.ABILITY) {
            event.cancel()
        }
    }


    private fun separate(string: String) = separate(Text.of(string))

    private fun separate(component: Component) {
        val message = Message(component, 0, 0.0)
        messages.add(message)
    }

    data class Message(val component: Component, var time: Long, var height: Double)
}
