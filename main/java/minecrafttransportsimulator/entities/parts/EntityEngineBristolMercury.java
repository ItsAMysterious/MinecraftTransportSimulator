package minecrafttransportsimulator.entities.parts;

import minecrafttransportsimulator.dataclasses.MTSRegistry;
import minecrafttransportsimulator.entities.core.EntityMultipartParent;
import minecrafttransportsimulator.multipart.main.EntityMultipartF_Plane;
import net.minecraft.item.Item;
import net.minecraft.world.World;

public class EntityEngineBristolMercury extends EntityEngineAircraft{
	public EntityEngineBristolMercury(World world){
		super(world);
	}
	
	public EntityEngineBristolMercury(World world, EntityMultipartParent parent, String parentUUID, float offsetX, float offsetY, float offsetZ, int propertyCode){
		super(world, (EntityMultipartF_Plane) parent, parentUUID, offsetX, offsetY, offsetZ);
	}

	@Override
	protected Item getEngineItem(){
		return MTSRegistry.engineBristolMercury;
	}
}
