package minecrafttransportsimulator.packets.control;

import io.netty.buffer.ByteBuf;
import minecrafttransportsimulator.MTS;
import minecrafttransportsimulator.multipart.main.EntityMultipartF_Plane;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class FlapPacket implements IMessage{	
	private int id;
	private byte flapAngle;	

	public FlapPacket() { }
	
	public FlapPacket(int id, byte flapAngle){
		this.id=id;
		this.flapAngle=flapAngle;
	}
	
	@Override
	public void fromBytes(ByteBuf buf){
		this.id=buf.readInt();
		this.flapAngle=buf.readByte();
	}

	@Override
	public void toBytes(ByteBuf buf){
		buf.writeInt(this.id);
		buf.writeByte(this.flapAngle);
	}

	public static class Handler implements IMessageHandler<FlapPacket, IMessage>{
		public IMessage onMessage(final FlapPacket message, final MessageContext ctx){
			FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(new Runnable(){
				@Override
				public void run(){
					EntityMultipartF_Plane thisEntity;
					if(ctx.side.isServer()){
						thisEntity = (EntityMultipartF_Plane) ctx.getServerHandler().playerEntity.worldObj.getEntityByID(message.id);
					}else{
						thisEntity = (EntityMultipartF_Plane) Minecraft.getMinecraft().theWorld.getEntityByID(message.id);
					}
					if(thisEntity!=null){
						if(ctx.side.isServer()){
							if(message.flapAngle + thisEntity.flapAngle >= 0 && message.flapAngle + thisEntity.flapAngle <= 350){
								thisEntity.flapAngle += message.flapAngle;
								MTS.MTSNet.sendToAll(message);
							}
						}else{
							thisEntity.flapAngle += message.flapAngle;
						}
					}
				}
			});
			return null;
		}
	}
}