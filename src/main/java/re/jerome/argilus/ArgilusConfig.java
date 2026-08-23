package re.jerome.argilus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

// Gameplay values live here rather than scattered as constants.
public record ArgilusConfig(int radius, int harvestIntervalTicks, int scanIntervalTicks) {
	private static final ArgilusConfig DEFAULTS = new ArgilusConfig(12, 20, 40);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static ArgilusConfig current = DEFAULTS;

	public static ArgilusConfig get() {
		return current;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("argilus.json");
		ArgilusConfig read = DEFAULTS;

		if (Files.exists(path)) {
			try (BufferedReader reader = Files.newBufferedReader(path)) {
				ArgilusConfig parsed = GSON.fromJson(reader, ArgilusConfig.class);
				if (parsed != null) {
					read = parsed;
				}
			} catch (IOException | JsonParseException e) {
				Argilus.LOGGER.warn("Unreadable config at {}, using defaults", path, e);
			}
		}

		// A hand-edited file must not be able to make the golem scan a huge
		// volume every tick, so every value is bounded before it is used.
		current = read.bounded();
		save(path);
	}

	private ArgilusConfig bounded() {
		return new ArgilusConfig(
				bound(this.radius, 4, 24, DEFAULTS.radius),
				bound(this.harvestIntervalTicks, 1, 200, DEFAULTS.harvestIntervalTicks),
				bound(this.scanIntervalTicks, 20, 400, DEFAULTS.scanIntervalTicks));
	}

	private static int bound(int value, int min, int max, int fallback) {
		if (value <= 0) {
			return fallback;
		}

		return Math.clamp(value, min, max);
	}

	private static void save(Path path) {
		try {
			Files.createDirectories(path.getParent());

			try (BufferedWriter writer = Files.newBufferedWriter(path)) {
				GSON.toJson(current, writer);
			}
		} catch (IOException e) {
			Argilus.LOGGER.warn("Could not write config to {}", path, e);
		}
	}
}
