package minecrafttransportsimulator.multipart.main;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;

import minecrafttransportsimulator.MTS;
import minecrafttransportsimulator.dataclasses.PackMultipartObject;
import minecrafttransportsimulator.dataclasses.PackMultipartObject.PackPart;
import minecrafttransportsimulator.multipart.parts.APart;
import minecrafttransportsimulator.packets.multipart.PacketMultipartClientInit;
import minecrafttransportsimulator.packets.multipart.PacketMultipartClientPartRemoval;
import minecrafttransportsimulator.systems.PackParserSystem;
import net.minecraft.entity.Entity;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**Base multipart class.  All multipart entities should extend this class.
 * It is primarily responsible for the adding and removal of multiparts,
 * as well as dealing with what happens when this part is killed.
 * It is NOT responsible for custom data sets, sounds, or movement.
 * That should be done in sub-classes to keep methods segregated.
 * 
 * @author don_bruce
 */
public abstract class EntityMultipartA_Base extends Entity{
	/**This name is identical to the unique name found in the {@link PackMultipartObject}
	 * It is present here to allow the pack system to properly identify this multipart
	 * during save/load operations, as well as determine some properties dynamically.
	 */
	public String multipartName="";
	
	/**Similar to the name above, this name is for the JSON file that the multipart came from.
	 * Used heavily in rendering operations as those are NOT unique to pack definitions
	 * like the individual multiparts are.
	 */
	public String multipartJSONName="";
	
	/**The pack for this multipart.  This is set upon NBT load on the server, but needs a packet
	 * to be present on the client.  Do NOT assume this will be valid simply because
	 * the multipart has been loaded!
	 */
	public PackMultipartObject pack;
	
	/**This list contains all parts this multipart has.  Do NOT use it in loops or you will get CMEs all over!
	 * Use the getMultipartsParts() method instead to return a loop-safe array.*/
	private final List<APart> parts = new ArrayList<APart>();

	/**Cooldown byte to prevent packet spam requests during client-side loading of part packs.**/
	private byte clientPackPacketCooldown = 0;
	
	public EntityMultipartA_Base(World world){
		super(world);
	}
	
	public EntityMultipartA_Base(World world, String multipartName){
		this(world);
		this.multipartName = multipartName;
		this.multipartJSONName = PackParserSystem.getMultipartJSONName(multipartName);
		this.pack = PackParserSystem.getMultipartPack(multipartName); 
	}
	
	@Override
	public void onEntityUpdate(){
		super.onEntityUpdate();
		//We need to get pack data manually if we are on the client-side.
		///Although we could call this in the constructor, Minecraft changes the
		//entity IDs after spawning and that fouls things up.
		if(pack == null){
			if(worldObj.isRemote){
				if(clientPackPacketCooldown == 0){
					clientPackPacketCooldown = 40;
					MTS.MTSNet.sendToServer(new PacketMultipartClientInit(this));
				}else{
					--clientPackPacketCooldown;
				}
			}
		}
	}
	
	@Override
	public void setDead(){
		super.setDead();
	}
	
    @Override
    @SideOnly(Side.CLIENT)
    public void setPositionAndRotationDirect(double posX, double posY, double posZ, float yaw, float pitch, int posRotationIncrements, boolean teleport){
    	//Overridden due to stupid tracker behavior.
    	//Client-side render changes calls put in its place.
    	this.setRenderDistanceWeight(100);
    	this.ignoreFrustumCheck = true;
    }
	
	public void addPart(APart part, boolean ignoreCollision){
		parts.add(part);
		if(!ignoreCollision){
			//Check for collision, and boost if needed.
			if(part.isPartCollidingWithBlocks(Vec3d.ZERO)){
				this.setPositionAndRotation(posX, posY +  Math.max(0, -part.offset.yCoord) + part.getHeight(), posZ, rotationYaw, rotationPitch);
			}
			
			//Sometimes we need to do this for parts that are deeper into the ground.
			if(part.isPartCollidingWithBlocks(new Vec3d(0, Math.max(0, -part.offset.yCoord) + part.getHeight(), 0))){
				this.setPositionAndRotation(posX, posY +  part.getHeight(), posZ, rotationYaw, rotationPitch);
			}
		}
	}
	
