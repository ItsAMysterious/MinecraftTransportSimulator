package minecrafttransportsimulator.multipart.parts;

import minecrafttransportsimulator.dataclasses.PackMultipartObject.PackPart;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

/**A fake ground device that will be added to the multipart when long ground devices are present.
 * Does not render and will be removed in tandem with the ground device that's linked to it.
 * These longer parts cannot go flat, therefore some methods have been simplified here.
 * 
 * @author don_bruce
 */
public final class PartGroundDeviceFake extends PartGroundDevice{
	private final PartGroundDevice masterPart;
	private boolean wasRemovedFromMultipart = false;
	
	public PartGroundDeviceFake(PartGroundDevice masterPart, PackPart packPart, String partName, NBTTagCompound dataTag){
		super(masterPart.multipart, packPart, partName, dataTag);
		this.masterPart = masterPart;
	}
	
	@Override
	public boolean isValid(){
		return false;
	}
	
	@Override
	public void removePart(){
		if(masterPart.isValid()){
			multipart.removePart(masterPart, false);
		}
		wasRemovedFromMultipart = true;
	}
	
	@Override
	public NBTTagCompound getPartNBTTag(){
		return new NBTTagCompound();
	}
	
	@Override
	public Item getItemForPart(){
		return wasRemovedFromMultipart ? super.getItemForPart() : null;
	}
	
	@Override
	public ResourceLocation getModelLocation(){
		return null;
	}
	
	@Override
	public ResourceLocation getTextureLocation(){
		return null;
	}
}
