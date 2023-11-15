package com.example;

import com.mojang.brigadier.context.CommandContext;
import it.unimi.dsi.fastutil.ints.Int2IntSortedMaps;
import net.fabricmc.api.ModInitializer;


import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.border.WorldBorder;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.minecraft.server.command.CommandManager.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;


public class ExampleMod implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger("pay_border");

	static final String DB_URL = "localhost";
	static final String USER = "root";
	static final String PASS = "";



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
			dispatcher.register(literal("initPayborder")
					.executes(this::initPayborder)
			);

		});
	}
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
			if (stack.getItem() != item) continue;
			itemCount += stack.getCount();

		}


		String csvFilePath = getFilePath(source);

		int price = 0;

		try {

			Map<String, Integer> dictionary = ReadFile(csvFilePath);

			if(dictionary == null){
				return -2;
			}

			if(!dictionary.containsKey(itemName)){
				dictionary.put(itemName, 0);
			}
			int count = dictionary.getOrDefault(itemName, 0);
			price = (int) Math.pow(2, count);

			if (price > itemCount) {
				context.getSource().sendMessage(Text.literal("Too few Items"));
				return -1;
			}

			// Writing
			try (FileWriter writer = new FileWriter(csvFilePath, false)) {
				// Write header (if needed)
				String finalItemName = itemName;
				dictionary.forEach((key, value) -> {

					try {
						if (key.equals(finalItemName)) {
							writer.write(key + "," + (value + 1) + "\n");
							return;
						}
						writer.write(key + "," + (value) + "\n");

					} catch (IOException e) {
						throw new RuntimeException(e);
					}

				});
				writer.flush(); // Add this line
			}

			return price;
		} catch (Exception e) {
			e.printStackTrace();
			context.getSource().sendMessage(Text.literal("Some error occured"));

		}

		int count = price;
		// Iterate through the player's inventory
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			if (stack.getItem() != item) continue;

			int itemsToRemove = Math.min(count, stack.getCount());
			stack.decrement(itemsToRemove);
			inventory.setStack(i, stack);
			count -= itemsToRemove;

			if (count <= 0) {
				break;
			}
		}

		ServerWorld serverWorld = player.getServer().getOverworld(); // You can use other dimensions if needed

		// Get the world border for the server world
		WorldBorder worldBorder = serverWorld.getWorldBorder();

		// Extend the world border by 1 block
		double newSize = worldBorder.getSize() + 1.0; // Adding 2.0 to extend it by 1 block on each side

		// Set the new size for the world border
		worldBorder.setSize(newSize);

		context.getSource().sendMessage(Text.literal("new size: " + newSize));

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

		int price = 0;
		try {

			Map<String, Integer> dictionary = ReadFile(getFilePath(source));

			int count = dictionary.getOrDefault(itemName, 0);
			price = (int) Math.pow(2, count);
			return price;

		} catch (Exception e) {
			e.printStackTrace();
		}
		context.getSource().sendMessage(Text.literal("Price:  " + price));

		return 0;
	}

	private int initPayborder(CommandContext<ServerCommandSource> context) {

		ServerCommandSource source = context.getSource();

		ServerPlayerEntity player = source.getPlayer();
		assert player != null;

		String csvFilePath = getFilePath(source);

		Map<String, Integer> dictionary = ReadFile(csvFilePath);
		if(dictionary.size() > 0){
			context.getSource().sendMessage(Text.literal("Already Initialized"));
			return -1;
		}

		var pos = player.getPos();

		float x = ((int)pos.x) + (pos.x>0?0.5f:-0.5f);
		float z = ((int)pos.z) + (pos.x>0?0.5f:-0.5f);

		ServerWorld serverWorld = player.getServer().getOverworld(); // You can use other dimensions if needed

		// Get the world border for the server world
		WorldBorder worldBorder = serverWorld.getWorldBorder();

		worldBorder.setCenter(x,z);
		// Set the new size for the world border
		worldBorder.setSize(1);


		context.getSource().sendMessage(Text.literal("Done"));
		return 0;
	}

	private String getFilePath(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		String world = Objects.requireNonNull(player.getServer()).getOverworld().toString();
		world = world.substring(world.indexOf("[") + 1);
		world = world.substring(0, world.indexOf("]"));
		return System.getProperty("user.dir") + "\\saves\\" + world + "\\payborder.csv";
	}


	private @Nullable Map<String, Integer> ReadFile(String csvFilePath)
	{
		try{
			Map<String, Integer> dictionary = new HashMap<>();
			System.out.println("File: " + csvFilePath);
			File file = new File(csvFilePath);

			if (!file.exists()) {
				// Create the file if it doesn't exist
				if (file.createNewFile()) {
					System.out.println("Created File: " + csvFilePath);
				}else{
					System.out.println("Failed to create the file: " + csvFilePath);
					return null; // Or handle the error in an appropriate way
				}
			}

			try (BufferedReader reader = new BufferedReader(new FileReader(csvFilePath))) {
				String line;

				while ((line = reader.readLine()) != null) {
					String[] values = line.split(",");
					dictionary.put(values[0], Integer.parseInt(values[1]));
				}
			}

			return dictionary;
		}catch (Exception e){
			e.printStackTrace();
		}
		return null;
	}


}