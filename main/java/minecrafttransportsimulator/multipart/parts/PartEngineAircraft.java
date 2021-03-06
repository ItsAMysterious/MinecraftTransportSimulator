package minecrafttransportsimulator.multipart.parts;

import minecrafttransportsimulator.dataclasses.PackMultipartObject.PackPart;
import minecrafttransportsimulator.multipart.main.EntityMultipartD_Moving;
import minecrafttransportsimulator.multipart.main.EntityMultipartF_Plane;
import net.minecraft.nbt.NBTTagCompound;

public class PartEngineAircraft extends APartEngine{
	public PartPropeller propeller;
	private final EntityMultipartF_Plane plane;

	public PartEngineAircraft(EntityMultipartD_Moving multipart, PackPart packPart, String partName, NBTTagCompound dataTag){
		super(multipart, packPart, partName, dataTag);
		this.plane = (EntityMultipartF_Plane) multipart;
	}
	
	@Override
	public void updatePart(){
		super.updatePart();
		if(state.running){
			double engineTargetRPM = plane.throttle/100F*(pack.engine.maxRPM - engineStartRPM*1.25 - hours) + engineStartRPM*1.25;
			double engineRPMDifference = engineTargetRPM - RPM;
			if(propeller != null){
				double propellerFeedback = -(plane.velocity - 0.0254*Math.abs(propeller.currentPitch)*RPM*pack.engine.gearRatios[0]/60/20 - this.getPropellerForcePenalty())*25;
				RPM += engineRPMDifference/10 - propellerFeedback;
			}else{
				RPM += engineRPMDifference/10;
			}
		}else{
			if(propeller != null){
				RPM = Math.max(RPM + (plane.velocity - 0.0254*Math.abs(propeller.currentPitch)*RPM*pack.engine.gearRatios[0]/60/20)*15 - 10, 0);
			}else{
				RPM = Math.max(RPM - 10, 0);
			}
		}
		
		engineRotationLast = engineRotation;
		engineRotation += RPM*1200D/360D;
		engineDriveshaftRotationLast = engineDriveshaftRotation;
		engineDriveshaftRotation += RPM*1200D/360D*pack.engine.gearRatios[0];
	}
	
	@Override
	public void removePart(){
		super.removePart();
		if(propeller != null && !multipart.worldObj.isRemote){
			multipart.removePart(propeller, false);
		}
	}
	
	@Override
	protected void explodeEngine(){
		super.explodeEngine();
		if(this.propeller != null && !multipart.worldObj.isRemote){
			propeller.dropAsItem();
		}
	}
	
	public double getPropellerForcePenalty(){
		return (propeller.pack.propeller.diameter - 75)/(50*this.pack.engine.fuelConsumption - 15);
	}
}
