package minecrafttransportsimulator.multipart.main;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import minecrafttransportsimulator.MTS;
import minecrafttransportsimulator.dataclasses.PackMultipartObject.PackPart;
import minecrafttransportsimulator.multipart.parts.APart;
import minecrafttransportsimulator.multipart.parts.PartCrate;
import minecrafttransportsimulator.multipart.parts.PartSeat;
import minecrafttransportsimulator.packets.multipart.PacketMultipartWindowBreak;
import minecrafttransportsimulator.packets.parts.PacketPartInteraction;
import minecrafttransportsimulator.packets.parts.PacketPartSeatRiderChange;
import minecrafttransportsimulator.systems.ConfigSystem;
import minecrafttransportsimulator.systems.RotationSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**This is the next class level above the base multipart.
 * At this level we add methods for the multipart's existence in the world.
 * Variables for position are defined here, but no methods for MOVING
 * this multipart are present until later sub-classes.  Also not present
 * are variables that define how this multipart COULD move (motions, states
 * of brakes/throttles, collision boxes, etc.)  This is where the pack information comes in
 * as this is where we start needing it.  This is also where we handle how this
 * mutlipart reacts with events like clicking and crashing with players inside.
 * 
 * @author don_bruce
 */
@Mod.EventBusSubscriber
public abstract class EntityMultipartB_Existing extends EntityMultipartA_Base{
	public boolean locked;
	public byte brokenWindows;
	public float rotationRoll;
	public float prevRotationRoll;
	public double airDensity;
	public double currentMass;
	public String ownerName="";
	public String displayText="";
	public Vec3d headingVec = Vec3d.ZERO;
	
	/**Cached map that links entity IDs to the seats riding them.  Used for mounting/dismounting functions.*/
	private final BiMap<Integer, PartSeat> riderSeats = HashBiMap.create();
	
	/**List for storage of rider linkages to seats.  Populated during NBT load and used to populate the riderSeats map after riders load.*/
	private List<Double[]> riderSeatPositions = new ArrayList<Double[]>();
	
	/**Names for reflection to get the entity any entity is riding.**/
	private static final String[] ridingEntityNames = { "ridingEntity", "field_73141_v", "field_184239_as"};
	
	/**ID of the current rider that wants to dismount this tick.  This gets set when the multipart detects that an entity wants to dismount.**/
	private int riderIDToDismountThisTick = -1;
	
	/**Boolean paired with above to determine if dismounting code needs to be inhibited or if we are just changing seats.**/
	private boolean didRiderClickSeat;
			
	public EntityMultipartB_Existing(World world){
		super(world);
	}
	
	public EntityMultipartB_Existing(World world, float posX, float posY, float posZ, float playerRotation, String multipartName){
		super(world, multipartName);
		//Set position to the spot that was clicked by the player.
		//Add a -90 rotation offset so the multipart is facing perpendicular.
		//Makes placement easier and is less likely for players to get stuck.
		this.setPositionAndRotation(posX, posY, posZ, playerRotation-90, 0);
		
		//This only gets done at the beginning when the entity is first spawned.
		this.displayText = pack.rendering.defaultDisplayText;
	}
	
	@Override
	public void onEntityUpdate(){
		super.onEntityUpdate();
		if(pack != null){
			currentMass = getCurrentMass();
			airDensity = 1.225*Math.pow(2, -posY/500);
			getBasicProperties();
		}
		
		if(riderIDToDismountThisTick != -1){
			Entity riderToDismount = worldObj.getEntityByID(riderIDToDismountThisTick);
			PartSeat seat = this.getSeatForRider(riderToDismount);
			if(seat != null){
				if(this.removeRiderFromSeat(riderToDismount, seat)){
					riderIDToDismountThisTick = -1;
				}
			}
		}
		
		didRiderClickSeat = false;
	}
	
	@Override
    public boolean processInitialInteract(EntityPlayer player, @Nullable ItemStack stack, EnumHand hand){
		//In all cases, interaction will be handled on the client and forwarded to the server.
		//However, there is one case where we can't forward an event, and that is if a player
		//right-clicks this with an empty hand.
		if(worldObj.isRemote && player.getHeldItemMainhand() == null){
			APart hitPart = getHitPart(player);
			if(hitPart != null){
				if(hitPart.interactPart(player)){
					MTS.MTSNet.sendToServer(new PacketPartInteraction(hitPart, player.getEntityId()));
				}
			}
		}
        return false;
    }
	
