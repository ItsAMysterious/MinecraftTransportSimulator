package minecrafttransportsimulator.guis;

import java.awt.Color;
import java.io.IOException;

import org.lwjgl.opengl.GL11;

import minecrafttransportsimulator.MTS;
import minecrafttransportsimulator.multipart.main.EntityMultipartE_Vehicle;
import minecrafttransportsimulator.multipart.main.EntityMultipartE_Vehicle.LightTypes;
import minecrafttransportsimulator.multipart.main.EntityMultipartF_Plane;
import minecrafttransportsimulator.multipart.parts.APartEngine;
import minecrafttransportsimulator.packets.control.LightPacket;
import minecrafttransportsimulator.packets.control.ReverseThrustPacket;
import minecrafttransportsimulator.packets.control.TrimPacket;
import minecrafttransportsimulator.packets.parts.PacketPartEngineSignal;
import minecrafttransportsimulator.packets.parts.PacketPartEngineSignal.PacketEngineTypes;
import minecrafttransportsimulator.rendering.RenderHUD;
import minecrafttransportsimulator.rendering.RenderInstruments;
import minecrafttransportsimulator.rendering.RenderMultipart;
import minecrafttransportsimulator.systems.CameraSystem;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

/**A GUI/control system hybrid, this takes the place of the HUD when called up.
 * Used for controlling engines, lights, trim, and other things.
 * 
 * @author don_bruce
 */
public class GUIPanelAircraft extends GuiScreen{
	private static final ResourceLocation toggleOn = new ResourceLocation("textures/blocks/redstone_lamp_on.png");
	private static final ResourceLocation toggleOff = new ResourceLocation("textures/blocks/redstone_lamp_off.png");
	private static final LightTypes[] lights = new LightTypes[]{LightTypes.NAVIGATIONLIGHT, LightTypes.STROBELIGHT, LightTypes.TAXILIGHT, LightTypes.LANDINGLIGHT};
	private static final String[] lightText = new String[]{I18n.format("gui.panel.navigationlights"), I18n.format("gui.panel.strobelights"), I18n.format("gui.panel.taxilights"), I18n.format("gui.panel.landinglights")};
	
	private final EntityMultipartE_Vehicle aircraft;
	private final APartEngine[] engines;
	private final boolean[] hasLight;
	private final int[][] lightButtonCoords;
	private final int[][] magnetoButtonCoords;
	private final int[][] starterButtonCoords;
	private final int[] reverseButtonCoords;
	
	private GuiButton aileronTrimUpButton;
	private GuiButton aileronTrimDownButton;
	private GuiButton elevatorTrimUpButton;
	private GuiButton elevatorTrimDownButton;
	private GuiButton rudderTrimUpButton;
	private GuiButton rudderTrimDownButton;
	
	private byte lastEngineStarted;
	private GuiButton lastButtonPressed;
	
	public GUIPanelAircraft(EntityMultipartE_Vehicle aircraft){
		super();
		this.aircraft = aircraft;
		engines = new APartEngine[aircraft.getNumberEngineBays()];
		for(byte i=0; i<engines.length; ++i){
			engines[i] = aircraft.getEngineByNumber(i);
		}
		hasLight = new boolean[4];
		lightButtonCoords = new int[4][4];
		for(byte i=0; i<lightButtonCoords.length; ++i){
			lightButtonCoords[i] = new int[]{16, 48, 280+50*i+32, 280+50*i};
		}
		magnetoButtonCoords = new int[engines.length][4];
		for(byte i=0; i<magnetoButtonCoords.length; ++i){
			magnetoButtonCoords[i] = new int[]{64, 96, 280+50*i+32, 280+50*i};
		}
		starterButtonCoords = new int[engines.length][4];
		for(byte i=0; i<starterButtonCoords.length; ++i){
			starterButtonCoords[i] = new int[]{96, 128, 280+50*i+32, 280+50*i};
		}
		reverseButtonCoords = new int[]{160, 160+32, 430+32, 430};
	}
	
