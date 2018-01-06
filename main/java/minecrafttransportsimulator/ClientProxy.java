package minecrafttransportsimulator;

import minecrafttransportsimulator.blocks.TileEntityPropellerBench;
import minecrafttransportsimulator.dataclasses.MTSRegistryClient;
import minecrafttransportsimulator.entities.core.EntityMultipartVehicle;
import minecrafttransportsimulator.guis.GUIManual;
import minecrafttransportsimulator.guis.GUIInstruments;
import minecrafttransportsimulator.guis.GUIPropellerBench;
import minecrafttransportsimulator.items.ItemManual;
import minecrafttransportsimulator.systems.ClientEventSystem;
import minecrafttransportsimulator.systems.ConfigSystem;
import minecrafttransportsimulator.systems.ControlSystem;
import minecrafttransportsimulator.systems.SFXSystem;
import minecrafttransportsimulator.systems.SFXSystem.SFXEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**Class responsible for performing client-only updates and operations.
 * Any version-updatable, client-based method should be put in here.
 * 
 * @author don_bruce
 */
@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy{
	
	@Override
	public void preInit(FMLPreInitializationEvent event){
		super.preInit(event);
		MTSRegistryClient.preInit();
		ConfigSystem.initClient();
	}
	
	@Override
	public void init(FMLInitializationEvent event){
		super.init(event);
		MTSRegistryClient.init();
		ControlSystem.init();
		MinecraftForge.EVENT_BUS.register(ClientEventSystem.instance);
	}
	
	@Override
	public void openGUI(Object clicked, EntityPlayer clicker){
		if(clicked instanceof EntityMultipartVehicle){
			FMLCommonHandler.instance().showGuiScreen(new GUIInstruments((EntityMultipartVehicle) clicked, clicker));
		}else if(clicked instanceof TileEntityPropellerBench){
			FMLCommonHandler.instance().showGuiScreen(new GUIPropellerBench((TileEntityPropellerBench) clicked, clicker));
		}else if(clicked instanceof ItemStack && ((ItemStack) clicked).getItem() instanceof ItemManual){
			FMLCommonHandler.instance().showGuiScreen(new GUIManual((ItemStack) clicked));
		}
	}
	
	@Override
	public void playSound(Entity noisyEntity, String soundName, float volume, float pitch){
		SFXSystem.playSound(noisyEntity, soundName, volume, pitch);
	}
	
	@Override
	public void updateSFXEntity(SFXEntity entity, World world){
		SFXSystem.doSFX(entity, world);
	}
}
