package com.example;

import com.mojang.brigadier.context.CommandContext;
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
import java.util.Map;
import java.util.Objects;


public class ExampleMod implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger("pay_border");



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
			if (stack.getItem() != item)continue;
			itemCount += stack.getCount();
		}

		String world = Objects.requireNonNull(player.getServer()).getOverworld().toString();
		world = world.substring(world.indexOf("[") + 1);
		world = world.substring(0, world.indexOf("]"));

		int price=0;
		try {
			String csvFilePath = System.getProperty("user.dir") + "\\saves\\" + world + "\\payborder.csv";
			Map<String, Integer> dictionary = ReadFile(csvFilePath);

			if(dictionary == null){
				context.getSource().sendMessage(Text.literal(errorMessage(-2)));
				return -2;
			}

			if(!dictionary.containsKey(itemName	)){
				dictionary.put(itemName, 0);
			}
			int count = dictionary.getOrDefault(itemName, 0);
			price = (int) Math.pow(2, count);

			if (price > itemCount) {
				context.getSource().sendMessage(Text.literal(errorMessage(-1)));
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
		} catch (Exception e) {
			e.printStackTrace();
		}


		int count = price;

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
		double newSize = worldBorder.getSize() + 1.0;
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

		String world = Objects.requireNonNull(player.getServer()).getOverworld().toString();
		world = world.substring(world.indexOf("[") + 1);
		world = world.substring(0, world.indexOf("]"));


		int price = 0;
		try {
			// Reading
			String csvFilePath = System.getProperty("user.dir") + "\\saves\\" + world + "\\payborder.csv";
			Map<String, Integer> dictionary = ReadFile(csvFilePath);

            assert dictionary != null;
            int count = dictionary.getOrDefault(itemName, 0);
			price = (int) Math.pow(2, count);

		} catch (Exception e) {
			e.printStackTrace();
		}
		if(price < 0)
		{
			context.getSource().sendMessage(Text.literal(errorMessage(-2)));
			return 1;
		}

		context.getSource().sendMessage(Text.literal("Price:  " + price));

		return 0;
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
        return switch (errorCode) {
            case -1 -> "Too few Items";
            case -2 -> "ERROR -> Connection to database failed";
            case -3 -> "Can't sell air";
            case -4 -> "ERROR -> too many entries";
            case -5 -> "ERROR -> too few arguments";
            default -> "ERROR -> Unknown error code";
        };
    }

}