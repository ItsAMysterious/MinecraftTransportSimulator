package minecrafttransportsimulator.entities.parts;

import minecrafttransportsimulator.dataclasses.MTSRegistry;
import minecrafttransportsimulator.entities.core.EntityMultipartParent;
import minecrafttransportsimulator.entities.main.EntityCar;
import net.minecraft.item.Item;
import net.minecraft.world.World;

public class EntityEngineCarDetroit extends EntityEngineCar{

	public EntityEngineCarDetroit(World world){
		super(world);
	}

	public EntityEngineCarDetroit(World world, EntityMultipartParent car, String parentUUID, float offsetX, float offsetY, float offsetZ, int propertyCode){
		super(world, (EntityCar) car, parentUUID, offsetX, offsetY, offsetZ);
	}
	
	@Override
	public float getWidth(){
		return 1.0F;
	}

	@Override
	public float getHeight(){
		return 1.0F;
	}

	@Override
	protected Item getEngineItem(){
		return MTSRegistry.engineDetroitDiesel;
	}
		
	@Override
	public float getRatioForGear(byte gearNumber){
		switch(gearNumber){
			case(-1): return -6.33F;
			case(1): return 11.83F;
			case(2): return 7.90F;
			case(3): return 5.79F;
			case(4): return 4.32F;
			case(5): return 3.20F;
			case(6): return 2.47F;
			case(7): return 1.81F;
			case(8): return 1.35F;
			case(9): return 1.00F;
			default: return 0.0F;
		}
	}
}