	@Override
	public void initGui(){
		for(byte i=0; i<lightButtonCoords.length; ++i){
			hasLight[i] = RenderMultipart.doesMultipartHaveLight(aircraft, lights[i]);
		}
		buttonList.add(aileronTrimUpButton = new GuiButton(0, 90, 175, 20, 20, "<"));
		buttonList.add(aileronTrimDownButton = new GuiButton(0, 110, 175, 20, 20, ">"));
		buttonList.add(elevatorTrimUpButton = new GuiButton(0, 90, 206, 20, 20, "/\\"));
		buttonList.add(elevatorTrimDownButton = new GuiButton(0, 110, 206, 20, 20, "\\/"));
		buttonList.add(rudderTrimUpButton = new GuiButton(0, 90, 237, 20, 20, "<"));
		buttonList.add(rudderTrimDownButton = new GuiButton(0, 110, 237, 20, 20, ">"));
	}
	
	@Override
    public void drawScreen(int mouseX, int mouseY, float renderPartialTicks){
		//The true span of this GUI is from height-112 to height.
		//This is because it's on the bottom of the screen, or the highest height value.
		if(aircraft.isDead){
			mc.thePlayer.closeScreen();
			return;
		}
		
		//If the navigation lights are on, we want to light up the switches in the panel.
		//This is done in numerous places.
		final boolean lightsOn = RenderInstruments.lightsOn(aircraft);
		
		//Disable the main HUD overlay if it's active.
		CameraSystem.disableHUD = true;
		
		//Scale this HUD by the current screen ratio.
		GL11.glPushMatrix();
		GL11.glScalef(1.0F*this.width/RenderHUD.screenDefaultX, 1.0F*this.height/RenderHUD.screenDefaultY, 0);
		
		//Render the main backplate and instruments.
		//Note that while this is technically a GUI, the GUI parameter is for the instrument GUI.
		RenderHUD.drawAuxiliaryHUD(aircraft, width, height, false);
		
		//Render the 4 light buttons if we have lights for them.
		for(byte i=0; i<hasLight.length; ++i){
			if(hasLight[i]){
				drawRedstoneButton(lightButtonCoords[i], aircraft.isLightOn(lights[i]));
			}
		}
		
		//Render the engine buttons.
		//Check if an engine is present and render the status if so.
		//Otherwise just leave the lights off.
		for(byte i=0; i<magnetoButtonCoords.length; ++i){
			drawRedstoneButton(magnetoButtonCoords[i], engines[i] != null ? engines[i].state.magnetoOn : false);
			drawRedstoneButton(starterButtonCoords[i], engines[i] != null ? engines[i].state.esOn : false);
		}
		
		//Render the trim buttons.
		//Scale mouse position to the scaled GUI;
		mouseX = (int) (1.0F*mouseX/width*RenderHUD.screenDefaultX*20F/32F);
		mouseY = (int) (1.0F*mouseY/height*RenderHUD.screenDefaultY*20F/32F);
		GL11.glPushMatrix();
		GL11.glScalef(32F/20F, 32F/20F, 0);
		for(GuiButton button : buttonList){
			button.drawButton(mc, mouseX, mouseY);
		}
		GL11.glPopMatrix();
		
		//Render the reverse button if we have such a feature.
		if(aircraft instanceof EntityMultipartF_Plane){
			drawRedstoneButton(reverseButtonCoords, ((EntityMultipartF_Plane) aircraft).reverseThrust);
		}

		//Render light button text.
		for(byte i=0; i<lightButtonCoords.length; ++i){
			if(hasLight[i]){
				int textX = lightButtonCoords[i][0] + (lightButtonCoords[i][1] - lightButtonCoords[i][0])/2;
				int textY = lightButtonCoords[i][2] + 2; 
				fontRendererObj.drawString(lightText[i], textX - fontRendererObj.getStringWidth(lightText[i])/2, textY, lightsOn ? Color.WHITE.getRGB() : Color.BLACK.getRGB());
			}
		}
		
		//Render engine button text.
		for(byte i=0; i<magnetoButtonCoords.length; ++i){
			int textX = magnetoButtonCoords[i][0] + (magnetoButtonCoords[i][1] - magnetoButtonCoords[i][0])/2;
			int textY = magnetoButtonCoords[i][2] + 2;
			fontRendererObj.drawString(I18n.format("gui.panel.magneto"), textX - fontRendererObj.getStringWidth(I18n.format("gui.panel.magneto"))/2, textY, lightsOn ? Color.WHITE.getRGB() : Color.BLACK.getRGB());
			fontRendererObj.drawString(I18n.format("gui.panel.starter"), textX - fontRendererObj.getStringWidth(I18n.format("gui.panel.starter"))/2 + 32, textY, lightsOn ? Color.WHITE.getRGB() : Color.BLACK.getRGB());
		}
		
		//Render trim button text.
		fontRendererObj.drawString(I18n.format("gui.panel.trim_roll"), 176 - fontRendererObj.getStringWidth(I18n.format("gui.panel.trim_roll"))/2, 312 + 2, lightsOn ? Color.WHITE.getRGB() : Color.BLACK.getRGB());
		fontRendererObj.drawString(I18n.format("gui.panel.trim_pitch"), 176 - fontRendererObj.getStringWidth(I18n.format("gui.panel.trim_pitch"))/2, 362 + 2, lightsOn ? Color.WHITE.getRGB() : Color.BLACK.getRGB());
		fontRendererObj.drawString(I18n.format("gui.panel.trim_yaw"), 176 - fontRendererObj.getStringWidth(I18n.format("gui.panel.trim_yaw"))/2, 412 + 2, lightsOn ? Color.WHITE.getRGB() : Color.BLACK.getRGB());
		
		//Render the reverse button text if we have such a feature.
		if(aircraft instanceof EntityMultipartF_Plane){
			fontRendererObj.drawString(I18n.format("gui.panel.reverse"), 176 - fontRendererObj.getStringWidth(I18n.format("gui.panel.reverse"))/2, 462 + 2, lightsOn ? Color.WHITE.getRGB() : Color.BLACK.getRGB());
		}
		
		GL11.glPopMatrix();
	}
	
