package net.modfest.utilities;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.modfest.utilities.config.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.http.HttpClient;
import java.util.List;

public class ModFestUtilities implements ModInitializer {
    public static final Logger LOGGER = LogManager.getLogger();
    public static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    public static final Gson GSON = new GsonBuilder().create();
    public static final Config CONFIG = new Config();
    
    private static JDA discord;

    @Override
    public void onInitialize() {
        CONFIG.load();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("modfest")
                        .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(4))
                        .executes(context -> 0)
                        .then(CommandManager.literal("reload").executes(context -> {
                            CONFIG.load();
                            restartJda(context.getSource().getServer());
                            context.getSource().sendFeedback(Text.literal("Reloaded ModFestChat config."), true);
                            return 0;
                        }))
                )
        );

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            restartJda(server);
            WebHookJson.createSystem("The server is starting...").send();
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            WebHookJson.createSystem("The server has started.").send();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ModFestUtilities.shutdown();
            WebHookJson.createSystem("The server has shutdown.").send();
        });
        
        new okio.Buffer(); //TODO remove
    }

    public static void restartJda(MinecraftServer server) {
        if (discord != null) {
            shutdown();
        }

        if(CONFIG.getToken().isEmpty()) {
            LOGGER.warn("No Discord token is specified. Mirroring from Discord to Minecraft is not possible.");
        } else if(CONFIG.getChannel().isEmpty()) {
            LOGGER.warn("No Discord channel ID is specified. Mirroring from Discord to Minecraft is not possible.");
        } else {
            try {
                discord = JDABuilder.createDefault(CONFIG.getToken())
                        .enableIntents(List.of(GatewayIntent.MESSAGE_CONTENT))
                        .addEventListeners(new DiscordChannelListener(server, CONFIG.getChannel()))
                        .build();
            } catch (Exception e) {
                LOGGER.warn("Exception initializing JDA", e);
            }
        }
    }

    public static void shutdown() {
        if (discord != null) {
            discord.shutdown();
            discord = null; // allow garbage collection, as the event listener has a reference to the MinecraftServer.
        }
    }
}
