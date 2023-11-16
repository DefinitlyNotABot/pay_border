package com.example;

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
			dispatcher.register(literal("payborder")
					.executes(context -> {
						// For versions below 1.19, use ''new LiteralText''.
						ServerCommandSource source = context.getSource();

						ServerPlayerEntity player = source.getPlayer();

						assert player != null;
						PlayerInventory inventory = player.getInventory();

						Item item = inventory.getMainHandStack().getItem();

						String itemName = item.getTranslationKey();

						itemName = itemName.split("\\.")[itemName.split("\\.").length-1];

						int itemCount = 0;


						for (ItemStack stack : inventory.main) {
							if (stack.getItem() == item) {
								itemCount += stack.getCount();
							}
						}
						String world = Objects.requireNonNull(player.getServer()).getOverworld().toString();

						world = world.substring(world.indexOf("[") + 1);
						world = world.substring(0, world.indexOf("]"));

						System.out.println("Call connection");

						int returnval = (this.acceptBlock( itemName, itemCount, world));

						System.out.println("ReturnValue: " + returnval);


						int returnInt = returnval;
						if(returnInt < 0){
							context.getSource().sendMessage(Text.literal(errorMessage(returnInt)));
							return 1;
						}

						int count = returnInt;
						// Iterate through the player's inventory
						for (int i = 0; i < inventory.size(); i++) {
							ItemStack stack = inventory.getStack(i);
							if (stack.getItem() == item) {
								int itemsToRemove = Math.min(count, stack.getCount());
								stack.decrement(itemsToRemove);
								inventory.setStack(i, stack);
								count -= itemsToRemove;
								if (count <= 0) {
									break;
								}
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
					})
			);

		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(literal("price")
					.executes(context -> {

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


						int returnval = this.blockPrice( itemName, world);
						if(returnval < 0)
						{
							context.getSource().sendMessage(Text.literal(errorMessage(returnval)));
							return 1;
						}

						context.getSource().sendMessage(Text.literal("Price:  " + returnval));

						return 0;
					})
			);

		});
	}
	private int acceptBlock( String block, int amount, String world)
	{

		try {
			String csvFilePath = System.getProperty("user.dir") + "\\saves\\" + world + "\\payborder.csv";
			Map<String, Integer> dictionary = ReadFile(csvFilePath);

            if(dictionary == null){
				return -2;
			}

			if(!dictionary.containsKey(block)){
				dictionary.put(block, 0);
			}
			int count = dictionary.getOrDefault(block, 0);
			int price = (int) Math.pow(2, count);

			if (price > amount) {
				return -1;
			}

			// Writing
			try (FileWriter writer = new FileWriter(csvFilePath, false)) {
				// Write header (if needed)
				dictionary.forEach((key, value) -> {

					try {
						if (key.equals(block)) {
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
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return -2;
	}

	private int blockPrice(String block, String world)
	{
		try {
			// Reading
			String csvFilePath = System.getProperty("user.dir") + "\\saves\\" + world + "\\payborder.csv";
			Map<String, Integer> dictionary = ReadFile(csvFilePath);

			int count = dictionary.getOrDefault(block, 0);
			int price = (int) Math.pow(2, count);
			return price;

		} catch (Exception e) {
			e.printStackTrace();
		}

		return -2;
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
	private String errorMessage(int errorCode)
	{
		switch (errorCode)
		{
			case -1:
				return "Too few Items";
			case -2:
				return "ERROR -> Connection to database failed";
			case -3:
				return "Can't sell air";
			case -4:
				return "ERROR -> too many entries";
			case -5:
				return "ERROR -> too few arguments";
		}
		return "ERROR -> Unknown error code";
	}

}