	private void drawRedstoneButton(int[] coords, boolean isOn){
		mc.getTextureManager().bindTexture(isOn ? toggleOn : toggleOff);
		GL11.glBegin(GL11.GL_QUADS);
		GL11.glTexCoord2f(0, 0);
		GL11.glVertex3d(coords[0], coords[3], 0);
		GL11.glTexCoord2f(0, 1);
		GL11.glVertex3d(coords[0], coords[2], 0);
		GL11.glTexCoord2f(1, 1);
		GL11.glVertex3d(coords[1], coords[2], 0);
		GL11.glTexCoord2f(1, 0);
		GL11.glVertex3d(coords[1], coords[3], 0);
		GL11.glEnd();
	}
	
	@Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException{
		//Scale clicks to the scaled GUI;
		mouseX = (int) (1.0F*mouseX/width*RenderHUD.screenDefaultX);
		mouseY = (int) (1.0F*mouseY/height*RenderHUD.screenDefaultY);
		lastEngineStarted = -1;
		if(mouseY < RenderHUD.screenDefaultY/2){
			mc.thePlayer.closeScreen();
		}else{
			//Check if a light button has been pressed.
			for(byte i=0; i<lightButtonCoords.length; ++i){
				if(mouseX > lightButtonCoords[i][0] && mouseX < lightButtonCoords[i][1] && mouseY < lightButtonCoords[i][2] && mouseY > lightButtonCoords[i][3]){
					if(hasLight[i]){
						MTS.MTSNet.sendToServer(new LightPacket(aircraft.getEntityId(), lights[i]));
					}
				}
			}
			
			//Check if a magneto button has been pressed.
			for(byte i=0; i<magnetoButtonCoords.length; ++i){
				if(engines[i] != null){
					if(mouseX > magnetoButtonCoords[i][0] && mouseX < magnetoButtonCoords[i][1] && mouseY < magnetoButtonCoords[i][2] && mouseY > magnetoButtonCoords[i][3]){
						MTS.MTSNet.sendToServer(new PacketPartEngineSignal(engines[i], engines[i].state.magnetoOn ? PacketEngineTypes.MAGNETO_OFF : PacketEngineTypes.MAGNETO_ON));
					}
				}
			}
			
			//Check if a starter button has been pressed.
			for(byte i=0; i<starterButtonCoords.length; ++i){
				if(engines[i] != null){
					if(mouseX > starterButtonCoords[i][0] && mouseX < starterButtonCoords[i][1] && mouseY < starterButtonCoords[i][2] && mouseY > starterButtonCoords[i][3]){
						if(!engines[i].state.esOn){
							MTS.MTSNet.sendToServer(new PacketPartEngineSignal(engines[i], PacketEngineTypes.ES_ON));
						}
						lastEngineStarted = i;
					}
				}
			}
			
			//Check if the reverse button was pressed.
			if(aircraft instanceof EntityMultipartF_Plane){
				if(mouseX > reverseButtonCoords[0] && mouseX < reverseButtonCoords[1] && mouseY < reverseButtonCoords[2] && mouseY > reverseButtonCoords[3]){
					MTS.MTSNet.sendToServer(new ReverseThrustPacket(aircraft.getEntityId(), !((EntityMultipartF_Plane) aircraft).reverseThrust));
					MTS.proxy.playSound(aircraft.getPositionVector(), MTS.MODID + ":stall_buzzer", 1.0F, 1.0F);
				}
			}
			
			//Check if a trim button has been pressed.
			lastButtonPressed = null;
			for(GuiButton button : buttonList){
				if(button.isMouseOver()){
					lastButtonPressed = button;
					return;
				}
			}
		}
	}
	
