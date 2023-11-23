package com.example;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.border.WorldBorder;
import org.apache.commons.lang3.EnumUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.minecraft.server.command.CommandManager.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;


public class ExampleMod implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger("pay_border");

	private static final int ModMayorVersion = 1;
	private static final int ModMidVersion = 0;
	private static final int ModMinorVersion = 6;

	private enum settings {
		DUFFUCULTY_LEVEL("difficulty_level"),
		MAX_USES_PER_ITEM("max_uses_per_item"),
		INCREASE_PER_PAY("increase_per_pay"),
		MOD_MAYOR_VERSION("mod_mayor_version"),
		MOD_MID_VERSION("mod_mid_version"),
		MOD_MINOR_VERSION("mod_minor_version"),
		;

		private final String text;
		settings(final String text) {
			this.text = text;
		}
		@Override
		public String toString() {
			return text;
		}
		public boolean equals(String s){
			return this.text.equals(s);
		}
	}

	private enum filepath{
		SETTINGS("settings.csv"),
		BLOCK_USAGES("payborder.csv"),
		DIR_IN_WORLD("payborder_data")
		;

		private final String text;
		filepath(final String text) {
			this.text = text;
		}

		public String getFile(String worldname) {
			return System.getProperty("user.dir") + "\\saves\\" + worldname + "\\payborder_data\\" + text;
		}
		public boolean equals(String s){
			return this.text.equals(s);
		}
	}

	private String[] keys = new String[]{
			settings.DUFFUCULTY_LEVEL.toString(),
			settings.MAX_USES_PER_ITEM.toString(),
			settings.INCREASE_PER_PAY.toString()
	};
	private int[] standardValues = new int[]{
			2000,
			10,
			1000
	};

	@Override
	public void onInitialize() {


		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			// For versions below 1.19, use ''new LiteralText''.
			dispatcher.register(literal("payborder")
					.executes(this::payborder)
			);

		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(literal("price")
					.executes(this::price)
			);
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(literal("init_payborder")
					.executes(this::init_payborder)
			);
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(literal("update_version")
					.executes(this::update_version)
			);
		});


		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					literal("change_settings")
							.then(argument("setting", StringArgumentType.word())
									.suggests((context, builder) -> suggestions_settings(builder))
									.then(argument("value", DoubleArgumentType.doubleArg())
											.executes(this::change_settings)
									)
							)
			);
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					literal("get_settings")
							.then(argument("setting", StringArgumentType.word())
									.suggests((context, builder) -> suggestions_settings(builder))
											.executes(this::get_settings)

							)
			);
		});

	}

	//region Player callable Functions
	private int payborder(CommandContext<ServerCommandSource> context) {

		ServerCommandSource source = context.getSource();
		ServerPlayerEntity player = source.getPlayer();
		assert player != null;
		PlayerInventory inventory = player.getInventory();

		Item item = inventory.getMainHandStack().getItem();
		String itemName = item.getTranslationKey();
		itemName = itemName.split("\\.")[itemName.split("\\.").length-1];

		int itemCount = 0;

		for (ItemStack stack : inventory.main) {
			if (stack.getItem() != item)continue;
			itemCount += stack.getCount();
		}

		String world = Objects.requireNonNull(player.getServer()).getOverworld().toString();
		world = world.substring(world.indexOf("[") + 1);
		world = world.substring(0, world.indexOf("]"));



		String csvFilePath = filepath.BLOCK_USAGES.getFile(world);
		Map<String, Integer> dictionary = ReadFile(csvFilePath);

		String settingsFilePath = filepath.SETTINGS.getFile(world);
		Map<String, Integer> dictionarySettings = ReadFile(settingsFilePath);

		if(dictionary == null || dictionarySettings == null){
			context.getSource().sendMessage(Text.literal(errorMessage(-2)));
			return -2;
		}
		compareVersions(dictionarySettings, context);
		if(!dictionary.containsKey(itemName	)){
			dictionary.put(itemName, 0);
		}
		int count = dictionary.getOrDefault(itemName, 0);
		if(count > dictionarySettings.get(settings.MAX_USES_PER_ITEM.toString())){
			context.getSource().sendMessage(Text.literal(errorMessage(-3)));
			return -1;
		}
		double value = dictionarySettings.get(settings.DUFFUCULTY_LEVEL.toString());
		int price = (int) Math.pow(value/1000, count);

		if (price > itemCount) {
			context.getSource().sendMessage(Text.literal(errorMessage(-1)));
			return -1;
		}
		dictionary.put(itemName, dictionary.get(itemName)+1);
		// Writing
		writeFile(csvFilePath, dictionary);



		count = price;

		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			if (stack.getItem() != item) continue;
			int itemsToRemove = Math.min(count, stack.getCount());
			stack.decrement(itemsToRemove);
			inventory.setStack(i, stack);
			if ((count-= itemsToRemove) <= 0) {
				break;
			}
		}

		ServerWorld serverWorld = player.getServer().getOverworld();
		WorldBorder worldBorder = serverWorld.getWorldBorder();
		double newSize = worldBorder.getSize() + dictionarySettings.get(settings.INCREASE_PER_PAY.toString())/1000f;
		worldBorder.setSize(newSize);

		List<ServerPlayerEntity> playerList = context.getSource().getServer().getPlayerManager().getPlayerList();

		String finalItemName = itemName;
		String playerName = player.getEntityName();
		playerList.forEach(player2 -> player2.sendMessage(Text.literal(playerName + " sold " + price + "x " + finalItemName), false));
		playerList.forEach(player2 -> player2.sendMessage(Text.literal("The world border has been increased to: " + newSize), false));


		return 0;
	}

	private int price(CommandContext<ServerCommandSource> context) {

		ServerCommandSource source = context.getSource();
		ServerPlayerEntity player = source.getPlayer();
		assert player != null;
		PlayerInventory inventory = player.getInventory();
		Item item = inventory.getMainHandStack().getItem();
		String itemName = item.getTranslationKey();

		itemName = itemName.split("\\.")[itemName.split("\\.").length-1];

		String world = Objects.requireNonNull(player.getServer()).getOverworld().toString();
		world = world.substring(world.indexOf("[") + 1);
		world = world.substring(0, world.indexOf("]"));


		int price = 0;

		// Reading
		String csvFilePath = filepath.BLOCK_USAGES.getFile(world);
		Map<String, Integer> dictionary = ReadFile(csvFilePath);

		String settingsFilePath = filepath.SETTINGS.getFile(world);
		Map<String, Integer> dictionarySettings = ReadFile(settingsFilePath);

		if(dictionary == null || dictionarySettings == null){
			context.getSource().sendMessage(Text.literal(errorMessage(-2)));
			return -2;
		}
		compareVersions(dictionarySettings, context);
		int count = dictionary.getOrDefault(itemName, 0);

		if(count > dictionarySettings.get(settings.MAX_USES_PER_ITEM.toString())){
			context.getSource().sendMessage(Text.literal(errorMessage(-3)));
			return -1;
		}

		double value = dictionarySettings.get(settings.DUFFUCULTY_LEVEL.toString());
		price = (int) Math.pow(value/1000, count);


		if(price < 0)
		{
			context.getSource().sendMessage(Text.literal(errorMessage(-2)));
			return 1;
		}

		context.getSource().sendMessage(Text.literal("Price:  " + (price<64?price:price/64+" stacks")));

		return 0;
	}

	private int init_payborder(CommandContext<ServerCommandSource> context) {

		ServerCommandSource source = context.getSource();
		ServerPlayerEntity player = source.getPlayer();
		assert player != null;

		String world = Objects.requireNonNull(player.getServer()).getOverworld().toString();
		world = world.substring(world.indexOf("[") + 1);
		world = world.substring(0, world.indexOf("]"));


		String folderFilePath = System.getProperty("user.dir") + "\\saves\\" + world + "\\payborder_data";

		File file = new File(folderFilePath);

		if (file.exists()) {
			context.getSource().sendMessage(Text.literal("Already Initialized"));
			return 0;
		}
		if (!file.mkdir()) {
			System.out.println("Failed to create directory!");
			return -1;
		}
		String blockFilePath = filepath.BLOCK_USAGES.getFile(world);
		Map<String, Integer> dictionary = new HashMap<>();

		createFile(blockFilePath);
		writeFile(blockFilePath, dictionary);

		String settingsFilePath = filepath.SETTINGS.getFile(world);
		dictionary.put(settings.MOD_MAYOR_VERSION.toString(), ModMayorVersion);
		dictionary.put(settings.MOD_MID_VERSION.toString(), ModMidVersion);
		dictionary.put(settings.MOD_MINOR_VERSION.toString(), ModMinorVersion);
		dictionary.put(settings.DUFFUCULTY_LEVEL.toString(), 2000);
		dictionary.put(settings.MAX_USES_PER_ITEM.toString(), 10);
		dictionary.put(settings.INCREASE_PER_PAY.toString(), 1000);


		createFile(settingsFilePath);
		writeFile(settingsFilePath, dictionary);

		ServerWorld serverWorld = player.getServer().getOverworld();
		WorldBorder worldBorder = serverWorld.getWorldBorder();
		worldBorder.setSize(1);

		Vec3d pos = player.getPos();
		worldBorder.setCenter(
				((int)pos.x) + (pos.x > 0 ? 0.5f : -0.5f),
				((int)pos.z) + (pos.z > 0 ? 0.5f : -0.5f));

		context.getSource().sendMessage(Text.literal("Done"));
		return 0;
	}

	private int update_version(CommandContext<ServerCommandSource> context){
		ServerCommandSource source = context.getSource();
		ServerPlayerEntity player = source.getPlayer();
		assert player != null;

		String world = Objects.requireNonNull(player.getServer()).getOverworld().toString();
		world = world.substring(world.indexOf("[") + 1);
		world = world.substring(0, world.indexOf("]"));
		String csvFilePath = System.getProperty("user.dir") + "\\saves\\" + world + "\\payborder.csv";
		File file = new File(csvFilePath);
		if(file.exists()){
			String folderFilePath = System.getProperty("user.dir") + "\\saves\\" + world + "\\payborder_data";

			file = new File(folderFilePath);

			if (file.exists()) {
				context.getSource().sendMessage(Text.literal("Already Initialized"));
				return 0;
			}
			if (!file.mkdir()) {
				System.out.println("Failed to create directory!");
				return -1;
			}
			String blockFilePath = filepath.BLOCK_USAGES.getFile(world);
			Map<String, Integer> dictionary = new HashMap<>();

			createFile(blockFilePath);
			writeFile(blockFilePath, dictionary);

			String settingsFilePath = filepath.SETTINGS.getFile(world);
			dictionary.put(settings.DUFFUCULTY_LEVEL.toString(), 2000);
			dictionary.put(settings.MAX_USES_PER_ITEM.toString(), 10);

			createFile(settingsFilePath);
			writeFile(settingsFilePath, dictionary);



			dictionary = ReadFile(csvFilePath);
			if(dictionary == null){
				context.getSource().sendMessage(Text.literal(errorMessage(-2)));
				return -2;
			}
			blockFilePath = filepath.BLOCK_USAGES.getFile(world);
			writeFile(blockFilePath, dictionary);

			csvFilePath = System.getProperty("user.dir") + "\\saves\\" + world + "\\payborder.csv";
			file = new File(csvFilePath);
			file.delete();

			context.getSource().sendMessage(Text.literal("Done! Enjoy the new functionality"));
			return 0;
		}

		String settingsFilePath = filepath.SETTINGS.getFile(world);
		Map<String, Integer> dictionarySettings = ReadFile(settingsFilePath);

		if(dictionarySettings == null){
			context.getSource().sendMessage(Text.literal("No mod to update found"));
			return -1;
		}

		if(compareVersions(dictionarySettings, context) <= 0){
			context.getSource().sendMessage(Text.literal("The version won't be changed"));
			return 0;
		}

		if(dictionarySettings.get(settings.MOD_MAYOR_VERSION.toString()) == 1 && dictionarySettings.get(settings.MOD_MID_VERSION.toString()) == 0 && dictionarySettings.get(settings.MOD_MINOR_VERSION.toString()) < 6){
			dictionarySettings.put(settings.DUFFUCULTY_LEVEL.toString(), dictionarySettings.get(settings.DUFFUCULTY_LEVEL.toString())*1000);
		}

		dictionarySettings.put(settings.MOD_MAYOR_VERSION.toString(), ModMayorVersion);
		dictionarySettings.put(settings.MOD_MID_VERSION.toString(), ModMidVersion);
		dictionarySettings.put(settings.MOD_MINOR_VERSION.toString(), ModMinorVersion);

		for(int i = 0; i < keys.length; i++){
			if(!dictionarySettings.containsKey(keys[i])){
				dictionarySettings.put(keys[i], standardValues[i]);
			}
		}

		writeFile(settingsFilePath, dictionarySettings);

		context.getSource().sendMessage(Text.literal("Done! Enjoy the new functionality"));

		return 0;
	}

	private int change_settings(CommandContext<ServerCommandSource> context){

		String setting = StringArgumentType.getString(context, "setting");
		boolean valid = false;
		for (settings settingCompare : settings.values()) {
			if(settingCompare.equals(setting)){
				valid = true;
			}
		}
		if(!valid){
			context.getSource().sendMessage(Text.literal("Not a valid setting"));
			return -1;
		}
		double value = DoubleArgumentType.getDouble(context, "value");

		ServerCommandSource source = context.getSource();
		ServerPlayerEntity player = source.getPlayer();
		assert player != null;

		String world = Objects.requireNonNull(player.getServer()).getOverworld().toString();
		world = world.substring(world.indexOf("[") + 1);
		world = world.substring(0, world.indexOf("]"));


		String settingsFilePath = filepath.SETTINGS.getFile(world);
		Map<String, Integer> dictionarySettings = ReadFile(settingsFilePath);

		if(compareVersions(dictionarySettings, context) != 0){
			context.getSource().sendMessage(Text.literal("Incompatible version"));
			return -1;
		}
		dictionarySettings.put(setting, (int)(value*1000));
		writeFile(settingsFilePath, dictionarySettings);

		context.getSource().sendMessage(Text.literal("Done"));

		return 0;
	}

	private int get_settings(CommandContext<ServerCommandSource> context){

		String setting = StringArgumentType.getString(context, "setting");
		boolean valid = false;
		for (settings settingCompare : settings.values()) {
			if(settingCompare.equals(setting)){
				valid = true;
			}
		}
		if(!valid){
			context.getSource().sendMessage(Text.literal("Not a valid setting"));
			return -1;
		}

		ServerCommandSource source = context.getSource();
		ServerPlayerEntity player = source.getPlayer();
		assert player != null;

		String world = Objects.requireNonNull(player.getServer()).getOverworld().toString();
		world = world.substring(world.indexOf("[") + 1);
		world = world.substring(0, world.indexOf("]"));


		String settingsFilePath = filepath.SETTINGS.getFile(world);
		Map<String, Integer> dictionarySettings = ReadFile(settingsFilePath);
		assert dictionarySettings != null;
		if(settings.DUFFUCULTY_LEVEL.equals(setting)){
            context.getSource().sendMessage(Text.literal("" + dictionarySettings.get(setting)/1000f));
		}
		if(settings.INCREASE_PER_PAY.equals(setting)){
			context.getSource().sendMessage(Text.literal("" + dictionarySettings.get(setting)/1000f));
		}
		if(settings.MAX_USES_PER_ITEM.equals(setting)){
			context.getSource().sendMessage(Text.literal("" + dictionarySettings.get(setting)));
		}

		return 0;
	}


	//endregion



	//region Helper Functions

	private CompletableFuture<Suggestions> suggestions_settings(SuggestionsBuilder builder) {
		List<settings> ignoreSettings = new ArrayList<settings>();
		ignoreSettings.add(settings.MOD_MAYOR_VERSION);
		ignoreSettings.add(settings.MOD_MID_VERSION);
		ignoreSettings.add(settings.MOD_MINOR_VERSION);

		for (settings setting : settings.values()) {
			if(ignoreSettings.contains(setting)){
				continue;
			}
			builder.suggest(setting.toString());
		}

		return builder.buildFuture();
	}


	private int compareVersions(Map<String, Integer> dictionarySettings, CommandContext<ServerCommandSource> context) {
		String newVer = "Your version is newer than the servers";
		String oldVer = "Your mod is outdated";
		if(!dictionarySettings.containsKey(settings.MOD_MAYOR_VERSION.toString())){
			context.getSource().sendMessage(Text.literal(newVer));
			return 1;
		}
		if(dictionarySettings.get(settings.MOD_MAYOR_VERSION.toString()) < ModMayorVersion)
		{
			context.getSource().sendMessage(Text.literal(newVer));
			return 1;
		}else if(dictionarySettings.get(settings.MOD_MAYOR_VERSION.toString()) < ModMayorVersion){
			context.getSource().sendMessage(Text.literal(oldVer));
			return -1;
		}

		if(dictionarySettings.get(settings.MOD_MID_VERSION.toString()) < ModMidVersion)
		{
			context.getSource().sendMessage(Text.literal(newVer));
			return 1;
		}else if(dictionarySettings.get(settings.MOD_MID_VERSION.toString()) < ModMidVersion){
			context.getSource().sendMessage(Text.literal(oldVer));
			return -1;
		}
		if(dictionarySettings.get(settings.MOD_MINOR_VERSION.toString()) < ModMinorVersion)
		{
			context.getSource().sendMessage(Text.literal(newVer));
			return 1;
		}else if(dictionarySettings.get(settings.MOD_MINOR_VERSION.toString()) < ModMinorVersion){
			context.getSource().sendMessage(Text.literal(oldVer));
			return -1;
		}
		return 0;
	}

	private @Nullable Map<String, Integer> ReadFile(String filePath) {
		try{
			Map<String, Integer> dictionary = new HashMap<>();
			System.out.println("File: " + filePath);

			try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
				String line;

				while ((line = reader.readLine()) != null) {
					String[] values = line.split(",");
					dictionary.put(values[0], Integer.parseInt(values[1]));
				}
			}

			return dictionary;
		}catch (Exception e){
			return null;
		}
	}

	private void writeFile(String filePath, @NotNull Map<String, Integer> dictionary) {

		try (FileWriter writer = new FileWriter(filePath, false)) {
			// Write header (if needed)
			dictionary.forEach((key, value) -> {
				try {
					writer.write(key + "," + (value) + "\n");
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

			});
			writer.flush(); // Add this line
		} catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

	private void createFile(String filePath) {
		File file = new File(filePath);
		try{
			if (file.exists()) {
				return;
			}
			if (file.createNewFile()) {
				System.out.println("Created File: " + filePath);
			}else{
				System.out.println("Failed to create the file: " + filePath);
				throw new Exception("Error creating file");
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private String errorMessage(int errorCode) {
        return switch (errorCode) {
            case -1 -> "Too few Items";
            case -2 -> "ERROR -> Could not find file";
            case -3 -> "Max usages reached";
            default -> "ERROR -> Unknown error code";
        };
    }

	//endregion
}