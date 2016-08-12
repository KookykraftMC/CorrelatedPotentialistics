package io.github.elytra.copo.client;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.BitSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.imageio.ImageIO;

import org.lwjgl.opengl.GL11;

import com.google.common.collect.Lists;
import io.github.elytra.copo.CoPo;
import io.github.elytra.copo.Proxy;
import io.github.elytra.copo.client.gui.GuiAbortRetryFail;
import io.github.elytra.copo.client.gui.GuiFakeReboot;
import io.github.elytra.copo.client.gui.GuiGlitchedMainMenu;
import io.github.elytra.copo.client.render.entity.RenderAutomaton;
import io.github.elytra.copo.client.render.entity.RenderThrownItem;
import io.github.elytra.copo.client.render.tile.RenderController;
import io.github.elytra.copo.client.render.tile.RenderDriveBay;
import io.github.elytra.copo.client.render.tile.RenderVT;
import io.github.elytra.copo.client.render.tile.RenderWirelessReceiver;
import io.github.elytra.copo.client.render.tile.RenderWirelessTransmitter;
import io.github.elytra.copo.entity.EntityAutomaton;
import io.github.elytra.copo.entity.EntityThrownItem;
import io.github.elytra.copo.item.ItemDrive;
import io.github.elytra.copo.item.ItemKeycard;
import io.github.elytra.copo.item.ItemMisc;
import io.github.elytra.copo.tile.TileEntityController;
import io.github.elytra.copo.tile.TileEntityDriveBay;
import io.github.elytra.copo.tile.TileEntityVT;
import io.github.elytra.copo.tile.TileEntityWirelessReceiver;
import io.github.elytra.copo.tile.TileEntityWirelessTransmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.Sound;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundList;
import net.minecraft.client.audio.Sound.Type;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.event.sound.SoundLoadEvent;
import net.minecraftforge.client.event.sound.SoundSetupEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import paulscode.sound.SoundSystemConfig;
import paulscode.sound.SoundSystemException;
import paulscode.sound.codecs.CodecIBXM;

public class ClientProxy extends Proxy {
	public static float ticks = 0;
	
	public static int glitchTicks = -1;
	private BitSet glitchJpeg;
	private int jpegTexture = -1;
	private Random rand = new Random();
	
	private Future<BufferedImage> corruptionFuture;
	private ExecutorService jpegCorruptor = Executors.newFixedThreadPool(1, (r) -> new Thread(r, "JPEG Corruption Thread"));
	private Callable<BufferedImage> jpegCorruptionTask = () -> {
		int tries = 0;
		while (true) {
			tries++;
			if (tries > 20) {
				return null;
			}
			int idx = rand.nextInt(glitchJpeg.size());
			glitchJpeg.flip(idx);
			try {
				BufferedImage jpeg = ImageIO.read(new ByteArrayInputStream(glitchJpeg.toByteArray()));
				return jpeg;
			} catch (IOException e1) {
				glitchJpeg.flip(idx);
				continue;
			}
		}
	};
	