	@Override
	public boolean attackEntityFrom(DamageSource source, float damage){
		if(!worldObj.isRemote){
			if(source.getSourceOfDamage() != null && !source.getSourceOfDamage().equals(source.getEntity())){
				//This is a projectile of some sort.  If this projectile is inside a part
				//make it hit the part rather than hit the multipart.
				Entity projectile = source.getSourceOfDamage();
				for(APart part : this.getMultipartParts()){
					//Expand this box by the speed of the projectile just in case the projectile is custom and
					//calls its attack code before it actually gets inside the collision box.
					if(part.getAABBWithOffset(Vec3d.ZERO).expand(Math.abs(projectile.motionX), Math.abs(projectile.motionY), Math.abs(projectile.motionZ)).isVecInside(projectile.getPositionVector())){
						part.attackPart(source, damage);
						return true;
					}
				}
			}else{
				//This is not a projectile, and therefore must be some sort of entity.
				//Check to see where this entity is looking and if it has hit a
				//part attack that part.
				Entity attacker = source.getEntity();
				if(attacker != null){
					APart hitPart = this.getHitPart(attacker);
					if(hitPart != null){
						hitPart.attackPart(source, damage);
						return true;
					}
				}
			}
			
			//Since we didn't forward any attacks or do special events, we must have attacked this multipart directly.
			//Send a packet to break a window if we need to.
			Entity damageSource = source.getEntity() != null && !source.getEntity().equals(source.getSourceOfDamage()) ? source.getSourceOfDamage() : source.getEntity();
			if(damageSource != null && this.brokenWindows < pack.rendering.numberWindows){
				++brokenWindows;
				this.playSound(SoundEvents.BLOCK_GLASS_BREAK, 2.0F, 1.0F);
				MTS.MTSNet.sendToAll(new PacketMultipartWindowBreak(this));
			}
		}
		return true;
	}
	
	/**
	 * Prevent dismounting from this multipart naturally as MC sucks at finding good spots to dismount.
	 * Instead, chose a better spot manually to prevent the player from getting stuck inside things.
	 * This event is used to populate a list of riders to dismount the next tick.  This list is cleared when the operation
	 * is done successfully.  If the operation fails, the rider will still be in the list so in that case the
	 * event should procede as normal to allow regular MC dismounting.
	 */
	@SubscribeEvent
	public static void on(EntityMountEvent event){
		if(event.getEntityBeingMounted() instanceof EntityMultipartB_Existing  && event.isDismounting() && event.getEntityMounting() != null && !event.getWorldObj().isRemote && !event.getEntityBeingMounted().isDead){
			EntityMultipartB_Existing multipart = (EntityMultipartB_Existing) event.getEntityBeingMounted();
			if(!multipart.didRiderClickSeat){
				if(multipart.riderIDToDismountThisTick == -1){
					multipart.riderIDToDismountThisTick = event.getEntityMounting().getEntityId();
					event.setCanceled(true);
				}else{
					multipart.riderIDToDismountThisTick = -1;
					event.setCanceled(false);
				}
			}
			multipart.didRiderClickSeat = false;
		}
	 }
	
	@Override
	public void updatePassenger(Entity passenger){
		PartSeat seat = this.getSeatForRider(passenger);
		if(seat != null){
			Vec3d posVec = RotationSystem.getRotatedPoint(seat.offset.addVector(0, -seat.getHeight()/2F + passenger.getYOffset() + passenger.height, 0), this.rotationPitch, this.rotationYaw, this.rotationRoll);
			passenger.setPosition(this.posX + posVec.xCoord, this.posY + posVec.yCoord - passenger.height, this.posZ + posVec.zCoord);
			passenger.motionX = this.motionX;
			passenger.motionY = this.motionY;
			passenger.motionZ = this.motionZ;
		}else if(pack != null && !this.riderSeatPositions.isEmpty()){
			Double[] seatLocation = this.riderSeatPositions.get(this.getPassengers().indexOf(passenger));
			APart part = getPartAtLocation(seatLocation[0], seatLocation[1], seatLocation[2]);
			if(part instanceof PartSeat){
				riderSeats.put(passenger.getEntityId(), (PartSeat) part);
			}else{
				MTS.MTSLog.error("ERROR: NO SEAT FOUND WHEN LINKING RIDER TO SEAT IN MULTIPART!");
				if(!worldObj.isRemote){
					passenger.dismountRidingEntity();
				}
				return;
			}
		}
	}
	
	@Override
	public void addPart(APart part, boolean ignoreCollision){
		if(!ignoreCollision){
			//Check if we are colliding and adjust roll before letting part addition continue.
			//This is needed as the master multipart system doesn't know about roll.
			if(part.isPartCollidingWithBlocks(Vec3d.ZERO)){
				this.rotationRoll = 0;
			}
		}
		super.addPart(part, ignoreCollision);
	}
	