	public void removePart(APart part, boolean playBreakSound){
		if(parts.contains(part)){
			parts.remove(part);
			if(part.isValid()){
				part.removePart();
				if(!worldObj.isRemote){
					MTS.MTSNet.sendToAll(new PacketMultipartClientPartRemoval(this, part.offset.xCoord, part.offset.yCoord, part.offset.zCoord));
				}
			}
			if(!worldObj.isRemote){
				if(playBreakSound){
					this.playSound(SoundEvents.ITEM_SHIELD_BREAK, 2.0F, 1.0F);
				}
			}
		}
	}
	
	/**
	 * Returns a loop-safe array for iterating over parts.
	 * Use this for everything that needs to look at parts.
	 */
	public APart[] getMultipartParts(){
		return ImmutableList.copyOf(parts).toArray(new APart[parts.size()]);
	}
	
	/**
	 * Gets the part at the specified location.
	 */
	public APart getPartAtLocation(double offsetX, double offsetY, double offsetZ){
		for(APart part : this.parts){
			if(part.offset.xCoord == offsetX && part.offset.yCoord == offsetY && part.offset.zCoord == offsetZ){
				return part;
			}
		}
		return null;
	}
	
	/**
	 * Gets all possible pack parts.  This includes additional parts on the multipart
	 * and extra parts of parts on other parts.  Map returned is the position of the
	 * part positions and the part pack information at those positions.
	 * Note that additional parts will not be added if no part is present
	 * in the primary location.
	 */
	public Map<Vec3d, PackPart> getAllPossiblePackParts(){
		Map<Vec3d, PackPart> packParts = new HashMap<Vec3d, PackPart>();
		//First get all the regular part spots.
		for(PackPart packPart : pack.parts){
			Vec3d partPos = new Vec3d(packPart.pos[0], packPart.pos[1], packPart.pos[2]);
			packParts.put(partPos, packPart);
			
			//Check to see if we can put an additional part in this location.
			if(packPart.additionalPart != null){
				for(APart part : this.parts){
					if(part.offset.equals(partPos)){
						packParts.put(new Vec3d(packPart.additionalPart.pos[0], packPart.additionalPart.pos[1], packPart.additionalPart.pos[2]), packPart.additionalPart);
						break;
					}
				}				
			}
		}
		
		//Next get any sub parts on parts that are present.
		for(APart part : this.parts){
			if(part.isValid() && part.pack.subParts != null){
				PackPart parentPack = getPackDefForLocation(part.offset.xCoord, part.offset.yCoord, part.offset.zCoord);
				for(PackPart extraPackPart : part.pack.subParts){
					PackPart correctedPack = getPackForSubPart(parentPack, extraPackPart);
					packParts.put(new Vec3d(correctedPack.pos[0], correctedPack.pos[1], correctedPack.pos[2]), correctedPack);
				}
			}
			
		}
		return packParts;
	}
	
	/**
	 * Gets the pack definition at the specified location.
	 */
	public PackPart getPackDefForLocation(double offsetX, double offsetY, double offsetZ){
		//Check to see if this is a main part.
		for(PackPart packPart : pack.parts){
			if(packPart.pos[0] == offsetX && packPart.pos[1] == offsetY && packPart.pos[2] == offsetZ){
				return packPart;
			}
			
			//Not a main part.  Check if this is an additional part.
			if(packPart.additionalPart != null){
				if(packPart.additionalPart.pos[0] == offsetX && packPart.additionalPart.pos[1] == offsetY && packPart.additionalPart.pos[2] == offsetZ){
					return packPart.additionalPart;
				}
			}
		}
		
		//If this is not a main part or an additional part, check the sub-parts.
		for(APart part : this.parts){
			if(part.isValid() && part.pack.subParts.size() > 0){
				PackPart parentPack = getPackDefForLocation(part.offset.xCoord, part.offset.yCoord, part.offset.zCoord);
				for(PackPart extraPackPart : part.pack.subParts){
					PackPart correctedPack = getPackForSubPart(parentPack, extraPackPart);
					if(correctedPack.pos[0] == offsetX && correctedPack.pos[1] == offsetY && correctedPack.pos[2] == offsetZ){
						return correctedPack;
					}
				}
			}
		}
		
		return null;
	}
	