	@Override
	protected void mouseReleased(int mouseX, int mouseY, int actionType){
	    if(actionType == 0){
	    	if(lastEngineStarted != -1 && starterButtonCoords.length > 0){
	    		MTS.MTSNet.sendToServer(new PacketPartEngineSignal(engines[lastEngineStarted], PacketEngineTypes.ES_OFF));
	    	}
	    	lastButtonPressed = null;
	    }
	}
	
	@Override
	public void updateScreen(){
		super.updateScreen();
		if(lastButtonPressed != null){
			if(lastButtonPressed.equals(aileronTrimUpButton)){
				MTS.MTSNet.sendToServer(new TrimPacket(aircraft.getEntityId(), (byte) 0));
			}else if(lastButtonPressed.equals(aileronTrimDownButton)){
				MTS.MTSNet.sendToServer(new TrimPacket(aircraft.getEntityId(), (byte) 8));
			}else if(lastButtonPressed.equals(elevatorTrimUpButton)){
				MTS.MTSNet.sendToServer(new TrimPacket(aircraft.getEntityId(), (byte) 9));
			}else if(lastButtonPressed.equals(elevatorTrimDownButton)){
				MTS.MTSNet.sendToServer(new TrimPacket(aircraft.getEntityId(), (byte) 1));
			}else if(lastButtonPressed.equals(rudderTrimUpButton)){
				MTS.MTSNet.sendToServer(new TrimPacket(aircraft.getEntityId(), (byte) 2));
			}else if(lastButtonPressed.equals(rudderTrimDownButton)){
				MTS.MTSNet.sendToServer(new TrimPacket(aircraft.getEntityId(), (byte) 10));
			}
		}
	}
	
	@Override
	public void onGuiClosed(){
		CameraSystem.disableHUD = false;
	}
	
	@Override
	public boolean doesGuiPauseGame(){
		return false;
	}
	
	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException{
		if(keyCode == 1 || mc.gameSettings.keyBindInventory.isActiveAndMatches(keyCode) || mc.gameSettings.keyBindSneak.isActiveAndMatches(keyCode)){
			super.keyTyped('0', 1);
        }
	}
}