    @Override
    public boolean shouldRenderInPass(int pass){
        //Need to render in pass 1 to render transparent things in the world like light beams.
    	return true;
    }
    
	@Override
	public boolean canBeCollidedWith(){
		//This gets overridden to allow players to interact with this multipart.
		return true;
	}
	
    /**
     * Checks to see if the entity passed in could have hit a part.
     * Is determined by the rotation of the entity and distance from parts.
     * If a part is found to be hit-able, it is returned.  Else null is returned.
     */
	public APart getHitPart(Entity entity){
		Vec3d lookVec = entity.getLook(1.0F);
		Vec3d hitVec = entity.getPositionVector().addVector(0, entity.getEyeHeight(), 0);
		for(float f=1.0F; f<4.0F; f += 0.1F){
			for(APart part : this.getMultipartParts()){
				if(part.getAABBWithOffset(Vec3d.ZERO).isVecInside(hitVec)){
					return part;
				}
			}
			hitVec = hitVec.addVector(lookVec.xCoord*0.1F, lookVec.yCoord*0.1F, lookVec.zCoord*0.1F);
		}
		return null;
	}
	
    /**
     * Adds a rider to this multipart and sets their seat.
     * All riders MUST be added through this method.
     */
	public void setRiderInSeat(Entity rider, PartSeat seat){
		this.didRiderClickSeat = true;
		riderSeats.put(rider.getEntityId(), seat);
		rider.startRiding(this, true);
		if(!worldObj.isRemote){
			MTS.MTSNet.sendToAll(new PacketPartSeatRiderChange(seat, rider, true));
		}
	}
	
	/**
     * Removes the rider safely from this multipart.  Returns true if removal was good, false if it failed.
     */
	public boolean removeRiderFromSeat(Entity rider, PartSeat seat){
		try{
			ObfuscationReflectionHelper.setPrivateValue(Entity.class, rider, null, ridingEntityNames);
		}catch (Exception e){
			MTS.MTSLog.fatal("ERROR IN MULTIPART RIDER REFLECTION!");
			return false;
   	    }
		
		riderSeats.remove(rider.getEntityId());
		this.removePassenger(rider);
		rider.setSneaking(false);
		if(!worldObj.isRemote){
			Vec3d placePosition;
			PackPart packPart = this.getPackDefForLocation(seat.offset.xCoord, seat.offset.yCoord, seat.offset.zCoord);
			if(packPart.dismountPos != null){
				placePosition = RotationSystem.getRotatedPoint(new Vec3d(packPart.dismountPos[0], packPart.dismountPos[1], packPart.dismountPos[2]), this.rotationPitch, this.rotationYaw, this.rotationRoll).add(this.getPositionVector());
			}else{
				placePosition = RotationSystem.getRotatedPoint(seat.offset.addVector(seat.offset.xCoord > 0 ? 2 : -2, 0, 0), this.rotationPitch, this.rotationYaw, this.rotationRoll).add(this.getPositionVector());	
			}
			AxisAlignedBB collisionDetectionBox = new AxisAlignedBB(new BlockPos(placePosition));
			if(!worldObj.collidesWithAnyBlock(collisionDetectionBox)){
				rider.setPositionAndRotation(placePosition.xCoord, collisionDetectionBox.minY, placePosition.zCoord, rider.rotationYaw, rider.rotationPitch);
			}else if(rider instanceof EntityLivingBase){
				((EntityLivingBase) rider).dismountEntity(this);
			}
			MTS.MTSNet.sendToAll(new PacketPartSeatRiderChange(seat, rider, false));
			return true;
		}
		return false;
	}
	
	public Entity getRiderForSeat(PartSeat seat){
		return riderSeats.inverse().containsKey(seat) ? worldObj.getEntityByID(riderSeats.inverse().get(seat)) : null;
	}
	
	public PartSeat getSeatForRider(Entity rider){
		return riderSeats.get(rider.getEntityId());
	}
	
	/**
	 * Call this to remove this multipart.  Set crashed to true to indicate a crash and damage players.
	 * Explosions may not occur in crashes depending on config settings or a lack of fuel or explodable cargo.
	 */
	public void destroyAtPosition(double x, double y, double z, boolean crashed){
		this.setDead();
		//If we are destroyed on the server, make sure to drop all the parts we have on the ground.
		if(!worldObj.isRemote && !crashed){
			for(APart part : getMultipartParts()){
				if(part.getItemForPart() != null){
					ItemStack partStack = new ItemStack(part.getItemForPart());
					NBTTagCompound stackTag = part.getPartNBTTag();
					if(stackTag != null){
						partStack.setTagCompound(stackTag);
					}
					worldObj.spawnEntityInWorld(new EntityItem(worldObj, part.partPos.xCoord, part.partPos.yCoord, part.partPos.zCoord, partStack));
				}
			}
		}
	}
	