	@SuppressWarnings("deprecation")
	@Override
	public void preInit() {
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityController.class, new RenderController());
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityDriveBay.class, new RenderDriveBay());
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityVT.class, new RenderVT());
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityWirelessReceiver.class, new RenderWirelessReceiver());
		ClientRegistry.bindTileEntitySpecialRenderer(TileEntityWirelessTransmitter.class, new RenderWirelessTransmitter());
		
		ForgeHooksClient.registerTESRItemStack(Item.getItemFromBlock(CoPo.wireless_endpoint), 0, TileEntityWirelessReceiver.class);
		ForgeHooksClient.registerTESRItemStack(Item.getItemFromBlock(CoPo.wireless_endpoint), 1, TileEntityWirelessTransmitter.class);
		
		RenderingRegistry.registerEntityRenderingHandler(EntityThrownItem.class, (rm) -> new RenderThrownItem(rm, Minecraft.getMinecraft().getRenderItem()));
		RenderingRegistry.registerEntityRenderingHandler(EntityAutomaton.class, RenderAutomaton::new);
		
		MinecraftForge.EVENT_BUS.register(this);

		int idx = 0;
		for (String s : ItemMisc.items) {
			ModelLoader.setCustomModelResourceLocation(CoPo.misc, idx++, new ModelResourceLocation(new ResourceLocation("correlatedpotentialistics", s), "inventory"));
		}
		idx = 0;
		for (String s : ItemKeycard.colors) {
			ModelLoader.setCustomModelResourceLocation(CoPo.keycard, idx++, new ModelResourceLocation(new ResourceLocation("correlatedpotentialistics", "keycard_"+s), "inventory"));
		}
	}
	@Override
	public void postInit() {
		Minecraft.getMinecraft().getItemColors().registerItemColorHandler(new IItemColor() {
			@Override
			@SuppressWarnings("fallthrough")
			public int getColorFromItemstack(ItemStack stack, int tintIndex) {
				if (stack == null || !(stack.getItem() instanceof ItemDrive)) return -1;
				ItemDrive id = (ItemDrive)stack.getItem();
				if (tintIndex == 1) {
					return id.getFullnessColor(stack);
				} else if (tintIndex == 2) {
					return id.getTierColor(stack);
				} else if (tintIndex == 3) {
					switch (id.getPartitioningMode(stack)) {
						case NONE:
							return 0x00FFAA;
						case WHITELIST:
							return 0xFFFFFF;
					}
				} else if (tintIndex >= 4 && tintIndex <= 6) {
					int uncolored;
					if (stack.getItemDamage() == 4) {
						uncolored = 0;
					} else {
						uncolored = 0x555555;
					}

					int left = uncolored;
					int middle = uncolored;
					int right = uncolored;
					switch (id.getPriority(stack)) {
						case HIGHEST:
							right = 0xFF0000;
						case HIGHER:
							middle = 0xFF0000;
						case HIGH:
							left = 0xFF0000;
							break;
						case LOWEST:
							left = 0x00FF00;
						case LOWER:
							middle = 0x00FF00;
						case LOW:
							right = 0x00FF00;
							break;
						default:
							break;
					}
					if (tintIndex == 4) {
						return left;
					} else if (tintIndex == 5) {
						return middle;
					} else if (tintIndex == 6) {
						return right;
					}
				}
				return id.getBaseColor(stack);
			}
			
		}, CoPo.drive);
	}
	@Override
	public void registerItemModel(Item item, int variants) {
		ResourceLocation loc = Item.REGISTRY.getNameForObject(item);
		if (variants < -1) {
			variants = (variants*-1)-1;
			loc = new ResourceLocation("correlatedpotentialistics", "tesrstack");
		}
		if (variants == -1) {
			List<ItemStack> li = Lists.newArrayList();
			item.getSubItems(item, CoPo.creativeTab, li);
			for (ItemStack is : li) {
				ModelLoader.setCustomModelResourceLocation(item, is.getItemDamage(), new ModelResourceLocation(loc, "inventory"));
			}
		} else if (variants == 0) {
			ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(loc, "inventory"));
		} else if (variants > 0) {
			for (int i = 0; i < variants; i++) {
				ModelLoader.setCustomModelResourceLocation(item, i, new ModelResourceLocation(loc, "inventory"+i));
			}
		}
	}
	@Override
	public void weldthrowerTick(EntityPlayer player) {
		Vec3d look = player.getLookVec();
		Vec3d right = look.rotateYaw(-90);
		double dist = 0.5;
		double gap = 1;
		double fuzz = 0.05;
		look.rotateYaw(20);
		for (int i = 0; i < 5; i++) {
			Random rand = player.worldObj.rand;
			ParticleWeldthrower dust = new ParticleWeldthrower(player.worldObj,
					player.posX+(right.xCoord*dist)+(look.xCoord*gap),
					player.posY+(player.getEyeHeight()-0.1)+(right.yCoord*dist)+(look.yCoord*gap),
					player.posZ+(right.zCoord*dist)+(look.zCoord*gap), 1);
			dust.setRBGColorF(0, 0.9725490196078431f-(rand.nextFloat()/5), 0.8235294117647059f-(rand.nextFloat()/5));
			dust.setMotion(look.xCoord+(rand.nextGaussian()*fuzz), look.yCoord+(rand.nextGaussian()*fuzz), look.zCoord+(rand.nextGaussian()*fuzz));
			Minecraft.getMinecraft().effectRenderer.addEffect(dust);
		}
	}
	@Override
	public void weldthrowerHeal(EntityAutomaton ent) {
		for (int i = 0; i < 5; i++) {
			Random rand = ent.worldObj.rand;
			ParticleWeldthrower dust = new ParticleWeldthrower(ent.worldObj,
					ent.posX+(rand.nextGaussian()*0.2),
					ent.posY+(rand.nextGaussian()*0.2),
					ent.posZ+(rand.nextGaussian()*0.2), 1);
			dust.setRBGColorF(0, 0.9725490196078431f-(rand.nextFloat()/5), 0.8235294117647059f-(rand.nextFloat()/5));
			Minecraft.getMinecraft().effectRenderer.addEffect(dust);
		}
	}
	@Override
	public void smokeTick(EntityAutomaton ent) {
		if (Minecraft.getMinecraft().theWorld == null || Minecraft.getMinecraft().theWorld.getTotalWorldTime() < 10) return;
		Random rand = ent.worldObj.rand;
		for (int i = 0; i < ent.getMaxHealth()-ent.getHealth(); i++) {
			if (rand.nextInt(5) == 0) {
				Minecraft.getMinecraft().effectRenderer.spawnEffectParticle(EnumParticleTypes.SMOKE_NORMAL.getParticleID(),
						ent.posX+(rand.nextGaussian()*0.2),
						ent.posY+(rand.nextGaussian()*0.2),
						ent.posZ+(rand.nextGaussian()*0.2),
						0, 0, 0);
			}
		}
		if (ent.getHealth() < ent.getMaxHealth()/2 && rand.nextInt(10) == 0) {
			Minecraft.getMinecraft().effectRenderer.spawnEffectParticle(EnumParticleTypes.LAVA.getParticleID(),
					ent.posX,
					ent.posY,
					ent.posZ,
					0, 0, 0);
		}
	}
	@SubscribeEvent
	public void onClientTick(ClientTickEvent e) {
		if (e.phase == Phase.START) {
			ticks++;
			if (glitchTicks > -1 && Minecraft.getMinecraft().theWorld == null) {
				glitchTicks = -1;
			}
			if (glitchTicks > -1 && jpegTexture != -1 && !Minecraft.getMinecraft().isGamePaused()) {
				glitchTicks++;
				if (glitchTicks >= 240) {
					glitchTicks = -1;
					Minecraft.getMinecraft().getSoundHandler().stopSounds();
					if (Minecraft.getMinecraft().thePlayer != null && !Minecraft.getMinecraft().thePlayer.isDead) {
						Minecraft.getMinecraft().displayGuiScreen(new GuiFakeReboot());
					}
				}
				if (glitchJpeg != null && corruptionFuture == null) {
					corruptionFuture = jpegCorruptor.submit(jpegCorruptionTask);
				}
			}
		}
	}
	@SubscribeEvent(priority=EventPriority.LOWEST)
	public void onRenderTick(RenderTickEvent e) {
		if (e.phase == Phase.START) {
			ticks = ((int)ticks)+e.renderTickTime;
		} else if (e.phase == Phase.END) {
			if (Minecraft.getMinecraft().currentScreen == null || Minecraft.getMinecraft().currentScreen instanceof GuiGlitchedMainMenu) {
				drawGlitch();
			}
		}
	}
	@SubscribeEvent(priority=EventPriority.LOWEST)
	public void onGuiDraw(GuiScreenEvent.DrawScreenEvent.Pre e) {
		if (!(e.getGui() instanceof GuiGlitchedMainMenu)) drawGlitch();
	}
	@SubscribeEvent
	public void onSoundSetup(SoundSetupEvent e) throws SoundSystemException {
		SoundSystemConfig.setCodec("xm", CodecIBXM.class);
		SoundSystemConfig.setCodec("s3m", CodecIBXM.class);
		SoundSystemConfig.setCodec("mod", CodecIBXM.class);
	}
	@SubscribeEvent
	public void onSoundPlay(PlaySoundEvent e) {
		if (glitchTicks >= 0 || Minecraft.getMinecraft().currentScreen instanceof GuiFakeReboot || Minecraft.getMinecraft().currentScreen instanceof GuiGlitchedMainMenu) {
			if (e.getSound() != null && !(e.getSound().getSoundLocation().getResourceDomain().equals("correlatedpotentialistics"))
					&& !(e.getSound().getSoundLocation().getResourcePath().contains("glitch"))) {
				e.setResultSound(null);
			}
		}
	}
	@SubscribeEvent
	public void onGuiOpen(GuiOpenEvent e) {
		if (glitchTicks >= 0 && e.getGui() != null && !(e.getGui() instanceof GuiIngameMenu)) {
			e.setCanceled(true);
		} else if (Minecraft.getMinecraft().theWorld != null
				&& !Minecraft.getMinecraft().theWorld.getWorldInfo().isHardcoreModeEnabled()
				&& e.getGui() instanceof GuiGameOver
				&& !(e.getGui() instanceof GuiAbortRetryFail)) {
			if (Minecraft.getMinecraft().thePlayer != null && Minecraft.getMinecraft().thePlayer.dimension == CoPo.limboDimId) {
				e.setGui(new GuiAbortRetryFail(ReflectionHelper.getPrivateValue(GuiGameOver.class, (GuiGameOver)e.getGui(), "field_184871_f", "causeOfDeath", "f")));
			}
		}
	}
	private void drawGlitch() {
		if (glitchTicks == 0) {
			if (jpegTexture != -1) {
				TextureUtil.deleteTexture(jpegTexture);
				jpegTexture = -1;
			}
			jpegTexture = TextureUtil.glGenTextures();
			BufferedImage raw = ScreenShotHelper.createScreenshot(Minecraft.getMinecraft().displayWidth, Minecraft.getMinecraft().displayHeight, Minecraft.getMinecraft().getFramebuffer());
			Image scaled = raw.getScaledInstance(854, 480, Image.SCALE_AREA_AVERAGING);
			BufferedImage screenshot = new BufferedImage(854, 480, BufferedImage.TYPE_3BYTE_BGR);
			Graphics g = screenshot.createGraphics();
			g.drawImage(scaled, 0, 0, null);
			g.dispose();
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try {
				if (corruptionFuture != null) {
					corruptionFuture.cancel(true);
					corruptionFuture = null;
				}
				ImageIO.write(screenshot, "JPEG", baos);
				glitchJpeg = BitSet.valueOf(baos.toByteArray());
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		} else if (glitchTicks == -1) {
			if (jpegTexture != -1) {
				if (corruptionFuture != null) {
					corruptionFuture.cancel(true);
					corruptionFuture = null;
				}
				TextureUtil.deleteTexture(jpegTexture);
				jpegTexture = -1;
			}
		} else if (glitchTicks > 0) {
			if (corruptionFuture != null && corruptionFuture.isDone()) {
				try {
					TextureUtil.uploadTextureImage(jpegTexture, corruptionFuture.get());
					corruptionFuture = null;
				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (ExecutionException e) {
					e.printStackTrace();
				}
			}
			Tessellator tess = Tessellator.getInstance();
			VertexBuffer vb = tess.getBuffer();
			GlStateManager.color(1, 1, 1);
			GlStateManager.disableDepth();
			GlStateManager.bindTexture(jpegTexture);
			ScaledResolution res = new ScaledResolution(Minecraft.getMinecraft());
			vb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
			vb.pos(0, res.getScaledHeight(), 0).tex(0, 1).endVertex();
			vb.pos(res.getScaledWidth(), res.getScaledHeight(), 0).tex(1, 1).endVertex();
			vb.pos(res.getScaledWidth(), 0, 0).tex(1, 0).endVertex();
			vb.pos(0, 0, 0).tex(0, 0).endVertex();
			tess.draw();
			GlStateManager.enableDepth();
		}
	}
	@SubscribeEvent
	public void onStitch(TextureStitchEvent.Pre e) {
		e.getMap().registerSprite(new ResourceLocation("correlatedpotentialistics", "blocks/wireless_endpoint_error"));
		e.getMap().registerSprite(new ResourceLocation("correlatedpotentialistics", "blocks/wireless_endpoint_linked"));
	}
	@SubscribeEvent
	public void onSoundLoad(SoundLoadEvent e) {
		for (String snd : CoPo.records) {
			String file = snd.substring(0, snd.indexOf('.'));
			String ext = snd.substring(snd.indexOf('.')+1);
			Sound danslarue = new Sound("correlatedpotentialistics:"+file, 1, 1, 1, Type.FILE, true) {
				@Override
				public ResourceLocation getSoundAsOggLocation() {
					return new ResourceLocation(getSoundLocation().getResourceDomain(), "sounds/music/" + getSoundLocation().getResourcePath() + "." + ext);
				}
			};
			SoundList li = new SoundList(Lists.newArrayList(danslarue), false, null);
			Method m = ReflectionHelper.findMethod(SoundHandler.class, e.getManager().sndHandler, new String[] {"func_147693_a", "loadSoundResource", "a"}, ResourceLocation.class, SoundList.class);
			try {
				m.invoke(e.getManager().sndHandler, new ResourceLocation("correlatedpotentialistics", file), li);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
}