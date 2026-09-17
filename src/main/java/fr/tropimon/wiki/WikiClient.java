package fr.tropimon.wiki;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

public final class WikiClient implements ClientModInitializer {
  public void onInitializeClient() {
    var key =
        KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                "key.tropimon_wiki.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                "category.tropimon_wiki"));
    ClientTickEvents.END_CLIENT_TICK.register(
        c -> {
          while (key.wasPressed()) c.setScreen(new WikiScreen());
        });
    TropimonSelfUpdater.start(LoggerFactory.getLogger("tropimon_wiki"));
  }
}
