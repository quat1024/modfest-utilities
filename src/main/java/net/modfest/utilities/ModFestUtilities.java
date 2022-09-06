package net.modfest.utilities;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.LiteralText;
import net.modfest.utilities.config.Config;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class ModFestUtilities implements ModInitializer {
    public static final Logger LOGGER = LogManager.getLogger();
    public static final OkHttpClient client = new OkHttpClient.Builder()
            .protocols(Collections.singletonList(Protocol.HTTP_1_1))
            .build();
    public static final Gson GSON = new GsonBuilder().create();
    private static JDA discord;

    @Override
    public void onInitialize() {
        Config.getInstance().load();

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) ->
                dispatcher.register(CommandManager.literal("modfest")
                        .requires(serverCommandSource -> serverCommandSource.hasPermissionLevel(4))
                        .executes(context -> 0)
                        .then(CommandManager.literal("reload").executes(context -> {
                            Config.getInstance().load();
                            restart(context.getSource().getMinecraftServer());
                            context.getSource().sendFeedback(new LiteralText("Reloaded ModFestChat config."), true);
                            return 0;
                        }))
                )
        );

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            restart(server);
            WebHookJson.createSystem("The server is starting...").send();
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            WebHookJson.createSystem("The server has started.").send();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ModFestUtilities.shutdown();
            WebHookJson.createSystem("The server has shutdown.").send();
        });
    }

    public static void restart(MinecraftServer server) {
        if (discord != null) {
            shutdown();
        }
        if (!Config.getInstance().getChannel().isEmpty() && !Config.getInstance().getToken().isEmpty()) {
            try {
                discord = JDABuilder.createDefault(Config.getInstance().getToken())
                        .enableIntents(List.of(GatewayIntent.MESSAGE_CONTENT))
                        .addEventListeners(new DiscordChannelListener(server))
                        .build();
            } catch (LoginException e) {
                e.printStackTrace();
            }
        }
    }

    public static void shutdown() {
        if (discord != null) {
            discord.shutdown();
            discord = null; // allow garbage collection, as the event listener has a reference to the MinecraftServer.
        }
    }

    public static void handleCrashReport(String report) {
        LOGGER.info("[ModFest] Publishing crash report.");
        RequestBody body = RequestBody.create(report, MediaType.get("text/html"));
        Request request = new Request.Builder()
                .url("https://hastebin.com/documents")
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            ResponseBody respBody = response.body();
            if (respBody != null) {
                HasteBinResponse haste = GSON.fromJson(respBody.string(), HasteBinResponse.class);
                LOGGER.info("[ModFest] Crash report available at: https://hastebin.com/" + haste.key);
                WebHookJson.createSystem("The server has crashed!\nReport: https://hastebin.com/" + haste.key).send().get();
            }
        } catch (IOException | ExecutionException | InterruptedException e) {
            ModFestUtilities.LOGGER.warn("[ModFest] Crash log failed to send.", e);
        }
    }

    public static class HasteBinResponse {
        @Expose public String key = "";
    }
}