	/**
	 * Returns a PackPart with the correct properties for a SubPart.  This is because
	 * subParts inherit some properties from their parent parts. 
	 */
	private PackPart getPackForSubPart(PackPart parentPack, PackPart subPack){
		PackPart correctPack = this.pack.new PackPart();
		correctPack.pos = new float[3];
		correctPack.pos[0] = parentPack.pos[0] >= 0 || parentPack.overrideMirror ? parentPack.pos[0] + subPack.pos[0] : parentPack.pos[0] - subPack.pos[0];
		correctPack.pos[1] = parentPack.pos[1] + subPack.pos[1];
		correctPack.pos[2] = parentPack.pos[2] + subPack.pos[2];
		
		if(parentPack.rot != null || subPack.rot != null){
			correctPack.rot = new float[3];
		}
		if(parentPack.rot != null){
			correctPack.rot[0] += parentPack.rot[0];
			correctPack.rot[1] += parentPack.rot[1];
			correctPack.rot[2] += parentPack.rot[2];
		}
		if(subPack.rot != null){
			correctPack.rot[0] += subPack.rot[0];
			correctPack.rot[1] += subPack.rot[1];
			correctPack.rot[2] += subPack.rot[2];
		}
		
		correctPack.turnsWithSteer = parentPack.turnsWithSteer;
		correctPack.isController = subPack.isController;
		correctPack.overrideMirror = parentPack.overrideMirror;
		correctPack.types = subPack.types;
		correctPack.customTypes = subPack.customTypes;
		correctPack.minValue = subPack.minValue;
		correctPack.maxValue = subPack.maxValue;
		return correctPack;
	}
			
    @Override
	public void readFromNBT(NBTTagCompound tagCompound){
		super.readFromNBT(tagCompound);
		this.multipartName = tagCompound.getString("multipartName");
		this.multipartJSONName = PackParserSystem.getMultipartJSONName(multipartName);
		this.pack = PackParserSystem.getMultipartPack(multipartName);
		
		if(this.parts.size() == 0){
			NBTTagList partTagList = tagCompound.getTagList("Parts", 10);
			for(byte i=0; i<partTagList.tagCount(); ++i){
				try{
					NBTTagCompound partTag = partTagList.getCompoundTagAt(i);
					PackPart packPart = getPackDefForLocation(partTag.getDouble("offsetX"), partTag.getDouble("offsetY"), partTag.getDouble("offsetZ"));
					Class<? extends APart> partClass = PackParserSystem.getPartPartClass(partTag.getString("partName"));
					Constructor<? extends APart> construct = partClass.getConstructor(EntityMultipartD_Moving.class, PackPart.class, String.class, NBTTagCompound.class);
					APart savedPart = construct.newInstance((EntityMultipartD_Moving) this, packPart, partTag.getString("partName"), partTag);
					this.addPart(savedPart, true);
				}catch(Exception e){
					MTS.MTSLog.error("ERROR IN LOADING PART FROM NBT!");
					e.printStackTrace();
				}
			}
		}
	}
    
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tagCompound){
		super.writeToNBT(tagCompound);
		tagCompound.setString("multipartName", this.multipartName);
		
		NBTTagList partTagList = new NBTTagList();
		for(APart part : this.getMultipartParts()){
			//Don't save the part if it's not valid.
			if(part.isValid()){
				NBTTagCompound partTag = part.getPartNBTTag();
				//We need to set some extra data here for the part to allow this multipart to know where it went.
				//This only gets set here during saving/loading, and is NOT returned in the item that comes from the part.
				partTag.setString("partName", part.partName);
				partTag.setDouble("offsetX", part.offset.xCoord);
				partTag.setDouble("offsetY", part.offset.yCoord);
				partTag.setDouble("offsetZ", part.offset.zCoord);
				partTagList.appendTag(partTag);
			}
		}
		tagCompound.setTag("Parts", partTagList);
		return tagCompound;
	}
	
	//Junk methods, forced to pull in.
	protected void entityInit(){}
	protected void readEntityFromNBT(NBTTagCompound p_70037_1_){}
	protected void writeEntityToNBT(NBTTagCompound p_70014_1_){}
}
