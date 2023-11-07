package com.example;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.minecraft.server.command.CommandManager.*;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;


public class ExampleMod implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger("pay_border");

	private WebDriver driver;

	@Override
	public void onInitialize() {


		System.setProperty("webdriver.gecko.driver", "D:\\QuickAndDirtyTesting\\geckodriver.exe");
		System.setProperty(FirefoxDriver.SystemProperty.DRIVER_USE_MARIONETTE,"true");
		System.setProperty(FirefoxDriver.SystemProperty.BROWSER_LOGFILE,"/dev/null");
		FirefoxOptions options = new FirefoxOptions();
		options.setProfile(new FirefoxProfile(new File("D:\\QuickAndDirtyTesting\\xdvcmxgm.BasicProfile")));
		options.setHeadless(true);
		driver = new FirefoxDriver(options);

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
						world = world.replaceAll(" ", "_");
						world = world.substring(world.indexOf("[") + 1);
						world = world.substring(0, world.indexOf("]"));

						System.out.println("Call connection");

						String returnval = (this.acceptBlock( itemName, itemCount, world));

						System.out.println("ReturnValue: " + returnval);
						System.out.println("ReturnValueLen: " + returnval.length());


						int returnInt = Integer.parseInt(returnval);
						if(returnInt < 0){
							context.getSource().sendMessage(Text.literal("Err.:" + returnInt));
							return 1;
						}

						int count = (int)Math.pow(2, returnInt);
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
						world = world.replaceAll(" ", "_");
						world = world.substring(world.indexOf("[") + 1);
						world = world.substring(0, world.indexOf("]"));


						int returnval = Integer.parseInt(this.blockPrice( itemName, world));
						if(returnval < 0)
						{
							context.getSource().sendMessage(Text.literal("Error:  " + returnval));
							return 1;
						}

						context.getSource().sendMessage(Text.literal("Price:  " + (int)Math.pow(2, (returnval))));

						return 0;
					})
			);

		});
	}
	private String acceptBlock( String block, int amount, String world)
	{
		try{


			// Define the API endpoint URL
			String apiUrl = "http://payborder.free.nf/index.php";
			apiUrl += "?block=" + block;
			apiUrl += "&amount=" + amount;
			apiUrl += "&worldname=" + world;

			driver.get(apiUrl);
			List<WebElement> textElements = driver.findElements(By.tagName("body"));

			StringBuilder entirePageText = new StringBuilder();

			for (WebElement element : textElements) {
				entirePageText.append(element.getText()).append(" ");
			}


			return entirePageText.toString().replaceAll("\\s+","");
		}catch (Exception e){
			System.out.println("in catch");
			e.printStackTrace();
		}
		return "";
	}

	private String blockPrice(String block, String world)
	{
		try{

			// Define the API endpoint URL
			String apiUrl = "http://payborder.free.nf/price.php";
			apiUrl += "?block=" + block;
			apiUrl += "&worldname=" + world;

			driver.get(apiUrl);
			List<WebElement> textElements = driver.findElements(By.tagName("body"));

			StringBuilder entirePageText = new StringBuilder();

			for (WebElement element : textElements) {
				entirePageText.append(element.getText()).append(" ");
			}

			return entirePageText.toString().replaceAll("\\s+","");
		}catch (Exception e){
			e.printStackTrace();
		}
		return "";

	}

}