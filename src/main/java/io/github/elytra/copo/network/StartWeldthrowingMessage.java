package io.github.elytra.copo.network;

import org.apache.commons.lang3.mutable.MutableInt;

import io.github.elytra.concrete.Message;
import io.github.elytra.concrete.NetworkContext;
import io.github.elytra.concrete.annotation.field.MarshalledAs;
import io.github.elytra.concrete.annotation.type.ReceivedOn;
import io.github.elytra.copo.CoPo;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@ReceivedOn(Side.CLIENT)
public class StartWeldthrowingMessage extends Message {
	@MarshalledAs("i32")
	public int entityId;
	
	public StartWeldthrowingMessage(NetworkContext ctx) {
		super(ctx);
	}
	public StartWeldthrowingMessage(int entityId) {
		super(CoPo.inst.network);
		this.entityId = entityId;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	protected void handle(EntityPlayer sender) {
		Entity e = Minecraft.getMinecraft().theWorld.getEntityByID(entityId);
		if (e instanceof EntityPlayer) {
			CoPo.weldthrower.weldthrowing.put((EntityPlayer)e, new MutableInt());
		}
	}

}