	protected float getCurrentMass(){
		int currentMass = pack.general.emptyMass;
		for(APart part : this.getMultipartParts()){
			if(part instanceof PartCrate){
				currentMass += calculateInventoryWeight(((PartCrate) part).crateInventory);
			}else{
				currentMass += 50;
			}
		}
		
		//Add passenger inventory mass as well.
		for(Entity passenger : this.getPassengers()){
			if(passenger instanceof EntityPlayer){
				currentMass += 100 + calculateInventoryWeight(((EntityPlayer) passenger).inventory);
			}else{
				currentMass += 100;
			}
		}
		return currentMass;
	}
	
	/**Calculates the weight of the inventory passed in.  Used for physics calculations.
	 */
	private static float calculateInventoryWeight(IInventory inventory){
		float weight = 0;
		for(int i=0; i<inventory.getSizeInventory(); ++i){
			ItemStack stack = inventory.getStackInSlot(i);
			if(stack != null){
				weight += 1.2F*stack.stackSize/stack.getMaxStackSize()*(ConfigSystem.getStringConfig("HeavyItems").contains(stack.getItem().getUnlocalizedName().substring(5)) ? 2 : 1);
			}
		}
		return weight;
	}
	
	protected void updateHeadingVec(){
        double f1 = Math.cos(-this.rotationYaw * 0.017453292F - (float)Math.PI);
        double f2 = Math.sin(-this.rotationYaw * 0.017453292F - (float)Math.PI);
        double f3 = -Math.cos(-this.rotationPitch * 0.017453292F);
        double f4 = Math.sin(-this.rotationPitch * 0.017453292F);
        headingVec = new Vec3d((f2 * f3), f4, (f1 * f3));
   	}
	
	/**
	 * Method block for basic properties like weight and vectors.
	 * This should be used by all multiparts to define all properties before
	 * calculating anything.
	 */
	protected abstract void getBasicProperties();
	
	/**
	 * Returns whatever the steering angle is.
	 * Used for rendering and possibly other things.
	 */
	public abstract float getSteerAngle();
	
    @Override
	public void readFromNBT(NBTTagCompound tagCompound){
		super.readFromNBT(tagCompound);
		this.locked=tagCompound.getBoolean("locked");
		this.brokenWindows=tagCompound.getByte("brokenWindows");
		this.rotationRoll=tagCompound.getFloat("rotationRoll");
		this.ownerName=tagCompound.getString("ownerName");
		this.displayText=tagCompound.getString("displayText");
		
		this.riderSeatPositions.clear();
		while(tagCompound.hasKey("Seat" + String.valueOf(riderSeatPositions.size()) + "0")){
			Double[] seatPosition = new Double[3];
			seatPosition[0] = tagCompound.getDouble("Seat" + String.valueOf(riderSeatPositions.size()) + "0");
			seatPosition[1] = tagCompound.getDouble("Seat" + String.valueOf(riderSeatPositions.size()) + "1");
			seatPosition[2] = tagCompound.getDouble("Seat" + String.valueOf(riderSeatPositions.size()) + "2");
			riderSeatPositions.add(seatPosition);
		}
	}
    
	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound tagCompound){
		super.writeToNBT(tagCompound);
		tagCompound.setBoolean("locked", this.locked);
		tagCompound.setByte("brokenWindows", this.brokenWindows);
		tagCompound.setFloat("rotationRoll", this.rotationRoll);
		tagCompound.setString("ownerName", this.ownerName);
		tagCompound.setString("displayText", this.displayText);
		
		//Correlate the order of passengers in the rider list with their location to save it to NBT.
		//That way riders don't get moved to other seats on world save/load.
		for(byte i=0; i<this.getPassengers().size(); ++i){
			Entity rider = this.getPassengers().get(i);
			PartSeat seat = this.getSeatForRider(rider);
			if(seat != null){
				tagCompound.setDouble("Seat" + String.valueOf(i) + "0", seat.offset.xCoord);
				tagCompound.setDouble("Seat" + String.valueOf(i) + "1", seat.offset.yCoord);
				tagCompound.setDouble("Seat" + String.valueOf(i) + "2", seat.offset.zCoord);
			}
		}
		return tagCompound;
	}
}
